package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.EpgState
import com.poweriptv.app.data.Programme
import com.poweriptv.app.ui.components.categoryLanguage
import com.poweriptv.app.ui.components.detectLanguages
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.data.Reminder
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.Danger
import com.poweriptv.desktop.ui.NetImage
import com.poweriptv.desktop.ui.handCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FAV_GROUP = "__fav__"
private val DP_PER_MIN: Dp = 6.dp
private val CHANNEL_COL: Dp = 220.dp
private val ROW_HEIGHT: Dp = 68.dp
private const val SLOT_MIN = 30L
private const val HOURS_BACK = 3L
private const val HOURS_AHEAD = 24L

private val timeFmt get() = SimpleDateFormat("HH:mm", Locale.GERMANY)

/** Programmfuehrer als Timeline-Raster (Sender x Zeit) – wie in der Android-App. */
@Composable
fun EpgScreen(app: AppState) {
    val src = app.source ?: return
    val lib = app.library ?: return
    val scope = rememberCoroutineScope()
    val epgState by app.epg.state.collectAsState()
    val favorites by lib.favorites.collectAsState()
    val settings by app.settings.state.collectAsState()
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var group by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<ContentItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<ContentItem, Programme>?>(null) }

    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val windowStart = remember {
        val slot = SLOT_MIN * 60_000L
        (System.currentTimeMillis() / slot) * slot - HOURS_BACK * 3600_000L
    }
    val windowEnd = windowStart + (HOURS_BACK + HOURS_AHEAD) * 3600_000L
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); now.longValue = System.currentTimeMillis() } }
    LaunchedEffect(reload) { withContext(Dispatchers.IO) { app.epg.ensureLoaded(src, force = reload > 0) } }
    LaunchedEffect(src) {
        runCatching { withContext(Dispatchers.IO) { src.categories(ContentType.LIVE) } }
            .onSuccess { all ->
                val cats = all.filterNot { app.parental.requiresPin(src.profile.id, ContentType.LIVE, it) }
                categories = cats
                if (group == null) {
                    val lang = settings.categoryLanguage
                    group = cats.firstOrNull { lang.isEmpty() || categoryLanguage(it.name) == lang }?.id ?: cats.firstOrNull()?.id ?: FAV_GROUP
                }
            }
            .onFailure { error = it.message }
    }
    LaunchedEffect(group, if (group == FAV_GROUP) favorites else Unit) {
        val g = group ?: return@LaunchedEffect
        channels = null
        channels = if (g == FAV_GROUP) app.parental.visible(src.profile.id, favorites.filter { it.type == ContentType.LIVE })
        else runCatching { withContext(Dispatchers.IO) { src.items(ContentType.LIVE, g) } }.getOrElse { error = it.message; emptyList() }
    }

    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    fun scrollToNow() = scope.launch {
        val offsetMin = (System.currentTimeMillis() - windowStart) / 60_000L - 30
        val px = with(density) { (DP_PER_MIN * offsetMin.toFloat()).roundToPx() }
        snapshotFlow { hScroll.maxValue }.first { it > 0 && it != Int.MAX_VALUE }
        hScroll.animateScrollTo(px.coerceIn(0, hScroll.maxValue))
    }
    LaunchedEffect(channels != null) { if (channels != null) scrollToNow() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TV-Guide (EPG)", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { scrollToNow() }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.MyLocation, "Jetzt") }
            IconButton(onClick = { reload++ }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Refresh, "EPG aktualisieren") }
        }
        // Gruppen (mit Sprachfilter wie bei Live TV)
        val language = settings.categoryLanguage
        val languages = remember(categories) { detectLanguages(categories) }
        val activeLang = language.takeIf { it in languages } ?: ""
        val shownCats = if (activeLang.isEmpty()) categories else categories.filter { categoryLanguage(it.name) == activeLang }
        var langMenu by remember { mutableStateOf(false) }
        LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (languages.size >= 2) item {
                Box {
                    FilterChip(selected = activeLang.isNotEmpty(), onClick = { langMenu = true }, label = { Text("🌐 " + activeLang.ifEmpty { "Alle Sprachen" } + " ▾") }, modifier = Modifier.handCursor())
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        DropdownMenuItem(text = { Text("Alle Sprachen") }, onClick = { app.settings.update { it.copy(categoryLanguage = "") }; langMenu = false })
                        languages.forEach { l ->
                            DropdownMenuItem(text = { Text(l) }, onClick = {
                                app.settings.update { it.copy(categoryLanguage = l) }; langMenu = false
                                categories.firstOrNull { categoryLanguage(it.name) == l }?.let { group = it.id }
                            })
                        }
                    }
                }
            }
            item { FilterChip(selected = group == FAV_GROUP, onClick = { group = FAV_GROUP }, label = { Text("★ Favoriten") }, modifier = Modifier.handCursor()) }
            items(shownCats, key = { it.id }) { c ->
                FilterChip(selected = c.id == group, onClick = { group = c.id }, label = { Text(c.name, maxLines = 1) }, modifier = Modifier.handCursor())
            }
        }
        when (val st = epgState) {
            EpgState.Loading -> Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("EPG wird geladen …", style = MaterialTheme.typography.bodySmall)
            }
            is EpgState.Error -> Text("EPG: ${st.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else -> Unit
        }
        val list = channels
        when {
            error != null && list.isNullOrEmpty() -> Text(error!!, color = MaterialTheme.colorScheme.error)
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyHint("Keine Sender in dieser Gruppe")
            else -> {
                // Zeitleiste
                Row(Modifier.fillMaxWidth().height(34.dp)) {
                    Box(Modifier.width(CHANNEL_COL).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Text(SimpleDateFormat("EEE, dd.MM.", Locale.GERMANY).format(Date(now.longValue)), style = MaterialTheme.typography.labelLarge)
                    }
                    Box(Modifier.horizontalScroll(hScroll)) {
                        Row {
                            var t = windowStart
                            while (t < windowEnd) {
                                Text(
                                    timeFmt.format(Date(t)), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(DP_PER_MIN * SLOT_MIN.toFloat()).padding(start = 4.dp, top = 8.dp),
                                )
                                t += SLOT_MIN * 60_000L
                            }
                        }
                        Box(Modifier.offset(x = DP_PER_MIN * ((now.longValue - windowStart) / 60_000f)).width(2.dp).height(34.dp).background(Danger))
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.key }) { ch ->
                        EpgRow(
                            app, ch, windowStart, windowEnd, now.longValue, hScroll, epgState,
                            isFavorite = favorites.any { it.key == ch.key },
                            onChannelClick = { app.play(ch, channels = list) },
                            onProgrammeClick = { p -> selected = ch to p },
                        )
                    }
                }
            }
        }
    }
    selected?.let { (ch, p) -> ProgrammeDialog(app, ch, p, channels.orEmpty()) { selected = null } }
}

