package com.poweriptv.app.record

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

/**
 * Schneidet einen Live-Stream mit (fuer Aufnahmen und Timeshift).
 * - MPEG-TS: Bytes werden direkt mitgeschrieben.
 * - HLS (.m3u8): Playlist wird zyklisch gelesen, neue Segmente werden angehaengt.
 * Laeuft ueber den App-OkHttp-Client (VPN-Kill-Switch + User-Agent gelten).
 */
class StreamCapture(private val http: OkHttpClient) {

    /**
     * @param keepGoing wird regelmaessig geprueft; false beendet die Aufnahme sauber.
     * @param onBytes   Gesamtzahl geschriebener Bytes.
     */
    suspend fun capture(url: String, out: OutputStream, keepGoing: () -> Boolean, onBytes: (Long) -> Unit) {
        if (url.contains(".m3u8", ignoreCase = true)) {
            captureHls(url, out, keepGoing, onBytes); return
        }
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Stream nicht erreichbar (HTTP ${resp.code})")
            val type = resp.header("Content-Type").orEmpty().lowercase()
            if ("mpegurl" in type) {
                resp.close()
                captureHls(resp.request.url.toString(), out, keepGoing, onBytes); return
            }
            val input = resp.body?.byteStream() ?: throw IOException("Leerer Stream")
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (keepGoing()) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                total += n
                onBytes(total)
            }
            out.flush()
        }
    }

    private suspend fun captureHls(url: String, out: OutputStream, keepGoing: () -> Boolean, onBytes: (Long) -> Unit) {
        var playlistUrl = url
        val seen = LinkedHashSet<String>()
        var total = 0L
        var initWritten = false
        while (keepGoing()) {
            currentCoroutineContext().ensureActive()
            val text = fetchText(playlistUrl)
            if (text.contains("#EXT-X-STREAM-INF")) {
                playlistUrl = pickVariant(playlistUrl, text); continue
            }
            if (text.contains("#EXT-X-KEY") && !text.contains("METHOD=NONE")) {
                throw IOException("Verschluesselte HLS-Streams koennen nicht aufgenommen werden")
            }
            var target = 6
            val lines = text.lines().map { it.trim() }
            for (line in lines) {
                when {
                    line.startsWith("#EXT-X-TARGETDURATION:") -> target = line.substringAfter(":").toIntOrNull() ?: 6
                    line.startsWith("#EXT-X-MAP:") && !initWritten -> {
                        val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                        if (uri != null) {
                            total += download(resolve(playlistUrl, uri), out); initWritten = true; onBytes(total)
                        }
                    }
                    line.isNotEmpty() && !line.startsWith("#") -> {
                        val segUrl = resolve(playlistUrl, line)
                        if (seen.add(segUrl)) {
                            if (!keepGoing()) break
                            total += download(segUrl, out)
                            onBytes(total)
                        }
                    }
                }
            }
            while (seen.size > 500) seen.remove(seen.first())
            if (text.contains("#EXT-X-ENDLIST")) break
            delay((target * 1000L / 2).coerceIn(1000L, 5000L))
        }
        out.flush()
    }

    private fun fetchText(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Playlist nicht erreichbar (HTTP ${resp.code})")
            resp.body?.string().orEmpty()
        }

    private fun download(url: String, out: OutputStream): Long =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Segment nicht erreichbar (HTTP ${resp.code})")
            resp.body?.byteStream()?.copyTo(out) ?: 0L
        }

    private fun pickVariant(base: String, master: String): String {
        val lines = master.lines().map { it.trim() }
        var best: String? = null
        var bestBw = -1L
        lines.forEachIndexed { i, l ->
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (uri != null && bw > bestBw) { bestBw = bw; best = uri }
            }
        }
        return resolve(base, best ?: throw IOException("Keine Stream-Variante gefunden"))
    }

    private fun resolve(base: String, ref: String): String =
        base.toHttpUrl().resolve(ref)?.toString() ?: ref
}
