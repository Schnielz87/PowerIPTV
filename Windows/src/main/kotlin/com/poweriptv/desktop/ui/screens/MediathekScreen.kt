package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.MediathekCatalog
import com.poweriptv.app.data.MediathekItem
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.ui.Page
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.desktop.ui.BrandCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mediatheken von ARD, ZDF, Arte & Co. (wie Android): links die Sender, rechts Suche und Sendungen. */
@Composable
fun MediathekScreen(app: AppState) {
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MediathekItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    val pageSize = 60

    LaunchedEffect(channel, query, reload) {
        items = null; error = null; hasMore = true
        delay(if (query.isBlank()) 0 else 500)
        runCatching { app.mediathek.search(query, channel, 0, pageSize) }
            .onSuccess { items = it; hasMore = it.size >= pageSize / 2 }
            .onFailure { error = it.message ?: "Mediathek nicht erreichbar" }
    }

    fun play(item: MediathekItem) {
        val title = if (item.topic.isNotBlank() && !item.title.contains(item.topic, true)) "${item.topic}: ${item.title}" else item.title
        app.play(ContentItem(id = "mediathek:" + item.id, name = title, type = ContentType.MOVIE, url = item.bestUrl()))
    }

    Page("Mediathek – ARD, ZDF, Arte & Co.", onBack = { app.back() }, maxWidth = 1400, scroll = false) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LazyColumn(Modifier.width(210.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(MediathekCatalog.channels) { c ->
                    val sel = channel == c.query
                    Text(
                        c.label, maxLines = 1,
                        color = if (sel) Color(0xFF06101E) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (sel) BrandCyan else Color.Transparent)
                            .handCursor().clickable { channel = c.query }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Sendung oder Thema suchen (z.B. Tatort, Tagesschau, Doku)") },
                )
                val list = items
                when {
                    error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { reload++ }, modifier = Modifier.handCursor()) { Text("Erneut versuchen") }
                    }
                    list == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> Text(if (query.isBlank()) "Keine Sendungen gefunden" else "Keine Treffer für „$query“", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(list, key = { it.id }) { item -> MediathekRow(item) { play(item) } }
                        if (hasMore) item {
                            OutlinedButton(
                                enabled = !loadingMore, modifier = Modifier.fillMaxWidth().handCursor(),
                                onClick = {
                                    loadingMore = true
                                    scope.launch {
                                        runCatching { app.mediathek.search(query, channel, list.size, pageSize) }.onSuccess { more ->
                                            val known = list.map { it.id }.toHashSet()
                                            val add = more.filter { it.id !in known }
                                            items = list + add; hasMore = add.isNotEmpty()
                                        }
                                        loadingMore = false
                                    }
                                },
                            ) { Text(if (loadingMore) "Lädt …" else "Weitere Sendungen laden") }
                        }
                    }
                }
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY)

@Composable
private fun MediathekRow(item: MediathekItem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)
            .handCursor().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.channel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF06101E),
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(BrandCyan).padding(horizontal = 6.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(item.topic, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
        item.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
