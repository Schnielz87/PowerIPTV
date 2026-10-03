package com.poweriptv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.ContentType
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen

/** Linke Navigationsleiste (wie bei Streaming-Apps am PC). */
@Composable
fun NavRail(app: AppState) {
    val current = app.screen
    Column(
        Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF08101E)).padding(vertical = 18.dp, horizontal = 12.dp),
    ) {
        // Portiva-Logo oben links: ein Klick fuehrt von jeder Seite zur Startseite (wie Android/Samsung)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 22.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .then(if (app.profile != null) Modifier.handCursor().clickable { app.navigate(Screen.Home) } else Modifier)
                .padding(start = 6.dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
        ) {
            PortivaLogo(Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            BrandWordmark()
        }
        val items = listOf(
            Triple("Start", Icons.Filled.Home, Screen.Home),
            Triple("Live TV", Icons.Filled.LiveTv, Screen.Browse(ContentType.LIVE)),
            Triple("Filme", Icons.Filled.Movie, Screen.Browse(ContentType.MOVIE)),
            Triple("Serien", Icons.Filled.VideoLibrary, Screen.Browse(ContentType.SERIES)),
            Triple("TV-Guide", Icons.Filled.CalendarViewWeek, Screen.Epg),
            Triple("Favoriten", Icons.Filled.Favorite, Screen.Favorites),
            Triple("Suche", Icons.Filled.Search, Screen.Search),
        )
        items.forEach { (label, icon, target) ->
            NavItem(label, icon, selected = current == target) { app.navigate(target) }
        }
        Spacer(Modifier.weight(1f))
        NavItem("Einstellungen", Icons.Filled.Settings, selected = current == Screen.Settings) { app.navigate(Screen.Settings) }
        NavItem(app.profile?.name ?: "Zugang", Icons.Filled.People, selected = current == Screen.Profiles) { app.navigate(Screen.Profiles) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> Accent.copy(alpha = 0.28f)
                    hovered -> SurfaceHigh
                    else -> Color.Transparent
                },
            )
            .hoverable(hover).handCursor().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) BrandCyan else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Spacer(Modifier.height(0.dp))
}
