package com.poweriptv.app.record

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
enum class RecStatus { SCHEDULED, RECORDING, COMPLETED, FAILED, CANCELLED }

@Serializable
data class Recording(
    val id: String,
    val title: String,
    val channelName: String,
    val url: String,
    val start: Long,
    val end: Long,
    val filePath: String,
    val logo: String? = null,
    val status: RecStatus = RecStatus.SCHEDULED,
    val bytes: Long = 0,
    val error: String? = null,
)

/** Geplante und laufende Aufnahmen (PVR). */
class RecordingRepository(private val context: Context, private val json: Json) {
    private val metaFile = File(context.filesDir, "recordings.json")
    private val serializer = ListSerializer(Recording.serializer())
    private val dir: File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "recordings").apply { mkdirs() }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Recording>> = _entries

    private fun load(): List<Recording> = runCatching {
        json.decodeFromString(serializer, metaFile.readText())
    }.getOrDefault(emptyList()).map {
        // App wurde waehrend einer Aufnahme beendet
        if (it.status == RecStatus.RECORDING) it.copy(status = if (File(it.filePath).length() > 0) RecStatus.COMPLETED else RecStatus.FAILED, error = "Unterbrochen")
        else it
    }

    @Synchronized
    private fun mutate(transform: (List<Recording>) -> List<Recording>) {
        val updated = transform(_entries.value)
        _entries.value = updated
        runCatching { metaFile.writeText(json.encodeToString(serializer, updated)) }
    }

    fun patch(id: String, f: (Recording) -> Recording) = mutate { list -> list.map { if (it.id == id) f(it) else it } }
    fun get(id: String) = _entries.value.firstOrNull { it.id == id }

    /** Plant eine Aufnahme. Liefert eine Statusmeldung fuer die Oberflaeche. */
    fun schedule(title: String, channelName: String, url: String, start: Long, end: Long, logo: String?): String {
        val clash = _entries.value.firstOrNull {
            it.channelName == channelName && it.start < end && it.end > start &&
                (it.status == RecStatus.SCHEDULED || it.status == RecStatus.RECORDING)
        }
        if (clash != null) return "Bereits geplant: ${clash.title}"
        val id = UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(start))
        val safe = "${channelName}_${title}".replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).trim()
        val file = File(dir, "${stamp}_$safe.ts")
        // 1 Minute Vorlauf, 2 Minuten Nachlauf
        val rec = Recording(id, title, channelName, url, start - 60_000L, end + 120_000L, file.absolutePath, logo)
        mutate { it + rec }
        arm(rec)
        return if (rec.start <= System.currentTimeMillis()) "Aufnahme laeuft" else "Aufnahme geplant"
    }

    fun recordNow(title: String, channelName: String, url: String, minutes: Int, logo: String?): String {
        val now = System.currentTimeMillis()
        return schedule(title, channelName, url, now + 60_000L, now + minutes * 60_000L - 120_000L, logo)
    }

    /** Startet die Aufnahme sofort oder setzt einen Wecker. */
    fun arm(rec: Recording) {
        if (rec.start <= System.currentTimeMillis()) {
            startService(rec.id); return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(rec.id)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rec.start, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, rec.start, pi)
        }
    }

    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** Nach Neustart des Geraets: alle geplanten Aufnahmen erneut stellen. */
    fun rearmAll() {
        val now = System.currentTimeMillis()
        _entries.value.filter { it.status == RecStatus.SCHEDULED }.forEach {
            if (it.end <= now) patch(it.id) { r -> r.copy(status = RecStatus.FAILED, error = "Verpasst (Geraet aus)") }
            else arm(it)
        }
    }

    fun stop(id: String) = patch(id) {
        when (it.status) {
            RecStatus.SCHEDULED -> it.copy(status = RecStatus.CANCELLED)
            RecStatus.RECORDING -> it.copy(end = System.currentTimeMillis())
            else -> it
        }
    }.also {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(id))
    }

    fun delete(id: String) {
        stop(id)
        get(id)?.let { File(it.filePath).delete() }
        mutate { list -> list.filterNot { it.id == id } }
    }

    fun startService(id: String) {
        val intent = Intent(context, RecordingService::class.java).putExtra(RecordingService.EXTRA_ID, id)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun alarmIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, RecordingAlarmReceiver::class.java).putExtra(RecordingService.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
