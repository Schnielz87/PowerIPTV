package com.poweriptv.app.data

import android.content.Context

/**
 * Wiedergabeposition fuer Filme/Serien merken ("Weiterschauen").
 * Schluessel ist die Stream-URL (eindeutig pro Film/Episode).
 */
class ResumeRepository(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)
    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Aendert sich bei Gesehen-/Fortschritts-Aenderungen (fuer die Oberflaeche). */
    val version: kotlinx.coroutines.flow.StateFlow<Int> = _version

    /** Gespeicherte Position in ms (0 = von vorne). */
    fun get(url: String): Long = prefs.getLong(url, 0L)

    /** Position merken; am Anfang (< 30 s) oder kurz vor Schluss wird der Eintrag geloescht. */
    fun save(url: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val nearEnd = positionMs >= durationMs - maxOf(60_000L, durationMs / 20)
        if (nearEnd) { clear(url); markWatched(url, true); return }
        if (positionMs < 30_000L) clear(url)
        else prefs.edit().putLong(url, positionMs).putLong("$url|d", durationMs).apply()
        _version.value++
    }

    /** Restzeit in ms (null = unbekannt). */
    fun remaining(url: String): Long? {
        val pos = get(url).takeIf { it > 0 } ?: return null
        val dur = prefs.getLong("$url|d", 0L).takeIf { it > 0 } ?: return null
        return (dur - pos).coerceAtLeast(0)
    }

    /** Fortschritt 0..1 fuer die Anzeige (null = nicht angefangen). */
    fun progress(url: String): Float? {
        val pos = get(url).takeIf { it > 0 } ?: return null
        val dur = prefs.getLong("$url|d", 0L).takeIf { it > 0 } ?: return null
        return (pos.toFloat() / dur).coerceIn(0f, 1f)
    }

    /** Zuletzt gesehene Folge einer Serie merken (fuer "Weiterschauen"). */
    fun setLastEpisode(seriesKey: String, url: String, label: String) {
        prefs.edit().putString("series|$seriesKey", "$url\n$label").apply()
    }

    /** (URL, Anzeigename z.B. "S2E5 Titel") der zuletzt gesehenen Folge. */
    fun lastEpisode(seriesKey: String): Pair<String, String>? {
        val v = prefs.getString("series|$seriesKey", null) ?: return null
        return v.substringBefore('\n') to v.substringAfter('\n', "")
    }

    /** Gelerntes Intro einer Serie (Start, Ende in ms) – aus dem Spulverhalten des Nutzers. */
    fun setIntro(seriesKey: String, start: Long, end: Long) {
        prefs.edit().putString("intro|$seriesKey", "$start:$end").apply()
    }

    fun intro(seriesKey: String): Pair<Long, Long>? {
        val v = prefs.getString("intro|$seriesKey", null) ?: return null
        val (a, b) = v.split(":").mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 } ?: return null
        return a to b
    }

    /** Film/Folge als gesehen (oder wieder ungesehen) markieren. */
    fun markWatched(url: String, watched: Boolean) {
        prefs.edit().apply { if (watched) putBoolean("w|$url", true) else remove("w|$url") }.apply()
        if (watched) clear(url)
        _version.value++
    }

    fun isWatched(url: String) = prefs.getBoolean("w|$url", false)

    /** Serie aus "Weiterschauen" nehmen (Fortschritt der Folgen bleibt). */
    fun clearLastEpisode(seriesKey: String) {
        prefs.edit().remove("series|$seriesKey").apply()
        _version.value++
    }

    fun clear(url: String) {
        if (!prefs.contains(url)) return
        prefs.edit().remove(url).remove("$url|d").apply()
        _version.value++
    }
}
