package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.AlbumItem
import com.example.model.MediaItem
import com.example.model.SystemAlbumType
import com.example.ui.animation.StaggeredGridItem
import com.example.ui.animation.rememberStaggerState
import com.example.ui.components.CreateOrAssignAlbumDialog
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.ResponsiveText
import com.example.viewmodel.GalleryUiState

@Composable
fun AlbumsScreen(
    uiState: GalleryUiState,
    onOpenAlbum: (AlbumItem) -> Unit,
    onCreateCustomAlbum: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateAlbumDialog by remember { mutableStateOf(false) }

    val customAlbums = remember(uiState.albums) {
        uiState.albums.filter { it.type == SystemAlbumType.USER_ALBUM }
    }
    val systemAlbums = remember(uiState.albums) {
        uiState.albums.filter {
            it.type != SystemAlbumType.USER_ALBUM && it.type != SystemAlbumType.DEVICE_FOLDER
        }
    }
    val deviceFolderAlbums = remember(uiState.albums) {
        uiState.albums.filter { it.type == SystemAlbumType.DEVICE_FOLDER }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                ResponsiveText(
                    text = "Albums",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("albums_screen_title")
                )
                Text(
                    text = "${uiState.albums.size} collections • ${customAlbums.size} custom",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showCreateAlbumDialog = true }
                        .testTag("create_album_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Album",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Album",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("albums_grid")
        ) {
            if (customAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ResponsiveText(
                            text = "My Custom Albums",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Room Local Mapping",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(customAlbums, key = { it.id }) { album ->
                    AlbumCoverCard(
                        album = album,
                        onClick = { onOpenAlbum(album) }
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                ResponsiveText(
                    text = "Essential Collections",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = if (customAlbums.isNotEmpty()) 8.dp else 0.dp, bottom = 2.dp)
                )
            }

            items(systemAlbums, key = { it.id }) { album ->
                AlbumCoverCard(
                    album = album,
                    onClick = { onOpenAlbum(album) }
                )
            }

            if (deviceFolderAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    ResponsiveText(
                        text = "Device Folders",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                }

                items(deviceFolderAlbums, key = { it.id }) { album ->
                    AlbumCoverCard(
                        album = album,
                        onClick = { onOpenAlbum(album) }
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                RecycleBinAlbumItem(
                    onClick = onOpenRecycleBin
                )
            }
        }
    }

    if (showCreateAlbumDialog) {
        CreateOrAssignAlbumDialog(
            existingCustomAlbums = emptyList(),
            onCreateNewAlbum = onCreateCustomAlbum,
            onSelectAlbum = {},
            onDismiss = { showCreateAlbumDialog = false }
        )
    }
}

@Composable
private fun RecycleBinAlbumItem(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("recycle_bin_album_item")
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Recycle Bin",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AlbumCoverCard(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val badgeIcon: ImageVector = when (album.type) {
        SystemAlbumType.CAMERA -> Icons.Filled.CameraAlt
        SystemAlbumType.SCREENSHOTS -> Icons.Filled.Screenshot
        SystemAlbumType.DOWNLOADS -> Icons.Filled.Download
        SystemAlbumType.VIDEOS -> Icons.Filled.Videocam
        SystemAlbumType.FAVORITES -> Icons.Filled.Favorite
        SystemAlbumType.RECENTLY_ADDED -> Icons.Filled.History
        SystemAlbumType.USER_ALBUM -> Icons.Filled.FolderSpecial
        SystemAlbumType.DEVICE_FOLDER -> Icons.Filled.Folder
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("album_card_${album.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (album.coverItem != null) {
                    AsyncImage(
                        model = remember(album.coverItem.uri) {
                            ImageRequest.Builder(context)
                                .data(album.coverItem.uri)
                                .size(500)
                                .crossfade(true)
                                .build()
                        },
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.42f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${album.mediaCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ResponsiveText(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            ResponsiveText(
                text = "${album.mediaCount} ${if (album.mediaCount == 1) "item" else "items"} • ${album.subtitle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun AlbumDetailScreen(
    album: AlbumItem,
    uiState: GalleryUiState,
    onBack: () -> Unit,
    onOpenPhoto: (MediaItem) -> Unit,
    onToggleSelect: (MediaItem) -> Unit,
    onEnterSelectionMode: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onPlaySlideshow: (List<MediaItem>) -> Unit,
    onMovePhotosIntoThisAlbum: (List<MediaItem>) -> Unit = {},
    onRemoveSelectedFromAlbum: (List<MediaItem>) -> Unit = {},
    onDeleteCustomAlbum: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val columns = uiState.preferences.gridDensity.columns
    var showAddPhotosPicker by remember { mutableStateOf(false) }

    val staggerState = rememberStaggerState(triggerKey = Pair(album.id, columns))
    val itemIndexMap = remember(album.items) {
        val map = mutableMapOf<Long, Int>()
        album.items.forEachIndexed { idx, item ->
            map[item.id] = idx
        }
        map
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("album_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ResponsiveText(
                        text = album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "${album.items.size} items • ${album.subtitle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
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
                        .testTag("album_detail_select_toggle_button")
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
                    val allSelected = album.items.isNotEmpty() &&
                        album.items.all { uiState.selection.selectedUris.contains(it.uri.toString()) }

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

                if (album.isUserCreated) {
                    Button(
                        onClick = { showAddPhotosPicker = true },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("album_detail_move_photos_here_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DriveFileMove,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Move Photos",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onDeleteCustomAlbum,
                        modifier = Modifier.testTag("delete_custom_album_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete Custom Album",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (album.items.isNotEmpty()) {
                    Button(
                        onClick = { onPlaySlideshow(album.items) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Slideshow",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Custom Album Selection Action Strip (Remove from album)
        if (album.isUserCreated && uiState.selection.isSelectionMode) {
            val selectedInAlbum = album.items.filter {
                uiState.selection.selectedUris.contains(it.uri.toString())
            }
            if (selectedInAlbum.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedInAlbum.size} selected in ${album.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { onRemoveSelectedFromAlbum(selectedInAlbum) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RemoveCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Remove from Album",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (album.items.isEmpty()) {
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
                    Icon(
                        imageVector = if (album.isUserCreated) Icons.Filled.FolderSpecial else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No media in ${album.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (album.isUserCreated) {
                            "Select photos from your library to move them into this custom album."
                        } else {
                            "Photos and videos matching this collection will automatically appear here."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (album.isUserCreated) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddPhotosPicker = true },
                            shape = CircleShape,
                            modifier = Modifier.testTag("empty_album_move_photos_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Photos to Move Here")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                val rows = album.items.chunked(columns)
                items(rows, key = { it.first().id }) { rowItems ->
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
            }
        }
    }

    if (showAddPhotosPicker) {
        val currentUris = remember(album.items) { album.items.map { it.uri.toString() }.toSet() }
        val candidatePhotos = remember(uiState.allMedia, currentUris) {
            uiState.allMedia.filter { !currentUris.contains(it.uri.toString()) }
        }
        val pickedSet = remember { mutableStateListOf<MediaItem>() }

        AlertDialog(
            onDismissRequest = { showAddPhotosPicker = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = {
                Column {
                    Text(
                        text = "Move Photos to \"${album.title}\"",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${pickedSet.size} selected • Tap photos below",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                if (candidatePhotos.isEmpty()) {
                    Text(
                        text = "All photos in your library are already in this album.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        items(candidatePhotos, key = { it.id }) { candidate ->
                            val isChosen = pickedSet.any { it.uri == candidate.uri }
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        if (isChosen) {
                                            Modifier.border(
                                                2.5.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(10.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable {
                                        if (isChosen) {
                                            pickedSet.removeAll { it.uri == candidate.uri }
                                        } else {
                                            pickedSet.add(candidate)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = candidate.uri,
                                    contentDescription = candidate.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isChosen) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMovePhotosIntoThisAlbum(pickedSet.toList())
                        showAddPhotosPicker = false
                    },
                    enabled = pickedSet.isNotEmpty(),
                    shape = CircleShape
                ) {
                    Text("Move ${pickedSet.size} Here")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPhotosPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
