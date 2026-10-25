package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Transformer
import com.example.model.ExportResult
import com.example.model.VideoEditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class VideoEditorViewModel(application: Application) : AndroidViewModel(application) {

    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    private val _videoEditState = MutableStateFlow(VideoEditState())
    val videoEditState: StateFlow<VideoEditState> = _videoEditState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _exportResult = MutableStateFlow<ExportResult>(ExportResult.Idle)
    val exportResult: StateFlow<ExportResult> = _exportResult.asStateFlow()

    private var currentUri: Uri? = null

    init {
        _exoPlayer = ExoPlayer.Builder(application).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = duration
                        if (_videoEditState.value.endTimeMs == 0L) {
                            _videoEditState.value = _videoEditState.value.copy(endTimeMs = duration)
                        }
                    }
                }
            })
        }
        
        viewModelScope.launch {
            while (true) {
                _exoPlayer?.let {
                    _currentPosition.value = it.currentPosition
                    
                    // Loop within trim bounds
                    val state = _videoEditState.value
                    if (it.isPlaying && it.currentPosition >= state.endTimeMs) {
                        it.seekTo(state.startTimeMs)
                    }
                }
                delay(100)
            }
        }
        viewModelScope.launch {
            _videoEditState.collect {
                updateEffects()
            }
        }
    }

    private fun updateEffects() {
        val effects = mutableListOf<androidx.media3.common.Effect>()
        val state = _videoEditState.value

        if (state.brightness != 1f) {
            effects.add(androidx.media3.effect.RgbAdjustment.Builder()
                .setRedScale(state.brightness)
                .setGreenScale(state.brightness)
                .setBlueScale(state.brightness)
                .build()
            )
        }
        if (state.saturation != 1f) {
            effects.add(androidx.media3.effect.HslAdjustment.Builder()
                .adjustSaturation(state.saturation * 100f - 100f)
                .build()
            )
        }

        when (state.selectedFilter) {
            "Grayscale" -> effects.add(androidx.media3.effect.RgbFilter.createGrayscaleFilter())
            "Invert" -> effects.add(androidx.media3.effect.RgbFilter.createInvertedFilter())
        }

        _exoPlayer?.setVideoEffects(effects)
    }

    fun loadVideo(uri: Uri) {
        currentUri = uri
        _exoPlayer?.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
        _exoPlayer?.prepare()
    }

    fun togglePlayPause() {
        _exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(position: Long) {
        _exoPlayer?.seekTo(position)
    }

    fun updateTrim(start: Long, end: Long) {
        _videoEditState.value = _videoEditState.value.copy(startTimeMs = start, endTimeMs = end)
        _exoPlayer?.seekTo(start)
    }

    fun updateSpeed(speed: Float) {
        _videoEditState.value = _videoEditState.value.copy(speed = speed)
        _exoPlayer?.setPlaybackSpeed(speed)
    }

    fun updateVolume(volume: Float) {
        _videoEditState.value = _videoEditState.value.copy(volume = volume, isMuted = volume == 0f)
        _exoPlayer?.volume = volume
    }

    fun updateBrightness(value: Float) {
        _videoEditState.value = _videoEditState.value.copy(brightness = value)
    }

    fun updateContrast(value: Float) {
        _videoEditState.value = _videoEditState.value.copy(contrast = value)
    }

    fun updateSaturation(value: Float) {
        _videoEditState.value = _videoEditState.value.copy(saturation = value)
    }

    fun applyFilter(filterName: String) {
        _videoEditState.value = _videoEditState.value.copy(selectedFilter = filterName)
    }

    fun exportVideo() {
        val uri = currentUri ?: return
        val state = _videoEditState.value
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            _exportResult.value = ExportResult.Progress(0f)
            
            try {
                val outputFileName = "edited_video_${System.currentTimeMillis()}.mp4"
                val outputFile = java.io.File(context.externalCacheDir, outputFileName)
                
                val effects = mutableListOf<androidx.media3.common.Effect>()
                if (state.brightness != 1f) {
                    effects.add(androidx.media3.effect.RgbAdjustment.Builder()
                        .setRedScale(state.brightness)
                        .setGreenScale(state.brightness)
                        .setBlueScale(state.brightness)
                        .build())
                }
                if (state.saturation != 1f) {
                    effects.add(androidx.media3.effect.HslAdjustment.Builder()
                        .adjustSaturation(state.saturation * 100f - 100f)
                        .build())
                }
                when (state.selectedFilter) {
                    "Grayscale" -> effects.add(androidx.media3.effect.RgbFilter.createGrayscaleFilter())
                    "Invert" -> effects.add(androidx.media3.effect.RgbFilter.createInvertedFilter())
                }

                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(state.startTimeMs)
                            .setEndPositionMs(state.endTimeMs)
                            .build()
                    )
                    .build()

                val editedMediaItem = androidx.media3.transformer.EditedMediaItem.Builder(mediaItem)
                    .setEffects(androidx.media3.transformer.Effects(emptyList(), effects))
                    .setRemoveAudio(state.isMuted)
                    .build()

                val transformer = withContext(Dispatchers.Main) {
                    Transformer.Builder(context)
                        .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                        .build()
                }

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, result: androidx.media3.transformer.ExportResult) {
                        viewModelScope.launch(Dispatchers.IO) {
                            saveExportedVideoToMediaStore(outputFile)
                        }
                    }

                    override fun onError(composition: androidx.media3.transformer.Composition, result: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                        _exportResult.value = ExportResult.Error(exportException.message ?: "Unknown export error")
                    }
                }

                withContext(Dispatchers.Main) {
                    transformer.addListener(listener)
                    transformer.start(editedMediaItem, outputFile.absolutePath)
                }

                // Polling progress because Media3 Transformer doesn't have a direct progress callback in 1.5.1
                // Actually it has Transformer.getProgress(ProgressHolder)
                val progressHolder = androidx.media3.transformer.ProgressHolder()
                while (_exportResult.value is ExportResult.Progress) {
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        _exportResult.value = ExportResult.Progress(progressHolder.progress.toFloat() / 100f)
                    } else if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                        // wait
                    } else {
                        break
                    }
                    delay(500)
                }

            } catch (e: Exception) {
                _exportResult.value = ExportResult.Error(e.message ?: "Failed to start export")
            }
        }
    }

    private fun saveExportedVideoToMediaStore(file: java.io.File) {
        val context = getApplication<Application>()
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/Gallery")
        }

        val uri = context.contentResolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            _exportResult.value = ExportResult.Success(uri)
        } else {
            _exportResult.value = ExportResult.Error("Failed to save video to MediaStore")
        }
    }

    fun resetExportResult() {
        _exportResult.value = ExportResult.Idle
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
