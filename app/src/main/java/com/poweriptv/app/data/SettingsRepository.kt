package com.poweriptv.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LiveFormat(val ext: String, val label: String) {
    TS("ts", "MPEG-TS (.ts)"),
    HLS("m3u8", "HLS (.m3u8)"),
}

/** Bildformat im Player. */
enum class VideoScale(val label: String) {
    FIT("Auto (Original-Seitenverhaeltnis)"),
    ZOOM("Zoom (Bildschirm fuellen, Raender abschneiden)"),
    FILL("Strecken (ganzer Bildschirm)"),
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
