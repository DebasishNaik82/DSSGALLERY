package com.example.model

import android.graphics.RectF
import android.net.Uri

/**
 * Common aspect ratios for photo and video editing.
 */
enum class AspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", -1f),
    SQUARE("1:1", 1f),
    PHOTO_3_4("3:4", 0.75f),
    PHOTO_4_3("4:3", 1.333f),
    VIDEO_9_16("9:16", 0.5625f),
    VIDEO_16_9("16:9", 1.777f)
}

/**
 * Complete state for the photo editor.
 */
data class PhotoEditState(
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val exposure: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 1f,
    val hue: Float = 0f,
    val sharpness: Float = 0f,
    val clarity: Float = 0f,
    val texture: Float = 0f,
    val dehaze: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val smoothIntensity: Float = 0f,
    val blemishIntensity: Float = 0f,
    val blurIntensity: Float = 0f,
    val selectedFilter: String = "None",
    val rotation: Float = 0f,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false,
    val cropRect: RectF? = null,
    val selectedAspectRatio: AspectRatio = AspectRatio.FREE,
    val textLayers: List<TextLayer> = emptyList(),
    val stickers: List<StickerLayer> = emptyList(),
    val drawings: List<DrawingStroke> = emptyList()
)

data class TextLayer(
    val id: String,
    val text: String,
    val color: Int,
    val x: Float,
    val y: Float,
    val fontSize: Float = 24f,
    val rotation: Float = 0f,
    val scale: Float = 1f
)

data class StickerLayer(
    val id: String,
    val stickerId: String,
    val x: Float,
    val y: Float,
    val rotation: Float = 0f,
    val scale: Float = 1f
)

data class DrawingStroke(
    val pathPoints: List<android.graphics.PointF>,
    val color: Int,
    val strokeWidth: Float
)

/**
 * State for video editing.
 */
data class VideoEditState(
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val speed: Float = 1.0f,
    val rotation: Float = 0f,
    val isFlippedHorizontal: Boolean = false,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val selectedFilter: String = "None"
)

/**
 * Result of an export operation.
 */
sealed interface ExportResult {
    data object Idle : ExportResult
    data class Progress(val percentage: Float) : ExportResult
    data class Success(val uri: Uri) : ExportResult
    data class Error(val message: String) : ExportResult
}
