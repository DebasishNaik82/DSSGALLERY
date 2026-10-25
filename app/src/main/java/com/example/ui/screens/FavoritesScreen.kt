package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.DateRangeFilter
import com.example.model.MediaFilterType
import com.example.model.MediaItem
import com.example.model.MemoryCollection
import com.example.ui.animation.StaggeredGridItem
import com.example.ui.animation.rememberStaggerState
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.ResponsiveText
import com.example.ui.components.TimelineGroupHeader
import com.example.viewmodel.GalleryUiState

@Composable
fun FavoritesScreen(
    uiState: GalleryUiState,
    onOpenPhoto: (MediaItem) -> Unit,
    onToggleSelect: (MediaItem) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectGroup: (List<MediaItem>, Boolean) -> Unit,
    onSetDateFilter: (DateRangeFilter) -> Unit,
    onSetMediaFilter: (MediaFilterType) -> Unit,
    onPlaySlideshow: (List<MediaItem>) -> Unit,
    onShareFavorites: (List<MediaItem>) -> Unit,
    onPrintOrExport: (List<MediaItem>) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDateDropdown by remember { mutableStateOf(false) }
    var showMediaTypeDropdown by remember { mutableStateOf(false) }
    val columns = uiState.preferences.gridDensity.columns
    val favItems = uiState.filteredFavorites

    val staggerState = rememberStaggerState(
        triggerKey = Triple(uiState.favoritesFilter, columns, favItems.isNotEmpty())
    )
    val itemIndexMap = remember(uiState.favoriteGroups) {
        val map = mutableMapOf<Long, Int>()
        var idx = 0
        uiState.favoriteGroups.forEach { group ->
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
        // Sub-Header & Controls Context (Matching 1st HTML Design)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                ResponsiveText(
                    text = "Favorites",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("favorites_screen_title")
                )
                    Text(
                        text = "${favItems.size} photos & videos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (uiState.selection.isSelectionMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (uiState.selection.isSelectionMode) onClearSelection()
                                else onEnterSelectionMode()
                            }
                            .testTag("favorites_select_toggle")
                    ) {
                        Text(
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
                        val allSelected = favItems.isNotEmpty() &&
                            favItems.all { uiState.selection.selectedUris.contains(it.uri.toString()) }

                        TextButton(
                            onClick = { onSelectAll(!allSelected) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            ResponsiveText(
                                text = if (allSelected) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action Pills Strip: Filter by date | Slideshow | Media type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Filter by date pill
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showDateDropdown = true }
                            .testTag("favorites_date_filter_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.favoritesFilter.dateFilter == DateRangeFilter.ALL_TIME) {
                                    "Filter by date"
                                } else {
                                    uiState.favoritesFilter.dateFilter.label
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showDateDropdown,
                        onDismissRequest = { showDateDropdown = false }
                    ) {
                        DateRangeFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label) },
                                onClick = {
                                    showDateDropdown = false
                                    onSetDateFilter(filter)
                                }
                            )
                        }
                    }
                }

                // 2. Slideshow pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = favItems.isNotEmpty()) {
                            onPlaySlideshow(favItems)
                        }
                        .testTag("favorites_slideshow_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Slideshow",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 3. Media type pill
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showMediaTypeDropdown = true }
                            .testTag("favorites_media_type_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.favoritesFilter.mediaFilter == MediaFilterType.ALL) {
                                    "Media type"
                                } else {
                                    uiState.favoritesFilter.mediaFilter.label
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMediaTypeDropdown,
                        onDismissRequest = { showMediaTypeDropdown = false }
                    ) {
                        MediaFilterType.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label) },
                                onClick = {
                                    showMediaTypeDropdown = false
                                    onSetMediaFilter(filter)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (favItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No favorites yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the heart icon on any photo or video to curate your favorite moments here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("favorites_timeline_list"),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
            ) {
                // Memory Highlight Banner ("Memory Reel — Best of Favorites")
                item(key = "favorites_memory_banner") {
                    val memory = MemoryCollection(
                        id = "fav_annual_recap",
                        badge = "Memory Reel",
                        title = "Best of Favorites",
                        subtitle = "Annual Recap",
                        coverItem = favItems.first(),
                        previewItems = favItems.take(3),
                        allItems = favItems
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        MemoryHighlightCard(
                            memory = memory,
                            onClick = { onPlaySlideshow(favItems) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Curated Media Timeline grouped by Date
                uiState.favoriteGroups.forEach { group ->
                    val groupUris = group.items.map { it.uri.toString() }
                    val allGroupSelected = groupUris.isNotEmpty() &&
                        groupUris.all { uiState.selection.selectedUris.contains(it) }

                    item(key = "fav_header_${group.key}") {
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
                    items(rows, key = { "fav_row_${group.key}_${it.first().id}" }) { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowItems.forEach { item ->
                                val itemIndex = itemIndexMap[item.id] ?: 0
                                val isSelected = uiState.selection.selectedUris.contains(item.uri.toString())
                                StaggeredGridItem(
                                    index = itemIndex,
                                    staggerState = staggerState,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MediaThumbnailTile(
                                        item = item,
                                        isSelectionMode = uiState.selection.isSelectionMode,
                                        isSelected = isSelected,
                                        showVideoDurationBadge = uiState.preferences.showVideoDurationBadges,
                                        onClick = {
                                            if (uiState.selection.isSelectionMode) {
                                                onToggleSelect(item)
                                            } else {
                                                onOpenPhoto(item)
                                            }
                                        },
                                        onLongClick = { onToggleSelect(item) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item(key = "fav_spacer_${group.key}") {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Quick Batch Actions Dock (Share | Collage | Order Prints / Export)
                item(key = "favorites_quick_actions_dock") {
                    val targetItems = if (uiState.selection.selectedUris.isNotEmpty()) {
                        favItems.filter { uiState.selection.selectedUris.contains(it.uri.toString()) }
                    } else {
                        favItems
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "QUICK ACTIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${targetItems.size} ${if (uiState.selection.isSelectionMode) "Selected" else "Favorites"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                QuickBatchActionButton(
                                    icon = Icons.Filled.Share,
                                    label = "Share",
                                    highlighted = false,
                                    onClick = { onShareFavorites(targetItems) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("favorites_quick_share")
                                )

                                QuickBatchActionButton(
                                    icon = Icons.Filled.Print,
                                    label = "Print Sheet",
                                    highlighted = true,
                                    onClick = { onPrintOrExport(targetItems) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("favorites_quick_print")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickBatchActionButton(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (highlighted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
