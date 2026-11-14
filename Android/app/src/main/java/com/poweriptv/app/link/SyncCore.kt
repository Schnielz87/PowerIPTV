package com.poweriptv.app.link

import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/*
 * Synchronisieren (Favoriten, eigene Listen, Weiterschauen) zwischen Geraeten mit demselben Zugang –
 * ueber Portiva Link im Heimnetz, ohne Cloud. Gleiches Format in Android/Windows (diese Datei),
 * iOS (Sync.swift), Samsung-TV/Fire TV (sync.js) und Enigma2 (sync.py).
 *
 * Jede Aenderung bekommt einen Zeitstempel (ms). Beim Abgleich gewinnt pro Eintrag die neuere Aenderung;
 * geloeschte Eintraege bleiben als "del" (Grabstein) 60 Tage erhalten, damit sie nicht zurueckkommen.
 * Eintraege ohne Zeitstempel (Daten von vor dem ersten Abgleich) haben ts = 0: dann gilt "vorhanden gewinnt".
 *
 * Schluessel: Titel "MOVIE:123" / "SERIES:45" / "LIVE:7"; Weiterschauen "MOVIE:123" (Film) bzw. "EP:678" (Folge).
 */

const val SYNC_VERSION = 1
const val DEFAULT_LIST_ID = "default"
private const val TOMBSTONE_DAYS = 60L

/** Titel in neutraler Form (jede Plattform rechnet in ihr eigenes Modell um). */
@Serializable
data class SyncItem(
    val id: String,
    val name: String,
    /** LIVE, MOVIE, SERIES */
    val type: String,
    val logo: String? = null,
    val ext: String? = null,
    val year: Int? = null,
    val rating: String? = null,
    val cat: String? = null,
    /** M3U: direkte Adresse */
    val url: String? = null,
    /** Live: Sender-Kennung fuer die TV-Zeitschrift */
    val epg: String? = null,
) {
    val key: String get() = "$type:$id"
}

fun ContentItem.toSync() = SyncItem(id, name, type.name, logo, containerExtension, year, rating, categoryId.ifBlank { null }, url, epgChannelId)

fun SyncItem.toContent() = ContentItem(
    id = id, name = name, type = runCatching { ContentType.valueOf(type) }.getOrDefault(ContentType.MOVIE),
    categoryId = cat.orEmpty(), logo = logo, url = url, containerExtension = ext, rating = rating, epgChannelId = epg, year = year,
)

@Serializable
data class SyncEntry(val k: String, val ts: Long = 0, val del: Boolean = false, val item: SyncItem? = null)

@Serializable
data class SyncList(val id: String, val name: String, val ts: Long = 0, val del: Boolean = false, val items: List<SyncEntry> = emptyList())

/** Folge einer Serie (fuer "Weiterschauen" auf dem anderen Geraet). */
@Serializable
data class SyncEpisode(val id: String, val season: Int = 0, val num: Int = 0, val title: String = "", val ext: String? = null)

@Serializable
data class SyncResume(
    val k: String,
    val pos: Long = 0,
    val dur: Long = 0,
    val ts: Long = 0,
    /** Bis zum Ende gesehen */
    val done: Boolean = false,
    /** Film bzw. Serie */
    val item: SyncItem? = null,
    val ep: SyncEpisode? = null,
)

@Serializable
data class SyncData(
    val v: Int = SYNC_VERSION,
    val lists: List<SyncList> = emptyList(),
    val resume: List<SyncResume> = emptyList(),
)

/** Anfrage an /portiva/sync: Kennung des Zugangs + eigener Stand. Antwort: zusammengefuehrter Stand. */
@Serializable
data class SyncRequest(val account: String, val data: SyncData, val from: String = "")

@Serializable
data class SyncReply(
    val ok: Boolean,
    val data: SyncData? = null,
    val name: String = "",
    val error: String? = null,
    /** Geraet kennt diesen Benutzer nicht (kein Fehler – wird uebersprungen). */
    val unknown: Boolean = false,
)

