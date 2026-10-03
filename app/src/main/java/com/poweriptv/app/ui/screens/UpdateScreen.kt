package com.poweriptv.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan
import com.poweriptv.app.ui.theme.Success
import java.text.DateFormat
import java.util.Date

/** Update direkt von GitHub: pruefen, herunterladen, installieren (ohne Play Store, ohne GitHub-Konto). */
@Composable
fun UpdateScreen(container: AppContainer, onBack: () -> Unit) {
    val u = container.updates
    val context = LocalContext.current
    val available by u.available.collectAsState()
    val checking by u.checking.collectAsState()
    val status by u.status.collectAsState()
    val progress by u.progress.collectAsState()
    val auto by u.autoCheck.collectAsState()
    Scaffold(
        topBar = { PowerTopBar("Update", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Installiert: Version ${u.currentVersion}", fontWeight = FontWeight.Bold)
                u.lastCheck.takeIf { it > 0 }?.let {
                    Text("Zuletzt geprüft: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val r = available
                if (r != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SystemUpdate, null, tint = BrandCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Neue Version ${r.tag} verfügbar", color = BrandCyan, fontWeight = FontWeight.Bold)
                    }
                    progress?.let { p ->
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { u.downloadAndInstall() }, enabled = progress == null, modifier = Modifier.tvFocus(RoundedCornerShape(50))) {
                        Text("Jetzt herunterladen & installieren")
                    }
                    if (!u.canInstall()) {
                        Text("Einmalig nötig: „Unbekannte Apps installieren“ für Portiva erlauben (wird beim Tippen geöffnet).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (u.lastCheck > 0 && !checking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Success)
                        Spacer(Modifier.width(8.dp))
                        Text("Du hast die neueste Version.")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { u.check() }, enabled = !checking, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Nach Updates suchen") }
                    if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatisch prüfen (alle 24 Stunden)")
                        Text("Bei einer neuen Version erscheint ein Hinweis auf der Startseite.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(auto, { u.setAutoCheck(it) }, modifier = Modifier.tvFocus(RoundedCornerShape(50)))
                }
                Text("Updates kommen direkt aus den GitHub-Releases von Portiva – ohne Play Store und ohne GitHub-Konto. Zugänge, Favoriten und Einstellungen bleiben erhalten.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available?.pageUrl ?: "https://github.com/${com.poweriptv.app.update.UpdateChecker.REPO}/releases"))) }
                }, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Versionen auf GitHub ansehen") }
            }
            available?.notes?.takeIf { it.isNotBlank() }?.let {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                ) {
                    Text("Was ist neu", fontWeight = FontWeight.Bold)
                    Text(it.replace("**", "").replace("`", ""), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
