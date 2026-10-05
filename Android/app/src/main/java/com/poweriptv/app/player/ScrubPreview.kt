package com.poweriptv.app.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.ui.theme.BrandCyan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Vorschaubilder beim Spulen: Automatisch / Immer / Aus. */
enum class ScrubPreviewMode(val label: String) {
    AUTO("Automatisch (nur wenn eine 2. Verbindung frei ist – sonst springt der Film direkt, ohne Vorschau)"),
    ALWAYS("Immer parallel (Film laeuft beim Spulen weiter, braucht eine 2. Verbindung)"),
    OFF("Aus"),
}

/**
 * Thumbnail-Scrubbing: holt beim Ziehen auf dem Zeitstrahl ein Standbild an der Zielposition.
 * Es wird immer nur das zuletzt angefragte Bild berechnet; fertige Bilder werden zwischengespeichert.
 */
class ScrubPreview private constructor(
    private val source: String,
    private val userAgent: String,
    /**
     * Ein-Verbindungs-Modus: Der Zugang erlaubt nur 1 Stream. Der Player gibt seine Verbindung
     * waehrend des Spulens frei, die Vorschau nutzt sie, danach geht es an der Zielstelle weiter.
     * So gibt es nie zwei Verbindungen gleichzeitig (kein Abbruch/Haenger beim Anbieter).
     */
    val exclusive: Boolean,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val pending = AtomicLong(-1L)
    private val cache = LruCache<Long, Bitmap>(60)
    private var retriever: MediaMetadataRetriever? = null
    @Volatile private var failed = false
    /** Zeitpunkt der letzten Vorschau-Anfrage (bricht der Film kurz danach ab, war es wohl die 2. Verbindung). */
    @Volatile var lastUsed = 0L
        private set

    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame

    fun request(positionMs: Long) {
        if (failed) return
        lastUsed = System.currentTimeMillis()
        cache.get(positionMs / BUCKET)?.let { _frame.value = it; return }
        if (pending.getAndSet(positionMs.coerceAtLeast(0)) == -1L) executor.execute(::work)
    }

    private fun work() {
        while (true) {
            val pos = pending.getAndSet(-1L)
            if (pos < 0) return
            val bmp = runCatching {
                val r = retriever ?: MediaMetadataRetriever().also { mm ->
                    if (source.startsWith("/")) mm.setDataSource(source)
                    else mm.setDataSource(source, mapOf("User-Agent" to userAgent))
                    retriever = mm
                }
                val us = pos * 1000
                if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scale(it) }
            }.onFailure { if (retriever == null) failed = true }.getOrNull()
            if (bmp != null) {
                cache.put(pos / BUCKET, bmp)
                _frame.value = bmp
            }
        }
    }

    private fun scale(b: Bitmap): Bitmap {
        val w = 320
        val h = (b.height * w / b.width.coerceAtLeast(1)).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(b, w, h, true).also { if (it !== b) b.recycle() }
    }

    /** Verbindung freigeben (nach dem Spulen); Bilder-Cache bleibt erhalten. onReleased laeuft danach (Hintergrund-Thread). */
    fun pause(onReleased: (() -> Unit)? = null) {
        pending.set(-1L)
        val task = Runnable { runCatching { retriever?.release() }; retriever = null; onReleased?.invoke() }
        if (runCatching { executor.execute(task) }.isFailure) onReleased?.invoke()
    }

    fun release() {
        pause()
        executor.shutdown()
    }

    /** Wurde die Vorschau gerade benutzt? (dann ist ein Abbruch des Films vermutlich ihre Schuld) */
    fun recentlyUsed() = System.currentTimeMillis() - lastUsed < 30_000

    companion object {
        private const val BUCKET = 5_000L

        /** Liefert eine Vorschau fuer den Titel – oder null, wenn sie hier nicht moeglich/erlaubt ist. */
        fun create(container: AppContainer, entry: PlayEntry?): ScrubPreview? {
            if (entry == null || entry.live) return null
            val local = entry.url.startsWith("/")
            val mode = container.settings.scrubPreviewEnum()
            // Genug Verbindungen frei? Dann parallel (Film laeuft beim Spulen weiter), sonst Ein-Verbindungs-Modus.
            val parallelOk = local || (!container.settings.scrubBlocked.value &&
                ((container.maxConnections ?: 1) - container.recordings.running().size) >= 2)
            val allowed = mode != ScrubPreviewMode.OFF
            // Automatisch: Vorschau nur mit freier 2. Verbindung. Bei nur 1 Verbindung KEINE Vorschau – sonst muesste der
            // Film fuers Spulen angehalten und neu verbunden werden (dauert bei vielen Anbietern 10–20 s). Wie bei IPTV
            // Smarters springt der Film dann einfach direkt an die gewaehlte Stelle.
            if (mode == ScrubPreviewMode.AUTO && !parallelOk) return null
            val exclusive = false
            if (!allowed) return null
            // Vorschau laeuft nicht ueber den VPN-Tunnel-Schutz der App -> bei Pflicht-VPN ohne Tunnel nicht laden
            if (!local && container.settings.vpnRequired.value && !container.vpn.isProtected()) return null
            return ScrubPreview(entry.url, container.settings.userAgent.value, exclusive)
        }
    }
}

/** Vorschaubild + Zeit ueber dem Zeitstrahl, horizontal an der Spulposition. */
@Composable
fun ScrubPreviewBubble(frame: Bitmap?, time: String, fraction: Float, bottomPadding: androidx.compose.ui.unit.Dp, showImage: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = 180.dp
        val x = (maxWidth * fraction.coerceIn(0f, 1f) - w / 2).coerceIn(8.dp, maxWidth - w - 8.dp)
        Column(
            Modifier.align(Alignment.BottomStart).offset(x = x).padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showImage) Box(
                Modifier.size(w, w * 9 / 16).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (frame != null) Image(frame.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else CircularProgressIndicator(Modifier.size(22.dp), color = BrandCyan, strokeWidth = 2.dp)
            }
            Text(
                time, color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
