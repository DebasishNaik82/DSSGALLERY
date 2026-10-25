package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import com.example.ui.animation.StaggeredGridItem
import com.example.ui.animation.rememberStaggerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.GridDensity
import com.example.model.MediaItem
import com.example.model.MemoryCollection
import com.example.model.PermissionAccessLevel
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.ResponsiveText
import com.example.ui.components.TimelineGroupHeader
import com.example.viewmodel.GalleryUiState

@Composable
fun PhotosHomeScreen(
    uiState: GalleryUiState,
    onOpenPhoto: (MediaItem) -> Unit,
    onToggleSelect: (MediaItem) -> Unit,
    onEnterSelectionMode: (MediaItem?) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectGroup: (List<MediaItem>, Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatesInspector: () -> Unit,
    onRequestPermissions: () -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onPlayMemoryReel: (MemoryCollection) -> Unit,
    onCycleGridDensity: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val columns = uiState.preferences.gridDensity.columns

    // Media Type Filters (Photos, Videos, All)
    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Photos", "Videos")

    // Filter the timeline groups based on selected media type
    val filteredTimelineGroups = remember(selectedFilter, uiState.timelineGroups) {
        when (selectedFilter) {
            "Photos" -> uiState.timelineGroups.map { group ->
                group.copy(items = group.items.filter { !it.isVideo })
            }.filter { it.items.isNotEmpty() }
            "Videos" -> uiState.timelineGroups.map { group ->
                group.copy(items = group.items.filter { it.isVideo })
            }.filter { it.items.isNotEmpty() }
            else -> uiState.timelineGroups
        }
    }

    // Stagger animation state triggered on first load, filter change, or density change
    val staggerState = rememberStaggerState(
        triggerKey = Triple(selectedFilter, columns, uiState.timelineGroups.isNotEmpty())
    )

    // Pre-calculate sequential item indices across groups for staggered cascading entrance
    val itemIndexMap = remember(filteredTimelineGroups) {
        val map = mutableMapOf<Long, Int>()
        var idx = 0
        filteredTimelineGroups.forEach { group ->
            group.items.forEach { item ->
                map[item.id] = idx++
            }
        }
        map
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Top App Bar: Elegant "Gallery" display title + Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                ResponsiveText(
                    text = "Gallery",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("home_gallery_title")
                )
                val photoCount = uiState.allMedia.count { !it.isVideo }
                val videoCount = uiState.allMedia.count { it.isVideo }
                ResponsiveText(
                    text = "$photoCount photos • $videoCount videos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Select / Done Pill
                Surface(
                    shape = CircleShape,
                    color = if (uiState.selection.isSelectionMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (uiState.selection.isSelectionMode) {
                                onClearSelection()
                            } else {
                                onEnterSelectionMode(null)
                            }
                        }
                        .testTag("home_select_toggle_button")
                ) {
                    ResponsiveText(
                        text = if (uiState.selection.isSelectionMode) "Done" else "Select",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.selection.isSelectionMode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                if (uiState.selection.isSelectionMode) {
                    val allVisibleUris = filteredTimelineGroups.flatMap { it.items }.map { it.uri.toString() }
                    val allSelected = allVisibleUris.isNotEmpty() &&
                        allVisibleUris.all { uiState.selection.selectedUris.contains(it) }

                    TextButton(
                        onClick = {
                            val visibleItems = filteredTimelineGroups.flatMap { it.items }
                            onSelectAll(!allSelected)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        ResponsiveText(
                            text = if (allSelected) "Deselect All" else "Select All",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Grid Density Toggle Icon
                IconButton(
                    onClick = onCycleGridDensity,
                    modifier = Modifier.testTag("home_grid_density_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = "Change Grid Density",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Settings Icon (Required by spec in top-right corner)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("home_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import via Photo Picker") },
                            leadingIcon = {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                            },
                            onClick = {
                                showOverflowMenu = false
                                onLaunchPhotoPicker()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Replay Stagger Animation") },
                            leadingIcon = {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                showOverflowMenu = false
                                staggerState.replay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Permissions & Empty States") },
                            leadingIcon = {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null)
                            },
                            onClick = {
                                showOverflowMenu = false
                                onOpenStatesInspector()
                            }
                        )
                    }
                }
            }
        }

        // Rounded Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onOpenSearch)
                    .testTag("home_search_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Search photos, albums, EXIF, or tags…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(filterOptions) { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { selectedFilter = filter }
                        .testTag("home_filter_$filter")
                ) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        

        // Optional Compact Permission Access Banner if full MediaStore permission not yet granted
        AnimatedVisibility(visible = uiState.permissionAccessLevel != PermissionAccessLevel.FULL_ACCESS) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clickable { onOpenStatesInspector() }
                    .testTag("permission_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (uiState.permissionAccessLevel == PermissionAccessLevel.PARTIAL_ACCESS) {
                                    "Partial Photo Access Enabled"
                                } else {
                                    "Full Device Media Access Available"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to grant all photos permission or pick specific photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestPermissions,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("banner_grant_permission_button")
                    ) {
                        Text(
                            text = "Allow",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        if (uiState.isLoading && uiState.allMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reading device MediaStore…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else if (uiState.allMedia.isEmpty()) {
            if (uiState.permissionAccessLevel == PermissionAccessLevel.FULL_ACCESS) {
                // Simple empty state when permission is granted but library is empty
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No photos or videos found in your library.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                // Permission required or partial access
                PermissionAndStatesScreen(
                    permissionAccessLevel = uiState.permissionAccessLevel,
                    initialTab = if (uiState.permissionAccessLevel == PermissionAccessLevel.DENIED) "perm" else "empty",
                    onRequestPermissions = onRequestPermissions,
                    onLaunchPhotoPicker = onLaunchPhotoPicker,
                    onBack = null
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("photos_timeline_list"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                // Horizontal Memories Section with Large Photo Previews
                if (uiState.preferences.showMemoriesSection && uiState.memories.isNotEmpty()) {
                    item(key = "memories_section") {
                        MemoriesCarouselSection(
                            memories = uiState.memories,
                            onPlayMemory = onPlayMemoryReel
                        )
                    }
                }

                // Date-Grouped Photo Timeline
                filteredTimelineGroups.forEach { group ->
                    val groupUris = group.items.map { it.uri.toString() }
                    val allGroupSelected = groupUris.isNotEmpty() &&
                        groupUris.all { uiState.selection.selectedUris.contains(it) }

                    item(key = "header_${group.key}") {
                        TimelineGroupHeader(
                            group = group,
                            isSelectionMode = uiState.selection.isSelectionMode,
                            allGroupSelected = allGroupSelected,
                            onToggleSelectGroup = {
                                onSelectGroup(group.items, !allGroupSelected)
                            }
                        )
                    }

                    val rows = group.items.chunked(columns)
                    items(
                        items = rows,
                        key = { row -> "row_${group.key}_${row.first().id}" }
                    ) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowItems.forEach { mediaItem ->
                                val itemIndex = itemIndexMap[mediaItem.id] ?: 0
                                val isSelected = uiState.selection.selectedUris.contains(mediaItem.uri.toString())
                                StaggeredGridItem(
                                    index = itemIndex,
                                    staggerState = staggerState,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MediaThumbnailTile(
                                        item = mediaItem,
                                        isSelectionMode = uiState.selection.isSelectionMode,
                                        isSelected = isSelected,
                                        showVideoDurationBadge = uiState.preferences.showVideoDurationBadges,
                                        onClick = {
                                            if (uiState.selection.isSelectionMode) {
                                                onToggleSelect(mediaItem)
                                            } else {
                                                onOpenPhoto(mediaItem)
                                            }
                                        },
                                        onLongClick = {
                                            onToggleSelect(mediaItem)
                                        },
                                        cornerRadiusDp = if (columns == 4) 8 else 12,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item(key = "spacer_${group.key}") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoriesCarouselSection(
    memories: List<MemoryCollection>,
    onPlayMemory: (MemoryCollection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Memories",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${memories.size} curated reels",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.testTag("memories_lazy_row")
        ) {
            items(memories, key = { it.id }) { memory ->
                MemoryHighlightCard(
                    memory = memory,
                    onClick = { onPlayMemory(memory) },
                    modifier = Modifier.width(305.dp)
                )
            }
        }
    }
}

@Composable
fun MemoryHighlightCard(
    memory: MemoryCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier
            .height(176.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("memory_card_${memory.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = remember(memory.coverItem.uri) {
                    ImageRequest.Builder(context)
                        .data(memory.coverItem.uri)
                        .size(800)
                        .crossfade(true)
                        .build()
                },
                contentDescription = memory.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Warm editorial dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.42f),
                                Color(0xFF1F1B19).copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Badge + Overlapping Mini Thumbnails
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.22f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFFFDAD3),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = memory.badge.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    // Stacked preview circles
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        memory.previewItems.take(3).forEachIndexed { idx, preview ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (idx * -6).dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                AsyncImage(
                                    model = preview.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Bottom Row: Title + Play Reel Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        ResponsiveText(
                            text = memory.subtitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFDAD3)
                        )
                        ResponsiveText(
                            text = memory.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleFilled,
                            contentDescription = "Play Reel",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play Reel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
