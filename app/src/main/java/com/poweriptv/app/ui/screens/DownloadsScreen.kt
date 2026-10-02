package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.download.DownloadEntry
import com.poweriptv.app.download.DownloadStatus
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.formatBytes
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus

@Composable
fun DownloadsScreen(container: AppContainer, onBack: () -> Unit) {
    val entries by container.downloads.entries.collectAsState()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<DownloadEntry?>(null) }

    Scaffold(
        topBar = { PowerTopBar("Downloads (offline)", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (entries.isEmpty()) {
            ErrorBox(
                "Noch keine Downloads.\nTippe bei einem Film oder einer Episode auf das Download-Symbol, um sie offline anzusehen.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val sorted = entries.sortedByDescending { it.createdAt }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
            items(sorted, key = { it.id }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .tvFocus(RoundedCornerShape(10.dp), 1.02f)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = e.status == DownloadStatus.COMPLETED) {
                            val done = sorted.filter { it.status == DownloadStatus.COMPLETED }
                            startPlayback(
                                context, container,
                                done.map { PlayEntry(it.title, it.filePath, null, live = false) },
                                done.indexOf(e),
                            )
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!e.poster.isNullOrBlank()) AsyncImage(e.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        val muted = MaterialTheme.colorScheme.onSurfaceVariant
                        when (e.status) {
                            DownloadStatus.COMPLETED -> Text("Offline verfuegbar · ${formatBytes(e.total)}", color = muted, style = MaterialTheme.typography.bodySmall)
                            DownloadStatus.FAILED -> Text("Fehler: ${e.error ?: "unbekannt"}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            else -> {
                                if (e.total > 0) LinearProgressIndicator(progress = { e.progress }, modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    when (e.status) {
                                        DownloadStatus.QUEUED -> "Wartet..."
                                        DownloadStatus.PAUSED -> "Pausiert · ${formatBytes(e.downloaded)}"
                                        else -> "${formatBytes(e.downloaded)} / ${formatBytes(e.total)}"
                                    },
                                    color = muted, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    when (e.status) {
                        DownloadStatus.COMPLETED -> Icon(Icons.Filled.PlayArrow, "Abspielen", modifier = Modifier.padding(8.dp))
                        DownloadStatus.RUNNING, DownloadStatus.QUEUED ->
                            IconButton(onClick = { container.downloads.pause(e.id) }) { Icon(Icons.Filled.Pause, "Pausieren") }
                        DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                            IconButton(onClick = { container.downloads.resume(e.id) }) { Icon(Icons.Filled.Refresh, "Fortsetzen") }
                    }
                    IconButton(onClick = { toDelete = e }) { Icon(Icons.Filled.Delete, "Loeschen") }
                }
            }
        }
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Download loeschen?") },
            text = { Text("\"${e.title}\" wird vom Geraet entfernt.") },
            confirmButton = { TextButton(onClick = { container.downloads.delete(e.id); toDelete = null }) { Text("Loeschen") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}
