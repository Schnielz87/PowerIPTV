package com.poweriptv.desktop.data

import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Zugaenge (Xtream / M3U). Passwoerter liegen DPAPI-verschluesselt auf der Platte. */
class ProfileStore {
    private val file = JsonFile(AppDirs.file("profiles.json"), ListSerializer(Profile.serializer())) { emptyList() }
    private val _profiles = MutableStateFlow(file.read().map { it.copy(password = SecretBox.open(it.password)) })
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    fun save(profile: Profile) {
        _profiles.update { list -> list.filterNot { it.id == profile.id } + profile }
        persist()
    }

    fun delete(id: String) {
        _profiles.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private fun persist() = file.write(_profiles.value.map { it.copy(password = SecretBox.seal(it.password)) })
}

@Serializable
data class DesktopSettings(
    val lastProfileId: String? = null,
    val introSound: Boolean = true,
    val volume: Int = 80,
    /** Bildformat: "fit" (Original), "fill" (Zoomen), "stretch" (Strecken). */
    val aspect: String = "fit",
    val hardwareDecoding: Boolean = true,
    /** Netzwerk-Puffer in ms (VLC). */
    val networkCaching: Int = 1500,
    val autoNextEpisode: Boolean = true,
    val startFullscreen: Boolean = false,
)

class SettingsStore {
    private val file = JsonFile(AppDirs.file("settings.json"), DesktopSettings.serializer()) { DesktopSettings() }
    private val _state = MutableStateFlow(file.read())
    val state: StateFlow<DesktopSettings> = _state.asStateFlow()
    val value: DesktopSettings get() = _state.value

    fun update(block: (DesktopSettings) -> DesktopSettings) {
        _state.update(block)
        file.write(_state.value)
    }
}

/** Ein Eintrag in "Weiterschauen" / "Zuletzt gesehen". */
@Serializable
data class WatchEntry(
    val item: ContentItem,
    /** Bei Serien: Episode, die zuletzt lief. */
    val episodeId: String? = null,
    val episodeTitle: String? = null,
    val season: Int? = null,
    val episodeNum: Int? = null,
    val episodeExt: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val updated: Long = System.currentTimeMillis(),
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** Favoriten, Wiedergabe-Positionen, Verlauf und "gesehen"-Markierungen – je Zugang. */
class LibraryStore(profileId: String) {
    private val favFile = JsonFile(AppDirs.file("favorites_$profileId.json"), ListSerializer(ContentItem.serializer())) { emptyList() }
    private val historyFile = JsonFile(AppDirs.file("history_$profileId.json"), ListSerializer(WatchEntry.serializer())) { emptyList() }
    private val posFile = JsonFile(AppDirs.file("positions_$profileId.json"), MapSerializer(String.serializer(), Long.serializer())) { emptyMap() }
    private val watchedFile = JsonFile(AppDirs.file("watched_$profileId.json"), ListSerializer(String.serializer())) { emptyList() }

    private val _favorites = MutableStateFlow(favFile.read())
    val favorites: StateFlow<List<ContentItem>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow(historyFile.read())
    /** Neueste zuerst. */
    val history: StateFlow<List<WatchEntry>> = _history.asStateFlow()

    private val positions = posFile.read().toMutableMap()
    private val _watched = MutableStateFlow(watchedFile.read().toSet())
    val watched: StateFlow<Set<String>> = _watched.asStateFlow()

    fun isFavorite(item: ContentItem) = _favorites.value.any { it.key == item.key }

    fun toggleFavorite(item: ContentItem) {
        _favorites.update { list -> if (list.any { it.key == item.key }) list.filterNot { it.key == item.key } else listOf(item) + list }
        favFile.write(_favorites.value)
    }

    /** Merkt sich die Position (Film oder Episode) und fuehrt den Verlauf. */
    fun savePosition(key: String, position: Long, duration: Long, entry: WatchEntry) {
        val nearEnd = duration > 0 && (duration - position < 90_000 || position.toFloat() / duration > 0.95f)
        if (nearEnd) {
            positions.remove(key); markWatched(key, true)
        } else if (position > 10_000) {
            positions[key] = position
        }
        posFile.write(positions)
        addHistory(entry.copy(position = if (nearEnd) duration else position, duration = duration))
    }

    fun position(key: String): Long = positions[key] ?: 0L

    fun addHistory(entry: WatchEntry) {
        _history.update { list -> (listOf(entry.copy(updated = System.currentTimeMillis())) + list.filterNot { it.item.key == entry.item.key }).take(200) }
        historyFile.write(_history.value)
    }

    fun removeHistory(item: ContentItem) {
        _history.update { list -> list.filterNot { it.item.key == item.key } }
        historyFile.write(_history.value)
    }

    fun clearHistory(type: ContentType? = null) {
        _history.update { list -> if (type == null) emptyList() else list.filterNot { it.item.type == type } }
        historyFile.write(_history.value)
    }

    fun markWatched(key: String, watched: Boolean) {
        _watched.update { if (watched) it + key else it - key }
        if (watched) positions.remove(key)
        watchedFile.write(_watched.value.toList())
        posFile.write(positions)
    }

    /** Laufende Filme/Serien, die noch nicht zu Ende sind. */
    fun continueWatching(): List<WatchEntry> = _history.value.filter { e ->
        e.item.type != ContentType.LIVE && e.duration > 0 && e.position > 10_000 && e.progress < 0.95f
    }

    companion object {
        fun episodeKey(episodeId: String) = "EPISODE:$episodeId"
    }
}
