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
data class FavoriteList(val id: String, val name: String, val items: List<ContentItem> = emptyList())

@Serializable
data class DesktopSettings(
    val lastProfileId: String? = null,
    val introSound: Boolean = true,
    val volume: Int = 80,
    /** Bildformat: "fit" (Original), "fill" (Zoomen), "stretch" (Strecken). */
    val aspect: String = "fit",
    val hardwareDecoding: Boolean = true,
    /** Netzwerk-Puffer in ms (VLC). */
    val networkCaching: Int = 3000,
    val autoNextEpisode: Boolean = true,
    val startFullscreen: Boolean = false,
    /** Bevorzugte Kategorie-Sprache (z.B. "DE"), leer = alle – wie in der Android-App. */
    val categoryLanguage: String = "",
    // --- wie Android: Netzwerk ---
    /** Live-Format bei Xtream: "ts" oder "m3u8". */
    val liveFormat: String = "ts",
    val userAgent: String = USER_AGENT,
    // --- Player ---
    /** Untertitel: KLEIN / NORMAL / GROSS / SEHR_GROSS und dunkler Hintergrund. */
    val subtitleSize: String = "NORMAL",
    val subtitleBackground: Boolean = false,
    /** Vorschaubilder beim Spulen: AUTO / ALWAYS / OFF. */
    val scrubPreview: String = "AUTO",
    /** Anbieter hat bei der Vorschau abgebrochen -> Automatik schaltet sie ab. */
    val scrubBlocked: Boolean = false,
    // --- Downloads & Aufnahmen ---
    /** Parallele Verbindungen pro Download (0 = automatisch nach Account-Limit). */
    val downloadConnections: Int = 0,
    val downloadDir: String = "",
    val recordingDir: String = "",
    // --- KI ---
    val aiModel: String = "gpt-4o-mini",
    val aiBaseUrl: String = "https://api.openai.com/v1",
    // --- VPN ---
    val vpnRequired: Boolean = false,
    val vpnAutoConnect: Boolean = false,
    val acceptExternalVpn: Boolean = true,
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

    // --- Eigene Listen (wie Android: Standard-Liste "Favoriten" + beliebig viele weitere) ---
    private val listsFile = JsonFile(AppDirs.file("lists_$profileId.json"), ListSerializer(FavoriteList.serializer())) { emptyList() }
    private val _lists = MutableStateFlow(listsFile.read())
    /** Eigene Listen (ohne die Standard-Favoriten). */
    val lists: StateFlow<List<FavoriteList>> = _lists.asStateFlow()

    fun createList(name: String): String {
        val id = java.util.UUID.randomUUID().toString()
        _lists.update { it + FavoriteList(id, name.trim().ifBlank { "Neue Liste" }) }
        listsFile.write(_lists.value)
        return id
    }

    fun renameList(id: String, name: String) {
        _lists.update { l -> l.map { if (it.id == id && name.isNotBlank()) it.copy(name = name.trim()) else it } }
        listsFile.write(_lists.value)
    }

    fun deleteList(id: String) {
        _lists.update { l -> l.filterNot { it.id == id } }
        listsFile.write(_lists.value)
    }

    fun isInList(id: String, item: ContentItem) = _lists.value.firstOrNull { it.id == id }?.items?.any { it.key == item.key } == true

    fun toggleInList(id: String, item: ContentItem) {
        _lists.update { l ->
            l.map {
                if (it.id != id) it
                else if (it.items.any { i -> i.key == item.key }) it.copy(items = it.items.filterNot { i -> i.key == item.key })
                else it.copy(items = listOf(item) + it.items)
            }
        }
        listsFile.write(_lists.value)
    }

    // --- Gelerntes Intro je Serie (Start, Ende in ms) ---
    private val introFile = JsonFile(AppDirs.file("intros_$profileId.json"), MapSerializer(String.serializer(), String.serializer())) { emptyMap() }
    private val intros = introFile.read().toMutableMap()

    fun setIntro(seriesKey: String, start: Long, end: Long) {
        intros[seriesKey] = "$start:$end"; introFile.write(intros)
    }

    fun intro(seriesKey: String): Pair<Long, Long>? {
        val (a, b) = intros[seriesKey]?.split(":")?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 2 } ?: return null
        return a to b
    }

    /** Fuer Backup/Wiederherstellung. */
    fun rawPositions(): Map<String, Long> = positions.toMap()
    fun importPositions(map: Map<String, Long>) { positions.putAll(map); posFile.write(positions) }

    companion object {
        fun episodeKey(episodeId: String) = "EPISODE:$episodeId"
    }
}

