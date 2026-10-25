package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ExifTelemetry
import com.example.model.GridDensity
import com.example.model.MediaItem
import com.example.model.ThemeOption
import com.example.ui.components.ContextualSelectionActionBar
import com.example.ui.components.CreateOrAssignAlbumDialog
import com.example.ui.components.MoveToBinConfirmationDialog
import com.example.ui.components.GalleryToastNotification
import com.example.ui.components.GalleryBottomTab
import com.example.ui.components.PorcelainFloatingBottomBar
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.PermissionAndStatesScreen
import com.example.ui.screens.PhotoViewerScreen
import com.example.ui.screens.PhotosHomeScreen
import com.example.ui.screens.SearchExploreScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SlideshowReelDialog
import com.example.ui.theme.GalleryTheme
import com.example.ui.screens.PhotoEditorScreen
import com.example.ui.screens.VideoEditorScreen
import com.example.viewmodel.GalleryViewModel

sealed interface GalleryDestination {
    data class MainTab(val tab: GalleryBottomTab) : GalleryDestination
    data class AlbumDetail(val albumId: String) : GalleryDestination
    data class PhotoViewer(val initialUri: String, val sourceAlbumId: String? = null) : GalleryDestination
    data class PhotoEditor(val mediaUri: String) : GalleryDestination
    data class VideoEditor(val mediaUri: String) : GalleryDestination
    data object RecycleBin : GalleryDestination
    data object Settings : GalleryDestination
    data object StatesInspector : GalleryDestination
}

