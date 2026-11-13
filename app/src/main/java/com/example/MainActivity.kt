package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.example.ui.GalleryApp
import com.example.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels {
        GalleryViewModel.Factory(application)
    }

    private var externalIntentUris by mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        externalIntentUris = handleIntent(intent)

        setContent {
            GalleryApp(
                viewModel = viewModel,
                externalIntentData = externalIntentUris
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalIntentUris = handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): List<Uri>? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { listOf(it) }
            }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                uri?.let { listOf(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            }
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updatePermissionStatus()
    }
}
