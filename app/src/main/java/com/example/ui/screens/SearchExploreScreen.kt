package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.DateRangeFilter
import com.example.model.MediaFilterType
import com.example.model.MediaItem
import com.example.ui.animation.StaggeredGridItem
import com.example.ui.animation.rememberStaggerState
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.ResponsiveText
import com.example.viewmodel.GalleryUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchExploreScreen(
    uiState: GalleryUiState,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onSelectMediaFilter: (MediaFilterType) -> Unit,
    onSelectDateFilter: (DateRangeFilter) -> Unit,
    onSelectAlbumFilter: (String?) -> Unit,
    onClearAllFilters: () -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onOpenPhoto: (MediaItem) -> Unit,
    onToggleSelect: (MediaItem) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val searchState = uiState.searchFilter
    val columns = uiState.preferences.gridDensity.columns

    val staggerState = rememberStaggerState(
        triggerKey = Triple(searchState, columns, uiState.searchResults.isNotEmpty())
    )
    val itemIndexMap = remember(uiState.searchResults) {
        val map = mutableMapOf<Long, Int>()
        uiState.searchResults.forEachIndexed { idx, item ->
            map[item.id] = idx
        }
        map
    }

    val discoveredTags = remember(uiState.allMedia) {
        uiState.allMedia.flatMap { it.tags }.distinct().take(10)
    }

    val albumNames = remember(uiState.albums) {
        uiState.albums.filter { it.mediaCount > 0 }.map { it.title }.distinct()
    }

    val hasActiveFilter = searchState.query.isNotBlank() ||
        searchState.mediaFilter != MediaFilterType.ALL ||
        searchState.dateFilter != DateRangeFilter.ALL_TIME ||
        searchState.albumFilter != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Title Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                ResponsiveText(
                    text = "Explore & Search",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("search_screen_title")
                )
                Text(
                    text = "Search by filename, album, date, or local tags",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                                onEnterSelectionMode()
                            }
                        }
                        .testTag("search_select_toggle_button")
                ) {
                    ResponsiveText(
                        text = if (uiState.selection.isSelectionMode) "Done" else "Select",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        color = if (uiState.selection.isSelectionMode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                if (uiState.selection.isSelectionMode) {
                    val allSelected = uiState.searchResults.isNotEmpty() &&
                        uiState.searchResults.all { uiState.selection.selectedUris.contains(it.uri.toString()) }

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

                if (hasActiveFilter) {
                    TextButton(
                        onClick = onClearAllFilters,
                        modifier = Modifier.testTag("reset_search_filters_button")
                    ) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Rounded Search Input Field
        OutlinedTextField(
            value = searchState.query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Search DSCF0842, Tokyo, Architecture, Camera…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (searchState.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("clear_search_query_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSubmitQuery(searchState.query)
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .testTag("search_input_field")
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
        ) {
            // 1. Media Type & Date Filters Row
            item(key = "media_and_date_filters") {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "Media Type & Date Range",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaFilterType.entries.forEach { type ->
                            val selected = searchState.mediaFilter == type
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectMediaFilter(type) },
                                label = { Text(type.label) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        DateRangeFilter.entries.drop(1).forEach { dateRange ->
                            val selected = searchState.dateFilter == dateRange
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onSelectDateFilter(
                                        if (selected) DateRangeFilter.ALL_TIME else dateRange
                                    )
                                },
                                label = { Text(dateRange.label) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // 2. Album Filter Strip
            if (albumNames.isNotEmpty()) {
                item(key = "album_filter_strip") {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Filter by Album",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = searchState.albumFilter == null,
                                onClick = { onSelectAlbumFilter(null) },
                                label = { Text("All Albums") },
                                shape = CircleShape
                            )
                            albumNames.forEach { albumTitle ->
                                val selected = searchState.albumFilter.equals(albumTitle, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onSelectAlbumFilter(if (selected) null else albumTitle)
                                    },
                                    label = { Text(albumTitle) },
                                    shape = CircleShape
                                )
                            }
                        }
                    }
                }
            }

            // 3. Recent Searches (shown when query is empty)
            if (searchState.query.isBlank() && uiState.recentSearches.isNotEmpty()) {
                item(key = "recent_searches_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = onClearRecentSearches) {
                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.recentSearches.forEach { recent ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSubmitQuery(recent) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = recent,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove recent search",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { onRemoveRecentSearch(recent) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Local Tags Quick Filter Pills
            if (discoveredTags.isNotEmpty() && searchState.query.isBlank()) {
                item(key = "discovered_tags_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Personal Tags & Metadata Topics",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            discoveredTags.forEach { tag ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable { onSubmitQuery(tag) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LocalOffer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Results Count Header
            item(key = "results_count_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasActiveFilter) "Matching Results" else "All Library Media",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${uiState.searchResults.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (uiState.searchResults.isEmpty()) {
                item(key = "empty_search_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No matching media found",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try searching for a different filename, tag, caption, or clearing active album/date filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val rows = uiState.searchResults.chunked(columns)
                items(rows, key = { "search_row_${it.first().id}" }) { rowItems ->
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
                                            if (searchState.query.isNotBlank()) {
                                                onSubmitQuery(searchState.query)
                                            }
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
            }
        }
    }
}