object SyncCore {
    /**
     * Kennung eines Zugangs (gleich auf allen Geraeten mit demselben Server/Benutzer/Passwort bzw. M3U-Link).
     * SHA-256 – verraet die Zugangsdaten nicht.
     */
    fun accountKey(serverUrl: String, username: String, password: String, m3uUrl: String): String {
        val raw = if (username.isNotBlank()) "portiva-sync-v1|${norm(serverUrl)}|${username.trim()}|$password"
        else "portiva-sync-v1|m3u|${norm(m3uUrl)}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Server-Adresse vereinheitlichen: ohne http(s)://, ohne / am Ende, klein geschrieben. */
    fun norm(u: String): String = u.trim().lowercase().removePrefix("http://").removePrefix("https://").trimEnd('/')

    private fun newer(a: Long, aDel: Boolean, b: Long, bDel: Boolean): Boolean =
        // true = a gewinnt. Gleicher Zeitstempel: "vorhanden" gewinnt gegen "geloescht"
        a > b || (a == b && !aDel && bDel)

    private fun mergeEntries(a: List<SyncEntry>, b: List<SyncEntry>): List<SyncEntry> {
        val out = LinkedHashMap<String, SyncEntry>()
        (a + b).forEach { e ->
            val cur = out[e.k]
            out[e.k] = when {
                cur == null -> e
                newer(e.ts, e.del, cur.ts, cur.del) -> e.copy(item = e.item ?: cur.item)
                else -> cur.copy(item = cur.item ?: e.item)
            }
        }
        return out.values.toList()
    }

    /** Zwei Staende zusammenfuehren (Reihenfolge egal: merge(a, b) == merge(b, a) inhaltlich). */
    fun merge(a: SyncData, b: SyncData, now: Long = System.currentTimeMillis()): SyncData {
        val cutoff = now - TOMBSTONE_DAYS * 86_400_000L
        val lists = LinkedHashMap<String, SyncList>()
        (a.lists + b.lists).forEach { l ->
            val cur = lists[l.id]
            lists[l.id] = if (cur == null) l else {
                val head = if (newer(l.ts, l.del, cur.ts, cur.del)) l else cur
                head.copy(items = mergeEntries(cur.items, l.items))
            }
        }
        val resume = LinkedHashMap<String, SyncResume>()
        (a.resume + b.resume).forEach { r ->
            val cur = resume[r.k]
            resume[r.k] = when {
                cur == null -> r
                r.ts > cur.ts -> r.copy(item = r.item ?: cur.item, ep = r.ep ?: cur.ep)
                else -> cur.copy(item = cur.item ?: r.item, ep = cur.ep ?: r.ep)
            }
        }
        return SyncData(
            lists = lists.values
                .filterNot { it.del && it.ts in 1 until cutoff }
                .map { l -> l.copy(items = l.items.filterNot { it.del && it.ts in 1 until cutoff }) },
            resume = resume.values.toList(),
        )
    }

    /** Wie viele Eintraege kamen durch den Abgleich neu dazu bzw. wurden entfernt (fuer die Meldung). */
    fun changes(before: SyncData, after: SyncData): Pair<Int, Int> {
        fun present(d: SyncData) = d.lists.filterNot { it.del }.flatMap { l -> l.items.filterNot { it.del }.map { "${l.id}|${it.k}" } }.toSet()
        val b = present(before)
        val a = present(after)
        val res = after.resume.count { r -> before.resume.none { it.k == r.k && it.ts >= r.ts } }
        return ((a - b).size + res) to (b - a).size
    }
}

/**
 * Zeitstempel der lokalen Aenderungen eines Profils (Schluessel -> ms; negativ = geloescht).
 *   "L|<listId>"          Liste angelegt/umbenannt (+) bzw. geloescht (−)
 *   "I|<listId>|<key>"    Titel in Liste aufgenommen (+) bzw. entfernt (−); Favoriten = Liste "default"
 *   "R|<resumeKey>"       Weiterschauen-Stand gespeichert
 */
class SyncMeta(private val load: () -> Map<String, Long>, private val store: (Map<String, Long>) -> Unit) {
    private var map: MutableMap<String, Long> = load().toMutableMap()

    fun reload() { map = load().toMutableMap() }
    fun all(): Map<String, Long> = map
    fun ts(key: String): Long = kotlin.math.abs(map[key] ?: 0L)
    fun deleted(key: String): Boolean = (map[key] ?: 0L) < 0

