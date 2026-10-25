package com.example.viewmodel

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GalleryRepository
import com.example.data.local.RecycleBinEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecycleBinViewModel(private val repository: GalleryRepository) : ViewModel() {

    val recycleBinItems: StateFlow<List<RecycleBinEntity>> = repository.getRecycleBinFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingDeleteIntentSender: StateFlow<IntentSender?> = repository.pendingDeleteIntentSender

    val statusMessage: StateFlow<String?> = repository.deleteStatusMessage

    fun restoreItems(items: List<RecycleBinEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.restoreFromRecycleBin(items)
        }
    }

    fun requestPermanentDelete(
        items: List<RecycleBinEntity>,
        onShowCustomConfirmation: () -> Unit
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val requiresConsent = repository.requiresSystemConsent(items)
            if (requiresConsent) {
                // Android's system consent dialog acts as the sole confirmation
                deletePermanently(items)
            } else {
                // System consent will not be shown by Android; display custom confirmation dialog
                onShowCustomConfirmation()
            }
        }
    }

    fun deletePermanently(
        items: List<RecycleBinEntity>,
        onIntentSenderRequired: ((IntentSender) -> Unit)? = null
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            try {
                val intentSender = repository.permanentlyDeleteFromRecycleBin(items)
                if (intentSender != null) {
                    onIntentSenderRequired?.invoke(intentSender)
                }
            } catch (_: Exception) {
                repository.setDeleteStatusMessage("Failed to permanently delete")
            }
        }
    }

    fun markConsentDialogLaunched(sender: IntentSender): Boolean {
        return repository.markConsentDialogLaunched(sender)
    }

    fun onDeleteConsentResult(approved: Boolean) {
        viewModelScope.launch {
            repository.handleDeleteConsentResult(approved)
        }
    }

    fun dismissStatusMessage() {
        repository.clearDeleteStatusMessage()
    }

    class Factory(private val repository: GalleryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecycleBinViewModel(repository) as T
        }
    }
}

