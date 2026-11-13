package com.example.ui.components

import android.graphics.Picture
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SvgImage(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var picture by remember(uri) { mutableStateOf<Picture?>(null) }
    var loadError by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        loadError = false
        picture = null
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val svg = SVG.getFromInputStream(inputStream)
                    val rendered = svg.renderToPicture()
                    if (rendered.width <= 0 || rendered.height <= 0) {
                        svg.renderToPicture(1024, 1024)
                    } else {
                        rendered
                    }
                }
            }.onSuccess { result ->
                if (result != null) {
                    picture = result
                } else {
                    loadError = true
                }
            }.onFailure { error ->
                Log.e("SvgImage", "Failed to render SVG: $uri", error)
                loadError = true
            }
        }
    }

    val loadedPicture = picture
    if (loadedPicture != null && loadedPicture.width > 0 && loadedPicture.height > 0) {
        Canvas(modifier = modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val picWidth = loadedPicture.width.toFloat().coerceAtLeast(1f)
                val picHeight = loadedPicture.height.toFloat().coerceAtLeast(1f)
                val scaleX = size.width / picWidth
                val scaleY = size.height / picHeight
                val scale = minOf(scaleX, scaleY)

                nativeCanvas.save()
                nativeCanvas.translate(
                    (size.width - picWidth * scale) / 2f,
                    (size.height - picHeight * scale) / 2f
                )
                nativeCanvas.scale(scale, scale)
                nativeCanvas.drawPicture(loadedPicture)
                nativeCanvas.restore()
            }
        }
    } else if (loadError) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Unable to display SVG image",
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}
