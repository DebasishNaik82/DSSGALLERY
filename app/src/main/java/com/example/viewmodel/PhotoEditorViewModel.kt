package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.PhotoEditState
import com.example.util.FileUtil
import com.example.util.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    private val _editedBitmap = MutableStateFlow<Bitmap?>(null)
    val editedBitmap: StateFlow<Bitmap?> = _editedBitmap.asStateFlow()

    private val _editState = MutableStateFlow(PhotoEditState())
    val editState: StateFlow<PhotoEditState> = _editState.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _saveSuccess = MutableStateFlow<Uri?>(null)
    val saveSuccess: StateFlow<Uri?> = _saveSuccess.asStateFlow()

    private val _history = MutableStateFlow(listOf(PhotoEditState()))
    private val _historyIndex = MutableStateFlow(0)

    val canUndo: StateFlow<Boolean> = combine(_history, _historyIndex) { h, i -> i > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canRedo: StateFlow<Boolean> = combine(_history, _historyIndex) { h, i -> i < h.size - 1 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun loadBitmap(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                _originalBitmap.value = bitmap
                _editedBitmap.value = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun updateState(newState: PhotoEditState) {
        val currentHistory = _history.value.take(_historyIndex.value + 1)
        _history.value = currentHistory + newState
        _historyIndex.value = _history.value.size - 1
        _editState.value = newState
        applyChanges()
    }

    fun undo() {
        if (_historyIndex.value > 0) {
            _historyIndex.value -= 1
            _editState.value = _history.value[_historyIndex.value]
            applyChanges()
        }
    }

    fun redo() {
        if (_historyIndex.value < _history.value.size - 1) {
            _historyIndex.value += 1
            _editState.value = _history.value[_historyIndex.value]
            applyChanges()
        }
    }

    fun reset() {
        updateState(PhotoEditState())
    }

    fun updateBrightness(value: Float) = updateState(_editState.value.copy(brightness = value))
    fun updateContrast(value: Float) = updateState(_editState.value.copy(contrast = value))
    fun updateSaturation(value: Float) = updateState(_editState.value.copy(saturation = value))
    fun updateWarmth(value: Float) = updateState(_editState.value.copy(warmth = value))
    fun updateTint(value: Float) = updateState(_editState.value.copy(tint = value))
    fun updateExposure(value: Float) = updateState(_editState.value.copy(exposure = value))
    fun updateHighlights(value: Float) = updateState(_editState.value.copy(highlights = value))
    fun updateShadows(value: Float) = updateState(_editState.value.copy(shadows = value))
    fun updateWhites(value: Float) = updateState(_editState.value.copy(whites = value))
    fun updateBlacks(value: Float) = updateState(_editState.value.copy(blacks = value))
    fun updateHue(value: Float) = updateState(_editState.value.copy(hue = value))
    fun updateVibrance(value: Float) = updateState(_editState.value.copy(vibrance = value))
    fun updateSharpness(value: Float) = updateState(_editState.value.copy(sharpness = value))
    fun updateClarity(value: Float) = updateState(_editState.value.copy(clarity = value))
    fun updateTexture(value: Float) = updateState(_editState.value.copy(texture = value))
    fun updateDehaze(value: Float) = updateState(_editState.value.copy(dehaze = value))
    fun updateFade(value: Float) = updateState(_editState.value.copy(fade = value))
    fun updateVignette(value: Float) = updateState(_editState.value.copy(vignette = value))
    fun updateGrain(value: Float) = updateState(_editState.value.copy(grain = value))

    fun rotate(degrees: Float) {
        val newRotation = (_editState.value.rotation + degrees) % 360f
        updateState(_editState.value.copy(rotation = newRotation))
    }

    fun flipHorizontal() {
        updateState(_editState.value.copy(isFlippedHorizontal = !_editState.value.isFlippedHorizontal))
    }

    fun flipVertical() {
        updateState(_editState.value.copy(isFlippedVertical = !_editState.value.isFlippedVertical))
    }

    fun applyFilter(filterName: String) {
        updateState(_editState.value.copy(selectedFilter = filterName))
    }

    fun addTextLayer(text: String, color: Int) {
        val newLayer = com.example.model.TextLayer(
            id = java.util.UUID.randomUUID().toString(),
            text = text,
            color = color,
            x = 0.5f,
            y = 0.5f
        )
        updateState(_editState.value.copy(textLayers = _editState.value.textLayers + newLayer))
    }

    fun addDrawing(stroke: com.example.model.DrawingStroke) {
        updateState(_editState.value.copy(drawings = _editState.value.drawings + stroke))
    }

    private var processingJob: kotlinx.coroutines.Job? = null

    private fun applyChanges() {
        val original = _originalBitmap.value ?: return
        processingJob?.cancel()
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            val state = _editState.value
            var result = ImageProcessor.applyAdjustments(original, state)
            if (state.selectedFilter != "None") {
                result = ImageProcessor.applyFilter(result, state.selectedFilter)
            }
            withContext(Dispatchers.Main) {
                _editedBitmap.value = result
                _isProcessing.value = false
            }
        }
    }

    fun saveImage() {
        val bitmap = _editedBitmap.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            val uri = FileUtil.saveBitmapToGallery(getApplication(), bitmap, "GalleryEdit_${System.currentTimeMillis()}")
            _saveSuccess.value = uri
            _isProcessing.value = false
        }
    }

    fun applySmooth() { android.util.Log.d("PhotoEditor", "Apply Smooth") }
    fun applyBlemish() { android.util.Log.d("PhotoEditor", "Apply Blemish") }
    fun applyBlur() { android.util.Log.d("PhotoEditor", "Apply Blur") }
    fun applyStickers() { android.util.Log.d("PhotoEditor", "Apply Stickers") }
    fun updateAspectRatio(ratio: com.example.model.AspectRatio) {
        updateState(_editState.value.copy(selectedAspectRatio = ratio))
    }

    fun updateSmoothIntensity(value: Float) = updateState(_editState.value.copy(smoothIntensity = value))
    fun updateBlemishIntensity(value: Float) = updateState(_editState.value.copy(blemishIntensity = value))
    fun updateBlurIntensity(value: Float) = updateState(_editState.value.copy(blurIntensity = value))
}