    fun touch(key: String, deleted: Boolean = false, at: Long = System.currentTimeMillis()) {
        map[key] = if (deleted) -at else at
        store(map)
    }

    /** Nach dem Abgleich: Zeitstempel vom zusammengefuehrten Stand uebernehmen (ein Speichervorgang). */
    fun setAll(values: Map<String, Long>) {
        map.putAll(values)
        val cutoff = System.currentTimeMillis() - 60L * 86_400_000L
        map.entries.removeAll { it.value < 0 && -it.value < cutoff }
        store(map)
    }
}

/** Gemeinsame Umrechnung "lokale Listen <-> Abgleich-Format" (Android und Windows). */
object SyncLists {
    data class Local(val id: String, val name: String, val items: List<SyncItem>)

    fun export(lists: List<Local>, meta: SyncMeta): List<SyncList> {
        val out = lists.map { l ->
            val present = l.items.map { SyncEntry(it.key, meta.ts("I|${l.id}|${it.key}"), false, it) }
            val keys = l.items.map { it.key }.toSet()
            val gone = meta.all().filter { (k, v) -> v < 0 && k.startsWith("I|${l.id}|") && k.removePrefix("I|${l.id}|") !in keys }
                .map { (k, v) -> SyncEntry(k.removePrefix("I|${l.id}|"), -v, true) }
            SyncList(l.id, l.name, meta.ts("L|${l.id}"), false, present + gone)
        }
        val ids = lists.map { it.id }.toSet()
        val deletedLists = meta.all().filter { (k, v) -> v < 0 && k.startsWith("L|") && k.removePrefix("L|") !in ids }
            .map { (k, v) -> SyncList(k.removePrefix("L|"), "", -v, true) }
        return out + deletedLists
    }

    /** Zusammengefuehrte Listen -> lokale Listen (neueste Eintraege zuerst) + neue Zeitstempel. */
    fun apply(merged: List<SyncList>): Pair<List<Local>, Map<String, Long>> {
        val meta = HashMap<String, Long>()
        val local = merged.mapNotNull { l ->
            meta["L|${l.id}"] = if (l.del) -l.ts else l.ts
            l.items.forEach { e -> meta["I|${l.id}|${e.k}"] = if (e.del) -e.ts else e.ts }
            if (l.del && l.id != DEFAULT_LIST_ID) return@mapNotNull null
            val items = l.items.filter { !it.del && it.item != null }
                .withIndex().sortedWith(compareByDescending<IndexedValue<SyncEntry>> { it.value.ts }.thenBy { it.index })
                .map { it.value.item!! }
            Local(l.id, l.name.ifBlank { if (l.id == DEFAULT_LIST_ID) "Favoriten" else "Liste" }, items)
        }
        // Eintraege ohne Zeitstempel (ts = 0) nicht als "geaendert" speichern
        return local to meta.filterValues { it != 0L }
    }

    /** "S2E5 Titel" -> Folge (Kennung aus der Adresse). */
    fun episodeFromUrl(url: String, label: String): SyncEpisode {
        val m = Regex("^S(\\d+)\\s*E(\\d+)\\s*(.*)$").find(label.trim())
        val key = resumeKeyFromUrl(url)
        val id = key?.first?.removePrefix("EP:") ?: url
        return SyncEpisode(id, m?.groupValues?.get(1)?.toIntOrNull() ?: 0, m?.groupValues?.get(2)?.toIntOrNull() ?: 0,
            m?.groupValues?.get(3) ?: label, key?.second)
    }

    fun episodeLabel(e: SyncEpisode): String = if (e.season > 0 || e.num > 0) "S${e.season}E${e.num} ${e.title}".trim() else e.title

    /** Weiterschauen-Schluessel aus einer Xtream-Adresse: (".../movie/u/p/123.mkv" -> "MOVIE:123", "mkv"). */
    fun resumeKeyFromUrl(url: String): Pair<String, String?>? {
        val m = Regex("/(movie|series)/[^/]+/[^/]+/([^/?#]+?)(?:\\.([A-Za-z0-9]+))?(?:[?#].*)?$").find(url) ?: return null
        val id = m.groupValues[2]
        val ext = m.groupValues[3].ifBlank { null }
        return (if (m.groupValues[1] == "movie") "MOVIE:$id" else "EP:$id") to ext
    }
}
