package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.FiberSmartRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poweriptv.app.data.AccountInfo
import com.poweriptv.app.data.ContentType
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.data.LibraryStore
import com.poweriptv.desktop.data.WatchEntry
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.MenuAction
import com.poweriptv.desktop.ui.Pill
import com.poweriptv.desktop.ui.PosterCard
import com.poweriptv.desktop.ui.SectionTitle
import com.poweriptv.desktop.ui.Success
import com.poweriptv.desktop.ui.Warning
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.app.data.Episode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(app: AppState) {
    val lib = app.library ?: return
    val history by lib.history.collectAsState()
    val favorites by lib.favorites.collectAsState()
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    LaunchedEffect(app.source) { account = runCatching { app.source?.accountInfo() }.getOrNull() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Willkommen zurück", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.profile?.name ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    account?.let { acc ->
                        acc.expiresAt?.let { Pill("Gültig bis " + SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(it))) }
                        acc.maxConnections?.let { Pill("Streams: ${acc.activeConnections ?: "0"} / $it") }
                        acc.status?.let { Pill(it, if (it.equals("Active", true)) Success.copy(alpha = 0.25f) else Warning.copy(alpha = 0.3f)) }
                    }
                }
            }
            ProfileSwitcher(app)
            Spacer(Modifier.width(6.dp))
            if (app.refreshing) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Playlist wird aktualisiert…", style = MaterialTheme.typography.bodySmall)
            } else {
                IconButton(onClick = { app.refresh() }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Sync, "Playlist aktualisieren") }
            }
        }
        app.refreshError?.let { Text("Aktualisierung fehlgeschlagen: $it", color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BigTile("LIVE TV", Icons.Filled.LiveTv, "live_tv_collage.webp", listOf(Color(0xFF1E6BFF), Color(0xFF0A2547)), Modifier.weight(1f)) {
                app.navigate(Screen.Browse(ContentType.LIVE))
            }
            BigTile("FILME", Icons.Filled.Movie, "movies_collage.webp", listOf(Color(0xFF7A3DFF), Color(0xFF1B1450)), Modifier.weight(1f)) {
                app.navigate(Screen.Browse(ContentType.MOVIE))
            }
            BigTile("SERIEN", Icons.Filled.VideoLibrary, "series_collage.webp", listOf(Color(0xFF0E8A8A), Color(0xFF0A2547)), Modifier.weight(1f)) {
                app.navigate(Screen.Browse(ContentType.SERIES))
            }
        }

        // Kleine Kacheln wie in der Android-App
        ToolTiles(app)

        val cont = remember(history) { lib.continueWatching() }
        if (cont.isNotEmpty()) {
            SectionTitle("Weiterschauen")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(cont, key = { it.item.key }) { e ->
                    PosterCard(
                        item = e.item, onClick = { resume(app, e) }, modifier = Modifier.width(150.dp),
                        progress = e.progress,
                        subtitle = e.episodeTitle?.let { "S${e.season} E${e.episodeNum}" } ?: "noch ${com.poweriptv.desktop.ui.formatTime(e.duration - e.position)}",
                        menu = listOf(
                            MenuAction("Details öffnen") { app.navigate(Screen.Detail(e.item)) },
                            MenuAction("Aus Liste entfernen") { lib.removeHistory(e.item) },
                        ),
                    )
                }
            }
        }

        val live = history.filter { it.item.type == ContentType.LIVE }.take(12)
        if (live.isNotEmpty()) {
            SectionTitle("Zuletzt gesehen – Live TV")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(live, key = { it.item.key }) { e ->
                    ChannelCard(
                        e.item, onClick = { app.play(e.item) }, modifier = Modifier.width(280.dp),
                        menu = listOf(
                            MenuAction(if (lib.isFavorite(e.item)) "Aus Favoriten entfernen" else "Zu Favoriten") { lib.toggleFavorite(e.item) },
                            MenuAction("Aus Liste entfernen") { lib.removeHistory(e.item) },
                        ),
                    )
                }
            }
        }

        for (t in listOf(ContentType.MOVIE, ContentType.SERIES)) {
            val recent = history.filter { it.item.type == t }.take(20)
            if (recent.isEmpty()) continue
            SectionTitle("Zuletzt gesehen – ${com.poweriptv.desktop.ui.typeLabel(t)}", action = {
                androidx.compose.material3.TextButton(onClick = {
                    app.selectedCategory[t] = com.poweriptv.app.ui.components.CAT_RECENT
                    app.navigate(Screen.Browse(t))
                }, modifier = Modifier.handCursor()) { Text("Alle anzeigen ›") }
            })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(recent, key = { "r_" + it.item.key }) { e ->
                    PosterCard(
                        e.item, onClick = { app.navigate(Screen.Detail(e.item)) }, modifier = Modifier.width(140.dp),
                        menu = listOf(MenuAction("Aus Liste entfernen") { lib.removeHistory(e.item) }),
                    )
                }
            }
        }

        val favVod = favorites.filter { it.type != ContentType.LIVE }.take(20)
        if (favVod.isNotEmpty()) {
            SectionTitle("Meine Favoriten")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(favVod, key = { it.key }) { item ->
                    PosterCard(item, onClick = { app.navigate(Screen.Detail(item)) }, modifier = Modifier.width(150.dp), favorite = true)
                }
            }
        }
    }
}

