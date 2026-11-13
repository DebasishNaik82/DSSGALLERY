package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun RenameDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lastDot = currentName.lastIndexOf('.')
    val hasExtension = lastDot > 0 && lastDot < currentName.length - 1
    val initialBase = if (hasExtension) currentName.substring(0, lastDot) else currentName
    val extension = if (hasExtension) currentName.substring(lastDot + 1) else ""

    var newName by remember(currentName) { mutableStateOf(initialBase) }

    val trimmed = newName.trim()
    val forbiddenChars = remember { charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000') }
    val validationError = when {
        trimmed.isEmpty() -> "File name cannot be empty"
        trimmed == "." || trimmed == ".." -> "Invalid file name"
        trimmed.length > 200 -> "File name is too long"
        trimmed.any { ch -> ch in forbiddenChars || ch.code < 32 } ->
            "Invalid characters: \\ / : * ? \" < > |"
        else -> null
    }
    val isUnchanged = trimmed == initialBase
    val isSaveEnabled = validationError == null && !isUnchanged

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename file") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    suffix = {
                        if (extension.isNotEmpty()) {
                            Text(".$extension")
                        }
                    },
                    isError = validationError != null,
                    supportingText = {
                        if (validationError != null) {
                            Text(
                                text = validationError,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (extension.isNotEmpty()) {
                            Text("Extension .$extension will be preserved")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullName = if (extension.isNotEmpty()) "$trimmed.$extension" else trimmed
                    onRename(fullName)
                },
                enabled = isSaveEnabled,
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("rename_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
