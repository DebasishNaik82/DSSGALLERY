package com.example.ui.screens

import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.ExifTelemetry
import com.example.model.MediaItem
import com.example.ui.components.CreateOrAssignAlbumDialog
import com.example.ui.components.DeleteMediaConfirmationDialog
import com.example.ui.components.ResponsiveText
import com.example.ui.components.ZoomableMediaPage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    mediaList: List<MediaItem>,
    initialMediaUri: String,
    activeExif: ExifTelemetry?,
    existingCustomAlbums: List<String>,
    onBack: () -> Unit,
    onLoadExif: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onShareItem: (MediaItem) -> Unit,
    onEditItem: (MediaItem) -> Unit,
    onSetAsWallpaperOrUseAs: (MediaItem) -> Unit,
    onPrintItem: (MediaItem) -> Unit,
    onDeleteItem: (MediaItem) -> Unit,
    onMoveToCustomAlbum: (MediaItem, String, Boolean) -> Unit,
    onRenameItem: (MediaItem, String) -> Unit,
    onSavePhotoDetails: (MediaItem, String, List<String>, String?) -> Unit,
    onExportExifReport: (MediaItem, ExifTelemetry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mediaList.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val initialIndex = remember(mediaList, initialMediaUri) {
        mediaList.indexOfFirst { it.uri.toString() == initialMediaUri }.coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaList.size }
    )

    val currentItem = mediaList.getOrNull(pagerState.currentPage) ?: mediaList.first()
    val exif = activeExif ?: ExifTelemetry()

    var showChrome by remember { mutableStateOf(true) }
    var showExifOverlayPanel by remember { mutableStateOf(false) }
    var showFullInfoScreen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAssignAlbumDialog by remember { mutableStateOf(false) }
    var moveModeSelected by remember { mutableStateOf(true) }
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showFullInfoScreen -> showFullInfoScreen = false
            showExifOverlayPanel -> showExifOverlayPanel = false
            else -> onBack()
        }
    }

    LaunchedEffect(currentItem.uri) {
        isCurrentPageZoomed = false
        onLoadExif(currentItem)
    }

    if (showFullInfoScreen) {
        PhotoInfoScreen(
            item = currentItem,
            exif = exif,
            existingCustomAlbums = existingCustomAlbums,
            onClose = { showFullInfoScreen = false },
            onToggleFavorite = { onToggleFavorite(currentItem) },
            onShare = { onShareItem(currentItem) },
            onMoveToAlbum = { albumName ->
                onMoveToCustomAlbum(currentItem, albumName, true)
            },
            onSaveDetails = { caption, tags ->
                onSavePhotoDetails(currentItem, caption, tags, currentItem.customAlbum)
            },
            onExportExif = {
                onExportExifReport(currentItem, exif)
            }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .testTag("photo_viewer_screen")
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentPageZoomed,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val item = mediaList[pageIndex]
            ZoomableMediaPage(
                item = item,
                onTapToggleChrome = {
                    if (showExifOverlayPanel) {
                        showExifOverlayPanel = false
                    } else {
                        showChrome = !showChrome
                    }
                },
                onZoomChanged = { zoomed ->
                    if (pageIndex == pagerState.currentPage) {
                        isCurrentPageZoomed = zoomed
                    }
                }
            )
        }

        // Top Immersive Toolbar
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.76f),
                                Color.Transparent
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                        ResponsiveText(
                            text = currentItem.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                            val dateFormatted = remember(currentItem.dateTakenMillis) {
                                SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US)
                                    .format(Date(currentItem.dateTakenMillis))
                            }
                            ResponsiveText(
                                text = "$dateFormatted • ${currentItem.effectiveAlbumName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Toggle Detailed EXIF Metadata Panel button
                        IconButton(
                            onClick = { showExifOverlayPanel = !showExifOverlayPanel },
                            modifier = Modifier.testTag("viewer_top_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Toggle EXIF Metadata Panel",
                                tint = if (showExifOverlayPanel) Color(0xFFE77D70) else Color.White
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("viewer_more_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("EXIF Metadata Panel") },
                                    leadingIcon = { Icon(Icons.Filled.Camera, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showExifOverlayPanel = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Full Photo Info & Journal") },
                                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showFullInfoScreen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Custom Album") },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showAssignAlbumDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onEditItem(currentItem)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Use As / Wallpaper") },
                                    leadingIcon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onSetAsWallpaperOrUseAs(currentItem)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Print / Export") },
                                    leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onPrintItem(currentItem)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Floating Chrome + Live EXIF HUD Strip + Expandable Detailed EXIF Panel
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expandable Detailed EXIF Metadata Panel (in-viewer overlay card)
                AnimatedVisibility(
                    visible = showExifOverlayPanel,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut()
                ) {
                    DetailedViewerExifMetadataPanel(
                        item = currentItem,
                        exif = exif,
                        onClose = { showExifOverlayPanel = false },
                        onOpenFullInspector = {
                            showExifOverlayPanel = false
                            showFullInfoScreen = true
                        },
                        onMoveToAlbumClick = { showAssignAlbumDialog = true },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Compact Live Camera EXIF Telemetry Strip (Always visible when chrome is shown)
                if (!showExifOverlayPanel) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF252322).copy(alpha = 0.88f),
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.14f),
                                shape = CircleShape
                            )
                            .clickable { showExifOverlayPanel = true }
                            .testTag("viewer_exif_summary_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Camera,
                                contentDescription = null,
                                tint = Color(0xFFE77D70),
                                modifier = Modifier.size(15.dp)
                            )
                            ResponsiveText(
                                text = exif.summaryStrip,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color.White.copy(alpha = 0.92f)
                            )
                            Icon(
                                imageVector = Icons.Filled.ExpandLess,
                                contentDescription = "Expand EXIF Panel",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Bottom Floating Action Bar
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1F1D1C).copy(alpha = 0.94f),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
                            shape = CircleShape
                        )
                        .testTag("viewer_bottom_action_bar")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ViewerActionPill(
                            icon = if (currentItem.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            label = "Favorite",
                            tint = if (currentItem.isFavorite) Color(0xFFE77D70) else Color.White,
                            onClick = { onToggleFavorite(currentItem) },
                            testTag = "viewer_favorite_button"
                        )
                        ViewerActionPill(
                            icon = Icons.Filled.Camera,
                            label = "EXIF",
                            tint = if (showExifOverlayPanel) Color(0xFFE77D70) else Color.White,
                            onClick = { showExifOverlayPanel = !showExifOverlayPanel },
                            testTag = "viewer_details_button"
                        )
                        ViewerActionPill(
                            icon = Icons.Filled.Edit,
                            label = "Edit",
                            onClick = { onEditItem(currentItem) },
                            testTag = "viewer_edit_button"
                        )
                        ViewerActionPill(
                            icon = Icons.Filled.DriveFileMove,
                            label = "Move",
                            onClick = { showAssignAlbumDialog = true },
                            testTag = "viewer_move_album_button"
                        )
                        ViewerActionPill(
                            icon = Icons.Filled.Share,
                            label = "Share",
                            onClick = { onShareItem(currentItem) },
                            testTag = "viewer_share_button"
                        )
                        ViewerActionPill(
                            icon = Icons.Filled.DeleteOutline,
                            label = "Delete",
                            tint = Color(0xFFFFB4AB),
                            onClick = { showDeleteConfirm = true },
                            testTag = "viewer_delete_button"
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        com.example.ui.components.MoveToBinConfirmationDialog(
            itemCount = 1,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteItem(currentItem)
                if (mediaList.size <= 1) {
                    onBack()
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    var showRenameDialog by remember { mutableStateOf(false) }

    // ... (previous code)

    if (showAssignAlbumDialog) {
        com.example.ui.components.AlbumSelectorDialog(
            existingCustomAlbums = existingCustomAlbums,
            onSelectAlbum = { albumName ->
                onMoveToCustomAlbum(currentItem, albumName, moveModeSelected)
                showAssignAlbumDialog = false
            },
            onDismiss = { showAssignAlbumDialog = false }
        )
    }

    if (showRenameDialog) {
        com.example.ui.components.RenameDialog(
            currentName = currentItem.displayName,
            onRename = { newName ->
                onRenameItem(currentItem, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun DetailedViewerExifMetadataPanel(
    item: MediaItem,
    exif: ExifTelemetry,
    onClose: () -> Unit,
    onOpenFullInspector: () -> Unit,
    onMoveToAlbumClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF1B1918).copy(alpha = 0.96f),
        tonalElevation = 12.dp,
        shadowElevation = 20.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(26.dp)
            )
            .testTag("viewer_exif_metadata_panel")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Camera Body, Lens Model, Format Badge & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFA03B25).copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Camera,
                            contentDescription = null,
                            tint = Color(0xFFFFB4A4),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ResponsiveText(
                                text = "${exif.cameraMake} ${exif.cameraModel}".trim(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFA03B25)
                            ) {
                                Text(
                                    text = exif.formatBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        ResponsiveText(
                            text = exif.lensModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = "Collapse EXIF Panel",
                        tint = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 9-Cell Camera Optical & Exposure Telemetry Grid (3x3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DarkExifCell(label = "ISO", value = exif.iso, modifier = Modifier.weight(1f))
                DarkExifCell(label = "SHUTTER", value = exif.shutterSpeed, modifier = Modifier.weight(1f))
                DarkExifCell(label = "APERTURE", value = exif.aperture, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DarkExifCell(
                    label = "FOCAL LENGTH",
                    value = "${exif.focalLength} (${exif.focalLength35mm.substringBefore(" ")})",
                    modifier = Modifier.weight(1f)
                )
                DarkExifCell(label = "EXP BIAS", value = exif.exposureBias, modifier = Modifier.weight(1f))
                DarkExifCell(label = "METERING", value = exif.meteringMode.substringBefore(" "), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DarkExifCell(label = "WHITE BAL", value = exif.whiteBalance, modifier = Modifier.weight(1f))
                DarkExifCell(label = "PROGRAM", value = exif.exposureProgram, modifier = Modifier.weight(1f))
                DarkExifCell(label = "FLASH", value = exif.flashState.substringBefore(','), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Luminance Histogram + Resolution & File Summary
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LUMINANCE HISTOGRAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.megapixelsFormatted} • ${item.resolutionFormatted} • ${item.fileSizeFormatted}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Text(
                            text = "${exif.colorSpace} • ${exif.filmSimulation}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB4A4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 32-bin Luminance Histogram Bar Chart
                    Canvas(
                        modifier = Modifier
                            .width(110.dp)
                            .height(38.dp)
                    ) {
                        val bins = exif.histogramBins
                        if (bins.isNotEmpty()) {
                            val barW = size.width / bins.size
                            bins.forEachIndexed { idx, norm ->
                                val barH = (norm * size.height).coerceAtLeast(3f)
                                drawRoundRect(
                                    color = Color(0xFFE77D70),
                                    topLeft = Offset(idx * barW, size.height - barH),
                                    size = Size((barW - 1.5f).coerceAtLeast(1.5f), barH),
                                    cornerRadius = CornerRadius(2f, 2f)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color.White.copy(alpha = 0.1f)
            )

            // Bottom Row: Album Mapping Badge + Move to Album + Full Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onMoveToAlbumClick)
                        .testTag("panel_move_album_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderSpecial,
                            contentDescription = null,
                            tint = Color(0xFFFFB4A4),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ResponsiveText(
                            text = "Album: ${item.effectiveAlbumName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Move",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFB4A4)
                        )
                    }
                }

                TextButton(
                    onClick = onOpenFullInspector,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("panel_open_full_info_button")
                ) {
                    Text(
                        text = "Full Info & Notes",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFFB4A4)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = null,
                        tint = Color(0xFFFFB4A4),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DarkExifCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.07f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
private fun ViewerActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    tint: Color = Color.White
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        ResponsiveText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
