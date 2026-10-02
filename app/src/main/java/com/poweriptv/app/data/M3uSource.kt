package com.poweriptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException

/** M3U / M3U8 Playlist (per URL oder lokaler Datei). */
class M3uSource(
    override val profile: Profile,
    private val http: OkHttpClient,
) : ContentSource {

    override val supportsDetails = false

    private val mutex = Mutex()
    private var parsed: Map<ContentType, List<ContentItem>>? = null
    private var headerEpgUrl: String? = null

    private suspend fun data(): Map<ContentType, List<ContentItem>> = mutex.withLock {
        parsed?.let { return it }
        val result = withContext(Dispatchers.IO) {
            when (profile.type) {
                ProfileType.M3U_FILE -> File(profile.localFile).bufferedReader().use { parse(it) }
                else -> {
                    val req = Request.Builder().url(profile.m3uUrl.trim()).build()
                    http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("Playlist nicht erreichbar (HTTP ${resp.code})")
                        val body = resp.body ?: throw IOException("Leere Playlist")
                        body.charStream().buffered().use { parse(it) }
                    }
                }
            }
        }
        if (result.items.values.all { it.isEmpty() }) throw IOException("Keine Kanaele in der Playlist gefunden")
        parsed = result.items
        headerEpgUrl = result.epgUrl
        result.items
    }

    /** Laedt und prueft die Playlist. */
    suspend fun validate(): Int = data().values.sumOf { it.size }

    override suspend fun categories(type: ContentType): List<Category> =
        data()[type].orEmpty().map { it.categoryId }.distinct().map { Category(it, it) }

    override suspend fun items(type: ContentType, categoryId: String?): List<ContentItem> {
        val all = data()[type].orEmpty()
        return if (categoryId == null) all else all.filter { it.categoryId == categoryId }
    }

    override suspend fun movieInfo(item: ContentItem): MovieInfo? = null
    override suspend fun seriesInfo(item: ContentItem): SeriesInfo? = null
    override suspend fun shortEpg(item: ContentItem): List<EpgEntry> = emptyList()
    override suspend fun accountInfo(): AccountInfo? = null
    override fun streamUrl(item: ContentItem): String = item.url ?: ""
    override fun episodeUrl(episode: Episode): String = episode.directUrl ?: ""
    override fun clearCache() {
        parsed = null
    }

    override suspend fun epgUrl(): String? {
        if (profile.epgUrl.isNotBlank()) return profile.epgUrl
        data()
        return headerEpgUrl
    }

    companion object {
        private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

        class Result(val items: Map<ContentType, List<ContentItem>>, val epgUrl: String?)

        fun parse(reader: BufferedReader): Result {
            var epgUrl: String? = null
            val out = mapOf(
                ContentType.LIVE to mutableListOf<ContentItem>(),
                ContentType.MOVIE to mutableListOf(),
                ContentType.SERIES to mutableListOf(),
            )
            var attrs: Map<String, String> = emptyMap()
            var title: String? = null
            var group: String? = null
            var counter = 0
            reader.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        val a = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        epgUrl = (a["url-tvg"] ?: a["x-tvg-url"])?.split(",")?.firstOrNull()?.trim()?.ifBlank { null }
                    }
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val header = line.substringBefore(",", line)
                        attrs = attrRegex.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        title = findTitle(line)
                    }
                    line.startsWith("#EXTGRP", ignoreCase = true) -> group = line.substringAfter(":").trim()
                    line.startsWith("#") -> Unit
                    else -> {
                        val url = line
                        val name = title?.takeIf { it.isNotBlank() } ?: attrs["tvg-name"] ?: url.substringAfterLast('/')
                        val cat = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: group ?: "Ohne Kategorie"
                        val type = classify(url, cat)
                        counter++
                        out.getValue(type).add(
                            ContentItem(
                                id = "m$counter",
                                name = name,
                                type = type,
                                categoryId = cat,
                                logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                                url = url,
                                epgChannelId = attrs["tvg-id"],
                                number = attrs["tvg-chno"]?.toIntOrNull(),
                                archiveDays = attrs["catchup-days"]?.toIntOrNull() ?: attrs["tvg-rec"]?.toIntOrNull() ?: 0,
                            )
                        )
                        attrs = emptyMap(); title = null; group = null
                    }
                }
            }
            return Result(out, epgUrl)
        }

        /** Titel = Text nach dem letzten Komma ausserhalb von Anfuehrungszeichen. */
        private fun findTitle(line: String): String {
            var inQuotes = false
            var idx = -1
            line.forEachIndexed { i, c ->
                if (c == '"') inQuotes = !inQuotes
                if (c == ',' && !inQuotes) idx = i
            }
            return if (idx >= 0) line.substring(idx + 1).trim() else ""
        }

        private fun classify(url: String, group: String): ContentType {
            val u = url.lowercase()
            val g = group.lowercase()
            return when {
                "/series/" in u || g.contains("serie") -> ContentType.SERIES
                "/movie/" in u || g.contains("vod") || g.contains("film") || g.contains("movie") -> ContentType.MOVIE
                u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") -> ContentType.MOVIE
                else -> ContentType.LIVE
            }
        }
    }
}
