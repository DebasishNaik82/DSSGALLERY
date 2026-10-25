package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.viewmodel.RecycleBinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    viewModel: RecycleBinViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (com.example.model.MediaItem) -> Unit
) {
    val items by viewModel.recycleBinItems.collectAsState()
    val pendingIntentSender by viewModel.pendingDeleteIntentSender.collectAsState()

    val selectedIds = remember { androidx.compose.runtime.mutableStateListOf<Long>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    var showPermanentDeleteConfirm by remember { mutableStateOf(false) }
    var itemsToPermanentlyDelete by remember { mutableStateOf<List<com.example.data.local.RecycleBinEntity>>(emptyList()) }
    var isEmptyBinAction by remember { mutableStateOf(false) }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteConsentResult(approved = result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pendingIntentSender) {
        val sender = pendingIntentSender
        if (sender != null && viewModel.markConsentDialogLaunched(sender)) {
            runCatching {
                deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }.onFailure {
                viewModel.onDeleteConsentResult(approved = false)
            }
        }
    }

    LaunchedEffect(items) {
        val existingIds = items.map { it.mediaId }.toSet()
        selectedIds.removeAll { it !in existingIds }
        if (selectedIds.isEmpty() && isSelectionMode) {
            isSelectionMode = false
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds.clear()
    }
    BackHandler(enabled = !isSelectionMode) { onBack() }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { isSelectionMode = false; selectedIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val itemsToProcess = items.filter { selectedIds.contains(it.mediaId) }
                            viewModel.restoreItems(itemsToProcess)
                            isSelectionMode = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore")
                        }
                        IconButton(onClick = {
                            val itemsToProcess = items.filter { selectedIds.contains(it.mediaId) }
                            if (itemsToProcess.isNotEmpty()) {
                                itemsToPermanentlyDelete = itemsToProcess
                                isEmptyBinAction = false
                                viewModel.requestPermanentDelete(itemsToProcess) {
                                    showPermanentDeleteConfirm = true
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Recycle Bin") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (items.isNotEmpty()) {
                                    itemsToPermanentlyDelete = items
                                    isEmptyBinAction = true
                                    viewModel.requestPermanentDelete(items) {
                                        showPermanentDeleteConfirm = true
                                    }
                                }
                            },
                            enabled = items.isNotEmpty()
                        ) {
                            Text("Empty")
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (showPermanentDeleteConfirm) {
            com.example.ui.components.PermanentlyDeleteConfirmationDialog(
                itemCount = itemsToPermanentlyDelete.size,
                isEmptyBin = isEmptyBinAction,
                onConfirm = {
                    showPermanentDeleteConfirm = false
                    viewModel.deletePermanently(itemsToPermanentlyDelete)
                    if (isSelectionMode) {
                        isSelectionMode = false
                        selectedIds.clear()
                    }
                },
                onDismiss = {
                    showPermanentDeleteConfirm = false
                }
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(items, key = { it.uriString }) { item ->
                val mediaItem = com.example.model.MediaItem(
                    id = item.mediaId,
                    uri = android.net.Uri.parse(item.uriString),
                    displayName = item.displayName,
                    mimeType = item.mimeType,
                    isVideo = item.isVideo,
                    dateAddedMillis = item.deletedAtMillis,
                    dateModifiedMillis = item.deletedAtMillis,
                    dateTakenMillis = item.deletedAtMillis,
                    sizeBytes = 0,
                    width = 0,
                    height = 0
                )
                com.example.ui.components.MediaThumbnailTile(
                    item = mediaItem,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedIds.contains(item.mediaId),
                    showVideoDurationBadge = true,
                    onClick = {
                        if (isSelectionMode) {
                            if (selectedIds.contains(item.mediaId)) {
                                selectedIds.remove(item.mediaId)
                            } else {
                                selectedIds.add(item.mediaId)
                            }
                        } else {
                            onOpenPhoto(mediaItem)
                        }
                    },
                    onLongClick = {
                        isSelectionMode = true
                        if (!selectedIds.contains(item.mediaId)) {
                            selectedIds.add(item.mediaId)
                        }
                    },
                    modifier = Modifier.padding(4.dp).aspectRatio(1f)
                )
            }
        }
    }
}

