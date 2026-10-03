package com.poweriptv.app.ui.screens

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.parental.ParentalControl
import com.poweriptv.app.parental.PinDialog
import com.poweriptv.app.parental.SetPinDialog
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.tvFocus

/** Einstellungen der Kindersicherung. */
@Composable
fun ParentalScreen(container: AppContainer, onBack: () -> Unit) {
    val pc = container.parental
    val enabled by pc.enabled.collectAsState()
    val autoAdult by pc.autoAdult.collectAsState()
    val protectSettings by pc.protectSettings.collectAsState()
    val lockedKeys by pc.lockedKeys.collectAsState()
    var unlocked by remember { mutableStateOf(!pc.hasPin() || pc.sessionUnlocked.value) }
    var setPin by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(ContentType.LIVE) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    val source = container.source

    LaunchedEffect(type, source) {
        categories = source?.let { s -> runCatching { s.categories(type) }.getOrDefault(emptyList()) }.orEmpty()
    }

    if (!unlocked) {
        PinDialog(pc, message = "PIN eingeben, um die Kindersicherung zu bearbeiten", onDismiss = onBack, onSuccess = { unlocked = true })
        return
    }

    Scaffold(
        topBar = { PowerTopBar("Kindersicherung", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Klarer Status: ist die Sperre wirklich scharf?
            item {
                val active = enabled && pc.hasPin()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (active) androidx.compose.ui.graphics.Color(0xFF1B5E20) else androidx.compose.ui.graphics.Color(0xFF8E2A2A))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (active) Icons.Filled.Lock else Icons.Filled.LockOpen, null, tint = androidx.compose.ui.graphics.Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (active) "Kindersicherung ist AKTIV" else "Kindersicherung ist AUS – derzeit ist nichts gesperrt",
                            color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (active) "Gesperrte Kategorien, Titel und Sender sind ueberall nur mit PIN sichtbar (auch in Verlauf, Favoriten, Suche und Weiterschauen)."
                            else if (!pc.hasPin()) "Tippe auf \"PIN festlegen\" – danach ist die Sperre sofort aktiv."
                            else "Schalte \"Kindersicherung aktiv\" ein.",
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                SettingsSection("PIN") {
                    Text(
                        if (pc.hasPin()) "Eine PIN ist festgelegt." else "Lege zuerst eine PIN fest, um die Kindersicherung zu aktivieren.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { setPin = true }) { Text(if (pc.hasPin()) "PIN aendern" else "PIN festlegen") }
                        if (pc.hasPin()) OutlinedButton(onClick = { pc.reset(); unlocked = true }) { Text("Entfernen") }
                    }
                    SwitchRow("Kindersicherung aktiv", null, enabled, enabled = pc.hasPin()) { pc.setEnabled(it) }
                    SwitchRow(
                        "Erwachseneninhalte automatisch sperren",
                        "Kategorien mit XXX, Adult, 18+, Erotik usw. werden erkannt.",
                        autoAdult,
                    ) { pc.setAutoAdult(it) }
                    SwitchRow("Einstellungen mit PIN schuetzen", "Auch VPN, Zugaenge und KI-Einstellungen.", protectSettings) { pc.setProtectSettings(it) }
                }
            }
            item {
                Text("Gesperrte Kategorien", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf(ContentType.LIVE to "Live TV", ContentType.MOVIE to "Filme", ContentType.SERIES to "Serien").forEach { (t, label) ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) })
                    }
                }
                if (source == null) Text("Kein Zugang ausgewaehlt", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(categories, key = { it.id }) { c ->
                val manual = pc.key(source!!.profile.id, type, c.id) in lockedKeys
                val auto = autoAdult && ParentalControl.isAdult(c.name)
                Row(
                    Modifier.fillMaxWidth().tvFocus().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (manual || auto) Icons.Filled.Lock else Icons.Filled.LockOpen, null,
                        tint = if (manual || auto) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.name)
                        if (auto) Text(
                            if (enabled) "automatisch gesperrt" else "wird gesperrt, sobald die Kindersicherung aktiv ist",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = manual || auto, enabled = !auto, onCheckedChange = { pc.setLocked(source.profile.id, type, c.id, it) })
                }
            }
        }
    }

    if (setPin) {
        SetPinDialog(onDismiss = { setPin = false }, onSet = {
            pc.setPin(it); pc.setEnabled(true); setPin = false
        })
    }
}
