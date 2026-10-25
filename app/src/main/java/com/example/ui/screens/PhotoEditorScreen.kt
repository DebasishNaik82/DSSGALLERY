package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.PhotoEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    uri: Uri,
    onNavigateBack: () -> Unit,
    viewModel: PhotoEditorViewModel = viewModel()
) {
    val editedBitmap by viewModel.editedBitmap.collectAsState()
    val editState by viewModel.editState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

    var selectedTab by remember { mutableStateOf("Adjust") }
    var showTextDialog by remember { mutableStateOf(false) }
    var showRetouchSheet by remember { mutableStateOf(false) }
    var showDesignSheet by remember { mutableStateOf(false) }
    var showTransformSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        viewModel.loadBitmap(uri)
    }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess != null) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            PhotoEditorTopBar(
                onCancel = onNavigateBack,
                onSave = { viewModel.saveImage() },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onReset = { viewModel.reset() },
                canUndo = canUndo,
                canRedo = canRedo
            )
        },
        bottomBar = {
            PhotoEditorBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                editState = editState,
                viewModel = viewModel,
                onShowTextDialog = { showTextDialog = true },
                onToggleDrawing = { /* toggle drawing */ },
                onShowRetouch = { showRetouchSheet = true },
                onShowDesign = { showDesignSheet = true },
                onShowTransform = { showTransformSheet = true }
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
            editedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Edited Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            }

            if (isProcessing) {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (showRetouchSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showRetouchSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Retouch", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Smooth Intensity")
                        Slider(value = editState.smoothIntensity, onValueChange = { viewModel.updateSmoothIntensity(it) }, valueRange = 0f..1f)
                        Text("Blemish Intensity")
                        Slider(value = editState.blemishIntensity, onValueChange = { viewModel.updateBlemishIntensity(it) }, valueRange = 0f..1f)
                        Text("Blur Intensity")
                        Slider(value = editState.blurIntensity, onValueChange = { viewModel.updateBlurIntensity(it) }, valueRange = 0f..1f)
                        Button(onClick = { showRetouchSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }

            if (showDesignSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showDesignSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Design", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { viewModel.applyStickers(); showDesignSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Add Stickers") }
                        Button(onClick = { showDesignSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
            
            if (showTransformSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showTransformSheet = false },
                    sheetState = rememberModalBottomSheetState()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Transform", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { viewModel.rotate(90f) }, modifier = Modifier.fillMaxWidth()) { Text("Rotate 90") }
                        Button(onClick = { showTransformSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoEditorTopBar(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    Surface(
        color = Color.Black.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                TextButton(onClick = onReset) {
                    Text("Reset", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PhotoEditorBottomBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    editState: com.example.model.PhotoEditState,
    viewModel: PhotoEditorViewModel,
    onShowTextDialog: () -> Unit,
    onToggleDrawing: () -> Unit,
    onShowRetouch: () -> Unit,
    onShowDesign: () -> Unit,
    onShowTransform: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Controls area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    "Adjust" -> AdjustmentControls(viewModel, editState)
                    "Retouch" -> RetouchControls(viewModel, editState, onShowRetouch)
                    "Filters" -> FilterControls(viewModel, editState)
                    "Design" -> DesignControls(viewModel, onShowTextDialog, onToggleDrawing, onShowDesign)
                    "Transform" -> TransformControls(viewModel, editState, onShowTransform)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tab strip
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                val tabs = listOf("Adjust", "Retouch", "Filters", "Design", "Transform")
                items(tabs) { tab ->
                    EditorTabItem(
                        label = tab,
                        isSelected = selectedTab == tab,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }
    }
}

@Composable
fun AdjustmentControls(viewModel: PhotoEditorViewModel, editState: com.example.model.PhotoEditState) {
    var selectedCategory by remember { mutableStateOf("Light") }
    var selectedAdj by remember { mutableStateOf("Exposure") }
    
    val categories = listOf("Light", "Color", "Detail", "Effects")
    val adjs = when(selectedCategory) {
        "Light" -> listOf("Exposure", "Brightness", "Contrast", "Highlights", "Shadows", "Whites", "Blacks")
        "Color" -> listOf("Saturation", "Vibrance", "Warmth", "Tint", "Hue")
        "Detail" -> listOf("Sharpness", "Clarity", "Texture", "Dehaze")
        "Effects" -> listOf("Vignette", "Grain", "Fade")
        else -> emptyList()
    }

    if (!adjs.contains(selectedAdj)) {
        selectedAdj = adjs.firstOrNull() ?: ""
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Category strip
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(categories) { cat ->
                Text(
                    text = cat,
                    color = if (selectedCategory == cat) MaterialTheme.colorScheme.primary else Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { selectedCategory = cat }
                )
            }
        }

        val currentValue = when(selectedAdj) {
            "Brightness" -> editState.brightness
            "Contrast" -> editState.contrast
            "Saturation" -> editState.saturation
            "Exposure" -> editState.exposure
            "Warmth" -> editState.warmth
            "Tint" -> editState.tint
            "Highlights" -> editState.highlights
            "Shadows" -> editState.shadows
            "Whites" -> editState.whites
            "Blacks" -> editState.blacks
            "Hue" -> editState.hue
            "Vibrance" -> editState.vibrance
            "Sharpness" -> editState.sharpness
            "Clarity" -> editState.clarity
            "Texture" -> editState.texture
            "Dehaze" -> editState.dehaze
            "Fade" -> editState.fade
            "Vignette" -> editState.vignette
            "Grain" -> editState.grain
            else -> 1f
        }
        
        Text(
            text = selectedAdj + ": " + if (selectedAdj == "Hue") "${currentValue.toInt()}°" else "${(currentValue * 100).toInt()}",
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
                    "Exposure" -> viewModel.updateExposure(it)
                    "Warmth" -> viewModel.updateWarmth(it)
                    "Tint" -> viewModel.updateTint(it)
                    "Highlights" -> viewModel.updateHighlights(it)
                    "Shadows" -> viewModel.updateShadows(it)
                    "Whites" -> viewModel.updateWhites(it)
                    "Blacks" -> viewModel.updateBlacks(it)
                    "Hue" -> viewModel.updateHue(it)
                    "Vibrance" -> viewModel.updateVibrance(it)
                    "Sharpness" -> viewModel.updateSharpness(it)
                    "Clarity" -> viewModel.updateClarity(it)
                    "Texture" -> viewModel.updateTexture(it)
                    "Dehaze" -> viewModel.updateDehaze(it)
                    "Fade" -> viewModel.updateFade(it)
                    "Vignette" -> viewModel.updateVignette(it)
                    "Grain" -> viewModel.updateGrain(it)
                }
            },
            valueRange = when(selectedAdj) {
                "Contrast", "Saturation", "Vibrance", "Brightness" -> 0f..2f
                "Hue" -> 0f..360f
                else -> -1f..1f
            },
            modifier = Modifier.padding(horizontal = 32.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary)
        )
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(adjs) { adj ->
                AdjustmentItem(
                    label = adj,
                    isSelected = selectedAdj == adj,
                    onClick = { selectedAdj = adj }
                )
            }
        }
    }
}

@Composable
fun RetouchControls(viewModel: PhotoEditorViewModel, editState: com.example.model.PhotoEditState, onShowRetouch: () -> Unit) {
    // Simplified retouching controls
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransformItem(label = "Smooth", icon = Icons.Default.Face, onClick = onShowRetouch)
        TransformItem(label = "Blemish", icon = Icons.Default.Healing, onClick = onShowRetouch)
        TransformItem(label = "Blur", icon = Icons.Default.BlurOn, onClick = onShowRetouch)
    }
}

@Composable
fun DesignControls(viewModel: PhotoEditorViewModel, onAddText: () -> Unit, onDraw: () -> Unit, onShowDesign: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransformItem(label = "Text", icon = Icons.Default.TextFields, onClick = onAddText)
        TransformItem(label = "Draw", icon = Icons.Default.Brush, onClick = onDraw)
        TransformItem(label = "Stickers", icon = Icons.Default.StickyNote2, onClick = onShowDesign)
    }
}

@Composable
fun TransformControls(viewModel: PhotoEditorViewModel, editState: com.example.model.PhotoEditState, onShowTransform: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransformItem(label = "Rotate", icon = Icons.Default.RotateRight, onClick = onShowTransform)
            TransformItem(label = "Flip H", icon = Icons.Default.Flip, onClick = { viewModel.flipHorizontal() })
            TransformItem(label = "Flip V", icon = Icons.Default.Flip, onClick = { viewModel.flipVertical() }, rotateIcon = 90f)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            val ratios = com.example.model.AspectRatio.entries
            items(ratios) { ratio ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (editState.selectedAspectRatio == ratio) MaterialTheme.colorScheme.primary else Color.DarkGray,
                    modifier = Modifier
                        .clickable { viewModel.updateAspectRatio(ratio) }
                        .padding(4.dp)
                ) {
                    Text(
                        text = ratio.label,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun AdjustmentItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun TransformItem(label: String, icon: ImageVector, onClick: () -> Unit, rotateIcon: Float = 0f) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp).graphicsLayer(rotationZ = rotateIcon)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun FilterControls(viewModel: PhotoEditorViewModel, editState: com.example.model.PhotoEditState) {
    val filters = listOf("None", "Vivid", "Warm", "Cool", "Cinematic", "Vintage", "BW", "Sepia")
    
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(filters) { filter ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { viewModel.applyFilter(filter) }
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (editState.selectedFilter == filter) MaterialTheme.colorScheme.primary else Color.DarkGray)
                        .border(
                            width = 2.dp,
                            color = if (editState.selectedFilter == filter) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(filter.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(
                    filter,
                    color = if (editState.selectedFilter == filter) MaterialTheme.colorScheme.primary else Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun EditorTabItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
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
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(16.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
