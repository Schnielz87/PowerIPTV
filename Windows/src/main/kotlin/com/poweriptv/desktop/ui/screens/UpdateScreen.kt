package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.update.UpdateChecker
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.Page
import com.poweriptv.desktop.ui.SettingsCard
import com.poweriptv.desktop.ui.Success
import com.poweriptv.desktop.ui.ToggleRow
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.desktop.ui.openUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Update direkt von GitHub – wie in der Android-App. */
@Composable
fun UpdateScreen(app: AppState) {
    val u = app.updates
    val available by u.available.collectAsState()
    val checking by u.checking.collectAsState()
    val status by u.status.collectAsState()
    val progress by u.progress.collectAsState()
    val auto by u.autoCheck.collectAsState()
    Page("Update") {
        SettingsCard {
            Text("Installiert: Version ${u.currentVersion}", fontWeight = FontWeight.Bold)
            u.lastCheck.takeIf { it > 0 }?.let {
                Text("Zuletzt geprüft: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val r = available
            if (r != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SystemUpdate, null, tint = BrandCyan); Spacer(Modifier.width(8.dp))
                    Text("Neue Version ${r.tag} verfügbar", color = BrandCyan, fontWeight = FontWeight.Bold)
                }
                progress?.let { p ->
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { u.downloadAndInstall() }, enabled = progress == null, modifier = Modifier.handCursor()) { Text("Jetzt herunterladen & installieren") }
                Text("Portiva schließt sich danach kurz, der Installer aktualisiert und du startest Portiva neu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (u.lastCheck > 0 && !checking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Success); Spacer(Modifier.width(8.dp)); Text("Du hast die neueste Version.")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { u.check() }, enabled = !checking, modifier = Modifier.handCursor()) { Text("Nach Updates suchen") }
                if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        SettingsCard {
            ToggleRow("Automatisch prüfen (alle 24 Stunden)", auto, "Bei einer neuen Version erscheint ein Hinweis auf der Startseite.") { u.setAutoCheck(it) }
            Text("Updates kommen direkt aus den GitHub-Releases von Portiva – ohne Store und ohne GitHub-Konto. Zugänge, Favoriten und Einstellungen bleiben erhalten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { openUrl(available?.pageUrl ?: "https://github.com/${UpdateChecker.REPO}/releases") }, modifier = Modifier.handCursor()) { Text("Versionen auf GitHub ansehen") }
        }
        available?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            SettingsCard("Was ist neu") { Text(notes.replace("**", "").replace("`", ""), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
