package com.poweriptv.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.poweriptv.app.record.RecStatus
import com.poweriptv.app.record.Recording
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.formatBytes
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.Danger
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordingsScreen(container: AppContainer, onBack: () -> Unit) {
    val entries by container.recordings.entries.collectAsState()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<Recording?>(null) }
    val exactOk = remember { container.recordings.canScheduleExact() }

    Scaffold(
        topBar = { PowerTopBar("Aufnahmen", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!exactOk && Build.VERSION.SDK_INT >= 31) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Fuer punktgenaue Aufnahmen bitte \"Wecker & Erinnerungen\" erlauben.",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("Erlauben") }
                }
            }
            if (entries.isEmpty()) {
                ErrorBox("Keine Aufnahmen.\nPlane Aufnahmen im TV-Guide oder starte sie im Player mit dem Aufnahme-Knopf.")
                return@Column
            }
            val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val sorted = entries.sortedWith(compareBy<Recording> { it.status != RecStatus.RECORDING }.thenByDescending { it.start })
            LazyColumn(contentPadding = PaddingValues(12.dp)) {
                items(sorted, key = { it.id }) { r ->
                    val playable = r.status == RecStatus.COMPLETED || (r.status == RecStatus.RECORDING && r.bytes > 0)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .tvFocus(RoundedCornerShape(10.dp), 1.02f)
                            .clickable(enabled = playable) {
                                startPlayback(context, container, listOf(PlayEntry("${r.channelName}: ${r.title}", r.filePath, null, live = false)), 0)
                            }
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (!r.logo.isNullOrBlank()) AsyncImage(r.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (r.status == RecStatus.RECORDING) {
                                    Icon(Icons.Filled.FiberManualRecord, null, tint = Danger, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                "${r.channelName} · ${fmt.format(Date(r.start))} – ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.end))}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when (r.status) {
                                    RecStatus.SCHEDULED -> "Geplant"
                                    RecStatus.RECORDING -> "Nimmt auf · ${formatBytes(r.bytes)}"
                                    RecStatus.COMPLETED -> "Fertig · ${formatBytes(r.bytes)}"
                                    RecStatus.FAILED -> "Fehlgeschlagen: ${r.error ?: "unbekannt"}"
                                    RecStatus.CANCELLED -> "Abgebrochen"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.status == RecStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (playable) Icon(Icons.Filled.PlayArrow, "Abspielen", modifier = Modifier.padding(8.dp))
                        if (r.status == RecStatus.SCHEDULED || r.status == RecStatus.RECORDING) {
                            IconButton(onClick = { container.recordings.stop(r.id) }) { Icon(Icons.Filled.Stop, "Stoppen") }
                        }
                        IconButton(onClick = { toDelete = r }) { Icon(Icons.Filled.Delete, "Loeschen") }
                    }
                }
            }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Aufnahme loeschen?") },
            text = { Text("\"${r.title}\" wird entfernt.") },
            confirmButton = { TextButton(onClick = { container.recordings.delete(r.id); toDelete = null }) { Text("Loeschen") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}
