package com.poweriptv.app.data

/** Gemeinsame Schnittstelle fuer Xtream Codes und M3U-Playlisten. */
interface ContentSource {
    val profile: Profile
    val supportsDetails: Boolean

    suspend fun categories(type: ContentType): List<Category>

    /** categoryId == null liefert alle Eintraege des Typs. */
    suspend fun items(type: ContentType, categoryId: String?): List<ContentItem>

    suspend fun movieInfo(item: ContentItem): MovieInfo?
    suspend fun seriesInfo(item: ContentItem): SeriesInfo?
    suspend fun shortEpg(item: ContentItem): List<EpgEntry>
    suspend fun accountInfo(): AccountInfo?

    fun streamUrl(item: ContentItem): String
    fun episodeUrl(episode: Episode): String

    fun clearCache()

    /** XMLTV-Quelle fuer den EPG (Xtream: xmltv.php, M3U: url-tvg / eigene URL). */
    suspend fun epgUrl(): String?

    /** Catch-up-URL fuer eine vergangene Sendung (nur wenn der Kanal ein Server-Archiv hat). */
    fun catchupUrl(item: ContentItem, start: Long, end: Long): String? = null
}
