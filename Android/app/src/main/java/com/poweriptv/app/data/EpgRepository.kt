package com.poweriptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

data class Programme(
    val channelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String? = null,
    /** Platzhalter fuer Zeitraeume ohne EPG-Daten (lueckenloses Raster). */
    val isGap: Boolean = false,
) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in start until end
}

sealed interface EpgState {
    data object Idle : EpgState
    data object Loading : EpgState
    data class Ready(val channels: Int, val programmes: Int) : EpgState
    data class Error(val message: String) : EpgState
}

/**
 * Laedt und verwaltet den elektronischen Programmfuehrer (XMLTV).
 * Die XMLTV-Datei wird serverseitig vom Anbieter erzeugt (Xtream: xmltv.php),
 * lokal zwischengespeichert (12 h) und im Speicher pro Kanal indiziert.
 */
class EpgRepository(
    /** Ordner fuer die zwischengespeicherte XMLTV-Datei. */
    private val cacheDir: () -> File,
    private val http: () -> OkHttpClient,
    /** XML-Parser der Plattform (Android: Xml.newPullParser(), Windows: KXmlParser). Geteilt mit Windows/. */
    private val newParser: () -> XmlPullParser,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<EpgState>(EpgState.Idle)
    val state: StateFlow<EpgState> = _state

    @Volatile private var loadedProfile: String? = null
    /** Wann der Programmfuehrer zuletzt eingelesen wurde (aelter als 3 h -> automatisch neu einlesen). */
    @Volatile private var loadedAt = 0L
    @Volatile private var byChannel: Map<String, List<Programme>> = emptyMap()
    @Volatile private var nameToId: Map<String, String> = emptyMap()

    suspend fun ensureLoaded(source: ContentSource, force: Boolean = false) = mutex.withLock {
        val profileId = source.profile.id
        val stale = System.currentTimeMillis() - loadedAt > RELOAD_AFTER
        if (!force && !stale && loadedProfile == profileId && _state.value is EpgState.Ready) return@withLock
        _state.value = EpgState.Loading
        try {
            val url = source.epgUrl() ?: throw IOException(
                "Keine EPG-Quelle gefunden. Bitte in den Zugangsdaten eine XMLTV-URL hinterlegen."
            )
            val file = withContext(Dispatchers.IO) { download(profileId, url, force) }
            withContext(Dispatchers.IO) { parse(file) }
            loadedProfile = profileId
            loadedAt = System.currentTimeMillis()
            _state.value = EpgState.Ready(byChannel.size, byChannel.values.sumOf { it.size })
        } catch (e: Exception) {
            _state.value = EpgState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    fun clear() {
        loadedProfile = null
        byChannel = emptyMap()
        nameToId = emptyMap()
        _state.value = EpgState.Idle
    }

    private fun download(profileId: String, url: String, force: Boolean): File {
        val dir = cacheDir().apply { mkdirs() }
        val file = File(dir, "$profileId.xml")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE
        if (fresh && !force) return file
        val tmp = File(dir, "$profileId.tmp")
        http().newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("EPG nicht erreichbar (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("EPG leer")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        tmp.renameTo(file)
        return file
    }

    private fun open(file: File): InputStream {
        val input = BufferedInputStream(file.inputStream())
        input.mark(2)
        val b1 = input.read(); val b2 = input.read()
        input.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(input)) else input
    }

    private fun parse(file: File) {
        val now = System.currentTimeMillis()
        val from = now - KEEP_PAST
        val to = now + KEEP_FUTURE
        val programmes = HashMap<String, MutableList<Programme>>()
        val names = HashMap<String, String>()

        open(file).use { input ->
            val parser = newParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)

            var channelId: String? = null
            var progChannel: String? = null
            var progStart = 0L
            var progEnd = 0L
            var title: String? = null
            var desc: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> channelId = parser.getAttributeValue(null, "id")
                        "display-name" -> {
                            val id = channelId
                            val name = parser.nextText()
                            if (id != null && name.isNotBlank()) names.putIfAbsent(normalize(name), id.lowercase())
                        }
                        "programme" -> {
                            progChannel = parser.getAttributeValue(null, "channel")?.lowercase()
                            progStart = parseTime(parser.getAttributeValue(null, "start"))
                            progEnd = parseTime(parser.getAttributeValue(null, "stop"))
                            title = null; desc = null
                        }
                        "title" -> if (progChannel != null && title == null) title = parser.nextText()
                        "desc" -> if (progChannel != null && desc == null) desc = parser.nextText().take(600)
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "channel" -> channelId = null
                        "programme" -> {
                            val ch = progChannel
                            if (ch != null && progEnd > from && progStart < to && progEnd > progStart) {
                                programmes.getOrPut(ch) { mutableListOf() }
                                    .add(Programme(ch, progStart, progEnd, title ?: "Ohne Titel", desc))
                            }
                            progChannel = null
                        }
                    }
                }
                event = parser.next()
            }
        }
        byChannel = programmes.mapValues { (_, v) -> v.sortedBy { it.start } }
        nameToId = names
    }

    /** Alle Sendungen eines Kanals (sortiert). */
    fun programmesFor(item: ContentItem): List<Programme> {
        val map = byChannel
        item.epgChannelId?.lowercase()?.let { id -> map[id]?.let { return it } }
        val byName = nameToId[normalize(item.name)] ?: return emptyList()
        return map[byName].orEmpty()
    }

    fun current(item: ContentItem, now: Long = System.currentTimeMillis()): Programme? =
        programmesFor(item).firstOrNull { it.isLive(now) }

    fun next(item: ContentItem, now: Long = System.currentTimeMillis()): Programme? =
        programmesFor(item).firstOrNull { it.start >= now }

    /**
     * Lueckenlose Zeitleiste fuer das Raster: fehlende Zeitraeume werden
     * mit Platzhaltern aufgefuellt, ueberlappende Sendungen gekuerzt.
     */
    fun timeline(item: ContentItem, from: Long, to: Long): List<Programme> {
        val out = mutableListOf<Programme>()
        var cursor = from
        val id = item.epgChannelId ?: item.id
        for (p in programmesFor(item)) {
            if (p.end <= cursor) continue
            if (p.start >= to) break
            val start = maxOf(p.start, cursor)
            if (start > cursor) out += Programme(id, cursor, start, "Keine Programminformation", isGap = true)
            val end = minOf(p.end, to)
            out += p.copy(start = start, end = end)
            cursor = end
            if (cursor >= to) break
        }
        if (cursor < to) out += Programme(id, cursor, to, "Keine Programminformation", isGap = true)
        return out
    }

    companion object {
        private const val MAX_AGE = 12 * 3600_000L
        private const val RELOAD_AFTER = 3 * 3600_000L
        private const val KEEP_PAST = 3 * 24 * 3600_000L
        private const val KEEP_FUTURE = 3 * 24 * 3600_000L

        private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
            .removeSuffix("hd").removeSuffix("fhd").removeSuffix("uhd")

        // Nur innerhalb von parse() (unter Mutex) verwendet -> nicht thread-uebergreifend
        private val formats by lazy {
            listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss", "yyyyMMddHHmm").map {
                SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            }
        }

        @Synchronized
        fun parseTime(value: String?): Long {
            if (value.isNullOrBlank()) return 0L
            val v = value.trim()
            for (f in formats) {
                val parsed = runCatching { f.parse(v)?.time }.getOrNull()
                if (parsed != null) return parsed
            }
            return 0L
        }
    }
}
