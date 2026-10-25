package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AlbumSelectorDialog(
    existingCustomAlbums: List<String>,
    onSelectAlbum: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text("Select Album") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(existingCustomAlbums) { albumName ->
                    ListItem(
                        headlineContent = { Text(albumName) },
                        modifier = Modifier.clickable { onSelectAlbum(albumName) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
