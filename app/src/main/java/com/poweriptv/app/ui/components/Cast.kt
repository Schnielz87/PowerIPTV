package com.poweriptv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.ui.theme.BrandCyan

/**
 * Cast-Symbol (Chromecast / Google TV). Unsichtbar, wenn Cast auf dem Geraet nicht moeglich ist.
 * [entry] = was beim Tippen auf "Auf dem TV abspielen" uebertragen wird (null = nur verbinden).
 */
@Composable
fun CastButton(
    container: AppContainer,
    entry: PlayEntry? = null,
    poster: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onCasting: () -> Unit = {},
) {
    val available by container.cast.available.collectAsState()
    if (!available) return
    val connected by container.cast.connectedTo.collectAsState()
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.tvFocus(CircleShape)) {
        Icon(
            if (connected != null) Icons.Filled.CastConnected else Icons.Filled.Cast,
            "Auf Fernseher uebertragen",
            tint = if (connected != null) BrandCyan else tint,
        )
    }
    if (open) CastDialog(container, entry, poster, onDismiss = { open = false }, onCasting = { open = false; onCasting() })
}

@Composable
private fun CastDialog(
    container: AppContainer,
    entry: PlayEntry?,
    poster: String?,
    onDismiss: () -> Unit,
    onCasting: () -> Unit,
) {
    val cast = container.cast
    val devices by cast.devices.collectAsState()
    val connected by cast.connectedTo.collectAsState()
    val casting = entry != null && !entry.url.startsWith("/")

    DisposableEffect(Unit) {
        cast.startDiscovery()
        onDispose { cast.stopDiscovery() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Cast, null, tint = BrandCyan) },
        title = { Text(if (connected != null) "Verbunden mit $connected" else "Auf Fernseher uebertragen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connected != null) {
                    if (casting) {
                        Button(onClick = { if (cast.cast(entry!!, poster)) onCasting() }, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                            Icon(Icons.Filled.Tv, null); Spacer(Modifier.width(8.dp)); Text("Auf dem TV abspielen")
                        }
                    } else if (entry != null) {
                        Text("Heruntergeladene Dateien koennen nicht uebertragen werden.", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { cast.disconnect(); onDismiss() }, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                        Text("Verbindung trennen")
                    }
                } else {
                    if (devices.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Suche Chromecast / Google TV im WLAN...")
                        }
                    }
                    devices.forEach { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .tvFocus(RoundedCornerShape(10.dp))
                                .clickable {
                                    cast.connect(d, entry?.takeIf { casting }, poster)
                                    if (casting) onCasting() else onDismiss()
                                }
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Tv, null, tint = BrandCyan)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(d.name, fontWeight = FontWeight.SemiBold)
                                d.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    Text(
                        "Unterstuetzt: Chromecast, Google TV und Fernseher mit \"Chromecast built-in\" im selben WLAN. " +
                            "Fire TV unterstuetzt kein Google Cast – dort die App direkt nutzen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Hinweis: Der Fernseher laedt den Stream selbst (nicht ueber das VPN der App).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Schliessen") } },
    )
}

/** Steuerung waehrend der Uebertragung: "Laeuft auf <TV>" mit Zurueck/Pause/Vor/Stopp. */
@Composable
fun CastingBar(container: AppContainer, modifier: Modifier = Modifier, onStop: () -> Unit = {}) {
    val now by container.cast.nowCasting.collectAsState()
    val device by container.cast.connectedTo.collectAsState()
    val paused by container.cast.paused.collectAsState()
    val title = now ?: return
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6101A2E))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CastConnected, null, tint = BrandCyan)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text("Laeuft auf ${device ?: "Fernseher"}", style = MaterialTheme.typography.labelMedium, color = BrandCyan)
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { container.cast.seekBy(-10_000) }, modifier = Modifier.tvFocus(CircleShape)) { Icon(Icons.Filled.Replay10, "10 s zurueck", tint = Color.White) }
        IconButton(onClick = { container.cast.togglePause() }, modifier = Modifier.tvFocus(CircleShape)) {
            Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, "Pause/Weiter", tint = Color.White)
        }
        IconButton(onClick = { container.cast.seekBy(30_000) }, modifier = Modifier.tvFocus(CircleShape)) { Icon(Icons.Filled.Forward10, "Vor", tint = Color.White) }
        IconButton(onClick = { container.cast.stopCasting(); onStop() }, modifier = Modifier.tvFocus(CircleShape)) { Icon(Icons.Filled.Stop, "Uebertragung beenden", tint = Color.White) }
    }
}
