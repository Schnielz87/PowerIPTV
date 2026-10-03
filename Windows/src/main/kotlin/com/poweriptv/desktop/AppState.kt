package com.poweriptv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowState
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.Episode
import com.poweriptv.app.data.Profile
import com.poweriptv.desktop.data.DiskCachedSource
import com.poweriptv.desktop.data.LibraryStore
import com.poweriptv.desktop.data.ProfileStore
import com.poweriptv.desktop.data.SettingsStore
import com.poweriptv.desktop.data.WatchEntry
import com.poweriptv.desktop.data.createSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class Browse(val type: ContentType) : Screen
    data object Favorites : Screen
    data object Search : Screen
    data object Settings : Screen
    data object Profiles : Screen
    data class Detail(val item: ContentItem) : Screen
}

/** Was der Player abspielen soll – inkl. Kontext fuer Senderwechsel und naechste Folge. */
data class PlayRequest(
    val item: ContentItem,
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val startAt: Long = 0L,
    /** Serien: aktuelle Episode und alle Episoden (fuer "Naechste Folge"). */
    val episode: Episode? = null,
    val episodes: List<Episode> = emptyList(),
    val seriesCover: String? = null,
    /** Live: Sender der aktuellen Liste (fuer Senderwechsel). */
    val channels: List<ContentItem> = emptyList(),
)

class AppState(val window: WindowState) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = SettingsStore()
    val profiles = ProfileStore()

    var profile by mutableStateOf<Profile?>(null); private set
    var source by mutableStateOf<DiskCachedSource?>(null); private set
    var library by mutableStateOf<LibraryStore?>(null); private set

    private val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    var playing by mutableStateOf<PlayRequest?>(null)
    /** Zuletzt gewaehlte Kategorie je Bereich (bleibt beim Hin- und Herwechseln erhalten). */
    val selectedCategory = androidx.compose.runtime.mutableStateMapOf<ContentType, String>()
    /** Filter & Sortierung je Bereich (bleiben erhalten, wie Android). */
    val browseFilters = androidx.compose.runtime.mutableStateMapOf<ContentType, com.poweriptv.app.ui.components.ContentFilter>()
    val categoryPrefs = com.poweriptv.desktop.data.CategoryPrefsStore()
    var refreshing by mutableStateOf(false); private set
    var refreshError by mutableStateOf<String?>(null); private set
    /** Wird bei jeder Aktualisierung erhoeht, damit Listen neu laden. */
    var dataVersion by mutableStateOf(0); private set

    init {
        val last = settings.value.lastProfileId
        val p = profiles.profiles.value.firstOrNull { it.id == last } ?: profiles.profiles.value.firstOrNull()
        if (p != null) activate(p) else stack[0] = Screen.Profiles
    }

    fun activate(p: Profile) {
        profile = p
        source = DiskCachedSource(createSource(p))
        library = LibraryStore(p.id)
        settings.update { it.copy(lastProfileId = p.id) }
        stack.clear(); stack += Screen.Home
        selectedCategory.clear()
        dataVersion++
        if (source?.isStale() == true) refresh()
    }

    /** Kein Zugang mehr vorhanden -> Einrichtung. */
    fun deactivate() {
        profile = null; source = null; library = null
        stack.clear(); stack += Screen.Profiles
    }

    fun navigate(s: Screen, replace: Boolean = false) {
        if (replace || isTopLevel(s)) {
            stack.clear()
            if (s != Screen.Home && profile != null) stack += Screen.Home
        }
        if (stack.lastOrNull() != s) stack += s
    }

    private fun isTopLevel(s: Screen) = s !is Screen.Detail

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun refresh() {
        val src = source ?: return
        if (refreshing) return
        refreshing = true; refreshError = null
        scope.launch {
            runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { src.refreshAll() } }
                .onFailure { refreshError = it.message ?: "Unbekannter Fehler" }
            refreshing = false
            dataVersion++
        }
    }

    // --- Wiedergabe ---

    fun play(item: ContentItem, channels: List<ContentItem> = emptyList(), startAt: Long? = null) {
        val src = source ?: return
        val lib = library ?: return
        val url = item.url ?: runCatching { src.streamUrl(item) }.getOrNull() ?: return
        val start = startAt ?: if (item.type == ContentType.MOVIE) lib.position(item.key) else 0L
        if (item.type == ContentType.LIVE) lib.addHistory(WatchEntry(item))
        playing = PlayRequest(item, url, item.name, startAt = start, channels = channels)
    }

    fun playEpisode(series: ContentItem, episode: Episode, episodes: List<Episode>, cover: String?, startAt: Long? = null) {
        val src = source ?: return
        val lib = library ?: return
        val url = episode.directUrl ?: src.episodeUrl(episode)
        val start = startAt ?: lib.position(LibraryStore.episodeKey(episode.id))
        playing = PlayRequest(
            item = series, url = url, title = series.name,
            subtitle = "S${episode.season} · E${episode.episodeNum} – ${episode.title}",
            startAt = start, episode = episode, episodes = episodes, seriesCover = cover,
        )
    }

    // --- Vollbild: eigenes randloses Fenster ueber den ganzen Bildschirm (ohne Titel- und Taskleiste) ---
    private var fullscreenState by mutableStateOf(false)
    /** Hauptfenster (fuer den Bildschirm, auf dem das Vollbild erscheinen soll). */
    var mainWindow: java.awt.Window? = null

    fun toggleFullscreen() = setFullscreen(!fullscreenState)

    fun setFullscreen(on: Boolean) { fullscreenState = on }

    val isFullscreen: Boolean get() = fullscreenState

    // --- Player: bleibt beim Wechsel zwischen Fenster und Vollbild erhalten (Stream laeuft weiter) ---
    var player: com.poweriptv.desktop.player.PlayerController? = null; private set
    var playerAspect by mutableStateOf(settings.value.aspect)

    fun playerController(): com.poweriptv.desktop.player.PlayerController =
        player ?: com.poweriptv.desktop.player.PlayerController(settings.value.networkCaching, settings.value.hardwareDecoding)
            .apply { setVolumeTo(settings.value.volume) }.also { player = it }

    /** Player schliessen: Einstellungen merken und VLC freigeben. */
    fun closePlayer() {
        player?.let { p -> settings.update { it.copy(volume = p.volume, aspect = playerAspect) }; p.release() }
        player = null
        playing = null
    }

    /** Startbildschirm nur einmal pro Programmstart. */
    var splashDone by mutableStateOf(System.getProperty("portiva.nosplash") != null)
}
