package com.poweriptv.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoLibrary
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
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.player.MultiViewActivity
import com.poweriptv.app.ui.components.BrandTopBar
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
    onEpg: () -> Unit,
    onRecordings: () -> Unit,
    onRecommendations: () -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: (ContentItem) -> Unit,
) {
    val source = container.source
    val context = LocalContext.current
    val history by container.history.items.collectAsState()
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    LaunchedEffect(source) { account = source?.accountInfo() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Scaffold(
        topBar = {
            BrandTopBar(subtitle = source?.profile?.name, actions = {
                IconButton(onClick = onSearch, modifier = Modifier.tvFocus()) { Icon(Icons.Filled.Search, "Suche") }
                VpnBadge(container, onVpn)
            })
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
                val big = listOf(
                    Triple("LIVE TV", Icons.Filled.LiveTv, ContentType.LIVE) to listOf(Color(0xFF5EC4F2), Color(0xFF2A7FC0)),
                    Triple("FILME", Icons.Filled.Movie, ContentType.MOVIE) to listOf(Color(0xFF3A8DC6), Color(0xFF123D6E)),
                    Triple("SERIEN", Icons.Filled.VideoLibrary, ContentType.SERIES) to listOf(Color(0xFF2266A8), Color(0xFF0A2547)),
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

                if (history.isNotEmpty() && source != null) {
                    Text("Zuletzt gesehen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(history.take(20), key = { it.key }) { item ->
                            Column(
                                Modifier
                                    .width(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .tvFocus(RoundedCornerShape(10.dp))
                                    .clickable {
                                        when {
                                            item.type == ContentType.LIVE || !source.supportsDetails ->
                                                startPlayback(context, container, listOf(PlayEntry(item.name, source.streamUrl(item), item, item.type == ContentType.LIVE)), 0)
                                            else -> onOpenDetail(item)
                                        }
                                    }
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (!item.logo.isNullOrBlank()) AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                }
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                account?.let { AccountCard(it) }
            }
        }
    }
}

@Composable
private fun BigTile(
    title: String,
    icon: ImageVector,
    colors: List<Color>,
    height: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .tvFocus(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

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
