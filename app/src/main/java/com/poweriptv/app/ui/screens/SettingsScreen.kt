package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.BuildConfigInfo
import com.poweriptv.app.data.LiveFormat
import com.poweriptv.app.data.SettingsRepository
import com.poweriptv.app.ui.components.PowerTopBar

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onVpn: () -> Unit) {
    val s = container.settings
    val liveFormat by s.liveFormat.collectAsState()
    val ua by s.userAgent.collectAsState()
    var uaInput by remember { mutableStateOf(ua) }

    Scaffold(
        topBar = { PowerTopBar("Einstellungen", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsSection("Sicherheit") {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onVpn).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("VPN & Kill-Switch")
                        Text(
                            "App-Daten per WireGuard-VPN absichern",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }

            SettingsSection("Live-TV Stream-Format (Xtream)") {
                LiveFormat.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { s.setLiveFormat(f); container.source?.clearCache() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = liveFormat == f.name, onClick = { s.setLiveFormat(f) })
                        Text(f.label)
                    }
                }
            }

            SettingsSection("Player") {
                OutlinedTextField(
                    value = uaInput,
                    onValueChange = { uaInput = it },
                    label = { Text("User-Agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = { s.setUserAgent(uaInput) }) { Text("Speichern") }
                    TextButton(onClick = {
                        uaInput = SettingsRepository.DEFAULT_UA; s.setUserAgent(uaInput)
                    }) { Text("Standard") }
                }
            }

            SettingsSection("Info") {
                Text("PowerIPTV ${BuildConfigInfo.VERSION}")
                Text(
                    "Zugangsdaten und VPN-Konfiguration werden verschluesselt (AES-256, Android Keystore) gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
