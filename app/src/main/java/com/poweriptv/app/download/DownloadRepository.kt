package com.poweriptv.app.download

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
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
        get(id)?.let { File(it.filePath).delete() }
        mutate { list -> list.filterNot { it.id == id } }
    }

    private fun startService() {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    fun nextQueued(): DownloadEntry? = _entries.value.firstOrNull { it.status == DownloadStatus.QUEUED }

    /** Fuehrt einen Download aus. Wird vom DownloadService aufgerufen. */
    suspend fun run(entry: DownloadEntry, onProgress: (DownloadEntry) -> Unit) = withContext(Dispatchers.IO) {
        patch(entry.id) { it.copy(status = DownloadStatus.RUNNING, error = null) }
        val file = File(entry.filePath)
        try {
            val existing = if (file.exists()) file.length() else 0L
            val req = Request.Builder().url(entry.url).apply {
                if (existing > 0) header("Range", "bytes=$existing-")
            }.build()
            http().newCall(req).execute().use { resp ->
                if (resp.code == 416) { // bereits vollstaendig
                    patch(entry.id) { it.copy(status = DownloadStatus.COMPLETED, downloaded = existing, total = existing) }
                    return@withContext
                }
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Leere Antwort")
                val append = resp.code == 206
                var done = if (append) existing else 0L
                val total = body.contentLength().let { if (it > 0) it + done else -1L }
                patch(entry.id) { it.copy(downloaded = done, total = total) }

                FileOutputStream(file, append).use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var lastUpdate = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val current = get(entry.id)
                        if (current == null || current.status != DownloadStatus.RUNNING) {
                            if (current == null) file.delete()
                            return@withContext // pausiert oder geloescht
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 700) {
                            lastUpdate = now
                            patch(entry.id) { it.copy(downloaded = done) }
                            get(entry.id)?.let(onProgress)
                        }
                    }
                }
                patch(entry.id) { it.copy(status = DownloadStatus.COMPLETED, downloaded = done, total = done) }
            }
        } catch (e: CancellationException) {
            patch(entry.id) { if (it.status == DownloadStatus.RUNNING) it.copy(status = DownloadStatus.PAUSED) else it }
            throw e
        } catch (e: Exception) {
            patch(entry.id) { it.copy(status = DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName) }
        }
    }
}
