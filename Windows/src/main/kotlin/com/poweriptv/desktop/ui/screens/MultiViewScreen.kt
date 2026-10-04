package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.categoryLanguage
import com.poweriptv.app.ui.components.detectLanguages
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.player.PlayerController
import com.poweriptv.desktop.player.VideoView
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.handCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Multi-Screen: bis zu 4 Sender gleichzeitig – gleiche Logik wie in der Android-App:
 * Ton nur aus dem aktiven Fenster, Limit des Zugangs wird beachtet (nur die zuletzt angeklickten laufen,
 * die uebrigen zeigen „Pausiert (Limit des Zugangs)“ mit dem letzten Bild), laufende Aufnahmen zaehlen mit.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MultiViewScreen(app: AppState) {
    val src = app.source ?: return
    val slots = remember { mutableStateListOf<ContentItem?>(null, null, null, null) }
    val players = remember { arrayOfNulls<PlayerController>(4) }
    val running = remember { mutableStateListOf<Int>() }
    val recent = remember { mutableListOf<Int>() }
    var fourWay by remember { mutableStateOf(false) }
    var audioSlot by remember { mutableStateOf(0) }
    var pickFor by remember { mutableStateOf<Int?>(null) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    LaunchedEffect(Unit) {
        if (app.maxConnections == null) {
            app.maxConnections = withContext(Dispatchers.IO) { runCatching { src.accountInfo()?.maxConnections?.toIntOrNull() }.getOrNull() }
        }
    }
    DisposableEffect(Unit) { onDispose { players.forEach { it?.release() } } }

    fun allowedStreams(): Int? = app.maxConnections?.let { (it - app.recordings.running().size).coerceAtLeast(1) }

    fun updateRunning() {
        val filled = (0 until 4).filter { slots[it] != null && (fourWay || it < 2) }
        val order = (listOf(audioSlot) + recent + filled).distinct().filter { it in filled }
        val shouldRun = order.take(allowedStreams() ?: filled.size).toSet()
        for (i in 0 until 4) {
            val item = slots[i]
            val p = players[i] ?: continue
            if (i in shouldRun && item != null) {
                if (i !in running) {
                    p.play(item.url ?: src.streamUrl(item), live = true); running.add(i)
                }
                p.muteAudio(i != audioSlot)
            } else if (i in running) {
                p.stopKeepFrame(); running.remove(i)
            }
        }
    }

    fun assign(index: Int, item: ContentItem) {
        val p = players[index] ?: PlayerController(app.settings.value.networkCaching, app.settings.value.hardwareDecoding).also { players[index] = it }
        p.stopKeepFrame()
        running.remove(index)
        slots[index] = item
        recent.remove(index); recent.add(0, index)
        if (slots.count { it != null } == 1) audioSlot = index
        updateRunning()
    }

    fun activate(index: Int) {
        audioSlot = index
        recent.remove(index); recent.add(0, index)
        updateRunning()
    }
    // Aus dem Live-Player geoeffnet: laufenden Sender ins erste Fenster
    LaunchedEffect(Unit) { app.multiViewStart?.let { assign(0, it) }; app.multiViewStart = null }

    val controlsVisible = now - lastMove < 4000 || slots.none { it != null }
    Box(
        Modifier.fillMaxSize().background(Color.Black).onPointerEvent(PointerEventType.Move) { lastMove = System.currentTimeMillis() },
    ) {
        Column(Modifier.fillMaxSize()) {
            val rows = if (fourWay) listOf(listOf(0, 1), listOf(2, 3)) else listOf(listOf(0, 1))
            rows.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { i ->
                        Box(
                            Modifier.weight(1f).fillMaxSize().padding(2.dp)
                                .border(if (i == audioSlot && slots[i] != null) 3.dp else 1.dp, if (i == audioSlot && slots[i] != null) BrandCyan else Color.White.copy(alpha = 0.15f))
                                .handCursor()
                                .clickable { lastMove = System.currentTimeMillis(); if (slots[i] == null) pickFor = i else activate(i) },
                        ) {
                            val p = players[i]
                            if (p != null) VideoView(p, "fit", Modifier.fillMaxSize())
                            val item = slots[i]
                            if (item == null) {
                                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Add, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
                                    Text("Sender wählen", color = Color.White.copy(alpha = 0.7f))
                                }
                            } else {
                                if (i !in running) {
                                    Text(
                                        "Pausiert (Limit des Zugangs)", color = Color.White,
                                        modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(10.dp),
                                    )
                                } else if (p != null && p.buffering) {
                                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp), color = BrandCyan)
                                }
                                if (controlsVisible) {
                                    Row(
                                        Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (i == audioSlot) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = BrandCyan, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                                        Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { pickFor = i }, modifier = Modifier.align(Alignment.TopEnd).handCursor()) { Text("Wechseln", color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (controlsVisible) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(onClick = { app.setFullscreen(false); app.back() }, modifier = Modifier.handCursor()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White) }
                FilterChip(selected = !fourWay, onClick = { fourWay = false; updateRunning() }, label = { Text("2 Fenster") }, modifier = Modifier.handCursor())
                FilterChip(selected = fourWay, onClick = { fourWay = true; updateRunning() }, label = { Text("4 Fenster") }, modifier = Modifier.handCursor())
                Text(
                    allowedStreams()?.let { "Streams: ${running.size} / $it" } ?: "Streams: ${running.size}",
                    color = Color.White, fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { app.toggleFullscreen() }, modifier = Modifier.handCursor()) {
                    Icon(if (app.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Vollbild", tint = Color.White)
                }
            }
        }
    }
    pickFor?.let { idx -> ChannelPicker(app, onDismiss = { pickFor = null }) { assign(idx, it); pickFor = null } }
}

/** Senderauswahl (Kategorie + Liste, mit Sprachfilter). */
@Composable
fun ChannelPicker(app: AppState, onDismiss: () -> Unit, onPick: (ContentItem) -> Unit) {
    val src = app.source ?: return
    val settings by app.settings.state.collectAsState()
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    var cat by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<ContentItem>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        cats = withContext(Dispatchers.IO) { runCatching { src.categories(ContentType.LIVE) }.getOrDefault(emptyList()) }
            .filterNot { app.parental.requiresPin(src.profile.id, ContentType.LIVE, it) }
        val lang = settings.categoryLanguage
        cat = cats.firstOrNull { lang.isNotEmpty() && categoryLanguage(it.name) == lang }?.id ?: cats.firstOrNull()?.id
    }
    LaunchedEffect(cat) {
        val c = cat ?: return@LaunchedEffect
        channels = null
        channels = withContext(Dispatchers.IO) { runCatching { src.items(ContentType.LIVE, c) }.getOrDefault(emptyList()) }
    }
    val languages = remember(cats) { detectLanguages(cats) }
    val lang = settings.categoryLanguage.takeIf { it in languages } ?: ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sender wählen") },
        text = {
            Column(Modifier.width(720.dp).height(560.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cats.filter { lang.isEmpty() || categoryLanguage(it.name) == lang }, key = { it.id }) { c ->
                        FilterChip(selected = c.id == cat, onClick = { cat = c.id }, label = { Text(c.name, maxLines = 1) }, modifier = Modifier.handCursor())
                    }
                }
                Spacer(Modifier.height(8.dp))
                SearchField(query, { query = it }, "Sender suchen")
                Spacer(Modifier.height(8.dp))
                val list = channels
                if (list == null) CircularProgressIndicator() else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.filter { matchesQuery(it.name, query) }, key = { it.key }) { ch -> ChannelCard(ch, onClick = { onPick(ch) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

