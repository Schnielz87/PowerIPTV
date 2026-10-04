package com.poweriptv.app.player

import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.EpgEntry

/**
 * Jetzt/Weiter fuer die Live-TV-Infoleiste: zuerst der volle Programmfuehrer (XMLTV), sonst das
 * Kurz-EPG des Anbieters. Wird von beiden Playern (Standard + VLC) genutzt.
 */
object LiveEpg {
    /** Schnell: was gerade im Speicher ist bzw. das Kurz-EPG. */
    suspend fun quick(container: AppContainer, item: ContentItem): List<EpgEntry> {
        val now = System.currentTimeMillis()
        val fromXmltv = container.epg.programmesFor(item).filter { it.end > now }.take(3)
            .map { EpgEntry(it.title, it.description, it.start, it.end) }
        return fromXmltv.ifEmpty { runCatching { container.source?.shortEpg(item).orEmpty() }.getOrDefault(emptyList()) }
    }

    /**
     * Programmfuehrer laden bzw. auffrischen (force = neu vom Anbieter herunterladen) und danach
     * Jetzt/Weiter neu ermitteln. Laeuft im Hintergrund, die Leiste zeigt solange den alten Stand.
     */
    suspend fun refresh(container: AppContainer, item: ContentItem, force: Boolean): List<EpgEntry> {
        val source = container.source
        if (source != null) runCatching { container.epg.ensureLoaded(source, force) }
        return quick(container, item)
    }
}
