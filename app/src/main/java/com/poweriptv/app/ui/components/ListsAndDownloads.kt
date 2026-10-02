package com.poweriptv.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.download.DownloadStatus
import com.poweriptv.app.ui.theme.Success

/** Button "Zu Liste hinzufuegen" mit Dialog fuer eigene Favoritenlisten. */
@Composable
fun AddToListButton(container: AppContainer, item: ContentItem, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Zu Liste hinzufuegen", tint = tint) }
    if (open) AddToListDialog(container, item) { open = false }
}

@Composable
fun AddToListDialog(container: AppContainer, item: ContentItem, onDismiss: () -> Unit) {
    val lists by container.favorites.lists.collectAsState()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu Liste hinzufuegen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                lists.forEach { l ->
                    val checked = l.items.any { it.key == item.key }
                    Row(
                        Modifier.fillMaxWidth().clickable { container.favorites.toggleInList(l.id, item) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { container.favorites.toggleInList(l.id, item) })
                        Text("${l.name} (${l.items.size})")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Neue Liste...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val id = container.favorites.createList(newName)
                            container.favorites.toggleInList(id, item)
                            newName = ""
                        },
                    ) { Text("Anlegen") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}

/** Download-Button mit Statusanzeige (fuer Filme und Episoden). */
@Composable
fun DownloadButton(container: AppContainer, title: String, url: String, extension: String?, poster: String?) {
    val entries by container.downloads.entries.collectAsState()
    val entry = entries.firstOrNull { it.url == url }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    IconButton(onClick = {
        if (entry == null || entry.status == DownloadStatus.FAILED || entry.status == DownloadStatus.PAUSED) {
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            container.downloads.enqueue(title, url, extension, poster)
        }
    }) {
        when (entry?.status) {
            null, DownloadStatus.PAUSED -> Icon(Icons.Filled.Download, "Herunterladen")
            DownloadStatus.QUEUED -> Icon(Icons.Filled.Downloading, "In Warteschlange")
            DownloadStatus.RUNNING -> CircularProgressIndicator(
                progress = { entry!!.progress }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp,
            )
            DownloadStatus.COMPLETED -> Icon(Icons.Filled.CloudDone, "Offline verfuegbar", tint = Success)
            DownloadStatus.FAILED -> Icon(Icons.Filled.ErrorOutline, "Fehlgeschlagen – erneut versuchen", tint = MaterialTheme.colorScheme.error)
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "?"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
fun SectionHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
}