@Serializable
private data class CategoryPrefsData(
    val hidden: Map<String, List<String>> = emptyMap(),
    val pinned: Map<String, List<String>> = emptyMap(),
)

/** Kategorien je Zugang und Bereich ausblenden oder oben anheften (wie Android). scope = "<profilId>|<Typ>". */
class CategoryPrefsStore {
    private val file = JsonFile(AppDirs.file("category_prefs.json"), CategoryPrefsData.serializer()) { CategoryPrefsData() }
    private var data = file.read()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun hidden(scope: String): Set<String> = data.hidden[scope].orEmpty().toSet()
    fun pinned(scope: String): List<String> = data.pinned[scope].orEmpty()

    fun setHidden(scope: String, id: String, hide: Boolean) {
        val set = hidden(scope).toMutableSet().apply { if (hide) add(id) else remove(id) }
        data = data.copy(hidden = data.hidden + (scope to set.toList()))
        if (hide) setPinned(scope, id, false) else save()
    }

    fun setPinned(scope: String, id: String, pin: Boolean) {
        val list = pinned(scope).filterNot { it == id }.let { if (pin) it + id else it }
        data = data.copy(pinned = data.pinned + (scope to list))
        save()
    }

    fun <T> arrange(scope: String, items: List<T>, id: (T) -> String): List<T> {
        val pins = pinned(scope)
        if (pins.isEmpty()) return items
        val byId = items.associateBy(id)
        return pins.mapNotNull { byId[it] } + items.filterNot { id(it) in pins }
    }

    private fun save() {
        file.write(data)
        _version.value++
    }

    /** Android-Format der Sicherung: "h|scope" = Menge, "p|scope" = Zeilen. */
    fun exportAndroid(): Map<String, kotlinx.serialization.json.JsonElement> {
        val out = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        data.hidden.forEach { (sc, ids) ->
            out["h|$sc"] = kotlinx.serialization.json.buildJsonObject {
                put("t", kotlinx.serialization.json.JsonPrimitive("set")); put("v", kotlinx.serialization.json.JsonArray(ids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
        }
        data.pinned.forEach { (sc, ids) ->
            out["p|$sc"] = kotlinx.serialization.json.buildJsonObject {
                put("t", kotlinx.serialization.json.JsonPrimitive("s")); put("v", kotlinx.serialization.json.JsonPrimitive(ids.joinToString("\n")))
            }
        }
        return out
    }

    fun importAndroid(values: Map<String, kotlinx.serialization.json.JsonObject>) {
        var d = data
        values.forEach { (k, o) ->
            val v = o["v"] ?: return@forEach
            when {
                k.startsWith("h|") -> d = d.copy(hidden = d.hidden + (k.removePrefix("h|") to (v as? kotlinx.serialization.json.JsonArray)?.map { (it as kotlinx.serialization.json.JsonPrimitive).content }.orEmpty()))
                k.startsWith("p|") -> d = d.copy(pinned = d.pinned + (k.removePrefix("p|") to (v as kotlinx.serialization.json.JsonPrimitive).content.split('\n').filter { it.isNotBlank() }))
            }
        }
        data = d
        save()
    }
}
