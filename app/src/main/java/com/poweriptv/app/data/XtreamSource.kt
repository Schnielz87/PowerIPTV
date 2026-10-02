package com.poweriptv.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class XtreamSource(
    override val profile: Profile,
    private val http: OkHttpClient,
    private val json: Json,
    private val liveExt: () -> String,
) : ContentSource {

    override val supportsDetails = true

    private val base = normalizeServer(profile.serverUrl)
    private val user = enc(profile.username)
    private val pass = enc(profile.password)
    @Volatile private var serverTimezone: String? = null
    private val catCache = ConcurrentHashMap<ContentType, List<Category>>()
    private val itemCache = ConcurrentHashMap<String, List<ContentItem>>()

    private suspend fun call(action: String?, vararg params: Pair<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            val b = "$base/player_api.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", profile.username)
                .addQueryParameter("password", profile.password)
            if (action != null) b.addQueryParameter("action", action)
            params.forEach { (k, v) -> b.addQueryParameter(k, v) }
            val req = Request.Builder().url(b.build()).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Server antwortet mit HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) JsonArray(emptyList()) else json.parseToJsonElement(body)
            }
        }

    /** Prueft die Zugangsdaten. Wirft eine Exception bei Fehlern. */
    suspend fun authenticate(): AccountInfo {
        val root = call(null) as? JsonObject ?: throw IOException("Ungueltige Server-Antwort")
        val info = root.obj("user_info") ?: throw IOException("Ungueltige Server-Antwort")
        if (info.int("auth") != 1) throw IOException("Benutzername oder Passwort falsch")
        root.obj("server_info")?.str("timezone")?.let { serverTimezone = it }
        return parseAccount(info)
    }

    override suspend fun accountInfo(): AccountInfo? = runCatching { authenticate() }.getOrNull()

    private fun parseAccount(info: JsonObject) = AccountInfo(
        status = info.str("status"),
        expiresAt = info.long("exp_date")?.times(1000),
        maxConnections = info.str("max_connections"),
        activeConnections = info.str("active_cons"),
        isTrial = info.str("is_trial") == "1",
    )

    override suspend fun categories(type: ContentType): List<Category> {
        catCache[type]?.let { return it }
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        val list = call(action).asArray().mapNotNull { o ->
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name") ?: "Unbenannt")
        }
        catCache[type] = list
        return list
    }

    override suspend fun items(type: ContentType, categoryId: String?): List<ContentItem> {
        val cacheKey = "${type.name}:${categoryId ?: "*"}"
        itemCache[cacheKey]?.let { return it }
        val action = when (type) {
            ContentType.LIVE -> "get_live_streams"
            ContentType.MOVIE -> "get_vod_streams"
            ContentType.SERIES -> "get_series"
        }
        val params = if (categoryId != null) arrayOf("category_id" to categoryId) else emptyArray()
        var raw = call(action, *params).asArray()
        if (categoryId == null && raw.isEmpty()) {
            // Manche Anbieter liefern ohne category_id nichts -> Kategorie fuer Kategorie einsammeln
            val cats = categories(type)
            raw = coroutineScope {
                cats.chunked(6).flatMap { chunk ->
                    chunk.map { c -> async { runCatching { call(action, "category_id" to c.id).asArray() }.getOrDefault(emptyList()) } }
                        .awaitAll().flatten()
                }
            }
        }
        val list = raw.distinctBy { it.str("stream_id") ?: it.str("series_id") }.mapNotNull { o ->
            when (type) {
                ContentType.LIVE -> ContentItem(
                    id = o.str("stream_id") ?: return@mapNotNull null,
                    name = o.str("name") ?: "",
                    type = type,
                    categoryId = o.str("category_id") ?: "",
                    logo = o.str("stream_icon"),
                    epgChannelId = o.str("epg_channel_id"),
                    number = o.int("num"),
                    archiveDays = if (o.int("tv_archive") == 1) (o.int("tv_archive_duration") ?: 1) else 0,
                )
                ContentType.MOVIE -> ContentItem(
                    id = o.str("stream_id") ?: return@mapNotNull null,
                    name = o.str("name") ?: "",
                    type = type,
                    categoryId = o.str("category_id") ?: "",
                    logo = o.str("stream_icon"),
                    containerExtension = o.str("container_extension"),
                    rating = o.str("rating"),
                    added = o.long("added")?.times(1000),
                    year = yearOf(o.str("year") ?: o.str("release_date"), o.str("name")),
                )
                ContentType.SERIES -> ContentItem(
                    id = o.str("series_id") ?: return@mapNotNull null,
                    name = o.str("name") ?: "",
                    type = type,
                    categoryId = o.str("category_id") ?: "",
                    logo = o.str("cover"),
                    rating = o.str("rating"),
                    added = o.long("last_modified")?.times(1000),
                    year = yearOf(o.str("releaseDate") ?: o.str("release_date") ?: o.str("year"), o.str("name")),
                    genre = o.str("genre"),
                )
            }
        }
        itemCache[cacheKey] = list
        return list
    }

    override suspend fun movieInfo(item: ContentItem): MovieInfo? {
        val root = call("get_vod_info", "vod_id" to item.id) as? JsonObject ?: return null
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val movie = root.obj("movie_data")
        return MovieInfo(
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            director = info.str("director"),
            cast = info.str("cast") ?: info.str("actors"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            duration = info.str("duration"),
            rating = info.str("rating"),
            cover = info.str("movie_image") ?: info.str("cover_big") ?: item.logo,
            backdrop = (info["backdrop_path"] as? JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: info.str("backdrop_path"),
            containerExtension = movie?.str("container_extension") ?: item.containerExtension,
            age = info.str("age") ?: info.str("mpaa_rating") ?: info.str("certification"),
            tmdbId = info.str("tmdb_id") ?: info.str("tmdb"),
        )
    }

    override suspend fun seriesInfo(item: ContentItem): SeriesInfo? {
        val root = call("get_series_info", "series_id" to item.id) as? JsonObject ?: return null
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val episodes = mutableMapOf<Int, MutableList<Episode>>()
        val seriesBackdrop = (info["backdrop_path"] as? JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: info.str("backdrop_path")
        val seriesCover = info.str("cover") ?: item.logo
        // Staffel-Cover als Ersatz-Vorschaubild
        val seasonCovers = root["seasons"].asArray().mapNotNull { s ->
            val nr = s.int("season_number") ?: s.int("season_num") ?: return@mapNotNull null
            val img = img(s.str("cover_big")) ?: img(s.str("cover")) ?: img(s.str("cover_tmdb")) ?: return@mapNotNull null
            nr to img
        }.toMap()
        val parseEp = { o: JsonObject, seasonFallback: Int ->
            val epInfo = o.obj("info")
            val season = o.int("season") ?: seasonFallback
            // Vorschaubild-Kandidaten: Episode -> Staffel-Cover -> Serien-Hintergrund -> Serien-Cover
            val candidates = listOfNotNull(
                img(epInfo?.str("movie_image")), img(epInfo?.str("cover_big")), img(epInfo?.str("cover")),
                img(epInfo?.str("still_path")), img(o.str("cover")), img(o.str("movie_image")),
                seasonCovers[season], img(seriesBackdrop), img(seriesCover), img(item.logo),
            ).distinct()
            val ep = Episode(
                id = o.str("id") ?: "",
                title = o.str("title") ?: "Episode ${o.str("episode_num") ?: ""}",
                season = season,
                episodeNum = o.int("episode_num") ?: 0,
                containerExtension = o.str("container_extension"),
                plot = epInfo?.str("plot")?.takeUnless { it.equals("n/a", true) || it.equals("null", true) },
                // Vorschaubild: alle ueblichen Felder, sonst Staffel-Cover / Serien-Hintergrund / Serien-Cover
                image = candidates.firstOrNull(),
                imageCandidates = candidates,
                duration = epInfo?.str("duration"),
            )
            if (ep.id.isNotBlank()) episodes.getOrPut(season) { mutableListOf() }.add(ep)
        }
        when (val eps = root["episodes"]) {
            is JsonObject -> eps.forEach { (season, arr) ->
                arr.asArray().forEach { parseEp(it, season.toIntOrNull() ?: 0) }
            }
            is JsonArray -> eps.forEachIndexed { idx, arr ->
                arr.asArray().forEach { parseEp(it, idx + 1) }
            }
            else -> Unit
        }
        return SeriesInfo(
            plot = info.str("plot"),
            genre = info.str("genre"),
            cast = info.str("cast"),
            releaseDate = info.str("releaseDate") ?: info.str("release_date"),
            rating = info.str("rating"),
            cover = info.str("cover") ?: item.logo,
            backdrop = seriesBackdrop,
            episodes = episodes.toSortedMap().mapValues { (_, v) -> v.sortedBy { it.episodeNum } },
            age = info.str("age") ?: info.str("mpaa_rating") ?: info.str("certification"),
            tmdbId = info.str("tmdb_id") ?: info.str("tmdb"),
        )
    }

    /** Bild-URL pruefen; TMDB-Pfade ("/abc.jpg") zu vollstaendigen URLs ergaenzen. */
    private fun img(v: String?): String? {
        val s = v?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) && !it.equals("n/a", true) } ?: return null
        return when {
            s.startsWith("http") -> s
            s.startsWith("/") -> "https://image.tmdb.org/t/p/w300$s"
            else -> null
        }
    }

    override suspend fun shortEpg(item: ContentItem): List<EpgEntry> = runCatching {
        val root = call("get_short_epg", "stream_id" to item.id, "limit" to "4") as? JsonObject
            ?: return emptyList()
        root["epg_listings"].asArray().map { o ->
            EpgEntry(
                title = decodeB64(o.str("title")) ?: "",
                description = decodeB64(o.str("description")),
                start = o.long("start_timestamp")?.times(1000) ?: parseDate(o.str("start")),
                end = o.long("stop_timestamp")?.times(1000) ?: parseDate(o.str("end")),
            )
        }
    }.getOrDefault(emptyList())

    override fun streamUrl(item: ContentItem): String = when (item.type) {
        ContentType.LIVE -> "$base/live/$user/$pass/${item.id}.${liveExt()}"
        ContentType.MOVIE -> "$base/movie/$user/$pass/${item.id}.${item.containerExtension ?: "mp4"}"
        ContentType.SERIES -> error("Serien werden ueber Episoden abgespielt")
    }

    override fun episodeUrl(episode: Episode): String =
        "$base/series/$user/$pass/${episode.id}.${episode.containerExtension ?: "mp4"}"

    override fun clearCache() {
        catCache.clear(); itemCache.clear()
    }

    override suspend fun epgUrl(): String? = profile.epgUrl.ifBlank {
        "$base/xmltv.php?username=$user&password=$pass"
    }

    /** Xtream-Timeshift-API: /timeshift/{user}/{pass}/{dauer_min}/{yyyy-MM-dd:HH-mm}/{stream_id}.ts */
    override fun catchupUrl(item: ContentItem, start: Long, end: Long): String? {
        if (item.type != ContentType.LIVE || item.archiveDays <= 0) return null
        val oldest = System.currentTimeMillis() - item.archiveDays * 24L * 3600_000L
        if (start < oldest || start > System.currentTimeMillis()) return null
        val tz = serverTimezone?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = tz }
        val minutes = ((end - start) / 60_000L).coerceAtLeast(1)
        return "$base/timeshift/$user/$pass/$minutes/${fmt.format(java.util.Date(start))}/${item.id}.ts"
    }

    private fun decodeB64(s: String?): String? = s?.let {
        runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(it)
    }

    private fun parseDate(s: String?): Long = s?.let {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(it)?.time
        }.getOrNull()
    } ?: 0L

    companion object {
        fun normalizeServer(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            u = u.removeSuffix("/player_api.php").removeSuffix("/get.php").trimEnd('/')
            return u
        }

        private val yearRegex = Regex("""\b(19[3-9]\d|20[0-4]\d)\b""")

        /** Jahr aus Datum/Jahr-Feld oder aus dem Titel, z.B. "Film (2019)". */
        fun yearOf(field: String?, name: String?): Int? =
            field?.let { yearRegex.find(it)?.value?.toIntOrNull() }
                ?: name?.let { n -> Regex("""\((19[3-9]\d|20[0-4]\d)\)""").find(n)?.groupValues?.get(1)?.toIntOrNull() }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
