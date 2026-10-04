package com.poweriptv.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LiveFormat(val ext: String, val label: String) {
    TS("ts", "MPEG-TS (.ts)"),
    HLS("m3u8", "HLS (.m3u8)"),
}

/** Wiedergabe-Engine. */
enum class PlayerEngine(val label: String) {
    AUTO("Automatisch (empfohlen) – Filme & Serien mit VLC, Live TV mit Standard-Player (Live-Pause)"),
    EXO("Immer Standard-Player (mit Timeshift & Aufnahme)"),
    VLC("Immer VLC (spielt fast jedes Format)"),
}

/** Bildformat im Player. */
/** Bildformate im Player. [ratio] = erzwungenes Seitenverhaeltnis (Bild wird darauf angepasst). */
enum class VideoScale(val label: String, val short: String, val ratio: Float? = null, val vlcRatio: String? = null) {
    FIT("Auto (Original-Seitenverhaeltnis)", "Original"),
    ZOOM("Zoom (Bildschirm fuellen, Raender abschneiden)", "Zoom"),
    FILL("Strecken (ganzer Bildschirm)", "Strecken"),
    R16_9("16:9 (Breitbild)", "16:9", 16f / 9f, "16:9"),
    R4_3("4:3 (altes TV-Format)", "4:3", 4f / 3f, "4:3"),
    R21_9("21:9 (Kino-Breitbild)", "21:9", 21f / 9f, "21:9"),
    R185("1,85:1 (Kino)", "1,85:1", 1.85f, "185:100"),
}

enum class Orientation(val label: String) {
    LANDSCAPE("Querformat"),
    PORTRAIT("Hochformat"),
    AUTO("Automatisch (Sensor)"),
}

