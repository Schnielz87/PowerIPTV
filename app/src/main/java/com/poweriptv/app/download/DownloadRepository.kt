package com.poweriptv.app.download

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

@Serializable
enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

@Serializable
data class DownloadEntry(
    val id: String,
    val title: String,
    val url: String,
    val filePath: String,
    val poster: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloaded: Long = 0,
    val total: Long = -1,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Verwaltet Offline-Downloads (Filme & Episoden).
 * Laedt ueber den App-eigenen OkHttp-Client – dadurch gelten VPN und Kill-Switch auch fuer Downloads.
 * Unterbrochene Downloads werden per HTTP-Range fortgesetzt.
 */
class DownloadRepository(
    private val context: Context,
    private val json: Json,
    private val http: () -> OkHttpClient,
    /** Anzahl paralleler Verbindungen pro Download. */
    private val connections: suspend () -> Int = { 1 },
) {
    private val metaFile = File(context.filesDir, "downloads.json")
    private val serializer = ListSerializer(DownloadEntry.serializer())
    private val dir: File =
        (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "downloads")).apply { mkdirs() }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<DownloadEntry>> = _entries

    private fun load(): List<DownloadEntry> = runCatching {
        json.decodeFromString(serializer, metaFile.readText())
    }.getOrDefault(emptyList()).map {
        // Nach einem Neustart laufende Downloads als pausiert markieren
        if (it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.PAUSED) else it
    }

    @Synchronized
    private fun mutate(transform: (List<DownloadEntry>) -> List<DownloadEntry>) {
        val updated = transform(_entries.value)
        _entries.value = updated
        runCatching { metaFile.writeText(json.encodeToString(serializer, updated)) }
    }

    private fun patch(id: String, f: (DownloadEntry) -> DownloadEntry) = mutate { list -> list.map { if (it.id == id) f(it) else it } }

    fun get(id: String) = _entries.value.firstOrNull { it.id == id }
    fun findByUrl(url: String) = _entries.value.firstOrNull { it.url == url }

    fun enqueue(title: String, url: String, extension: String?, poster: String?) {
        if (findByUrl(url) != null) {
            findByUrl(url)?.let { if (it.status == DownloadStatus.FAILED || it.status == DownloadStatus.PAUSED) resume(it.id) }
            return
        }
        val id = UUID.randomUUID().toString()
        val safe = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).trim()
        val ext = (extension ?: url.substringAfterLast('.', "mp4").substringBefore('?')).take(5).ifBlank { "mp4" }
        val file = File(dir, "${safe}_${id.take(8)}.$ext")
        mutate { it + DownloadEntry(id, title, url, file.absolutePath, poster) }
        startService()
    }

    fun pause(id: String) = patch(id) {
        if (it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED) it.copy(status = DownloadStatus.PAUSED) else it
    }

    fun resume(id: String) {
        patch(id) { it.copy(status = DownloadStatus.QUEUED, error = null) }
        startService()
    }

    fun delete(id: String) {
        get(id)?.let { e ->
            File(e.filePath).delete()
            partFiles(e.filePath).forEach { it.delete() }
        }
        mutate { list -> list.filterNot { it.id == id } }
    }

    private fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    fun nextQueued(): DownloadEntry? = _entries.value.firstOrNull { it.status == DownloadStatus.QUEUED }

    private fun partFiles(path: String): List<File> {
        val f = File(path)
        return f.parentFile?.listFiles { _, name -> name.startsWith(f.name + ".part") }?.toList().orEmpty()
    }

    private val _speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Aktuelle Geschwindigkeit je Download (Bytes/Sekunde). */
    val speeds: StateFlow<Map<String, Long>> = _speeds

    private fun setSpeed(id: String, bps: Long?) {
        _speeds.value = if (bps == null) _speeds.value - id else _speeds.value + (id to bps)
    }

    /** Fuehrt einen Download aus. Wird vom DownloadService aufgerufen. */
    suspend fun run(entry: DownloadEntry, onProgress: (DownloadEntry) -> Unit) = withContext(Dispatchers.IO) {
        patch(entry.id) { it.copy(status = DownloadStatus.RUNNING, error = null) }
        try {
            val n = connections().coerceIn(1, MAX_CONNECTIONS)
            val total = if (n > 1) probeLength(entry.url) else null
            val done = if (total != null && total >= MIN_SEGMENTED_SIZE) {
                try {
                    runSegmented(entry, total, n, onProgress)
                } catch (e: SegmentRejectedException) {
                    // Server erlaubt keine parallelen Verbindungen -> mit einer Verbindung weiter
                    partFiles(entry.filePath).forEach { it.delete() }
                    runSingle(entry, onProgress)
                }
            } else {
                runSingle(entry, onProgress)
            }
            if (done) {
                val size = File(entry.filePath).length()
                patch(entry.id) { it.copy(status = DownloadStatus.COMPLETED, downloaded = size, total = size) }
            }
        } catch (e: CancellationException) {
            patch(entry.id) { if (it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.PAUSED) else it }
            throw e
        } catch (e: Exception) {
            patch(entry.id) { it.copy(status = DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName) }
        } finally {
            setSpeed(entry.id, null)
        }
        Unit
    }

    private fun isActive(id: String) = get(id)?.status == DownloadStatus.RUNNING

    /** Gesamtgroesse ermitteln und pruefen, ob der Server Teil-Downloads (Range) unterstuetzt. */
    private fun probeLength(url: String): Long? = runCatching {
        http().newCall(Request.Builder().url(url).header("Range", "bytes=0-0").build()).execute().use { resp ->
            if (resp.code != 206) return@use null
            resp.header("Content-Range")?.substringAfter("/")?.trim()?.toLongOrNull()
        }
    }.getOrNull()

    /** Eine Verbindung, mit Fortsetzen ueber Range. Liefert true, wenn fertig. */
    private suspend fun runSingle(entry: DownloadEntry, onProgress: (DownloadEntry) -> Unit): Boolean {
        val file = File(entry.filePath)
        val existing = if (file.exists()) file.length() else 0L
        val req = Request.Builder().url(entry.url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
        }.build()
        http().newCall(req).execute().use { resp ->
            if (resp.code == 416) return true // bereits vollstaendig
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Leere Antwort")
            val append = resp.code == 206
            var done = if (append) existing else 0L
            val total = body.contentLength().let { if (it > 0) it + done else -1L }
            patch(entry.id) { it.copy(downloaded = done, total = total) }
            val meter = SpeedMeter(done)
            BufferedOutputStream(FileOutputStream(file, append), BUFFER).use { out ->
                val input = body.byteStream()
                val buffer = ByteArray(BUFFER)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!isActive(entry.id)) {
                        if (get(entry.id) == null) file.delete()
                        return false // pausiert oder geloescht
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    done += read
                    meter.tick(done)?.let { bps ->
                        setSpeed(entry.id, bps)
                        patch(entry.id) { it.copy(downloaded = done) }
                        get(entry.id)?.let(onProgress)
                    }
                }
            }
        }
        return true
    }

    /**
     * Paralleler Download: Datei in [n] Teile aufteilen, jeder Teil ueber eine eigene Verbindung.
     * Viele IPTV-Server drosseln pro Verbindung – so wird die Bandbreite vervielfacht.
     * Teile liegen als .partN-Dateien vor und werden beim Fortsetzen weitergeladen.
     */
    private suspend fun runSegmented(entry: DownloadEntry, total: Long, n: Int, onProgress: (DownloadEntry) -> Unit): Boolean {
        val target = File(entry.filePath)
        target.delete()
        val partSize = total / n
        val ranges = (0 until n).map { i ->
            val start = i * partSize
            val end = if (i == n - 1) total - 1 else start + partSize - 1
            start..end
        }
        val parts = ranges.indices.map { File(entry.filePath + ".part$it") }
        fun downloaded() = parts.sumOf { if (it.exists()) it.length() else 0L }
        patch(entry.id) { it.copy(total = total, downloaded = downloaded()) }

        val finished = coroutineScope {
            val ticker = launch {
                val meter = SpeedMeter(downloaded())
                while (true) {
                    delay(700)
                    val d = downloaded()
                    meter.tick(d, force = true)?.let { setSpeed(entry.id, it) }
                    patch(entry.id) { it.copy(downloaded = d) }
                    get(entry.id)?.let(onProgress)
                }
            }
            val results = ranges.mapIndexed { i, range ->
                async { downloadPart(entry, parts[i], range, firstAttempt = downloaded() == 0L) }
            }.awaitAll()
            ticker.cancel()
            results.all { it }
        }
        if (!finished) {
            if (get(entry.id) == null) parts.forEach { it.delete() }
            return false
        }
        // Teile zusammenfuegen
        BufferedOutputStream(FileOutputStream(target), BUFFER).use { out ->
            parts.forEach { p -> p.inputStream().use { it.copyTo(out, BUFFER) } }
        }
        parts.forEach { it.delete() }
        return true
    }

    private suspend fun downloadPart(entry: DownloadEntry, part: File, range: LongRange, firstAttempt: Boolean): Boolean {
        val size = range.last - range.first + 1
        var retries = 0
        while (true) {
            val have = if (part.exists()) part.length() else 0L
            if (have >= size) return true
            if (!isActive(entry.id)) return false
            try {
                val req = Request.Builder().url(entry.url)
                    .header("Range", "bytes=${range.first + have}-${range.last}")
                    .build()
                http().newCall(req).execute().use { resp ->
                    if (resp.code != 206) {
                        // z.B. 403/429/458: zu viele Verbindungen fuer diesen Account
                        if (firstAttempt && have == 0L) throw SegmentRejectedException(resp.code)
                        throw IOException("HTTP ${resp.code}")
                    }
                    val input = resp.body?.byteStream() ?: throw IOException("Leere Antwort")
                    BufferedOutputStream(FileOutputStream(part, true), BUFFER).use { out ->
                        val buffer = ByteArray(BUFFER)
                        var written = have
                        while (written < size) {
                            currentCoroutineContext().ensureActive()
                            if (!isActive(entry.id)) return false
                            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), size - written).toInt())
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            written += read
                        }
                    }
                }
            } catch (e: SegmentRejectedException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (++retries > 5) throw e
                delay(2000L * retries)
            }
        }
    }

    /** Misst die Geschwindigkeit (gleitend ueber ~2 Sekunden). */
    private class SpeedMeter(startBytes: Long) {
        private var lastBytes = startBytes
        private var lastTime = System.currentTimeMillis()
        private var smoothed = 0.0

        fun tick(bytes: Long, force: Boolean = false): Long? {
            val now = System.currentTimeMillis()
            val dt = now - lastTime
            if (!force && dt < 700) return null
            if (dt <= 0) return null
            val bps = (bytes - lastBytes) * 1000.0 / dt
            smoothed = if (smoothed == 0.0) bps else smoothed * 0.6 + bps * 0.4
            lastBytes = bytes
            lastTime = now
            return smoothed.toLong()
        }
    }

    private class SegmentRejectedException(code: Int) : IOException("Parallele Verbindungen abgelehnt (HTTP $code)")

    companion object {
        const val MAX_CONNECTIONS = 4
        private const val BUFFER = 256 * 1024
        private const val MIN_SEGMENTED_SIZE = 30L * 1024 * 1024
    }
}
