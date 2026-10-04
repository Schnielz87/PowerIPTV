@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.poweriptv.app.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.player.PlayerActivity
import com.poweriptv.app.player.VlcPlayerActivity
import com.poweriptv.app.data.PlayerEngine
import com.poweriptv.app.ui.theme.Danger
import com.poweriptv.app.ui.theme.Success
import com.poweriptv.app.vpn.VpnState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    /** Transparent ueber einem Hintergrundbild (Detailseiten auf Tablet/TV). */
    transparent: Boolean = false,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.tvFocus(CircleShape, 1.15f)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                }
                // Portiva-"P": von jeder Seite direkt zur Startseite
                HomeLogoButton()
            }
        },
        actions = { actions() },
        // Im Querformat flacher (48 dp statt 64 dp)
        expandedHeight = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 48.dp
        else TopAppBarDefaults.TopAppBarExpandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (transparent) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background),
    )
}

/** Zeigt an, ob die App durch ein VPN geschuetzt ist. */
@Composable
fun VpnBadge(container: AppContainer, onClick: () -> Unit) {
    val state by container.vpn.state.collectAsState()
    var external by remember { mutableStateOf(container.vpn.isProtected()) }
    LaunchedEffect(state) {
        while (true) {
            external = container.vpn.isProtected()
            delay(3000)
        }
    }
    val protected = state == VpnState.CONNECTED || external
    val color = when {
        state == VpnState.CONNECTING -> MaterialTheme.colorScheme.onSurfaceVariant
        protected -> Success
        else -> Danger
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (protected) Icons.Filled.GppGood else Icons.Filled.GppBad, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                state == VpnState.CONNECTING -> "Verbinde..."
                protected -> "VPN aktiv"
                else -> "Kein VPN"
            },
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    /** Bereits gesehen (gruener Haken) bzw. angefangen (Fortschrittsbalken). */
    watched: Boolean = false,
    progress: Float? = null,
    /** Favorit (Herz) und langes Druecken (z.B. Favorit umschalten). */
    favorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .tvFocus(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            WatchedOverlay(watched, progress)
            if (favorite) FavoriteBadge()
        }
        Text(
            title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}

/**
 * Laedt das erste funktionierende Bild aus einer Liste von URLs
 * (manche Anbieter liefern kaputte Bild-Links).
 */
@Composable
fun FallbackImage(urls: List<String>, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var index by remember(urls) { mutableStateOf(0) }
    val url = urls.getOrNull(index) ?: return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        onError = { index++ },
    )
}

/** Schluessel "Kategorie braucht VLC" (pro Zugang und Bereich). */
fun vlcCategoryKey(profileId: String?, item: com.poweriptv.app.data.ContentItem) =
    "${profileId ?: ""}|${item.type.name}|${item.categoryId}"

/** Startet den Player mit einer Wiedergabeliste. */
fun startPlayback(context: Context, container: AppContainer, entries: List<PlayEntry>, index: Int, askResume: Boolean = true) {
    if (entries.isEmpty()) return
    // Kindersicherung: gesperrter Titel -> erst PIN, gesperrte Nachbarn aus der Warteschlange nehmen
    val pid = container.source?.profile?.id
    if (pid != null) {
        val target = entries[index.coerceIn(0, entries.lastIndex)]
        if (target.item?.let { container.parental.isItemBlocked(pid, it) } == true) {
            container.pinGate.value = { startPlayback(context, container, entries, index, askResume) }
            return
        }
        val clean = entries.filter { it === target || it.item == null || !container.parental.isItemBlocked(pid, it.item) }
        if (clean.size != entries.size) {
            startPlayback(context, container, clean, clean.indexOf(target), askResume); return
        }
    }
    // Film/Folge schon angefangen? -> erst fragen: weiterschauen oder von vorne
    val first = entries[index.coerceIn(0, entries.lastIndex)]
    if (askResume && !first.live) {
        val pos = container.resume.get(first.url)
        if (pos > 0) {
            container.resumePrompt.value = com.poweriptv.app.ResumePrompt(entries, index, pos)
            return
        }
    }
    container.playQueue = entries
    container.playIndex = index.coerceIn(0, entries.lastIndex)
    val url = entries[container.playIndex].url
    val useVlc = when (container.settings.playerEngineEnum()) {
        PlayerEngine.VLC -> true
        PlayerEngine.EXO -> false
        // Automatisch: VLC ist der gemeinsame Player (Android, Windows, iOS) fuer Filme & Serien
        // (spielt jedes Ton-/Bildformat, z.B. DTS). Live TV bleibt beim Standard-Player wegen Live-Pause/Timeshift;
        // kann der Standard-Player einen Sender nicht, wechselt er wie bisher automatisch auf VLC.
        PlayerEngine.AUTO -> !entries[container.playIndex].live || container.settings.needsVlc(url)
    }
    context.startActivity(Intent(context, if (useVlc) VlcPlayerActivity::class.java else PlayerActivity::class.java).apply {
        // Start ohne Activity (z.B. Wiedergabe von einem anderen Geraet uebernommen)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

/** Dialog "Weiterschauen ab ... / Von vorne beginnen" (einmal global in MainActivity eingebunden). */
@Composable
fun ResumePromptDialog(container: AppContainer) {
    val prompt by container.resumePrompt.collectAsState()
    val p = prompt ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val entry = p.entries[p.index.coerceIn(0, p.entries.lastIndex)]
    val dismiss = { container.resumePrompt.value = null }
    val s = p.positionMs / 1000
    val time = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    val resumeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Filled.Movie, null, tint = com.poweriptv.app.ui.theme.BrandCyan) },
        title = { Text("Weiterschauen?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("\"${entry.title}\" hast du bei $time unterbrochen.", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { dismiss(); startPlayback(context, container, p.entries, p.index, askResume = false) },
                    modifier = Modifier.fillMaxWidth().focusRequester(resumeFocus).tvFocus(RoundedCornerShape(50)),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp)); Text("Weiterschauen ab $time")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        dismiss()
                        container.resume.clear(entry.url)
                        startPlayback(context, container, p.entries, p.index, askResume = false)
                    },
                    modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50)),
                ) {
                    Icon(Icons.Filled.Replay, null)
                    Spacer(Modifier.width(8.dp)); Text("Von vorne beginnen")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = dismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Abbrechen") }
        },
    )
    LaunchedEffect(p) { runCatching { resumeFocus.requestFocus() } }
}