/** Weiterschauen: Film direkt ab Position, Serie mit der zuletzt gesehenen Episode. */
fun resume(app: AppState, e: WatchEntry) {
    if (e.item.type == ContentType.SERIES && e.episodeId != null) {
        val ep = Episode(e.episodeId, e.episodeTitle ?: "", e.season ?: 0, e.episodeNum ?: 0, e.episodeExt, null, null, null)
        app.playEpisode(e.item, ep, emptyList(), e.item.logo, app.library?.position(LibraryStore.episodeKey(e.episodeId)))
    } else {
        app.play(e.item)
    }
}

@Suppress("DEPRECATION")
@Composable
private fun BigTile(title: String, icon: ImageVector, image: String, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier.height(190.dp).scale(if (hovered) 1.02f else 1f).clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors))
            .then(if (hovered) Modifier.border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(18.dp)) else Modifier)
            .hoverable(hover).handCursor().clickable(onClick = onClick),
    ) {
        Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.last().copy(alpha = 0.9f)))))
        Row(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class Tile(val label: String, val icon: ImageVector, val soon: Boolean = false, val onClick: () -> Unit)

/** Kachel-Raster der Android-Startseite; noch nicht portierte Funktionen sind als „bald“ markiert. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ToolTiles(app: AppState) {
    var soonInfo by remember { mutableStateOf<String?>(null) }
    val soon = { name: String -> { soonInfo = name } }
    val tiles = listOf(
        Tile("Suche", Icons.Filled.Search) { app.navigate(Screen.Search) },
        Tile("Playlist aktualisieren", Icons.Filled.Sync) { app.refresh() },
        Tile("TV-Guide (EPG)", Icons.Filled.CalendarViewWeek) { app.navigate(Screen.Epg) },
        Tile("Aufnahmen", Icons.Filled.FiberSmartRecord, true, soon("Aufnahmen")),
        Tile("Multi-Screen", Icons.Filled.GridView, true, soon("Multi-Screen")),
        Tile("KI-Empfehlungen", Icons.Filled.AutoAwesome, true, soon("KI-Empfehlungen")),
        Tile("Favoriten & Listen", Icons.Filled.Favorite) { app.navigate(Screen.Favorites) },
        Tile("Downloads", Icons.Filled.DownloadForOffline, true, soon("Downloads")),
        Tile("VPN & Sicherheit", Icons.Filled.Shield, true, soon("VPN & Sicherheit")),
        Tile("Benutzer wechseln", Icons.Filled.People) { app.navigate(Screen.Profiles) },
        Tile("Einstellungen", Icons.Filled.Settings) { app.navigate(Screen.Settings) },
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tiles.forEach { t -> SmallTile(t) }
    }
    soonInfo?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { soonInfo = null },
            title = { Text(name) },
            text = { Text("Diese Funktion gibt es bereits in der Android-App und kommt mit dem nächsten Windows-Update.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { soonInfo = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SmallTile(t: Tile) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.width(250.dp).height(64.dp).clip(RoundedCornerShape(14.dp))
            .background(if (hovered) com.poweriptv.desktop.ui.SurfaceHigh else com.poweriptv.desktop.ui.Surface)
            .then(if (hovered) Modifier.border(1.5.dp, com.poweriptv.desktop.ui.BrandCyan, RoundedCornerShape(14.dp)) else Modifier)
            .hoverable(hover).handCursor().clickable(onClick = t.onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(t.icon, null, tint = if (t.soon) Color.White.copy(alpha = 0.35f) else com.poweriptv.desktop.ui.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            t.label, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (t.soon) Color.White.copy(alpha = 0.45f) else Color.White,
            modifier = Modifier.weight(1f),
        )
        if (t.soon) Text("bald", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
    }
}

/** Benutzer-Maennchen: schneller Wechsel zwischen Zugaengen (nur Wechseln, wie Android). */
@Composable
private fun ProfileSwitcher(app: AppState) {
    val profiles by app.profiles.profiles.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.handCursor()) {
            Box(
                Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(com.poweriptv.desktop.ui.Accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Person, "Benutzer wechseln", tint = com.poweriptv.desktop.ui.BrandCyan) }
        }
        androidx.compose.material3.DropdownMenu(open, onDismissRequest = { open = false }) {
            Text("Benutzer wechseln", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            profiles.forEach { p ->
                val active = p.id == app.profile?.id
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(p.name, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) com.poweriptv.desktop.ui.BrandCyan else Color.White) },
                    leadingIcon = { Icon(Icons.Filled.Person, null, tint = if (active) com.poweriptv.desktop.ui.BrandCyan else MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = { if (active) Icon(Icons.Filled.Check, "Aktiv", tint = com.poweriptv.desktop.ui.BrandCyan) },
                    onClick = { open = false; if (!active) app.activate(p) },
                )
            }
        }
    }
}
