package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.download.DownloadStatus
import com.poweriptv.app.record.RecStatus
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.ConfirmDialog
import com.poweriptv.desktop.ui.Danger
import com.poweriptv.desktop.ui.NetImage
import com.poweriptv.desktop.ui.Page
import com.poweriptv.desktop.ui.Success
import com.poweriptv.desktop.ui.Surface
import com.poweriptv.desktop.ui.SurfaceHigh
import com.poweriptv.desktop.ui.formatBytes
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.desktop.ui.openFolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Aufnahmen – wie Android: geplant, laeuft, fertig; abspielen, stoppen, loeschen. */
@Composable
fun RecordingsScreen(app: AppState) {
    val entries by app.recordings.entries.collectAsState()
    var toDelete by remember { mutableStateOf<String?>(null) }
    val full = SimpleDateFormat("EEE dd.MM., HH:mm", Locale.GERMANY)
    val time = SimpleDateFormat("HH:mm", Locale.GERMANY)
    Page("Aufnahmen", actions = {
        TextButton(onClick = { openFolder(app.recordingDir()) }, modifier = Modifier.handCursor()) {
            Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Ordner öffnen")
        }
    }) {
        if (entries.isEmpty()) {
            EmptyHint("Keine Aufnahmen.\nPlane Aufnahmen im TV-Guide oder starte sie im Player mit dem Aufnahme-Knopf.")
        }
        entries.sortedByDescending { it.start }.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetImage(listOf(r.logo), Modifier.size(width = 70.dp, height = 46.dp), ContentScale.Fit)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.channelName} · ${full.format(Date(r.start))} – ${time.format(Date(r.end))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.status == RecStatus.RECORDING) Icon(Icons.Filled.FiberManualRecord, null, tint = Danger, modifier = Modifier.size(12.dp))
                        Text(
                            when (r.status) {
                                RecStatus.SCHEDULED -> "Geplant"
                                RecStatus.RECORDING -> " Nimmt auf · ${formatBytes(r.bytes)}"
                                RecStatus.COMPLETED -> "Fertig · ${formatBytes(r.bytes)}"
                                RecStatus.FAILED -> "Fehlgeschlagen: ${r.error ?: "unbekannt"}"
                                RecStatus.CANCELLED -> "Abgebrochen"
                            },
                            color = when (r.status) {
                                RecStatus.RECORDING -> Danger
                                RecStatus.COMPLETED -> Success
                                RecStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val file = File(r.filePath)
                if (file.exists() && file.length() > 0) {
                    IconButton(onClick = { app.playFile("${r.channelName}: ${r.title}", file, "Aufnahme") }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.PlayArrow, "Abspielen") }
                }
                if (r.status == RecStatus.SCHEDULED || r.status == RecStatus.RECORDING) {
                    IconButton(onClick = { app.recordings.stop(r.id) }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Stop, "Stoppen") }
                }
                IconButton(onClick = { toDelete = r.id }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Delete, "Löschen") }
            }
        }
    }
    toDelete?.let { id ->
        val r = app.recordings.get(id)
        ConfirmDialog("Aufnahme löschen?", "„${r?.title}“ wird vom PC gelöscht.", "Löschen", onDismiss = { toDelete = null }) { app.recordings.delete(id) }
    }
}

/** Downloads (offline) – wie Android, derselbe Download-Code (parallel, fortsetzbar). */
@Composable
fun DownloadsScreen(app: AppState) {
    val entries by app.downloads.entries.collectAsState()
    val speeds by app.downloads.speeds.collectAsState()
    var toDelete by remember { mutableStateOf<String?>(null) }
    Page("Downloads (offline)", actions = {
        TextButton(onClick = { openFolder(app.downloadDir()) }, modifier = Modifier.handCursor()) {
            Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Ordner öffnen")
        }
    }) {
        if (entries.isEmpty()) {
            EmptyHint("Noch keine Downloads.\nKlicke bei einem Film oder einer Episode auf „Herunterladen“, um sie offline anzusehen.")
        }
        entries.sortedByDescending { it.createdAt }.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetImage(listOf(e.poster), Modifier.size(width = 54.dp, height = 80.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceHigh))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val bps = speeds[e.id]
                    Text(
                        when (e.status) {
                            DownloadStatus.QUEUED -> "Wartet …"
                            DownloadStatus.RUNNING -> buildString {
                                append("${formatBytes(e.downloaded)} / ${formatBytes(e.total)}")
                                if (bps != null && bps > 0) {
                                    append("  ·  ${formatBytes(bps)}/s")
                                    if (e.total > 0) {
                                        val secs = (e.total - e.downloaded) / bps
                                        append("  ·  noch " + if (secs > 3600) "${secs / 3600} h ${(secs % 3600) / 60} min" else if (secs > 60) "${secs / 60} min" else "$secs s")
                                    }
                                }
                            }
                            DownloadStatus.PAUSED -> "Pausiert · ${formatBytes(e.downloaded)}"
                            DownloadStatus.COMPLETED -> "Offline verfügbar · ${formatBytes(e.total)}"
                            DownloadStatus.FAILED -> "Fehler: ${e.error ?: "unbekannt"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (e.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (e.status == DownloadStatus.RUNNING || e.status == DownloadStatus.PAUSED) {
                        LinearProgressIndicator(progress = { e.progress }, modifier = Modifier.fillMaxWidth().height(5.dp), color = BrandCyan)
                    }
                }
                when (e.status) {
                    DownloadStatus.COMPLETED -> IconButton(onClick = { app.playFile(e.title, File(e.filePath), "Download") }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.PlayArrow, "Abspielen") }
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> IconButton(onClick = { app.downloads.pause(e.id) }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Pause, "Pausieren") }
                    else -> OutlinedButton(onClick = { app.downloads.resume(e.id) }, modifier = Modifier.handCursor()) { Text("Fortsetzen") }
                }
                IconButton(onClick = { toDelete = e.id }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Delete, "Löschen") }
            }
        }
    }
    toDelete?.let { id ->
        val e = app.downloads.get(id)
        ConfirmDialog("Download löschen?", "„${e?.title}“ wird vom PC gelöscht.", "Löschen", onDismiss = { toDelete = null }) { app.downloads.delete(id) }
    }
}

