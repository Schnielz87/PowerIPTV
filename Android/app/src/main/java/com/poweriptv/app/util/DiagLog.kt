package com.poweriptv.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fehlerprotokoll: die letzten Ereignisse von Player, VLC, Updates und Abstuerzen – damit Probleme gleich
 * gemeldet werden koennen (Einstellungen → Fehlerprotokoll: anzeigen oder als QR-Code ans Handy).
 * Zugangsdaten werden nie gespeichert: Benutzer/Passwort in Stream-Adressen werden durch *** ersetzt.
 * Bleibt nur auf dem Geraet (Datei im App-Speicher, max. ca. 600 Zeilen).
 */
object DiagLog {
    private const val MAX = 600
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private var header = ""
    private val time = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY)

    fun init(context: Context) {
        val f = File(context.filesDir, "diag.log")
        file = f
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val kind = if (DeviceInfo.isFireTv(context)) "Fire TV" else if (DeviceInfo.isTv(context)) "TV" else "Handy/Tablet"
        header = "Portiva $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · $kind"
        synchronized(lines) {
            runCatching { if (f.exists()) f.readLines().takeLast(MAX).forEach { lines.addLast(it) } }
        }
        log("App", "Start – $header")
        // Abstuerze mitschreiben (danach wie gewohnt beenden)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                log("ABSTURZ", "${e.javaClass.simpleName}: ${e.message} (Thread ${t.name})")
                e.stackTrace.take(12).forEach { log("ABSTURZ", "  at $it") }
                generateSequence(e.cause) { it.cause }.take(2).forEach { c -> log("ABSTURZ", "Ursache: ${c.javaClass.simpleName}: ${c.message}") }
            }
            previous?.uncaughtException(t, e)
        }
    }

    /** Benutzer/Passwort aus Adressen entfernen (Xtream-Pfade und ?username=&password=). */
    fun redact(s: String): String = s
        .replace(Regex("(/(?:live|movie|series|timeshift|hls)/)[^/\\s]+/[^/\\s]+/"), "$1***/***/")
        .replace(Regex("(?i)\\b(username|password|user|pass|token)=[^&\\s]+"), "$1=***")

    fun log(tag: String, msg: String) {
        val line = "${time.format(Date())} $tag: ${redact(msg)}".take(400)
        synchronized(lines) {
            lines.addLast(line)
            val overflow = lines.size > MAX
            while (lines.size > MAX) lines.removeFirst()
            runCatching {
                val f = file ?: return
                // Datei gelegentlich kuerzen, sonst nur anhaengen
                if (overflow && f.length() > 200_000) f.writeText(lines.joinToString("\n", postfix = "\n"))
                else f.appendText(line + "\n")
            }
        }
    }

    fun text(lastLines: Int = MAX): String = synchronized(lines) {
        header + "\n" + lines.toList().takeLast(lastLines).joinToString("\n")
    }

    fun isEmpty() = synchronized(lines) { lines.isEmpty() }

    fun clear() {
        synchronized(lines) {
            lines.clear()
            runCatching { file?.writeText("") }
        }
    }

    /**
     * Fuer QR-Codes: die neuesten Zeilen in Seiten zu hoechstens maxChars (Seite 1 = neueste Ereignisse).
     * Jede Seite beginnt mit Geraete-Angaben und "Seite x/y".
     */
    fun pages(maxChars: Int = 1500, maxPages: Int = 6): List<String> {
        val all = synchronized(lines) { lines.toList() }.reversed()
        val pages = mutableListOf<MutableList<String>>()
        var cur = mutableListOf<String>()
        var size = 0
        for (l in all) {
            if (size + l.length + 1 > maxChars && cur.isNotEmpty()) {
                pages += cur
                if (pages.size >= maxPages) break
                cur = mutableListOf(); size = 0
            }
            cur += l; size += l.length + 1
        }
        if (cur.isNotEmpty() && pages.size < maxPages) pages += cur
        if (pages.isEmpty()) pages += mutableListOf("(Protokoll ist leer)")
        // QR-Codes koennen nur Latin-1 sicher: typografische Zeichen ersetzen
        fun plain(t: String) = t.replace('„', '"').replace('“', '"').replace('–', '-').replace("…", "...").replace('→', '>')
        return pages.mapIndexed { i, p -> plain("$header\nSeite ${i + 1}/${pages.size}\n" + p.reversed().joinToString("\n")) }
    }
}
