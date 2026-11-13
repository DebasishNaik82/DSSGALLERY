package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ZoomableMediaPage(
    item: MediaItem,
    isCurrentPage: Boolean,
    showChrome: Boolean,
    onTapToggleChrome: () -> Unit,
    onVideoControllerVisibilityChanged: (Boolean) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnTap by rememberUpdatedState(onTapToggleChrome)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    val scaleAnim = remember(item.uri) { Animatable(1f) }
    val offsetAnim = remember(item.uri) { Animatable(Offset.Zero, Offset.VectorConverter) }

    LaunchedEffect(isCurrentPage, item.uri) {
        if (!isCurrentPage && (scaleAnim.value > 1f || offsetAnim.value != Offset.Zero)) {
            scaleAnim.snapTo(1f)
            offsetAnim.snapTo(Offset.Zero)
            currentOnZoomChanged(false)
        }
    }

    if (item.isVideo) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("viewer_video_page_${item.id}"),
            contentAlignment = Alignment.Center
        ) {
            ExoVideoPlayer(
                uri = item.uri,
                isCurrentPage = isCurrentPage,
                showChrome = showChrome,
                onControllerVisibilityChanged = onVideoControllerVisibilityChanged
            )
        }
    } else {
        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(item.uri) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.05f) {
                                currentOnZoomChanged(false)
                                launch { scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetAnim.animateTo(Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow)) }
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val maxX = (size.width * (targetScale - 1f)) / 2f
                                val maxY = (size.height * (targetScale - 1f)) / 2f
                                val targetOffset = Offset(
                                    x = ((center.x - tapOffset.x) * (targetScale - 1f)).coerceIn(-maxX, maxX),
                                    y = ((center.y - tapOffset.y) * (targetScale - 1f)).coerceIn(-maxY, maxY)
                                )
                                currentOnZoomChanged(true)
                                launch { scaleAnim.animateTo(targetScale, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetAnim.animateTo(targetOffset, spring(stiffness = Spring.StiffnessMediumLow)) }
                            }
                        }
                    }
                )
            }
            .pointerInput(item.uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoomAccum = 1f
                    var panAccum = Offset.Zero
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (canceled) break

                        val pressedCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val currentScale = scaleAnim.value

                        // Leave single-finger unzoomed horizontal/vertical swipes unconsumed so HorizontalPager can swipe cleanly
                        val shouldHandleTransform = pressedCount > 1 || currentScale > 1.02f
                        if (!shouldHandleTransform) {
                            continue
                        }

                        if (!pastTouchSlop) {
                            zoomAccum *= zoomChange
                            panAccum += panChange
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = abs(1f - zoomAccum) * centroidSize
                            val panMotion = panAccum.getDistance()
                            if (pressedCount > 1 || zoomMotion > touchSlop || panMotion > touchSlop) {
                                pastTouchSlop = true
                            }
                        }

                        if (pastTouchSlop) {
                            val newScale = (currentScale * zoomChange).coerceIn(1f, 4.5f)
                            val newOffset = if (newScale > 1.02f) {
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val scaleRatio = if (currentScale > 0f) newScale / currentScale else 1f
                                val focalAdjustedOffset = if (centroid != Offset.Unspecified && scaleRatio != 1f) {
                                    val focalPoint = centroid - center
                                    (offsetAnim.value - focalPoint) * scaleRatio + focalPoint
                                } else {
                                    offsetAnim.value
                                }
                                val maxX = (size.width * (newScale - 1f)) / 2f
                                val maxY = (size.height * (newScale - 1f)) / 2f
                                Offset(
                                    x = (focalAdjustedOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (focalAdjustedOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                Offset.Zero
                            }

                            val finalScale = if (newScale <= 1.02f) 1f else newScale
                            coroutineScope.launch {
                                scaleAnim.snapTo(finalScale)
                                offsetAnim.snapTo(newOffset)
                            }
                            currentOnZoomChanged(finalScale > 1.02f)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }

        Box(
            modifier = modifier
                .then(gestureModifier)
                .testTag("viewer_image_page_${item.id}"),
            contentAlignment = Alignment.Center
        ) {
            if (item.mimeType == "image/svg+xml") {
                SvgImage(
                    uri = item.uri,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleAnim.value
                            scaleY = scaleAnim.value
                            translationX = offsetAnim.value.x
                            translationY = offsetAnim.value.y
                        }
                )
            } else {
                AsyncImage(
                    model = remember(item.uri) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleAnim.value
                            scaleY = scaleAnim.value
                            translationX = offsetAnim.value.x
                            translationY = offsetAnim.value.y
                        }
                )
            }
        }
    }
}
