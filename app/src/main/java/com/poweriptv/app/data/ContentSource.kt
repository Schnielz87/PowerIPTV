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
}
