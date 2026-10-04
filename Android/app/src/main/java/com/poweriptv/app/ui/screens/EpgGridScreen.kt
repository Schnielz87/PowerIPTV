@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.poweriptv.app.ui.screens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.EpgState
import com.poweriptv.app.data.Programme
import com.poweriptv.app.parental.PinDialog
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan
import com.poweriptv.app.ui.theme.Danger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val FAV_GROUP = "__fav__"
private val DP_PER_MIN: Dp = 5.dp
private val CHANNEL_COL: Dp = 150.dp
private val ROW_HEIGHT: Dp = 64.dp
private const val SLOT_MIN = 30L
private const val HOURS_BACK = 3L
private const val HOURS_AHEAD = 24L

/** Elektronischer Programmfuehrer als Timeline-Raster (Kanaele x Zeit). */
@Composable
fun EpgGridScreen(container: AppContainer, onBack: () -> Unit) {
    val source = container.source ?: run { ErrorBox("Kein Zugang ausgewaehlt"); return }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val epgState by container.epg.state.collectAsState()
    val favorites by container.favorites.favorites.collectAsState()
    // Langes Druecken: Menue (Favorit, Teilen)
    var actionsFor by remember { mutableStateOf<ContentItem?>(null) }
    actionsFor?.let { com.poweriptv.app.ui.components.ItemActionsDialog(container, it, onDismiss = { actionsFor = null }) }
    val parentalUnlocked by container.parental.sessionUnlocked.collectAsState()

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<ContentItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var pinFor by remember { mutableStateOf<Category?>(null) }
    var selected by remember { mutableStateOf<Pair<ContentItem, Programme>?>(null) }

    // Zeitfenster: auf 30 Minuten gerundet, 3 h zurueck (Catch-up) bis 24 h voraus
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val windowStart = remember {
        val slot = SLOT_MIN * 60_000L
        (System.currentTimeMillis() / slot) * slot - HOURS_BACK * 3600_000L
    }
    val windowEnd = windowStart + (HOURS_BACK + HOURS_AHEAD) * 3600_000L
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(30_000); now.longValue = System.currentTimeMillis() }
    }

    LaunchedEffect(source, reload) {
        container.epg.ensureLoaded(source, force = reload > 0)
    }
    LaunchedEffect(source) {
        runCatching { source.categories(ContentType.LIVE) }
            .onSuccess { cats ->
                categories = cats
                if (group == null) {
                    // Erste Kategorie der gewaehlten Sprache bevorzugen
                    val lang = container.settings.categoryLanguage.value
                    val ok = { c: Category -> !container.parental.requiresPin(source.profile.id, ContentType.LIVE, c) }
                    group = cats.firstOrNull { ok(it) && (lang.isEmpty() || com.poweriptv.app.ui.components.categoryLanguage(it.name) == lang) }?.id
                        ?: cats.firstOrNull(ok)?.id ?: FAV_GROUP
                }
            }
            .onFailure { error = it.message }
    }
    LaunchedEffect(group, if (group == FAV_GROUP) favorites else Unit) {
        val g = group ?: return@LaunchedEffect
        channels = null
        channels = if (g == FAV_GROUP) container.parental.visible(source.profile.id, favorites.filter { it.type == ContentType.LIVE })
        else runCatching { source.items(ContentType.LIVE, g) }.getOrElse { error = it.message; emptyList() }
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

    Scaffold(
        topBar = {
            PowerTopBar("TV-Guide (EPG)", onBack = onBack, actions = {
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { scrollToNow() }) { Icon(Icons.Filled.MyLocation, "Jetzt") }
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "EPG aktualisieren") }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Kanalgruppen
            val lockIcon: @Composable () -> Unit = { Icon(Icons.Filled.Lock, null, Modifier.size(16.dp)) }
            // Sprachfilter wie bei Live TV / Filme / Serien (gleiche Einstellung)
            val language by container.settings.categoryLanguage.collectAsState()
            val languages = remember(categories) { com.poweriptv.app.ui.components.detectLanguages(categories) }
            val activeLang = language.takeIf { it in languages } ?: ""
            val shownCats = if (activeLang.isEmpty()) categories
                else categories.filter { com.poweriptv.app.ui.components.categoryLanguage(it.name) == activeLang }
            var langMenu by remember { mutableStateOf(false) }
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (languages.size >= 2) item {
                    Box {
                        FilterChip(
                            modifier = Modifier.tvFocus(RoundedCornerShape(8.dp), 1.06f),
                            selected = activeLang.isNotEmpty(),
                            onClick = { langMenu = true },
                            label = { Text("🌐 " + activeLang.ifEmpty { "Alle Sprachen" } + " ▾") },
                        )
                        androidx.compose.material3.DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Alle Sprachen") }, onClick = { container.settings.setCategoryLanguage(""); langMenu = false })
                            languages.forEach { l ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = {
                                    container.settings.setCategoryLanguage(l); langMenu = false
                                    // erste Kategorie dieser Sprache anzeigen
                                    categories.firstOrNull { com.poweriptv.app.ui.components.categoryLanguage(it.name) == l }?.let { group = it.id }
                                })
                            }
                        }
                    }
                }
                item {
                    FilterChip(modifier = Modifier.tvFocus(RoundedCornerShape(8.dp), 1.06f), selected = group == FAV_GROUP, onClick = { group = FAV_GROUP }, label = { Text("★ Favoriten") })
                }
                items(shownCats, key = { it.id }) { c ->
                    val locked = container.parental.isConfiguredLocked(source.profile.id, ContentType.LIVE, c) &&
                        container.parental.enabled.value
                    FilterChip(
                        selected = c.id == group,
                        onClick = {
                            if (container.parental.requiresPin(source.profile.id, ContentType.LIVE, c)) pinFor = c
                            else group = c.id
                        },
                        label = { Text(c.name, maxLines = 1) },
                        leadingIcon = if (locked && !parentalUnlocked) lockIcon else null,
                    )
                }
            }

            when (val st = epgState) {
                EpgState.Loading -> Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("EPG wird geladen...", style = MaterialTheme.typography.bodySmall)
                }
                is EpgState.Error -> Text(
                    "EPG: ${st.message}", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp),
                )
                else -> Unit
            }

            val list = channels
            when {
                error != null && list.isNullOrEmpty() -> ErrorBox(error!!, onRetry = { error = null; reload++ })
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> ErrorBox("Keine Kanaele in dieser Gruppe")
                else -> {
                    // Zeitleiste
                    Row(Modifier.fillMaxWidth().height(32.dp)) {
                        Box(Modifier.width(CHANNEL_COL).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now.longValue)), style = MaterialTheme.typography.labelMedium)
                        }
                        Box(Modifier.horizontalScroll(hScroll)) {
                            Row {
                                var t = windowStart
                                val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
                                while (t < windowEnd) {
                                    Text(
                                        fmt.format(Date(t)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(DP_PER_MIN * SLOT_MIN.toFloat()).padding(start = 4.dp, top = 8.dp),
                                    )
                                    t += SLOT_MIN * 60_000L
                                }
                            }
                            // Jetzt-Markierung
                            val nowOffset = DP_PER_MIN * ((now.longValue - windowStart) / 60_000f)
                            Box(Modifier.offset(x = nowOffset).width(2.dp).height(32.dp).background(Danger))
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.key }) { ch ->
                            EpgRow(
                                container = container,
                                channel = ch,
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                now = now.longValue,
                                hScroll = hScroll,
                                onChannelClick = {
                                    val entries = list.map { PlayEntry(it.name, source.streamUrl(it), it, live = true) }
                                    startPlayback(context, container, entries, list.indexOf(ch))
                                },
                                onProgrammeClick = { p -> selected = ch to p },
                                isFavorite = favorites.any { it.key == ch.key },
                                onChannelLongClick = { actionsFor = ch },
                            )
                        }
                    }
                }
            }
        }
    }

    pinFor?.let { cat ->
        PinDialog(container.parental, onDismiss = { pinFor = null }, onSuccess = { group = cat.id; pinFor = null })
    }

    selected?.let { (ch, p) ->
        ProgrammeDialog(container, ch, p, list = channels.orEmpty(), onDismiss = { selected = null })
    }
}

