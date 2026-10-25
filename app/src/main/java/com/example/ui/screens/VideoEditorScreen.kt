package com.example.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.viewmodel.VideoEditorViewModel

import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    uri: Uri,
    onNavigateBack: () -> Unit,
    viewModel: VideoEditorViewModel = viewModel()
) {
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val editState by viewModel.videoEditState.collectAsStateWithLifecycle()
    
    var selectedTab by remember { mutableStateOf("Timeline") }

    LaunchedEffect(uri) {
        viewModel.loadVideo(uri)
    }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(exportResult) {
        if (exportResult is com.example.model.ExportResult.Success) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            VideoEditorTopBar(
                onBack = onNavigateBack,
                onSave = { viewModel.exportVideo() }
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // Player
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = viewModel.exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickableWithoutRipple { viewModel.togglePlayPause() }
            )

            // Bottom Controls Area
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Controls area based on tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedTab) {
                            "Timeline" -> {
                                if (duration > 0) {
                                    VideoTimeline(
                                        duration = duration,
                                        currentPosition = currentPosition,
                                        startTime = editState.startTimeMs,
                                        endTime = editState.endTimeMs,
                                        onTrimChange = { start, end -> viewModel.updateTrim(start, end) },
                                        onSeek = { viewModel.seekTo(it) }
                                    )
                                }
                            }
                            "Adjust" -> VideoAdjustmentControls(viewModel, editState)
                            "Filters" -> VideoFilterControls(viewModel, editState)
                            "Tools" -> VideoToolsControls(viewModel, editState, isPlaying)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        VideoEditorTabItem("Timeline", selectedTab == "Timeline") { selectedTab = "Timeline" }
                        VideoEditorTabItem("Adjust", selectedTab == "Adjust") { selectedTab = "Adjust" }
                        VideoEditorTabItem("Filters", selectedTab == "Filters") { selectedTab = "Filters" }
                        VideoEditorTabItem("Tools", selectedTab == "Tools") { selectedTab = "Tools" }
                    }
                }
            }

            // Export Progress Overlay
            if (exportResult !is com.example.model.ExportResult.Idle) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (val result = exportResult) {
                                is com.example.model.ExportResult.Progress -> {
                                    CircularProgressIndicator(
                                        progress = { result.percentage },
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Exporting: ${(result.percentage * 100).toInt()}%",
                                        color = Color.White
                                    )
                                }
                                is com.example.model.ExportResult.Error -> {
                                    Text("Error: ${result.message}", color = Color.Red)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.resetExportResult() }) {
                                        Text("Dismiss")
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoEditorTabItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun VideoAdjustmentControls(viewModel: VideoEditorViewModel, editState: com.example.model.VideoEditState) {
    var selectedAdj by remember { mutableStateOf("Brightness") }
    val adjs = listOf("Brightness", "Contrast", "Saturation")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val currentValue = when(selectedAdj) {
            "Brightness" -> editState.brightness
            "Contrast" -> editState.contrast
            "Saturation" -> editState.saturation
            else -> 1f
        }
        
        Text(
            text = "${(currentValue * 100).toInt()}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium
        )

        Slider(
            value = currentValue,
            onValueChange = {
                when(selectedAdj) {
                    "Brightness" -> viewModel.updateBrightness(it)
                    "Contrast" -> viewModel.updateContrast(it)
                    "Saturation" -> viewModel.updateSaturation(it)
                }
            },
            valueRange = 0.5f..1.5f,
            modifier = Modifier.padding(horizontal = 32.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary)
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            adjs.forEach { adj ->
                Text(
                    text = adj,
                    color = if (selectedAdj == adj) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.clickable { selectedAdj = adj },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun VideoFilterControls(viewModel: VideoEditorViewModel, editState: com.example.model.VideoEditState) {
    val filters = listOf("None", "Vivid", "Warm", "Cool", "BW", "Sepia")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(filters) { filter ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { viewModel.applyFilter(filter) }
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (editState.selectedFilter == filter) MaterialTheme.colorScheme.primary else Color.DarkGray)
                        .border(
                            width = 2.dp,
                            color = if (editState.selectedFilter == filter) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(filter.take(2).uppercase(), color = Color.White)
                }
                Text(filter, color = if (editState.selectedFilter == filter) MaterialTheme.colorScheme.primary else Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun VideoToolsControls(viewModel: VideoEditorViewModel, editState: com.example.model.VideoEditState, isPlaying: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoToolItem(
            label = "Speed",
            value = "${editState.speed}x",
            onClick = {
                val nextSpeed = when (editState.speed) {
                    1.0f -> 1.5f
                    1.5f -> 2.0f
                    2.0f -> 0.5f
                    else -> 1.0f
                }
                viewModel.updateSpeed(nextSpeed)
            }
        )

        IconButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        VideoToolItem(
            label = if (editState.isMuted) "Muted" else "Volume",
            value = if (editState.isMuted) "0%" else "${(editState.volume * 100).toInt()}%",
            onClick = {
                val nextVolume = if (editState.volume > 0f) 0f else 1.0f
                viewModel.updateVolume(nextVolume)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTimeline(
    duration: Long,
    currentPosition: Long,
    startTime: Long,
    endTime: Long,
    onTrimChange: (Long, Long) -> Unit,
    onSeek: (Long) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            RangeSlider(
                value = startTime.toFloat()..endTime.toFloat().coerceAtLeast(startTime.toFloat() + 1f),
                onValueChange = { range ->
                    onTrimChange(range.start.toLong(), range.endInclusive.toLong())
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    thumbColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Start: ${formatTime(startTime)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Text("End: ${formatTime(endTime)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun VideoToolItem(label: String, value: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun VideoEditorTopBar(onBack: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        
        com.example.ui.components.ResponsiveText(
            text = "Video Editor",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = onSave,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Export")
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
    indication = null,
    onClick = onClick
)