@Composable
private fun EpgRow(
    app: AppState,
    channel: ContentItem,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    hScroll: ScrollState,
    epgState: EpgState,
    isFavorite: Boolean,
    onChannelClick: () -> Unit,
    onProgrammeClick: (Programme) -> Unit,
) {
    val lib = app.library ?: return
    val timeline = remember(channel.key, epgState) { app.epg.timeline(channel, windowStart, windowEnd) }
    val recordings by app.recordings.entries.collectAsState()
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 2.dp)) {
        ContextMenuArea(items = {
            listOf(ContextMenuItem(if (isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten") { lib.toggleFavorite(channel) })
        }) {
            Row(
                Modifier.width(CHANNEL_COL).fillMaxHeight().padding(end = 2.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface).handCursor().clickable(onClick = onChannelClick).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetImage(listOf(channel.logo), Modifier.size(width = 52.dp, height = 40.dp), ContentScale.Fit)
                Spacer(Modifier.width(8.dp))
                Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (isFavorite) Icon(Icons.Filled.Favorite, "Favorit", tint = Danger, modifier = Modifier.size(14.dp))
            }
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            timeline.forEach { p ->
                val minutes = ((p.end - p.start) / 60_000f).coerceAtLeast(1f)
                val live = p.isLive(now)
                val past = p.end <= now
                val reminded = app.reminders.has(channel.name, p.start)
                Box(
                    Modifier.width(DP_PER_MIN * minutes).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(6.dp))
                        .handCursor().clickable { onProgrammeClick(p) }
                        .background(
                            when {
                                p.isGap -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                live -> BrandCyan.copy(alpha = 0.22f)
                                past -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .then(if (live) Modifier.border(1.dp, BrandCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp)) else Modifier)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (reminded) Icon(Icons.Filled.Notifications, "Erinnerung", tint = BrandCyan, modifier = Modifier.size(12.dp))
                            if (recordings.any { r -> r.channelName == channel.name && r.start < p.end && r.end > p.start && r.status != com.poweriptv.app.record.RecStatus.CANCELLED }) {
                                Icon(Icons.Filled.FiberManualRecord, "Aufnahme", tint = Danger, modifier = Modifier.size(10.dp))
                            }
                            if (!p.isGap && past && app.source?.catchupUrl(channel, p.start, p.end) != null) {
                                Icon(Icons.Filled.History, "Catch-up", tint = BrandCyan, modifier = Modifier.size(12.dp))
                            }
                            Text(
                                p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                                color = if (p.isGap || past) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                            )
                        }
                        if (!p.isGap) {
                            Text(
                                "${timeFmt.format(Date(p.start))} – ${timeFmt.format(Date(p.end))}", maxLines = 1,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeDialog(app: AppState, channel: ContentItem, p: Programme, list: List<ContentItem>, onDismiss: () -> Unit) {
    val src = app.source ?: return
    val now = System.currentTimeMillis()
    val catchup = if (p.end <= now || p.isLive(now)) src.catchupUrl(channel, p.start, p.end) else null
    var info by remember { mutableStateOf<String?>(null) }
    var reminded by remember { mutableStateOf(app.reminders.has(channel.name, p.start)) }
    val full = SimpleDateFormat("EEE dd.MM., HH:mm", Locale.GERMANY)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.isGap) channel.name else p.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${channel.name} · ${full.format(Date(p.start))} – ${timeFmt.format(Date(p.end))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                p.description?.let { Text(it) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (p.isLive(now) || p.isGap) {
                    Button(onClick = { onDismiss(); app.play(channel, channels = list) }, modifier = Modifier.handCursor()) { Text("Live ansehen") }
                }
                if (catchup != null && !p.isGap) {
                    OutlinedButton(onClick = { onDismiss(); app.playCatchup(channel, p.title, catchup) }, modifier = Modifier.handCursor()) {
                        Text(if (p.isLive(now)) "Von Beginn an (Timeshift)" else "Nachträglich ansehen (Catch-up)")
                    }
                }
                if (p.end > now && !p.isGap) {
                    OutlinedButton(onClick = {
                        info = app.recordings.schedule(p.title, channel.name, channel.url ?: src.streamUrl(channel), maxOf(p.start, now), p.end, channel.logo)
                    }, modifier = Modifier.handCursor()) {
                        Icon(Icons.Filled.FiberManualRecord, null, tint = Danger, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (p.isLive(now)) "Jetzt aufnehmen (bis Sendungsende)" else "Aufnahme planen")
                    }
                }
                if (p.start > now && !p.isGap) {
                    OutlinedButton(onClick = {
                        if (reminded) {
                            app.reminders.remove(channel.name, p.start); reminded = false; info = "Erinnerung entfernt"
                        } else {
                            app.reminders.add(Reminder(p.title, channel.name, channel.key, p.start, channel.logo))
                            reminded = true; info = "Erinnerung gesetzt – 5 Minuten vor Beginn"
                        }
                    }, modifier = Modifier.handCursor()) { Text(if (reminded) "🔕 Erinnerung entfernen" else "🔔 Erinnern (5 Min. vorher)") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
