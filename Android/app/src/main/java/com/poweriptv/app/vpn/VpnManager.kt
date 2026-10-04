package com.poweriptv.app.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import com.poweriptv.app.data.SecureStore
import com.poweriptv.app.data.SettingsRepository
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * Eingebautes VPN auf Basis von WireGuard.
 * Die Konfiguration (.conf) bekommt man von fast jedem VPN-Anbieter
 * (z.B. Mullvad, ProtonVPN, Surfshark, NordVPN/NordLynx, eigener Server).
 */
class VpnManager(
    context: Context,
    private val settings: SettingsRepository,
    private val secure: SecureStore,
) {
    private val appContext = context.applicationContext
    private val backend by lazy { GoBackend(appContext) }

    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _hasConfig = MutableStateFlow(secure.read(CONFIG_FILE) != null)
    val hasConfig: StateFlow<Boolean> = _hasConfig

    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            _state.value = if (newState == Tunnel.State.UP) VpnState.CONNECTED else VpnState.DISCONNECTED
        }
    }

    fun configText(): String? = secure.read(CONFIG_FILE)

    /** Prueft und speichert eine WireGuard-Konfiguration (verschluesselt). */
    fun saveConfig(text: String): Result<Unit> = runCatching {
        parse(text, appOnly = false)
        secure.write(CONFIG_FILE, text.trim())
        _hasConfig.value = true
    }.recoverCatching { throw IllegalArgumentException("Ungueltige WireGuard-Konfiguration: ${it.message ?: it.javaClass.simpleName}") }

    fun serverEndpoint(): String? = configText()?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Endpoint", ignoreCase = true) }
        ?.substringAfter("=")?.trim()

    suspend fun deleteConfig() {
        disconnect()
        secure.write(CONFIG_FILE, null)
        _hasConfig.value = false
    }

    /** Liefert ein Intent, falls der Nutzer die VPN-Berechtigung noch bestaetigen muss. */
    fun permissionIntent(): Intent? = VpnService.prepare(appContext)

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        val text = configText() ?: return@withContext Result.failure(IllegalStateException("Keine VPN-Konfiguration hinterlegt"))
        _error.value = null
        _state.value = VpnState.CONNECTING
        runCatching {
            val config = parse(text, settings.vpnAppOnly.value)
            backend.setState(tunnel, Tunnel.State.UP, config)
            _state.value = VpnState.CONNECTED
        }.onFailure {
            _state.value = VpnState.ERROR
            _error.value = it.message ?: it.javaClass.simpleName
        }.map { }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        _state.value = VpnState.DISCONNECTED
    }

    /** Ist irgendein VPN auf dem Geraet aktiv (auch eines anderen Anbieters)? */
    fun isSystemVpnActive(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    /** True, wenn der Datenverkehr der App aktuell durch ein VPN geschuetzt ist. */
    fun isProtected(): Boolean =
        _state.value == VpnState.CONNECTED || (settings.acceptExternalVpn.value && isSystemVpnActive())

    private fun parse(text: String, appOnly: Boolean): Config {
        var cfg = text.trim()
        if (appOnly) {
            // Split-Tunneling: nur PowerIPTV laeuft durch das VPN
            val lines = cfg.lines().filterNot {
                val l = it.trim().lowercase()
                l.startsWith("includedapplications") || l.startsWith("excludedapplications")
            }.toMutableList()
            val idx = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
            if (idx >= 0) lines.add(idx + 1, "IncludedApplications = ${appContext.packageName}")
            cfg = lines.joinToString("\n")
        }
        return Config.parse(ByteArrayInputStream(cfg.toByteArray()))
    }

    companion object {
        private const val CONFIG_FILE = "wireguard"
        private const val TUNNEL_NAME = "poweriptv"
    }
}
