package com.poweriptv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.Profile
import com.poweriptv.app.ui.theme.Accent
import com.poweriptv.app.ui.theme.BrandCyan

/**
 * Benutzer-Maennchen fuer den schnellen Wechsel zwischen Zugaengen – nur Wechseln,
 * Bearbeiten/Loeschen bleibt unter "Benutzer wechseln".
 */
@Composable
fun ProfileSwitcher(container: AppContainer, onSwitched: (Profile) -> Unit) {
    val profiles by container.profiles.profiles.collectAsState()
    val activeId = container.source?.profile?.id
    var open by remember { mutableStateOf(false) }
    Box {
        // Gleiches Format wie die anderen Symbole der Kopfzeile (kein Extra-Kreis)
        IconButton(onClick = { open = true }, modifier = Modifier.tvFocus(CircleShape, 1.15f)) {
            Icon(Icons.Filled.Person, "Benutzer wechseln", tint = BrandCyan)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                "Benutzer wechseln",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            profiles.forEach { p ->
                val active = p.id == activeId
                DropdownMenuItem(
                    text = { Text(p.name, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) BrandCyan else MaterialTheme.colorScheme.onSurface) },
                    leadingIcon = { Icon(Icons.Filled.Person, null, tint = if (active) BrandCyan else MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = { if (active) Icon(Icons.Filled.Check, "Aktiv", tint = BrandCyan) },
                    onClick = {
                        open = false
                        if (!active) {
                            container.activate(p)
                            onSwitched(p)
                        }
                    },
                    modifier = Modifier.tvFocus(),
                )
            }
        }
    }
}