@Composable
private fun EpgRow(
    container: AppContainer,
    channel: ContentItem,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    hScroll: androidx.compose.foundation.ScrollState,
    onChannelClick: () -> Unit,
    onProgrammeClick: (Programme) -> Unit,
    isFavorite: Boolean = false,
    onChannelLongClick: () -> Unit = {},
) {
    val epgState by container.epg.state.collectAsState()
    val timeline = remember(channel.key, epgState) { container.epg.timeline(channel, windowStart, windowEnd) }
    val recordings by container.recordings.entries.collectAsState()

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(vertical = 2.dp)) {
        Row(
            Modifier
                .width(CHANNEL_COL)
                .fillMaxHeight()
                .padding(end = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .tvFocus(RoundedCornerShape(8.dp), 1f)
                // Lange druecken: Sender als Favorit markieren
                .combinedClickable(onClick = onChannelClick, onLongClick = onChannelLongClick)
                .background(MaterialTheme.colorScheme.surface)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (!channel.logo.isNullOrBlank()) AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(6.dp))
            Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (isFavorite) Icon(Icons.Filled.Favorite, "Favorit", tint = androidx.compose.ui.graphics.Color(0xFFFF5370), modifier = Modifier.size(14.dp))
        }
        Row(Modifier.horizontalScroll(hScroll)) {
            timeline.forEach { p ->
                val minutes = ((p.end - p.start) / 60_000f).coerceAtLeast(1f)
                val live = p.isLive(now)
                val past = p.end <= now
                val recorded = recordings.any { r -> r.channelName == channel.name && r.start < p.end && r.end > p.start }
                Box(
                    Modifier
                        .width(DP_PER_MIN * minutes)
                        .fillMaxHeight()
                        .padding(1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .tvFocus(RoundedCornerShape(6.dp), 1f)
                        .clickable { onProgrammeClick(p) }
                        .background(
                            when {
                                p.isGap -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                live -> BrandCyan.copy(alpha = 0.22f)
                                past -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .then(if (live) Modifier.border(1.dp, BrandCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp)) else Modifier)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (recorded) Icon(Icons.Filled.FiberManualRecord, "Aufnahme", tint = Danger, modifier = Modifier.size(10.dp))
                            if (!p.isGap && past && container.source?.catchupUrl(channel, p.start, p.end) != null) {
                                Icon(Icons.Filled.History, "Catch-up", tint = BrandCyan, modifier = Modifier.size(12.dp))
                            }
                            Text(
                                p.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                                color = if (p.isGap || past) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                            )
                        }
                        if (!p.isGap) {
                            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
                            Text(
                                "${fmt.format(Date(p.start))} – ${fmt.format(Date(p.end))}",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeDialog(
    container: AppContainer,
    channel: ContentItem,
    p: Programme,
    list: List<ContentItem>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val source = container.source ?: return
    val now = System.currentTimeMillis()
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val catchup = if (p.end <= now || p.isLive(now)) source.catchupUrl(channel, p.start, p.end) else null
    var info by remember { mutableStateOf<String?>(null) }
    var reminded by remember { mutableStateOf(container.reminders.has(channel.name, p.start)) }
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (p.isGap) channel.name else p.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${channel.name} · ${fmt.format(Date(p.start))} – ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(p.end))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                p.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(4.dp))
                if (p.isLive(now) || p.isGap) {
                    Button(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                        onDismiss()
                        val entries = list.map { PlayEntry(it.name, source.streamUrl(it), it, live = true) }
                        startPlayback(context, container, entries, list.indexOf(channel).coerceAtLeast(0))
                    }) { Text("Live ansehen") }
                }
                if (catchup != null && !p.isGap) {
                    OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                        onDismiss()
                        startPlayback(context, container, listOf(PlayEntry("${channel.name}: ${p.title} (Catch-up)", catchup, null, live = false)), 0)
                    }) { Text(if (p.isLive(now)) "Von Beginn an (Timeshift)" else "Nachtraeglich ansehen (Catch-up)") }
                }
                // Erinnerung: 5 Minuten vor Beginn benachrichtigen
                if (p.start > now && !p.isGap) {
                    OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                        if (reminded) {
                            container.reminders.remove(channel.name, p.start)
                            reminded = false; info = "Erinnerung entfernt"
                        } else {
                            if (android.os.Build.VERSION.SDK_INT >= 33) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            container.reminders.add(p.title, channel.name, source.streamUrl(channel), p.start, channel.logo)
                            reminded = true; info = "Erinnerung gesetzt – 5 Minuten vor Beginn"
                        }
                    }) { Text(if (reminded) "🔕 Erinnerung entfernen" else "🔔 Erinnern (5 Min. vorher)") }
                }
                if (p.end > now && !p.isGap) {
                    OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                        val result = container.recordings.schedule(
                            title = p.title,
                            channelName = channel.name,
                            url = source.streamUrl(channel),
                            start = maxOf(p.start, now),
                            end = p.end,
                            logo = channel.logo,
                        )
                        info = result
                    }) {
                        Icon(Icons.Filled.FiberManualRecord, null, tint = Danger, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (p.isLive(now)) "Jetzt aufnehmen (bis Sendungsende)" else "Aufnahme planen")
                    }
                }
            }
        },
        confirmButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = onDismiss) { Text("Schliessen") } },
    )
}
