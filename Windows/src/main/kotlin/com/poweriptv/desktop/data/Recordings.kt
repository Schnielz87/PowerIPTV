package com.poweriptv.desktop.data

import com.poweriptv.app.record.RecStatus
import com.poweriptv.app.record.Recording
import com.poweriptv.app.record.StreamCapture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Aufnahmen (PVR) – gleiches Verhalten wie in der Android-App:
 * 1 Min. Vorlauf, 2 Min. Nachlauf, automatisches Neuverbinden bis Sendungsende, mehrere parallel.
 * Der Mitschnitt selbst (TS/HLS) ist der geteilte [StreamCapture].
 */
class DesktopRecordings(private val dirProvider: () -> File) {
    private val file = JsonFile(AppDirs.file("recordings.json"), ListSerializer(Recording.serializer())) { emptyList() }
    private val _entries = MutableStateFlow(
        file.read().map {
            // Programm wurde waehrend einer Aufnahme beendet
            if (it.status == RecStatus.RECORDING) it.copy(status = if (File(it.filePath).length() > 0) RecStatus.COMPLETED else RecStatus.FAILED, error = "Unterbrochen")
            else it
        },
    )
    val entries: StateFlow<List<Recording>> = _entries.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    init {
        val now = System.currentTimeMillis()
        _entries.value.filter { it.status == RecStatus.SCHEDULED && it.end <= now }
            .forEach { patch(it.id) { r -> r.copy(status = RecStatus.FAILED, error = "Verpasst (PC war aus)") } }
        // Planer: geplante Aufnahmen rechtzeitig starten
        scope.launch {
            while (true) {
                val t = System.currentTimeMillis()
                _entries.value.filter { it.status == RecStatus.SCHEDULED && it.start <= t && jobs[it.id]?.isActive != true }
                    .forEach { r -> jobs[r.id] = scope.launch { record(r.id) } }
                delay(5_000)
            }
        }
    }

    @Synchronized
    private fun mutate(transform: (List<Recording>) -> List<Recording>) {
        _entries.value = transform(_entries.value)
        file.write(_entries.value)
    }

    fun patch(id: String, f: (Recording) -> Recording) = mutate { list -> list.map { if (it.id == id) f(it) else it } }
    fun get(id: String) = _entries.value.firstOrNull { it.id == id }
    fun running(): List<Recording> = _entries.value.filter { it.status == RecStatus.RECORDING }
    fun activeFor(url: String): Recording? = running().firstOrNull { it.url == url }

    /** Plant eine Aufnahme. Liefert eine Statusmeldung fuer die Oberflaeche. */
    fun schedule(title: String, channelName: String, url: String, start: Long, end: Long, logo: String?): String {
        val clash = _entries.value.firstOrNull {
            it.channelName == channelName && it.start < end && it.end > start &&
                (it.status == RecStatus.SCHEDULED || it.status == RecStatus.RECORDING)
        }
        if (clash != null) return "Bereits geplant: ${clash.title}"
        val id = UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(start))
        val safe = "${channelName}_$title".replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).trim()
        val dir = dirProvider().apply { mkdirs() }
        val rec = Recording(id, title, channelName, url, start - 60_000L, end + 120_000L, File(dir, "${stamp}_$safe.ts").absolutePath, logo)
        mutate { it + rec }
        if (rec.start <= System.currentTimeMillis()) jobs[id] = scope.launch { record(id) }
        return if (rec.start <= System.currentTimeMillis()) "Aufnahme läuft" else "Aufnahme geplant"
    }

    fun recordNow(title: String, channelName: String, url: String, minutes: Int, logo: String?): String {
        val now = System.currentTimeMillis()
        return schedule(title, channelName, url, now + 60_000L, now + minutes * 60_000L - 120_000L, logo)
    }

    fun stop(id: String) = patch(id) {
        when (it.status) {
            RecStatus.SCHEDULED -> it.copy(status = RecStatus.CANCELLED)
            RecStatus.RECORDING -> it.copy(end = System.currentTimeMillis())
            else -> it
        }
    }

    fun delete(id: String) {
        stop(id)
        jobs.remove(id)?.cancel()
        get(id)?.let { File(it.filePath).delete() }
        mutate { list -> list.filterNot { it.id == id } }
    }

    private suspend fun record(id: String) {
        val rec = get(id) ?: return
        if (rec.status != RecStatus.SCHEDULED) return
        patch(id) { it.copy(status = RecStatus.RECORDING, error = null) }
        val target = File(rec.filePath).apply { parentFile?.mkdirs() }
        var attempts = 0
        var lastUpdate = 0L
        try {
            FileOutputStream(target, true).use { out ->
                // Bei Abbruechen bis Sendungsende automatisch neu verbinden
                while (System.currentTimeMillis() < (get(id)?.end ?: 0L)) {
                    try {
                        StreamCapture(Http).capture(
                            url = rec.url, out = out,
                            keepGoing = { System.currentTimeMillis() < (get(id)?.end ?: 0L) },
                            onBytes = {
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 3000) { lastUpdate = now; patch(id) { r -> r.copy(bytes = target.length()) } }
                            },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (++attempts > 20) throw e
                        delay(3000)
                    }
                    patch(id) { it.copy(bytes = target.length()) }
                }
            }
            patch(id) { it.copy(status = RecStatus.COMPLETED, bytes = target.length()) }
            DesktopNotifier.show("Aufnahme fertig", "${rec.channelName}: ${rec.title}")
        } catch (e: CancellationException) {
            patch(id) { it.copy(status = RecStatus.COMPLETED, bytes = target.length()) }
        } catch (e: Exception) {
            patch(id) { it.copy(status = if (target.length() > 0) RecStatus.COMPLETED else RecStatus.FAILED, error = e.message, bytes = target.length()) }
        } finally {
            jobs.remove(id)
        }
    }
}