/** Einfache App-Einstellungen (keine sensiblen Daten). */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = MutableStateFlow(prefs.getBoolean(key, def))
    private fun str(key: String, def: String) = MutableStateFlow(prefs.getString(key, def) ?: def)

    private val _vpnRequired = bool(K_VPN_REQUIRED, false)
    private val _vpnAutoConnect = bool(K_VPN_AUTO, false)
    private val _vpnAppOnly = bool(K_VPN_APP_ONLY, true)
    private val _acceptExternalVpn = bool(K_EXT_VPN, true)
    private val _liveFormat = str(K_LIVE_FORMAT, LiveFormat.TS.name)
    private val _userAgent = str(K_UA, DEFAULT_UA)
    private val _lastProfile = MutableStateFlow(prefs.getString(K_LAST_PROFILE, null))
    private val _aiModel = str(K_AI_MODEL, DEFAULT_AI_MODEL)
    private val _orientation = str(K_ORIENTATION, Orientation.LANDSCAPE.name)
    private val _language = str(K_LANGUAGE, "")
    private val _afr = bool(K_AFR, true)
    private val _dlConnections = MutableStateFlow(prefs.getInt(K_DL_CONN, 0))
    private val _engine = str(K_ENGINE, PlayerEngine.AUTO.name)
    private val _scrub = str(K_SCRUB, "AUTO")
    private val _subSize = str(K_SUB_SIZE, "NORMAL")
    private val _vlcPerf = str(K_VLC_PERF, "AUTO")
    private val _introSound = bool(K_INTRO_SOUND, true)
    private val _subBg = bool(K_SUB_BG, false)
    private val _scrubBlocked = bool(K_SCRUB_BLOCKED, false)
    private val _resize = str(K_RESIZE, VideoScale.FIT.name)
    private val _aiBaseUrl = str(K_AI_URL, DEFAULT_AI_URL)

    /** Kill-Switch: Kein Datenverkehr der App ohne aktives VPN. */
    val vpnRequired: StateFlow<Boolean> = _vpnRequired
    val vpnAutoConnect: StateFlow<Boolean> = _vpnAutoConnect
    /** Nur PowerIPTV durch den Tunnel leiten (Split-Tunneling). */
    val vpnAppOnly: StateFlow<Boolean> = _vpnAppOnly
    /** Auch ein VPN einer anderen App (z.B. NordVPN, Surfshark) als Schutz akzeptieren. */
    val acceptExternalVpn: StateFlow<Boolean> = _acceptExternalVpn
    val liveFormat: StateFlow<String> = _liveFormat
    val userAgent: StateFlow<String> = _userAgent
    val lastProfileId: StateFlow<String?> = _lastProfile
    /** ChatGPT / OpenAI-kompatibles Modell fuer Empfehlungen. */
    val aiModel: StateFlow<String> = _aiModel
    val aiBaseUrl: StateFlow<String> = _aiBaseUrl
    /** Bildschirmausrichtung der App (Standard: Querformat). */
    val orientation: StateFlow<String> = _orientation
    /** Bevorzugtes Sprach-Praefix der Kategorien (z.B. "DE"), leer = alle. */
    val categoryLanguage: StateFlow<String> = _language
    /** Bildwiederholrate des Fernsehers an das Video anpassen (AFR). */
    val autoFrameRate: StateFlow<Boolean> = _afr
    /** Wiedergabe-Engine: Standard (ExoPlayer), VLC oder automatisch. */
    val playerEngine: StateFlow<String> = _engine
    /** Vorschaubilder beim Spulen (Thumbnail-Scrubbing). */
    val scrubPreview: StateFlow<String> = _scrub
    /** Untertitel: Groesse (KLEIN/NORMAL/GROSS/SEHR_GROSS) und dunkler Hintergrund. */
    val subtitleSize: StateFlow<String> = _subSize
    /** VLC-Leistung: AUTO (schwache Geraete -> FAST), QUALITY oder FAST. */
    val vlcPerformance: StateFlow<String> = _vlcPerf
    /** Start-Klang beim Oeffnen der App. */
    val introSound: StateFlow<Boolean> = _introSound
    fun setIntroSound(v: Boolean) = putBool(K_INTRO_SOUND, v, _introSound)
    fun setVlcPerformance(v: String) = putStr(K_VLC_PERF, v, _vlcPerf)
    val subtitleBackground: StateFlow<Boolean> = _subBg
    fun setSubtitleSize(v: String) = putStr(K_SUB_SIZE, v, _subSize)
    fun setSubtitleBackground(v: Boolean) = putBool(K_SUB_BG, v, _subBg)
    /** Skalierung der Untertitel (1.0 = normal). */
    fun subtitleScale(): Float = when (_subSize.value) { "KLEIN" -> 0.8f; "GROSS" -> 1.3f; "SEHR_GROSS" -> 1.6f; else -> 1.0f }
    /** Anbieter hat beim Spulen mit Vorschau den Film abgebrochen -> Automatik schaltet Vorschau ab. */
    val scrubBlocked: StateFlow<Boolean> = _scrubBlocked
    fun setScrubBlocked(v: Boolean) = putBool(K_SCRUB_BLOCKED, v, _scrubBlocked)
    /** Parallele Verbindungen pro Download (0 = automatisch nach Account-Limit). */
    val downloadConnections: StateFlow<Int> = _dlConnections
    /** Bildformat im Player. */
    val videoScale: StateFlow<String> = _resize

    fun setVpnRequired(v: Boolean) = putBool(K_VPN_REQUIRED, v, _vpnRequired)
    fun setVpnAutoConnect(v: Boolean) = putBool(K_VPN_AUTO, v, _vpnAutoConnect)
    fun setVpnAppOnly(v: Boolean) = putBool(K_VPN_APP_ONLY, v, _vpnAppOnly)
    fun setAcceptExternalVpn(v: Boolean) = putBool(K_EXT_VPN, v, _acceptExternalVpn)
    fun setLiveFormat(v: LiveFormat) = putStr(K_LIVE_FORMAT, v.name, _liveFormat)
    fun setUserAgent(v: String) = putStr(K_UA, v.ifBlank { DEFAULT_UA }, _userAgent)
    fun setOrientation(v: Orientation) = putStr(K_ORIENTATION, v.name, _orientation)
    fun orientationEnum(): Orientation = runCatching { Orientation.valueOf(_orientation.value) }.getOrDefault(Orientation.LANDSCAPE)
    fun setAutoFrameRate(v: Boolean) = putBool(K_AFR, v, _afr)
    fun setPlayerEngine(v: PlayerEngine) = putStr(K_ENGINE, v.name, _engine)
    fun setScrubPreview(v: com.poweriptv.app.player.ScrubPreviewMode) { putStr(K_SCRUB, v.name, _scrub); setScrubBlocked(false) }
    fun scrubPreviewEnum() = runCatching { com.poweriptv.app.player.ScrubPreviewMode.valueOf(_scrub.value) }.getOrDefault(com.poweriptv.app.player.ScrubPreviewMode.AUTO)
    fun playerEngineEnum(): PlayerEngine = runCatching { PlayerEngine.valueOf(_engine.value) }.getOrDefault(PlayerEngine.AUTO)

    /** Streams, bei denen der Standard-Player kein Bild dekodieren konnte (gemerkt als Hash). */
    fun needsVlc(url: String) = url.hashCode().toString() in (prefs.getStringSet(K_VLC_STREAMS, emptySet()) ?: emptySet())
    fun markNeedsVlc(url: String) {
        val set = (prefs.getStringSet(K_VLC_STREAMS, emptySet()) ?: emptySet()).toMutableSet()
        set += url.hashCode().toString()
        prefs.edit().putStringSet(K_VLC_STREAMS, set).apply()
    }
    fun clearVlcStreams() = prefs.edit().remove(K_VLC_STREAMS).remove(K_VLC_CATEGORIES).apply()

    /** Kategorien, in denen schon ein Titel VLC brauchte -> weitere Titel direkt mit VLC starten. */
    fun categoryNeedsVlc(key: String) = key in (prefs.getStringSet(K_VLC_CATEGORIES, emptySet()) ?: emptySet())
    fun markCategoryNeedsVlc(key: String) {
        val set = (prefs.getStringSet(K_VLC_CATEGORIES, emptySet()) ?: emptySet()).toMutableSet()
        set += key
        prefs.edit().putStringSet(K_VLC_CATEGORIES, set).apply()
    }
    fun setDownloadConnections(v: Int) {
        prefs.edit().putInt(K_DL_CONN, v).apply(); _dlConnections.value = v
    }
    fun setVideoScale(v: VideoScale) = putStr(K_RESIZE, v.name, _resize)
    fun videoScaleEnum(): VideoScale = runCatching { VideoScale.valueOf(_resize.value) }.getOrDefault(VideoScale.FIT)
    fun setCategoryLanguage(v: String) = putStr(K_LANGUAGE, v, _language)
    fun setAiModel(v: String) = putStr(K_AI_MODEL, v.trim().ifBlank { DEFAULT_AI_MODEL }, _aiModel)
    fun setAiBaseUrl(v: String) = putStr(K_AI_URL, v.trim().trimEnd('/').ifBlank { DEFAULT_AI_URL }, _aiBaseUrl)
    fun setLastProfileId(v: String?) {
        prefs.edit().putString(K_LAST_PROFILE, v).apply()
        _lastProfile.value = v
    }

    fun liveFormatEnum(): LiveFormat = runCatching { LiveFormat.valueOf(_liveFormat.value) }.getOrDefault(LiveFormat.TS)

    private fun putBool(key: String, v: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, v).apply(); flow.value = v
    }

    private fun putStr(key: String, v: String, flow: MutableStateFlow<String>) {
        prefs.edit().putString(key, v).apply(); flow.value = v
    }

    companion object {
        const val DEFAULT_UA = "PowerIPTV/1.0 (Linux; Android)"
        const val DEFAULT_AI_MODEL = "gpt-4o-mini"
        const val DEFAULT_AI_URL = "https://api.openai.com/v1"
        private const val K_AI_MODEL = "ai_model"
        private const val K_ORIENTATION = "orientation"
        private const val K_AFR = "auto_frame_rate"
        private const val K_ENGINE = "player_engine"
        private const val K_SCRUB = "scrub_preview"
        private const val K_SUB_SIZE = "subtitle_size"
        private const val K_VLC_PERF = "vlc_performance"
        private const val K_INTRO_SOUND = "intro_sound"
        private const val K_SUB_BG = "subtitle_bg"
        private const val K_SCRUB_BLOCKED = "scrub_preview_blocked"
        private const val K_VLC_STREAMS = "vlc_streams"
        private const val K_VLC_CATEGORIES = "vlc_categories"
        private const val K_DL_CONN = "download_connections"
        private const val K_RESIZE = "video_scale"
        private const val K_LANGUAGE = "category_language"
        private const val K_AI_URL = "ai_base_url"
        private const val K_VPN_REQUIRED = "vpn_required"
        private const val K_VPN_AUTO = "vpn_auto"
        private const val K_VPN_APP_ONLY = "vpn_app_only"
        private const val K_EXT_VPN = "vpn_accept_external"
        private const val K_LIVE_FORMAT = "live_format"
        private const val K_UA = "user_agent"
        private const val K_LAST_PROFILE = "last_profile"
    }
}
