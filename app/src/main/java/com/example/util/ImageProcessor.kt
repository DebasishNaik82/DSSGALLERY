package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.model.PhotoEditState
import com.example.model.AspectRatio
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ImageProcessor {

    fun applyAdjustments(
        original: Bitmap,
        state: PhotoEditState
    ): Bitmap {
        // 1. Base Transform (Rotate & Flip)
        val matrix = android.graphics.Matrix()
        if (state.rotation != 0f) matrix.postRotate(state.rotation)
        if (state.isFlippedHorizontal) matrix.postScale(-1f, 1f, original.width / 2f, original.height / 2f)
        if (state.isFlippedVertical) matrix.postScale(1f, -1f, original.width / 2f, original.height / 2f)

        var transformed = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

        // 2. Crop
        state.cropRect?.let { rect ->
            val left = (rect.left * transformed.width).toInt().coerceIn(0, transformed.width - 1)
            val top = (rect.top * transformed.height).toInt().coerceIn(0, transformed.height - 1)
            val right = (rect.right * transformed.width).toInt().coerceIn(left + 1, transformed.width)
            val bottom = (rect.bottom * transformed.height).toInt().coerceIn(top + 1, transformed.height)
            
            if (right > left && bottom > top) {
                transformed = Bitmap.createBitmap(transformed, left, top, right - left, bottom - top)
            }
        }

        val result = transformed.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 3. Color Adjustments
        val cm = ColorMatrix()
        
        // Brightness
        val brightness = state.brightness // 0 to 2
        val bMat = floatArrayOf(
            1f, 0f, 0f, 0f, (brightness - 1f) * 255f,
            0f, 1f, 0f, 0f, (brightness - 1f) * 255f,
            0f, 0f, 1f, 0f, (brightness - 1f) * 255f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(bMat))

        // Contrast
        val contrast = state.contrast // 0.5 to 1.5
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val cMat = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(cMat))

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(state.saturation)
        cm.postConcat(satMatrix)

        // Warmth & Tint
        val warmth = state.warmth // -1 to 1
        val tint = state.tint // -1 to 1
        val wtMat = floatArrayOf(
            1f + (warmth * 0.1f), 0f, 0f, 0f, 0f,
            0f, 1f + (tint * 0.1f), 0f, 0f, 0f,
            0f, 0f, 1f - (warmth * 0.1f), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(wtMat))

        // Highlights & Shadows
        // A simple way to adjust them is using a color matrix that maps intensities differently.
        // For a more professional result, one would use a LUT or a shader.
        // Here we'll use a simplified additive/subtractive approach.
        val hsMat = floatArrayOf(
            1f, 0f, 0f, 0f, (state.highlights * 0.2f + state.shadows * 0.1f) * 255f,
            0f, 1f, 0f, 0f, (state.highlights * 0.2f + state.shadows * 0.1f) * 255f,
            0f, 0f, 1f, 0f, (state.highlights * 0.2f + state.shadows * 0.1f) * 255f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(hsMat))

        // Whites & Blacks
        val wbMat = floatArrayOf(
            1f, 0f, 0f, 0f, (state.whites * 0.3f + state.blacks * 0.1f) * 255f,
            0f, 1f, 0f, 0f, (state.whites * 0.3f + state.blacks * 0.1f) * 255f,
            0f, 0f, 1f, 0f, (state.whites * 0.3f + state.blacks * 0.1f) * 255f,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(wbMat))

        // Hue
        if (state.hue != 0f) {
            val hueMat = ColorMatrix()
            // Hue rotation is usually done by rotating around the luminance axis
            // setRotate(0, angle) is for Red, 1 for Green, 2 for Blue. 
            // We need a proper hue rotation matrix.
            val cosVal = cos(state.hue * PI / 180f).toFloat()
            val sinVal = sin(state.hue * PI / 180f).toFloat()
            val lumR = 0.213f
            val lumG = 0.715f
            val lumB = 0.072f
            val hueRotationMat = floatArrayOf(
                lumR + cosVal * (1 - lumR) + sinVal * (-lumR), lumG + cosVal * (-lumG) + sinVal * (-lumG), lumB + cosVal * (-lumB) + sinVal * (1 - lumB), 0f, 0f,
                lumR + cosVal * (-lumR) + sinVal * (0.143f), lumG + cosVal * (1 - lumG) + sinVal * (0.140f), lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0f, 0f,
                lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR)), lumG + cosVal * (-lumG) + sinVal * (lumG), lumB + cosVal * (1 - lumB) + sinVal * (lumB), 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            cm.postConcat(ColorMatrix(hueRotationMat))
        }

        // Vibrance (Simple boost to saturation)
        if (state.vibrance != 1f) {
            val vMat = ColorMatrix()
            vMat.setSaturation(state.vibrance)
            cm.postConcat(vMat)
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
            isAntiAlias = true
        }
        
        canvas.drawBitmap(result, 0f, 0f, paint)

        // 4. Effects
        if (state.sharpness != 0f) {
            applySharpness(result, state.sharpness)
        }
        if (state.vignette != 0f) {
            applyVignette(canvas, result.width, result.height, state.vignette)
        }
        if (state.grain != 0f) {
            applyGrain(result, state.grain)
        }

        // 5. Overlays (Text, Stickers, Drawings)
        drawOverlays(canvas, state)

        return result
    }

    private fun applySharpness(bitmap: Bitmap, intensity: Float) {
        if (intensity == 0f) return
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val resultPixels = IntArray(width * height)
        // Simple Sharpening Kernel
        // 0 -1 0
        // -1 5 -1
        // 0 -1 0
        val kernel = floatArrayOf(
            0f, -intensity, 0f,
            -intensity, 1f + 4f * intensity, -intensity,
            0f, -intensity, 0f
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * width + (x + kx)]
                        val weight = kernel[(ky + 1) * 3 + (kx + 1)]
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                resultPixels[y * width + x] = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
            }
        }
        bitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
    }

    private fun applyVignette(canvas: Canvas, width: Int, height: Int, intensity: Float) {
        val radius = Math.sqrt((width * width + height * height).toDouble()).toFloat() / 2f
        val colors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK)
        val stops = floatArrayOf(0f, 0.7f - (intensity * 0.3f), 1f)
        val gradient = android.graphics.RadialGradient(
            width / 2f, height / 2f, radius,
            colors, stops, android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
            alpha = (intensity * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun applyGrain(bitmap: Bitmap, intensity: Float) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val random = java.util.Random()
        val amount = (intensity * 50).toInt()
        
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            
            val noise = random.nextInt(amount * 2 + 1) - amount
            
            pixels[i] = Color.rgb(
                (r + noise).coerceIn(0, 255),
                (g + noise).coerceIn(0, 255),
                (b + noise).coerceIn(0, 255)
            )
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun drawOverlays(canvas: Canvas, state: PhotoEditState) {
        // Drawings
        val drawPaint = Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        state.drawings.forEach { stroke ->
            drawPaint.color = stroke.color
            drawPaint.strokeWidth = stroke.strokeWidth
            val path = android.graphics.Path()
            if (stroke.pathPoints.isNotEmpty()) {
                path.moveTo(stroke.pathPoints[0].x * canvas.width, stroke.pathPoints[0].y * canvas.height)
                for (i in 1 until stroke.pathPoints.size) {
                    path.lineTo(stroke.pathPoints[i].x * canvas.width, stroke.pathPoints[i].y * canvas.height)
                }
            }
            canvas.drawPath(path, drawPaint)
        }

        // Text
        val textPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        state.textLayers.forEach { layer ->
            textPaint.color = layer.color
            textPaint.textSize = layer.fontSize
            
            canvas.save()
            canvas.translate(layer.x * canvas.width, layer.y * canvas.height)
            canvas.rotate(layer.rotation)
            canvas.scale(layer.scale, layer.scale)
            canvas.drawText(layer.text, 0f, 0f, textPaint)
            canvas.restore()
        }
        
        // Stickers could be drawn here similarly by loading bitmaps from resources
    }

    fun applyFilter(original: Bitmap, filterName: String): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        
        when (filterName.lowercase()) {
            "vivid" -> {
                cm.setSaturation(1.6f)
                val contrast = 1.1f
                val scale = contrast
                val translate = (-0.5f * scale + 0.5f) * 255f
                val cMat = floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(cMat))
            }
            "warm" -> {
                val wMat = floatArrayOf(
                    1.15f, 0f, 0f, 0f, 10f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 0.85f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(wMat))
            }
            "cool" -> {
                val cMat = floatArrayOf(
                    0.85f, 0f, 0f, 0f, -5f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 1.2f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(cMat))
            }
            "bw", "monochrome" -> {
                cm.setSaturation(0f)
            }
            "vintage" -> {
                val vMat = floatArrayOf(
                    0.9f, 0.1f, 0.1f, 0f, 20f,
                    0.1f, 0.8f, 0.1f, 0f, 10f,
                    0.1f, 0.1f, 0.7f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(vMat))
            }
            "sepia" -> {
                val sMat = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(sMat))
            }
            "cinematic" -> {
                val cMat = floatArrayOf(
                    0.9f, 0f, 0f, 0f, -10f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.1f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.postConcat(ColorMatrix(cMat))
                val sat = ColorMatrix()
                sat.setSaturation(0.8f)
                cm.postConcat(sat)
            }
            else -> return original
        }

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
            isAntiAlias = true
        }
        val canvas = Canvas(result)
        canvas.drawBitmap(result, 0f, 0f, paint)
        return result
    }
}
