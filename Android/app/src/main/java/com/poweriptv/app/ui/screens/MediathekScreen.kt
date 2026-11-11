package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.MediathekCatalog
import com.poweriptv.app.data.MediathekItem
import com.poweriptv.app.ui.components.CompactSearchField
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.LoadingBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mediatheken von ARD, ZDF, Arte & Co. – kostenlos und unabhaengig vom IPTV-Zugang.
 * Querformat/TV: links die Sender, rechts Suche und Sendungen (neueste zuerst). Handy: Sender als Leiste.
 */
@Composable
fun MediathekScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MediathekItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    val pageSize = 60

    LaunchedEffect(channel, query, reload) {
        items = null; error = null; hasMore = true
        delay(if (query.isBlank()) 0 else 500) // beim Tippen nicht jede Taste abfragen
        runCatching { container.mediathek.search(query, channel, 0, pageSize) }
            .onSuccess { items = it; hasMore = it.size >= pageSize / 2 }
            .onFailure { error = it.message ?: "Mediathek nicht erreichbar" }
    }

    fun loadMore() {
        val cur = items ?: return
        loadingMore = true
        scope.launch {
            runCatching { container.mediathek.search(query, channel, cur.size, pageSize) }
                .onSuccess { more ->
                    val known = cur.map { it.id }.toHashSet()
                    val add = more.filter { it.id !in known }
                    items = cur + add
                    hasMore = add.isNotEmpty()
                }
            loadingMore = false
        }
    }

    fun play(item: MediathekItem) {
        val title = if (item.topic.isNotBlank() && !item.title.contains(item.topic, true)) "${item.topic}: ${item.title}" else item.title
        startPlayback(context, container, listOf(PlayEntry(title, item.bestUrl(), null, live = false)), 0)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth > 600.dp
            Column(Modifier.fillMaxSize()) {
                if (!wide) PowerTopBar("Mediathek", onBack = onBack)
                Row(Modifier.fillMaxSize()) {
                    if (wide) {
                        // Senderspalte links (wie die Kategorien in Live TV/Filme)
                        Column(Modifier.width(220.dp).fillMaxHeight().statusBarsPadding().padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack, modifier = Modifier.tvFocus(CircleShape, 1.15f)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                                }
                                Text("Mediathek", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                                items(MediathekCatalog.channels) { c ->
                                    val sel = channel == c.query
                                    Text(
                                        c.label,
                                        maxLines = 1,
                                        color = if (sel) Color(0xFF06101E) else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                            .tvFocus(RoundedCornerShape(8.dp), 1.02f)
                                            .background(if (sel) BrandCyan else Color.Transparent)
                                            .clickable { channel = c.query }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                    )
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().then(if (wide) Modifier.statusBarsPadding() else Modifier)) {
                        CompactSearchField(
                            value = query, onValueChange = { query = it },
                            placeholder = "Sendung oder Thema suchen (z.B. Tatort, Tagesschau, Doku)",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        if (!wide) {
                            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(MediathekCatalog.channels) { c ->
                                    FilterChip(selected = channel == c.query, onClick = { channel = c.query }, label = { Text(c.label) })
                                }
                            }
                        }
                        val list = items
                        when {
                            error != null -> ErrorBox(error!!, onRetry = { reload++ })
                            list == null -> LoadingBox()
                            list.isEmpty() -> ErrorBox(if (query.isBlank()) "Keine Sendungen gefunden" else "Keine Treffer für „$query“")
                            else -> LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(list, key = { it.id }) { item -> MediathekRow(item, showDescription = wide) { play(item) } }
                                if (hasMore) item {
                                    OutlinedButton(
                                        onClick = ::loadMore, enabled = !loadingMore,
                                        modifier = Modifier.fillMaxWidth().padding(8.dp).tvFocus(RoundedCornerShape(50)),
                                    ) { Text(if (loadingMore) "Lädt …" else "Weitere Sendungen laden") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY)

@Composable
private fun MediathekRow(item: MediathekItem, showDescription: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .tvFocus(RoundedCornerShape(10.dp), 1.01f)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.channel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF06101E),
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(BrandCyan).padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                item.topic, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        Text(
            listOfNotNull(
                item.timestamp.takeIf { it > 0 }?.let { dateFmt.format(Date(it * 1000)) },
                item.duration.takeIf { it > 0 }?.let { "${(it + 59) / 60} Min." },
                if (item.urlHd != null) "HD" else null,
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showDescription) item.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
