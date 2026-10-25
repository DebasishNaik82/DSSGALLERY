package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.GalleryPreferences
import com.example.model.GridDensity
import com.example.model.MediaItem
import com.example.model.SortOption
import com.example.model.ThemeOption
import com.example.ui.components.ResponsiveText
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    preferences: GalleryPreferences,
    totalMediaCount: Int,
    favoritesCount: Int,
    onBack: () -> Unit,
    onSelectTheme: (ThemeOption) -> Unit,
    onSelectGridDensity: (GridDensity) -> Unit,
    onSelectSortOption: (SortOption) -> Unit,
    onToggleShowVideos: (Boolean) -> Unit,
    onToggleShowVideoDuration: (Boolean) -> Unit,
    onToggleShowMemories: (Boolean) -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onOpenPermissionsScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 64.dp)
            .testTag("settings_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                ResponsiveText(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Appearance, grid layout, sorting & offline privacy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 1. Appearance / Theme Card
        SettingsSectionCard(
            title = "Theme & Appearance",
            icon = Icons.Filled.DarkMode
        ) {
            com.example.ui.components.SettingsDropdownRow(
                title = "App Theme",
                subtitle = "Choose light or dark mode",
                options = ThemeOption.entries,
                selectedOption = preferences.themeOption,
                onOptionSelected = onSelectTheme,
                optionLabel = { it.label }
            )
        }

        // 2. Grid Density Card
        SettingsSectionCard(
            title = "Timeline Grid Density",
            icon = Icons.Filled.GridView
        ) {
            com.example.ui.components.SettingsDropdownRow(
                title = "Grid Layout",
                subtitle = "Adjust photo grid density",
                options = GridDensity.entries,
                selectedOption = preferences.gridDensity,
                onOptionSelected = onSelectGridDensity,
                optionLabel = { it.label }
            )
        }

        // 3. Photo Sorting Preferences
        SettingsSectionCard(
            title = "Photo Sorting Order",
            icon = Icons.Filled.Sort
        ) {
            com.example.ui.components.SettingsDropdownRow(
                title = "Sort By",
                subtitle = "Change media display order",
                options = SortOption.entries,
                selectedOption = preferences.sortOption,
                onOptionSelected = onSelectSortOption,
                optionLabel = { it.label }
            )
        }

        // 4. Video & Timeline Visibility Preferences
        SettingsSectionCard(
            title = "Video & Timeline Visibility",
            icon = Icons.Filled.Videocam
        ) {
            SettingsSwitchRow(
                title = "Show Videos in Timeline",
                subtitle = "Include device video clips alongside photos in main timeline",
                checked = preferences.showVideosInTimeline,
                onCheckedChange = onToggleShowVideos,
                testTag = "toggle_show_videos"
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            )
            SettingsSwitchRow(
                title = "Video Duration Badges",
                subtitle = "Display duration indicators on video thumbnails",
                checked = preferences.showVideoDurationBadges,
                onCheckedChange = onToggleShowVideoDuration,
                testTag = "toggle_video_duration"
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            )
            SettingsSwitchRow(
                title = "Show Memories Carousel",
                subtitle = "Display curated highlight reels at the top of Photos",
                checked = preferences.showMemoriesSection,
                onCheckedChange = onToggleShowMemories,
                testTag = "toggle_show_memories"
            )
        }

        // 5. Device MediaStore Tools
        SettingsSectionCard(
            title = "Device MediaStore & Import Tools",
            icon = Icons.Filled.PhotoLibrary
        ) {
            Text(
                text = "Library Status: $totalMediaCount items ($favoritesCount favorited)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(onClick = onLaunchPhotoPicker)
                ) {
                    Text(
                        text = "Android Photo Picker",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenPermissionsScreen)
                ) {
                    Text(
                        text = "Permission States",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // 6. App Information & Strict Offline Privacy Manifesto
        SettingsSectionCard(
            title = "Privacy & Offline Architecture",
            icon = Icons.Filled.Security
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "100% Local & Offline First",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• Zero INTERNET permission requested in AndroidManifest.xml.\n" +
                    "• Zero cloud servers, analytics, or external telemetry SDKs.\n" +
                    "• Reads device media directly via Android MediaStore & Photo Picker.\n" +
                    "• Stores favorites, tags, captions, and preferences strictly on-device using Room SQLite and Jetpack DataStore.\n" +
                    "• Gallery v1.0 • Porcelain Edition",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                ResponsiveText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun SlideshowReelDialog(
    title: String,
    items: List<MediaItem>,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    BackHandler(onBack = onDismiss)

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(currentIndex, isPlaying, items.size) {
        if (isPlaying && items.size > 1) {
            delay(3200)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    val currentItem = items[currentIndex.coerceIn(0, items.lastIndex)]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("slideshow_reel_dialog")
    ) {
        Crossfade(
            targetState = currentItem,
            label = "slideshow_crossfade",
            modifier = Modifier.fillMaxSize()
        ) { media ->
            AsyncImage(
                model = media.uri,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top & Bottom Ambient Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        // Top Progress Indicators + Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.take(12).forEachIndexed { idx, _ ->
                    LinearProgressIndicator(
                        progress = {
                            when {
                                idx < currentIndex -> 1f
                                idx == currentIndex -> 0.65f
                                else -> 0f
                            }
                        },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${currentIndex + 1} of ${items.size} • ${currentItem.bucketDisplayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Slideshow",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Controls & Caption
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentItem.caption.ifBlank { currentItem.displayName },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.18f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            currentIndex = if (currentIndex - 1 < 0) items.lastIndex else currentIndex - 1
                        }
                    ) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                    }
                    IconButton(
                        onClick = { isPlaying = !isPlaying }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            currentIndex = (currentIndex + 1) % items.size
                        }
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
    }
}
