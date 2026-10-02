package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileType
import com.poweriptv.app.ui.components.BrandTopBar
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.components.PortivaLogo
import com.poweriptv.app.ui.components.VpnBadge
import com.poweriptv.app.ui.theme.Accent

@Composable
fun ProfilesScreen(
    container: AppContainer,
    onSelected: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onVpn: () -> Unit,
    onDownloads: () -> Unit,
) {
    val profiles by container.profiles.profiles.collectAsState()
    var toDelete by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        topBar = {
            BrandTopBar(actions = {
                IconButton(onClick = onDownloads) { Icon(Icons.Filled.DownloadForOffline, "Downloads") }
                VpnBadge(container, onVpn)
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Neuer Zugang") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    PortivaLogo(Modifier.size(96.dp))
                    Spacer(Modifier.size(20.dp))
                    Text("Willkommen bei Portiva – PowerIPTV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Fuege deinen ersten Zugang hinzu – per Xtream Codes API, M3U-URL oder M3U-Datei.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    Text(
                        "Wer schaut?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(profiles, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .tvFocus(RoundedCornerShape(14.dp), 1.02f)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                container.activate(p)
                                onSelected()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Person, null, tint = Accent) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (p.type) {
                                    ProfileType.XTREAM -> "Xtream Codes · ${p.username}"
                                    ProfileType.M3U_URL -> "M3U-URL"
                                    ProfileType.M3U_FILE -> "M3U-Datei"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Filled.Edit, "Bearbeiten") }
                        IconButton(onClick = { toDelete = p }) { Icon(Icons.Filled.Delete, "Loeschen") }
                    }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Zugang loeschen?") },
            text = { Text("\"${p.name}\" wird inklusive Favoriten entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    container.profiles.delete(p.id)
                    container.favorites.clear(p.id)
                    if (container.source?.profile?.id == p.id) container.activate(null)
                    toDelete = null
                }) { Text("Loeschen") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}