@Composable
fun GalleryApp(
    viewModel: GalleryViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentDestination by remember {
        mutableStateOf<GalleryDestination>(GalleryDestination.MainTab(GalleryBottomTab.PHOTOS))
    }
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        com.example.ui.screens.SplashScreen(onFinished = { showSplash = false })
        return
    }

    var lastTab by remember { mutableStateOf(GalleryBottomTab.PHOTOS) }

    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchAssignAlbum by remember { mutableStateOf(false) }
    var batchMoveMode by remember { mutableStateOf(true) }
    var activeSlideshow by remember { mutableStateOf<Pair<String, List<MediaItem>>?>(null) }
    var showPermissionDialog by remember { mutableStateOf(uiState.preferences.showPermissionDialog) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.updatePermissionStatus()
        viewModel.setShowPermissionDialog(false)
        showPermissionDialog = false
    }


    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onPickedMedia(uris)
        }
    }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.clearPendingDeleteRequest(completed = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingDeleteIntentSender, currentDestination) {
        val sender = uiState.pendingDeleteIntentSender
        if (sender != null && currentDestination != GalleryDestination.RecycleBin) {
            if (viewModel.markConsentDialogLaunched(sender)) {
                runCatching {
                    deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }.onFailure {
                    viewModel.clearPendingDeleteRequest(completed = false)
                }
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissStatusMessage()
        }
    }

    val isDarkTheme = when (uiState.preferences.themeOption) {
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeOption.LIGHT -> false
        ThemeOption.DARK -> true
    }

    val isViewerOpen = currentDestination is GalleryDestination.PhotoViewer || activeSlideshow != null

    GalleryTheme(
        darkTheme = isDarkTheme,
        forceDarkStatusBar = isViewerOpen
    ) {
        BackHandler(enabled = uiState.selection.isSelectionMode) {
            viewModel.clearSelection()
        }

        BackHandler(
            enabled = !uiState.selection.isSelectionMode &&
                currentDestination is GalleryDestination.MainTab &&
                (currentDestination as GalleryDestination.MainTab).tab != GalleryBottomTab.PHOTOS
        ) {
            lastTab = GalleryBottomTab.PHOTOS
            currentDestination = GalleryDestination.MainTab(GalleryBottomTab.PHOTOS)
        }

        Scaffold(
            snackbarHost = {
                val isMainTabWithBottomBar = currentDestination is GalleryDestination.MainTab && !uiState.selection.isSelectionMode
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (isMainTabWithBottomBar) 92.dp else 16.dp)
                ) { data ->
                    GalleryToastNotification(message = data.visuals.message)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                if (showPermissionDialog) {
                    com.example.ui.components.PermissionDialog(
                        onDismiss = {
                            viewModel.setShowPermissionDialog(false)
                            showPermissionDialog = false
                        },
                        onConfirm = {
                            permissionLauncher.launch(viewModel.requiredPermissions)
                        }
                    )
                }
                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        if (targetState is GalleryDestination.PhotoViewer) {
                            (scaleIn(
                                initialScale = 0.85f,
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = tween(260)))
                                .togetherWith(
                                    scaleOut(
                                        targetScale = 1.08f,
                                        animationSpec = tween(220)
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                        } else if (initialState is GalleryDestination.PhotoViewer) {
                            (scaleIn(
                                initialScale = 1.08f,
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = tween(240)))
                                .togetherWith(
                                    scaleOut(
                                        targetScale = 0.85f,
                                        animationSpec = tween(200)
                                    ) + fadeOut(animationSpec = tween(180))
                                )
                        } else {
                            fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(180)))
                        }
                    },
                    label = "gallery_destination_transition",
                    modifier = Modifier.fillMaxSize()
                ) { dest ->
                    when (dest) {
                        is GalleryDestination.MainTab -> {
                            when (dest.tab) {
                            GalleryBottomTab.PHOTOS -> {
                                PhotosHomeScreen(
                                    uiState = uiState,
                                    onOpenPhoto = { item ->
                                        currentDestination = GalleryDestination.PhotoViewer(item.uri.toString())
                                    },
                                    onToggleSelect = { item ->
                                        viewModel.toggleSelection(item.uri.toString())
                                    },
                                    onEnterSelectionMode = { item ->
                                        viewModel.enterSelectionMode(item?.uri?.toString())
                                    },
                                    onClearSelection = { viewModel.clearSelection() },
                                    onSelectAll = { selectAll ->
                                        viewModel.selectItems(uiState.allMedia, selectAll)
                                    },
                                    onSelectGroup = { groupItems, selectAll ->
                                        viewModel.selectItems(groupItems, selectAll)
                                    },
                                    onOpenSearch = {
                                        lastTab = GalleryBottomTab.SEARCH
                                        currentDestination = GalleryDestination.MainTab(GalleryBottomTab.SEARCH)
                                    },
                                    onOpenSettings = {
                                        currentDestination = GalleryDestination.Settings
                                    },
                                    onOpenStatesInspector = {
                                        currentDestination = GalleryDestination.StatesInspector
                                    },
                                    onRequestPermissions = {
                                        permissionLauncher.launch(viewModel.requiredPermissions)
                                    },
                                    onLaunchPhotoPicker = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    onPlayMemoryReel = { memory ->
                                        activeSlideshow = memory.title to memory.allItems
                                    },
                                    onCycleGridDensity = {
                                        val next = when (uiState.preferences.gridDensity) {
                                            GridDensity.STANDARD_3 -> GridDensity.COMPACT_4
                                            GridDensity.COMPACT_4 -> GridDensity.COMFORTABLE_2
                                            GridDensity.COMFORTABLE_2 -> GridDensity.STANDARD_3
                                        }
                                        viewModel.setGridDensity(next)
                                    }
                                )
                            }

                            GalleryBottomTab.ALBUMS -> {
                                AlbumsScreen(
                                    uiState = uiState,
                                    onOpenAlbum = { album ->
                                        currentDestination = GalleryDestination.AlbumDetail(album.id)
                                    },
                                    onCreateCustomAlbum = { name ->
                                        viewModel.createCustomAlbum(name)
                                    },
                                    onOpenSettings = {
                                        currentDestination = GalleryDestination.Settings
                                    },
                                    onOpenRecycleBin = {
                                        currentDestination = GalleryDestination.RecycleBin
                                    }
                                )
                            }

                            GalleryBottomTab.FAVORITES -> {
                                FavoritesScreen(
                                    uiState = uiState,
                                    onOpenPhoto = { item ->
                                        currentDestination = GalleryDestination.PhotoViewer(
                                            initialUri = item.uri.toString(),
                                            sourceAlbumId = "system_favorites"
                                        )
                                    },
                                    onToggleSelect = { item ->
                                        viewModel.toggleSelection(item.uri.toString())
                                    },
                                    onEnterSelectionMode = {
                                        viewModel.enterSelectionMode()
                                    },
                                    onClearSelection = {
                                        viewModel.clearSelection()
                                    },
                                    onSelectAll = { selectAll ->
                                        viewModel.selectItems(uiState.filteredFavorites, selectAll)
                                    },
                                    onSelectGroup = { groupItems, selectAll ->
                                        viewModel.selectItems(groupItems, selectAll)
                                    },
                                    onSetDateFilter = { viewModel.setFavoritesDateFilter(it) },
                                    onSetMediaFilter = { viewModel.setFavoritesMediaFilter(it) },
                                    onPlaySlideshow = { items ->
                                        activeSlideshow = "Best of Favorites" to items
                                    },
                                    onShareFavorites = { items ->
                                        shareMediaItems(context, items)
                                    },
                                    onPrintOrExport = { items ->
                                        shareMediaItems(context, items, chooserTitle = "Print or Export Favorites")
                                    },
                                    onOpenSearch = {
                                        lastTab = GalleryBottomTab.SEARCH
                                        currentDestination = GalleryDestination.MainTab(GalleryBottomTab.SEARCH)
                                    },
                                    onOpenSettings = {
                                        currentDestination = GalleryDestination.Settings
                                    }
                                )
                            }

                            GalleryBottomTab.SEARCH -> {
                                SearchExploreScreen(
                                    uiState = uiState,
                                    onQueryChange = { viewModel.setSearchQuery(it) },
                                    onSubmitQuery = { viewModel.submitSearchQuery(it) },
                                    onSelectMediaFilter = { viewModel.setSearchMediaFilter(it) },
                                    onSelectDateFilter = { viewModel.setSearchDateFilter(it) },
                                    onSelectAlbumFilter = { viewModel.setSearchAlbumFilter(it) },
                                    onClearAllFilters = { viewModel.clearSearchFilters() },
                                    onRemoveRecentSearch = { viewModel.removeRecentSearch(it) },
                                    onClearRecentSearches = { viewModel.clearRecentSearches() },
                                    onOpenPhoto = { item ->
                                        currentDestination = GalleryDestination.PhotoViewer(item.uri.toString())
                                    },
                                    onToggleSelect = { item ->
                                        viewModel.toggleSelection(item.uri.toString())
                                    },
                                    onEnterSelectionMode = { viewModel.enterSelectionMode() },
                                    onClearSelection = { viewModel.clearSelection() },
                                    onSelectAll = { selectAll ->
                                        viewModel.selectItems(uiState.searchResults, selectAll)
                                    }
                                )
                            }
                        }
                    }

                    is GalleryDestination.AlbumDetail -> {
                        val album = uiState.albums.find { it.id == dest.albumId }
                        if (album != null) {
                            AlbumDetailScreen(
                                album = album,
                                uiState = uiState,
                                onBack = {
                                    viewModel.clearSelection()
                                    currentDestination = GalleryDestination.MainTab(GalleryBottomTab.ALBUMS)
                                },
                                onOpenPhoto = { item ->
                                    currentDestination = GalleryDestination.PhotoViewer(
                                        initialUri = item.uri.toString(),
                                        sourceAlbumId = album.id
                                    )
                                },
                                onToggleSelect = { item ->
                                    viewModel.toggleSelection(item.uri.toString())
                                },
                                onEnterSelectionMode = { viewModel.enterSelectionMode() },
                                onClearSelection = { viewModel.clearSelection() },
                                onSelectAll = { selectAll ->
                                    viewModel.selectItems(album.items, selectAll)
                                },
                                onPlaySlideshow = { items ->
                                    activeSlideshow = album.title to items
                                },
                                onMovePhotosIntoThisAlbum = { items ->
                                    viewModel.assignItemsToAlbum(items, album.title, moveItems = true)
                                },
                                onRemoveSelectedFromAlbum = { items ->
                                    viewModel.removeItemsFromCustomAlbum(items, album.title)
                                },
                                onDeleteCustomAlbum = {
                                    viewModel.deleteCustomAlbum(album.title)
                                    currentDestination = GalleryDestination.MainTab(GalleryBottomTab.ALBUMS)
                                }
                            )
                        } else {
                            currentDestination = GalleryDestination.MainTab(GalleryBottomTab.ALBUMS)
                        }
                    }

                    is GalleryDestination.PhotoViewer -> {
                        val sourceList = remember(dest.sourceAlbumId, uiState.allMedia, uiState.albums, uiState.filteredFavorites) {
                            when (dest.sourceAlbumId) {
                                "system_favorites" -> uiState.filteredFavorites.ifEmpty { uiState.allMedia }
                                null -> uiState.allMedia
                                else -> uiState.albums.find { it.id == dest.sourceAlbumId }?.items?.ifEmpty { uiState.allMedia }
                                    ?: uiState.allMedia
                            }
                        }

                        val allAlbumNames = remember(uiState.albums) {
                            uiState.albums.map { it.title }
                        }

                        PhotoViewerScreen(
                            mediaList = sourceList,
                            initialMediaUri = dest.initialUri,
                            activeExif = uiState.activeExifTelemetry,
                            existingCustomAlbums = allAlbumNames,
                            onBack = {
                                currentDestination = if (dest.sourceAlbumId != null && dest.sourceAlbumId != "system_favorites") {
                                    GalleryDestination.AlbumDetail(dest.sourceAlbumId)
                                } else {
                                    GalleryDestination.MainTab(lastTab)
                                }
                            },
                            onLoadExif = { viewModel.loadExifFor(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onShareItem = { shareMediaItems(context, listOf(it)) },
                            onEditItem = { item ->
                                if (item.isVideo) {
                                    currentDestination = GalleryDestination.VideoEditor(item.uri.toString())
                                } else {
                                    currentDestination = GalleryDestination.PhotoEditor(item.uri.toString())
                                }
                            },
                            onSetAsWallpaperOrUseAs = { item ->
                                launchUseAsIntent(context, item) { fallbackMsg ->
                                    viewModel.showStatusMessage(fallbackMsg)
                                }
                            },
                            onPrintItem = { item ->
                                shareMediaItems(context, listOf(item), chooserTitle = "Print or Export Photo")
                            },
                            onDeleteItem = { item ->
                                viewModel.deleteItems(listOf(item))
                            },
                            onMoveToCustomAlbum = { item, albumName, moveMode ->
                                viewModel.assignItemsToAlbum(listOf(item), albumName, moveItems = moveMode)
                            },
                            onRenameItem = { item, newName ->
                                viewModel.renameMedia(item, newName)
                            },
                            onSavePhotoDetails = { item, caption, tags, album ->
                                viewModel.savePhotoDetails(item, caption, tags, album)
                            },
                            onExportExifReport = { item, exif ->
                                shareExifTelemetryReport(context, item, exif)
                            }
                        )
                    }

                    GalleryDestination.Settings -> {
                        SettingsScreen(
                            preferences = uiState.preferences,
                            totalMediaCount = uiState.allMedia.size,
                            favoritesCount = uiState.favorites.size,
                            onBack = {
                                currentDestination = GalleryDestination.MainTab(lastTab)
                            },
                            onSelectTheme = { viewModel.setThemeOption(it) },
                            onSelectGridDensity = { viewModel.setGridDensity(it) },
                            onSelectSortOption = { viewModel.setSortOption(it) },
                            onToggleShowVideos = { viewModel.setShowVideosInTimeline(it) },
                            onToggleShowVideoDuration = { viewModel.setShowVideoDurationBadges(it) },
                            onToggleShowMemories = { viewModel.setShowMemoriesSection(it) },
                            onLaunchPhotoPicker = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            onOpenPermissionsScreen = {
                                currentDestination = GalleryDestination.StatesInspector
                            }
                        )
                    }

                    GalleryDestination.StatesInspector -> {
                        PermissionAndStatesScreen(
                            permissionAccessLevel = uiState.permissionAccessLevel,
                            onRequestPermissions = {
                                permissionLauncher.launch(viewModel.requiredPermissions)
                            },
                            onLaunchPhotoPicker = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            onBack = {
                                currentDestination = GalleryDestination.MainTab(lastTab)
                            }
                        )
                    }

                    is GalleryDestination.PhotoEditor -> {
                        PhotoEditorScreen(
                            uri = Uri.parse(dest.mediaUri),
                            onNavigateBack = {
                                currentDestination = GalleryDestination.PhotoViewer(dest.mediaUri)
                            }
                        )
                    }

                    is GalleryDestination.VideoEditor -> {
                        VideoEditorScreen(
                            uri = Uri.parse(dest.mediaUri),
                            onNavigateBack = {
                                currentDestination = GalleryDestination.PhotoViewer(dest.mediaUri)
                            }
                        )
                    }

                        GalleryDestination.RecycleBin -> {
                            val recycleBinViewModel: com.example.viewmodel.RecycleBinViewModel = viewModel(
                                factory = com.example.viewmodel.RecycleBinViewModel.Factory(
                                    repository = viewModel.galleryRepository
                                )
                            )
                            com.example.ui.screens.RecycleBinScreen(
                                viewModel = recycleBinViewModel,
                                onBack = {
                                    currentDestination = GalleryDestination.MainTab(lastTab)
                                },
                                onOpenPhoto = { item ->
                                    currentDestination = GalleryDestination.PhotoViewer(item.uri.toString())
                                }
                            )
                        }
                    }
                }

                val showBottomChrome = currentDestination is GalleryDestination.MainTab ||
                    currentDestination is GalleryDestination.AlbumDetail

                if (showBottomChrome) {
                    AnimatedVisibility(
                        visible = uiState.selection.isSelectionMode,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        ContextualSelectionActionBar(
                            selectedCount = uiState.selection.selectedUris.size,
                            onClearSelection = { viewModel.clearSelection() },
                            onShareSelected = {
                                val selected = viewModel.getSelectedMediaItems()
                                shareMediaItems(context, selected)
                            },
                            onFavoriteSelected = {
                                viewModel.favoriteSelectedItems(true)
                            },
                            onAddToAlbumSelected = {
                                batchMoveMode = true
                                showBatchAssignAlbum = true
                            },

                            onDeleteSelected = {
                                if (uiState.selection.selectedUris.isNotEmpty()) {
                                    showBatchDeleteConfirm = true
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = !uiState.selection.isSelectionMode && currentDestination is GalleryDestination.MainTab,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        PorcelainFloatingBottomBar(
                            currentTab = (currentDestination as? GalleryDestination.MainTab)?.tab ?: lastTab,
                            onSelectTab = { tab ->
                                lastTab = tab
                                currentDestination = GalleryDestination.MainTab(tab)
                            }
                        )
                    }
                }

                val slideshow = activeSlideshow
                if (slideshow != null) {
                    SlideshowReelDialog(
                        title = slideshow.first,
                        items = slideshow.second,
                        onDismiss = { activeSlideshow = null }
                    )
                }
            }
        }

        if (showBatchDeleteConfirm) {
            MoveToBinConfirmationDialog(
                itemCount = uiState.selection.selectedUris.size,
                onConfirm = {
                    showBatchDeleteConfirm = false
                    viewModel.deleteSelectedItems()
                },
                onDismiss = { showBatchDeleteConfirm = false }
            )
        }

        if (showBatchAssignAlbum) {
            val customAlbumNames = uiState.albums.filter { it.isUserCreated }.map { it.title }
            CreateOrAssignAlbumDialog(
                existingCustomAlbums = customAlbumNames,
                selectedItemCount = uiState.selection.selectedUris.size,
                onMoveModeChanged = { batchMoveMode = it },
                onCreateNewAlbum = { name ->
                    viewModel.createCustomAlbum(
                        name = name,
                        assignSelected = true,
                        moveItems = batchMoveMode
                    )
                },
                onSelectAlbum = { name ->
                    val selected = viewModel.getSelectedMediaItems()
                    viewModel.assignItemsToAlbum(
                        items = selected,
                        albumName = name,
                        moveItems = batchMoveMode
                    )
                },
                onDismiss = { showBatchAssignAlbum = false }
            )
        }
    }
}

private fun shareMediaItems(
    context: Context,
    items: List<MediaItem>,
    chooserTitle: String = "Share Media"
) {
    if (items.isEmpty()) return
    runCatching {
        val intent = if (items.size == 1) {
            val single = items.first()
            Intent(Intent.ACTION_SEND).apply {
                type = single.mimeType
                putExtra(Intent.EXTRA_STREAM, single.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val uris = ArrayList<Uri>(items.map { it.uri })
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}

private fun launchExternalEditor(
    context: Context,
    item: MediaItem,
    onFallbackMessage: (String) -> Unit
) {
    runCatching {
        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(editIntent, "Edit ${item.displayName}")
        context.startActivity(chooser)
    }.onFailure {
        onFallbackMessage("No compatible external image editor found on device")
    }
}

private fun launchUseAsIntent(
    context: Context,
    item: MediaItem,
    onFallbackMessage: (String) -> Unit
) {
    runCatching {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(item.uri, item.mimeType)
            putExtra("mimeType", item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Set as"))
    }.onFailure {
        onFallbackMessage("Unable to launch system wallpaper handler")
    }
}

private fun shareExifTelemetryReport(
    context: Context,
    item: MediaItem,
    exif: ExifTelemetry
) {
    val report = buildString {
        appendLine("GALLERY — EXIF & TELEMETRY REPORT")
        appendLine("File: ${item.displayName}")
        appendLine("Album: ${item.effectiveAlbumName}")
        appendLine("Format: ${exif.formatBadge} (${item.mimeType})")
        appendLine("Resolution: ${item.megapixelsFormatted} (${item.resolutionFormatted})")
        appendLine("File Size: ${item.fileSizeFormatted}")
        appendLine("Camera: ${exif.cameraMake} ${exif.cameraModel}")
        appendLine("Lens: ${exif.lensModel}")
        appendLine("Exposure: ${exif.aperture} • ${exif.shutterSpeed} • ${exif.iso} • ${exif.focalLength} (${exif.focalLength35mm})")
        appendLine("Exposure Bias: ${exif.exposureBias} • Metering: ${exif.meteringMode} • Program: ${exif.exposureProgram}")
        appendLine("White Balance: ${exif.whiteBalance} • Flash: ${exif.flashState}")
        appendLine("Color Profile: ${exif.colorSpace} • ${exif.filmSimulation}")
        appendLine("Location: ${exif.locationName}")
        appendLine("Path: ${item.relativePath}")
        if (item.caption.isNotBlank()) {
            appendLine("Notes: ${item.caption}")
        }
    }
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EXIF Report — ${item.displayName}")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Export EXIF Telemetry"))
    }
}
