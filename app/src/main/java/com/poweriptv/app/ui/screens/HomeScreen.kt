@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.poweriptv.app.ui.screens

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import com.poweriptv.app.R
import com.poweriptv.app.ui.theme.BrandCyan
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberSmartRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.AccountInfo
import com.poweriptv.app.data.CachedSource
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.player.MultiViewActivity
import com.poweriptv.app.ui.components.BrandTopBar
import com.poweriptv.app.ui.components.ProfileSwitcher
import androidx.compose.foundation.layout.statusBarsPadding
import com.poweriptv.app.ui.components.PortivaLogo
import com.poweriptv.app.ui.components.CastButton
import com.poweriptv.app.ui.components.CastingBar
import com.poweriptv.app.ui.components.VpnBadge
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpen: (ContentType) -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onVpn: () -> Unit,
    onDownloads: () -> Unit,
    onSwitchProfile: () -> Unit,
    onProfileSwitched: () -> Unit = {},
    onEpg: () -> Unit,
    onRecordings: () -> Unit,
    onRecommendations: () -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: (ContentItem) -> Unit,
    onOpenRecent: (ContentType) -> Unit = {},
) {
    val source = container.source
    val context = LocalContext.current
    val historyAll by container.history.items.collectAsState()
    val parentalOn by container.parental.enabled.collectAsState()
    val parentalUnlocked by container.parental.sessionUnlocked.collectAsState()
    // Kindersicherung: gesperrte Titel nicht im Verlauf/Weiterschauen zeigen
    var actionsFor by remember { mutableStateOf<Pair<ContentItem, String>?>(null) }
    val history = remember(historyAll, parentalOn, parentalUnlocked) { container.parental.visible(container.source?.profile?.id, historyAll) }
    val refreshing by container.refreshing.collectAsState()
    val refreshError by container.refreshError.collectAsState()
    val cached = source as? CachedSource
    // Beim Oeffnen: automatisch aktualisieren, wenn die letzte Aktualisierung > 24 h her ist
    LaunchedEffect(source) { container.refreshPlaylist(force = false) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    LaunchedEffect(source) { account = source?.accountInfo() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Scaffold(
        topBar = {
            // Kopfzeile: Logo + gleich grosse Symbole in gleichmaessigen Abstaenden
            BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp)) {
                val narrow = maxWidth < 600.dp
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (narrow) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp),
                ) {
                    PortivaLogo(Modifier.size(40.dp))
                    if (!narrow) Spacer(Modifier.weight(1f))
                    ProfileSwitcher(container) { onProfileSwitched() }
                    CastButton(container)
                    IconButton(onClick = onSearch, modifier = Modifier.tvFocus()) { Icon(Icons.Filled.Search, "Suche") }
                    IconButton(onClick = { container.refreshPlaylist(force = true) }, enabled = !refreshing, modifier = Modifier.tvFocus()) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Sync, "Playlist aktualisieren")
                    }
                    VpnBadge(container, onVpn)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth > 600.dp
            val bigHeight: Dp = if (wide) 180.dp else 130.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CastingBar(container, Modifier.fillMaxWidth())
                if (refreshing) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Playlist und TV-Guide werden aktualisiert...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                refreshError?.let {
                    Text("Aktualisierung fehlgeschlagen: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                // Collage-Hintergruende: res/drawable-nodpi/*_collage.webp (einfach austauschbar)
                val big = listOf(
                    Triple("LIVE TV", Icons.Filled.LiveTv, ContentType.LIVE) to BigTileStyle(listOf(Color(0xFF1E6BFF), Color(0xFF0A2547)), R.drawable.live_tv_collage),
                    Triple("FILME", Icons.Filled.Movie, ContentType.MOVIE) to BigTileStyle(listOf(Color(0xFF7A3DFF), Color(0xFF1B1450)), R.drawable.movies_collage),
                    Triple("SERIEN", Icons.Filled.VideoLibrary, ContentType.SERIES) to BigTileStyle(listOf(Color(0xFF0E8A8A), Color(0xFF0A2547)), R.drawable.series_collage),
                )
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        big.forEachIndexed { i, (t, colors) ->
                            BigTile(
                                t.first, t.second, colors, bigHeight,
                                Modifier.weight(1f).then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            ) { onOpen(t.third) }
                        }
                    }
                } else {
                    big.forEachIndexed { i, (t, colors) ->
                        BigTile(
                            t.first, t.second, colors, bigHeight,
                            Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        ) { onOpen(t.third) }
                    }
                }

                val tiles: List<Triple<String, ImageVector, () -> Unit>> = listOf(
                    Triple("Suche", Icons.Filled.Search, onSearch),
                    Triple("Playlist aktualisieren", Icons.Filled.Sync, { container.refreshPlaylist(force = true) }),
                    Triple("TV-Guide (EPG)", Icons.Filled.CalendarViewWeek, onEpg),
                    Triple("Aufnahmen", Icons.Filled.FiberSmartRecord, onRecordings),
                    Triple("Multi-Screen", Icons.Filled.GridView, {
                        context.startActivity(Intent(context, MultiViewActivity::class.java))
                    }),
                    Triple("KI-Empfehlungen", Icons.Filled.AutoAwesome, onRecommendations),
                    Triple("Favoriten & Listen", Icons.Filled.Favorite, onFavorites),
                    Triple("Downloads", Icons.Filled.DownloadForOffline, onDownloads),
                    Triple("VPN & Sicherheit", Icons.Filled.Shield, onVpn),
                    Triple("Benutzer wechseln", Icons.Filled.People, onSwitchProfile),
                    Triple("Einstellungen", Icons.Filled.Settings, onSettings),
                )
                val perRow = if (wide) 3 else 2
                tiles.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { (label, icon, action) -> SmallTile(label, icon, Modifier.weight(1f), action) }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                // Weiterschauen: angefangene Filme + zuletzt gesehene Folge jeder Serie
                val resumeVersion by container.resume.version.collectAsState()
                if (history.isNotEmpty() && source != null && resumeVersion >= 0) {
                    val cont = history.mapNotNull { item ->
                        when (item.type) {
                            ContentType.MOVIE -> {
                                val url = source.streamUrl(item)
                                val p = container.resume.progress(url) ?: return@mapNotNull null
                                val left = container.resume.remaining(url)?.let { "Noch ${(it / 60_000).coerceAtLeast(1)} Min." }
                                Triple(item, p, left ?: "${(p * 100).toInt()} % gesehen")
                            }
                            ContentType.SERIES -> {
                                val (url, label) = container.resume.lastEpisode(item.key) ?: return@mapNotNull null
                                Triple(item, container.resume.progress(url) ?: 0f, label)
                            }
                            else -> null
                        }
                    }.take(20)
                    if (cont.isNotEmpty()) {
                        Text("Weiterschauen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(cont, key = { "c_" + it.first.key }) { (item, progress, sub) ->
                                Column(
                                    Modifier.width(130.dp).clip(RoundedCornerShape(10.dp)).tvFocus(RoundedCornerShape(10.dp))
                                        .combinedClickable(onLongClick = { actionsFor = item to "cont" }) {
                                            if (item.type == ContentType.MOVIE) {
                                                // Fragt automatisch "Weiterschauen ab … / Von vorne"
                                                startPlayback(context, container, listOf(PlayEntry(item.name, source.streamUrl(item), item, live = false)), 0)
                                            } else onOpenDetail(item)
                                        }
                                        .background(MaterialTheme.colorScheme.surface).padding(8.dp),
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!item.logo.isNullOrBlank()) AsyncImage(item.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        // Play-Symbol
                                        Box(
                                            Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) }
                                    }
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = BrandCyan,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                                    Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Zuletzt gesehen – getrennt nach Live TV, Filme und Serien
                if (history.isNotEmpty() && source != null) {
                    listOf(
                        ContentType.LIVE to "Zuletzt gesehen: Live TV",
                        ContentType.MOVIE to "Zuletzt gesehen: Filme",
                        ContentType.SERIES to "Zuletzt gesehen: Serien",
                    ).forEach { (type, label) ->
                        val list = history.filter { it.type == type }.take(20)
                        if (list.isNotEmpty()) {
                            // Ueberschrift antippen -> eigene Uebersichtsseite
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).tvFocus(RoundedCornerShape(8.dp))
                                    .clickable { onOpenRecent(type) }.padding(vertical = 2.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("  Alle anzeigen ›", color = BrandCyan, style = MaterialTheme.typography.bodySmall)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(list, key = { it.key }) { item ->
                                    // Filme/Serien im Hochformat (Poster), Sender im Querformat (Logo)
                                    val poster = type != ContentType.LIVE
                                    Column(
                                        Modifier
                                            .width(if (poster) 110.dp else 150.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .tvFocus(RoundedCornerShape(10.dp))
                                            .combinedClickable(onLongClick = { actionsFor = item to "recent" }) {
                                                when {
                                                    item.type == ContentType.LIVE || !source.supportsDetails -> {
                                                        // Live: alle zuletzt gesehenen Sender als Liste (Kanal vor/zurueck)
                                                        val entries = list.map { PlayEntry(it.name, source.streamUrl(it), it, it.type == ContentType.LIVE) }
                                                        startPlayback(context, container, entries, list.indexOf(item))
                                                    }
                                                    else -> onOpenDetail(item)
                                                }
                                            }
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(8.dp),
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth().height(if (poster) 140.dp else 80.dp).clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (!item.logo.isNullOrBlank()) AsyncImage(
                                                item.logo, null,
                                                contentScale = if (poster) ContentScale.Crop else ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                account?.let { AccountCard(it) }
                cached?.let { c ->
                    val last = if (refreshing) null else c.lastRefresh
                    Text(
                        "Playlist zuletzt aktualisiert: " + when {
                            last == null -> "laeuft gerade..."
                            last == 0L -> "noch nie"
                            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))
                        } + " · automatisch alle 24 Stunden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    actionsFor?.let { (item, kind) ->
        com.poweriptv.app.ui.components.ItemActionsDialog(
            container, item,
            onDismiss = { actionsFor = null },
            removeLabel = if (kind == "cont") "Aus „Weiterschauen“ entfernen" else "Aus „Zuletzt gesehen“ entfernen",
            onRemove = {
                if (kind == "cont") {
                    // Nur aus Weiterschauen nehmen – der Titel selbst bleibt erhalten
                    if (item.type == ContentType.SERIES) container.resume.clearLastEpisode(item.key)
                    else container.source?.let { container.resume.clear(it.streamUrl(item)) }
                } else container.history.remove(item.key)
            },
        )
    }
}

/** Farbton + Collage-Hintergrund einer grossen Startseiten-Kachel. */
private data class BigTileStyle(val colors: List<Color>, @DrawableRes val background: Int)

/**
 * Grosse Startseiten-Kachel (Live TV / Filme / Serien).
 * Ebenen: 1. Collage  2. Farb-/Abdunkel-Overlay  3. Vignette hinter Icon+Text  4. Icon  5. Beschriftung
 */
@Composable
private fun BigTile(
    title: String,
    icon: ImageVector,
    style: BigTileStyle,
    height: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = style.colors
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .tvFocus(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        // 1. Collage (fuellt die Kachel, Seitenverhaeltnis bleibt erhalten)
        Image(
            painter = painterResource(style.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 2. Abdunkeln + Farbton der Kachel (Blau / Violett / Teal) -> klar unterscheidbar
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(colors[0].copy(alpha = 0.35f), colors[1].copy(alpha = 0.65f)))
            )
        )
        Box(Modifier.fillMaxSize().background(Color(0xFF060B16).copy(alpha = 0.25f)))
        // 3. Vignette: Mitte dunkler, damit Icon und Schrift immer klar lesbar sind
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0f to Color(0xFF060B16).copy(alpha = 0.55f),
                    0.6f to Color(0xFF060B16).copy(alpha = 0.15f),
                    1f to Color.Transparent,
                )
            )
        )
        // dezente Akzentlinie unten (Portiva-Farben)
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, BrandCyan, Accent2Violet, Magenta, Color.Transparent))
            )
        )
        // 4. + 5. Icon und Beschriftung (unveraendert, mit Schatten fuer Lesbarkeit)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge.copy(
                    shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 3f), blurRadius = 10f),
                ),
            )
        }
    }
}

private val Accent2Violet = Color(0xFF7A3DFF)
private val Magenta = Color(0xFFE6007E)

@Composable
private fun SmallTile(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .tvFocus(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccountCard(info: AccountInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Konto", fontWeight = FontWeight.Bold)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        info.status?.let { Text("Status: $it", color = muted) }
        Text(
            "Ablaufdatum: " + (info.expiresAt?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "Unbegrenzt"),
            color = muted,
        )
        if (info.maxConnections != null) {
            Text("Verbindungen: ${info.activeConnections ?: "0"} / ${info.maxConnections}", color = muted)
        }
        if (info.isTrial) Text("Testzugang", color = muted)
    }
}
