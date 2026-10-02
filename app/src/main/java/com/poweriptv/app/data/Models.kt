package com.poweriptv.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class ProfileType { XTREAM, M3U_URL, M3U_FILE }

/** Ein Zugang (wie "Benutzer" in IPTV Smarters). */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val type: ProfileType,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    /** Pfad der importierten lokalen M3U-Datei im App-Speicher. */
    val localFile: String = "",
)

@Serializable
enum class ContentType { LIVE, MOVIE, SERIES }

@Serializable
data class Category(val id: String, val name: String)

@Serializable
data class ContentItem(
    val id: String,
    val name: String,
    val type: ContentType,
    val categoryId: String = "",
    val logo: String? = null,
    /** Direkte Stream-URL (M3U). Bei Xtream wird sie aus den Zugangsdaten gebaut. */
    val url: String? = null,
    val containerExtension: String? = null,
    val rating: String? = null,
    val epgChannelId: String? = null,
    val number: Int? = null,
) {
    val key: String get() = "${type.name}:$id"
}

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val director: String?,
    val cast: String?,
    val releaseDate: String?,
    val duration: String?,
    val rating: String?,
    val cover: String?,
    val backdrop: String?,
    val containerExtension: String?,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNum: Int,
    val containerExtension: String?,
    val plot: String?,
    val image: String?,
    val duration: String?,
    val directUrl: String? = null,
)

data class SeriesInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val releaseDate: String?,
    val rating: String?,
    val cover: String?,
    val backdrop: String?,
    val episodes: Map<Int, List<Episode>>,
)

data class EpgEntry(
    val title: String,
    val description: String?,
    val start: Long,
    val end: Long,
)

data class AccountInfo(
    val status: String?,
    val expiresAt: Long?,
    val maxConnections: String?,
    val activeConnections: String?,
    val isTrial: Boolean,
)
