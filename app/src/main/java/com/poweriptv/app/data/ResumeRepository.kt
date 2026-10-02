package com.poweriptv.app.data

import android.content.Context

/**
 * Wiedergabeposition fuer Filme/Serien merken ("Weiterschauen").
 * Schluessel ist die Stream-URL (eindeutig pro Film/Episode).
 */
class ResumeRepository(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)

    /** Gespeicherte Position in ms (0 = von vorne). */
    fun get(url: String): Long = prefs.getLong(url, 0L)

    /** Position merken; am Anfang (< 30 s) oder kurz vor Schluss wird der Eintrag geloescht. */
    fun save(url: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val nearEnd = positionMs >= durationMs - maxOf(60_000L, durationMs / 20)
        if (positionMs < 30_000L || nearEnd) clear(url)
        else prefs.edit().putLong(url, positionMs).putLong("$url|d", durationMs).apply()
    }

    /** Fortschritt 0..1 fuer die Anzeige (null = nicht angefangen). */
    fun progress(url: String): Float? {
        val pos = get(url).takeIf { it > 0 } ?: return null
        val dur = prefs.getLong("$url|d", 0L).takeIf { it > 0 } ?: return null
        return (pos.toFloat() / dur).coerceIn(0f, 1f)
    }

    fun clear(url: String) {
        prefs.edit().remove(url).remove("$url|d").apply()
    }
}
