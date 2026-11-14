package com.poweriptv.app

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.poweriptv.app.data.ContentSource
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.Episode
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileType
import com.poweriptv.app.link.LinkClient
import com.poweriptv.app.link.SyncCore
import com.poweriptv.app.link.SyncData
import com.poweriptv.app.link.SyncEpisode
import com.poweriptv.app.link.SyncLists
import com.poweriptv.app.link.SyncReply
import com.poweriptv.app.link.SyncRequest
import com.poweriptv.app.link.SyncResume
import com.poweriptv.app.link.toContent
import com.poweriptv.app.link.toSync

/**
 * "Synchronisieren": Favoriten, eigene Listen und Weiterschauen mit allen Geraeten im Heimnetz abgleichen,
 * auf denen derselbe Zugang (Server + Benutzer + Passwort bzw. M3U-Link) eingerichtet ist.
 */
class SyncManager(private val c: AppContainer) {
    private val lock = Any()

    /** Lokale M3U-Dateien haben keine gemeinsame Kennung -> nicht abgleichbar. */
    fun canSync(p: Profile) = p.type != ProfileType.M3U_FILE && (p.username.isNotBlank() || p.m3uUrl.isNotBlank())

    fun key(p: Profile) = SyncCore.accountKey(p.serverUrl, p.username, p.password, p.m3uUrl)

    private fun sourceFor(p: Profile): ContentSource = c.source?.takeIf { it.profile.id == p.id } ?: c.createSource(p)

    fun export(p: Profile): SyncData {
        val src = sourceFor(p)
        val resume = c.history.itemsFor(p.id).mapNotNull { item ->
            when (item.type) {
                ContentType.MOVIE -> {
                    val url = src.streamUrl(item)
                    val ts = c.resume.changedAt(url).takeIf { it > 0 } ?: return@mapNotNull null
                    SyncResume(item.key, c.resume.get(url), c.resume.duration(url), ts, c.resume.isWatched(url), item.toSync())
                }
                ContentType.SERIES -> {
                    val sts = c.resume.seriesChangedAt(item.key)
                    val last = c.resume.lastEpisode(item.key)
                    val epTs = last?.let { c.resume.changedAt(it.first) } ?: 0L
                    val ts = maxOf(sts, epTs).takeIf { it > 0 } ?: return@mapNotNull null
                    SyncResume(
                        item.key, last?.let { c.resume.get(it.first) } ?: 0L, last?.let { c.resume.duration(it.first) } ?: 0L, ts, false,
                        item.toSync(), last?.let { SyncLists.episodeFromUrl(it.first, it.second) },
                    )
                }
                ContentType.LIVE -> null
            }
        }
        return SyncData(lists = c.favorites.exportSync(p.id), resume = resume)
    }

    private fun episodeUrl(src: ContentSource, e: SyncEpisode): String =
        if (e.id.startsWith("http")) e.id
        else src.episodeUrl(Episode(e.id, e.title, e.season, e.num, e.ext, null, null, null))

    /** Zusammengefuehrten Stand uebernehmen; Weiterschauen nur, wo das andere Geraet neuer ist. */
    fun apply(p: Profile, merged: SyncData, before: SyncData) {
        c.favorites.applySync(p.id, merged.lists)
        val src = sourceFor(p)
        val local = before.resume.associate { it.k to it.ts }
        val added = mutableListOf<Pair<Long, com.poweriptv.app.data.ContentItem>>()
        merged.resume.forEach { r ->
            if (r.ts <= (local[r.k] ?: 0L)) return@forEach
            val item = r.item?.toContent() ?: return@forEach
            when (item.type) {
                ContentType.MOVIE -> {
                    c.resume.applySynced(src.streamUrl(item), r.pos, r.dur, r.done, r.ts)
                    if (r.pos > 0 && !r.done) added += r.ts to item
                }
                ContentType.SERIES -> {
                    val ep = r.ep
                    val url = ep?.let { episodeUrl(src, it) }
                    c.resume.applySyncedSeries(item.key, url, ep?.let { SyncLists.episodeLabel(it) }.orEmpty(), r.ts)
                    if (url != null) {
                        if (r.pos > 0) c.resume.applySynced(url, r.pos, r.dur, false, r.ts)
                        added += r.ts to item
                    }
                }
                ContentType.LIVE -> Unit
            }
        }
        c.history.addSynced(p.id, added.sortedByDescending { it.first }.map { it.second })
    }

    /** Server-Seite (/portiva/sync, Hintergrund-Thread). null = Benutzer hier nicht eingerichtet. */
    fun handle(req: SyncRequest): SyncReply? {
        val p = c.profiles.profiles.value.firstOrNull { canSync(it) && key(it) == req.account } ?: return null
        val merged = synchronized(lock) {
            val before = export(p)
            SyncCore.merge(before, req.data).also { apply(p, it, before) }
        }
        if (req.from.isNotBlank()) Handler(Looper.getMainLooper()).post {
            Toast.makeText(c.app, "Favoriten & Weiterschauen mit „${req.from}“ synchronisiert", Toast.LENGTH_SHORT).show()
        }
        return SyncReply(true, merged, c.deviceName())
    }

    /** Mit allen gefundenen Geraeten abgleichen (blockierend, im Hintergrund aufrufen). Liefert die Meldung. */
    fun syncAll(p: Profile, progress: (String) -> Unit = {}): String {
        if (!canSync(p)) return "Mit einer lokalen M3U-Datei ist kein Abgleich möglich."
        progress("Suche Geräte im Heimnetz …")
        val devices = LinkClient.discover(c.link.port, extraHosts = c.settings.linkHosts())
        if (devices.isEmpty()) return "Kein anderes Gerät mit Portiva gefunden.\nIm gleichen WLAN? Portiva dort geöffnet?"
        val done = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var added = 0
        var removed = 0
        devices.forEach { d ->
            progress("Gleiche ab mit „${d.name}“ …")
            val reply = synchronized(lock) {
                val before = export(p)
                val r = LinkClient.sync(d, SyncRequest(key(p), before, c.deviceName()))
                val data = r.data
                if (r.ok && data != null) {
                    val merged = SyncCore.merge(before, data)
                    apply(p, merged, before)
                    val (a, b) = SyncCore.changes(before, merged)
                    added += a; removed += b
                }
                r
            }
            when {
                reply.ok -> done += d.name
                reply.unknown -> Unit
                else -> failed += reply.error ?: d.name
            }
        }
        return buildString {
            if (done.isEmpty()) append("Kein Gerät mit diesem Benutzer gefunden.\nAuf dem anderen Gerät muss derselbe Zugang eingerichtet und Portiva geöffnet sein.")
            else {
                append("Synchronisiert mit ${done.joinToString(", ")}.")
                append(if (added == 0 && removed == 0) "\nAlles war schon aktuell." else "\n$added neu oder aktualisiert, $removed entfernt.")
            }
            if (failed.isNotEmpty()) append("\n\nNicht geklappt: ${failed.joinToString("; ")}")
        }
    }
}
