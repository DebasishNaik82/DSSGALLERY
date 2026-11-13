package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayer(
    uri: Uri,
    isCurrentPage: Boolean,
    showChrome: Boolean,
    onControllerVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnVisibilityChanged by rememberUpdatedState(onControllerVisibilityChanged)
    val currentIsCurrentPage by rememberUpdatedState(isCurrentPage)
    var playbackError by remember(uri) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            this.playWhenReady = isCurrentPage && playWhenReady
            repeatMode = Player.REPEAT_MODE_ONE

            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("ExoVideoPlayer", "Playback error for $uri", error)
                    playbackError = "Unable to play video: ${error.localizedMessage ?: "Unsupported format"}"
                }
            })
        }
    }

    LaunchedEffect(isCurrentPage, exoPlayer) {
        if (isCurrentPage) {
            if (playWhenReady) {
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            }
        } else {
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    var volume by remember(uri) { mutableFloatStateOf(1f) }
    val activity = remember(context) { context.findActivity() }
    val initialBrightness = remember(activity) {
        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
    }
    var brightness by remember(uri) { mutableFloatStateOf(initialBrightness) }
    var brightnessAdjusted by remember(uri) { mutableStateOf(false) }

    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        onDispose {
            if (brightnessAdjusted && activity != null) {
                runCatching {
                    val attrs = activity.window.attributes
                    attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity.window.attributes = attrs
                }
            }
        }
    }

    val chromeBottomOffsetPx = with(LocalDensity.current) { 104.dp.roundToPx() }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(showChrome, playerViewRef) {
        val pv = playerViewRef ?: return@LaunchedEffect
        if (showChrome && !pv.isControllerFullyVisible) {
            pv.showController()
        } else if (!showChrome && pv.isControllerFullyVisible) {
            pv.hideController()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("exo_video_player")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerShowTimeoutMs = 4000
                    controllerAutoShow = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            if (currentIsCurrentPage) {
                                currentOnVisibilityChanged(visibility == View.VISIBLE)
                            }
                        }
                    )
                    playerViewRef = this
                }
            },
            update = { pv ->
                if (pv.player !== exoPlayer) {
                    pv.player = exoPlayer
                }
                runCatching {
                    val bottomBar = pv.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)
                    val params = bottomBar?.layoutParams as? ViewGroup.MarginLayoutParams
                    if (params != null && params.bottomMargin != chromeBottomOffsetPx) {
                        params.bottomMargin = chromeBottomOffsetPx
                        bottomBar.layoutParams = params
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uri) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            val sensitivity = 0.004f
                            if (change.position.x < size.width / 2f) {
                                brightness = (brightness - dragAmount * sensitivity).coerceIn(0.05f, 1f)
                                brightnessAdjusted = true
                                activity?.let { act ->
                                    runCatching {
                                        val attrs = act.window.attributes
                                        attrs.screenBrightness = brightness
                                        act.window.attributes = attrs
                                    }
                                }
                                showBrightnessOverlay = true
                            } else {
                                volume = (volume - dragAmount * sensitivity).coerceIn(0f, 1f)
                                exoPlayer.volume = volume
                                showVolumeOverlay = true
                            }
                        },
                        onDragEnd = {
                            showVolumeOverlay = false
                            showBrightnessOverlay = false
                        },
                        onDragCancel = {
                            showVolumeOverlay = false
                            showBrightnessOverlay = false
                        }
                    )
                }
        )

        if (showVolumeOverlay) {
            VideoControlOverlay(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                value = (volume * 100).toInt(),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        if (showBrightnessOverlay) {
            VideoControlOverlay(
                icon = Icons.Default.BrightnessLow,
                value = (brightness * 100).toInt(),
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        playbackError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = Color.White,
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VideoControlOverlay(
    icon: ImageVector,
    value: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(32.dp)
            .background(Color.Black.copy(alpha = 0.55f), shape = CircleShape)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Text(text = "$value%", color = Color.White)
        }
    }
}