/** Gesehen-Haken oben rechts und Fortschrittsbalken unten auf einem Vorschaubild. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.WatchedOverlay(watched: Boolean, progress: Float?) {
    if (watched) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                .clip(androidx.compose.foundation.shape.CircleShape).background(Success),
            contentAlignment = Alignment.Center,
        ) { Icon(androidx.compose.material.icons.Icons.Filled.Check, "Gesehen", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(15.dp)) }
    }
    if (progress != null && !watched) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
            color = com.poweriptv.app.ui.theme.BrandCyan,
            trackColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
        )
    }
}

/** Kleines Herz oben links (Favorit). */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FavoriteBadge() {
    Box(
        Modifier.align(Alignment.TopStart).padding(5.dp).size(22.dp)
            .clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) { Icon(androidx.compose.material.icons.Icons.Filled.Favorite, "Favorit", tint = androidx.compose.ui.graphics.Color(0xFFFF5370), modifier = Modifier.size(14.dp)) }
}

/** Favorit umschalten – ueberall gleich, mit kurzer Rueckmeldung. */
fun toggleFavorite(context: Context, container: AppContainer, item: com.poweriptv.app.data.ContentItem) {
    container.favorites.toggle(item)
    val now = container.favorites.isFavorite(item)
    android.widget.Toast.makeText(
        context, if (now) "♥ ${item.name} zu Favoriten hinzugefuegt" else "${item.name} aus Favoriten entfernt", android.widget.Toast.LENGTH_SHORT,
    ).show()
}

/** PIN-Abfrage fuer gesperrte Inhalte (einmal global in MainActivity eingebunden). */
@Composable
fun PinGateDialog(container: AppContainer) {
    val action by container.pinGate.collectAsState()
    val a = action ?: return
    com.poweriptv.app.parental.PinDialog(
        container.parental,
        message = "Dieser Inhalt ist durch die Kindersicherung gesperrt",
        onDismiss = { container.pinGate.value = null },
        onSuccess = { container.pinGate.value = null; a() },
    )
}

/**
 * Menue beim langen Druecken: Favorit, Teilen und (optional) aus einer Liste entfernen.
 * Geteilt wird nur der Titel – niemals der Stream-Link (der enthaelt die Zugangsdaten).
 */
@Composable
fun ItemActionsDialog(
    container: AppContainer,
    item: com.poweriptv.app.data.ContentItem,
    onDismiss: () -> Unit,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val favs by container.favorites.favorites.collectAsState()
    val isFav = favs.any { it.key == item.key }
    val btn = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = { toggleFavorite(context, container, item); onDismiss() }, modifier = btn) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Favorite, null, tint = androidx.compose.ui.graphics.Color(0xFFFF5370))
                    Spacer(Modifier.width(8.dp)); Text(if (isFav) "Aus Favoriten entfernen" else "Zu Favoriten hinzufuegen")
                }
                androidx.compose.material3.OutlinedButton(onClick = { shareItem(context, item); onDismiss() }, modifier = btn) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Share, null)
                    Spacer(Modifier.width(8.dp)); Text("Teilen (WhatsApp & Co.)")
                }
                if (onRemove != null) {
                    androidx.compose.material3.OutlinedButton(onClick = { onRemove(); onDismiss() }, modifier = btn) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Delete, null)
                        Spacer(Modifier.width(8.dp)); Text(removeLabel ?: "Aus Liste entfernen")
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Schliessen") } },
    )
}

/** Titel teilen (ohne Stream-Link/Zugangsdaten). */
fun shareItem(context: Context, item: com.poweriptv.app.data.ContentItem) {
    val kind = when (item.type) {
        com.poweriptv.app.data.ContentType.MOVIE -> "Film-Tipp"
        com.poweriptv.app.data.ContentType.SERIES -> "Serien-Tipp"
        else -> "Sender-Tipp"
    }
    val title = item.name.replace(Regex("\\s*#\\s*[A-Z]{2,3}$"), "").trim()
    val text = buildString {
        append("📺 $kind: $title")
        item.year?.let { append(" ($it)") }
        item.ratingValue?.let { append(" – ★ %.1f".format(it)) }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Teilen").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
