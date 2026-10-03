package com.poweriptv.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * VPN unter Windows – wie in der Android-App auf Basis von WireGuard (.conf vom VPN-Anbieter).
 * Der Tunnel laeuft ueber den offiziellen „WireGuard für Windows“-Dienst (einmalig installieren);
 * Portiva richtet ihn ein, verbindet/trennt (Windows fragt dabei nach Administrator-Rechten)
 * und blockiert per Kill-Switch jeden Abruf, solange kein VPN aktiv ist.
 */
class DesktopVpn(private val settings: SettingsStore) {
    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _hasConfig = MutableStateFlow(DesktopSecretStore.read(CONFIG) != null)
    val hasConfig: StateFlow<Boolean> = _hasConfig.asStateFlow()

    init {
        refreshState()
    }

    val wireGuardExe: File?
        get() = listOfNotNull(System.getenv("ProgramFiles"), "C:\\Program Files")
            .map { File(it, "WireGuard\\wireguard.exe") }.firstOrNull { it.exists() }

    val wireGuardInstalled: Boolean get() = wireGuardExe != null

    fun configText(): String? = DesktopSecretStore.read(CONFIG)

    fun serverEndpoint(): String? = configText()?.lineSequence()?.map { it.trim() }
        ?.firstOrNull { it.startsWith("Endpoint", ignoreCase = true) }?.substringAfter("=")?.trim()

    /** Prueft und speichert eine WireGuard-Konfiguration (verschluesselt). */
    fun saveConfig(text: String): Result<Unit> = runCatching {
        val t = text.trim()
        require(t.contains("[Interface]", true) && t.contains("[Peer]", true)) { "Abschnitte [Interface] und [Peer] fehlen" }
        require(Regex("(?im)^\\s*PrivateKey\\s*=").containsMatchIn(t)) { "PrivateKey fehlt" }
        require(Regex("(?im)^\\s*Endpoint\\s*=").containsMatchIn(t)) { "Endpoint fehlt" }
        DesktopSecretStore.write(CONFIG, t)
        _hasConfig.value = true
    }.recoverCatching { throw IllegalArgumentException("Ungültige WireGuard-Konfiguration: ${it.message}") }

    suspend fun deleteConfig() {
        disconnect()
        DesktopSecretStore.write(CONFIG, null)
        _hasConfig.value = false
    }

    /** Ist unser Tunnel ("portiva") aktiv? */
    private fun tunnelUp(): Boolean = interfaces().any { (it.name + " " + it.displayName).lowercase().contains(TUNNEL) && it.isUp }

    /** Ist irgendein VPN aktiv (auch eines anderen Anbieters, z.B. NordVPN, Surfshark)? */
    fun isSystemVpnActive(): Boolean = interfaces().any { n ->
        val d = (n.name + " " + n.displayName).lowercase()
        n.isUp && !n.isLoopback && listOf("wireguard", "wintun", "tap-windows", "tap-nord", "openvpn", "nordlynx", "vpn", "tunnel", TUNNEL).any { it in d }
    }

    private fun interfaces(): List<NetworkInterface> = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())

    fun isProtected(): Boolean = tunnelUp() || (settings.value.acceptExternalVpn && isSystemVpnActive())

    fun refreshState() {
        if (_state.value != VpnState.CONNECTING) _state.value = if (tunnelUp()) VpnState.CONNECTED else if (_state.value == VpnState.ERROR) VpnState.ERROR else VpnState.DISCONNECTED
    }

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        val text = configText() ?: return@withContext Result.failure(IllegalStateException("Keine VPN-Konfiguration hinterlegt"))
        val exe = wireGuardExe ?: return@withContext Result.failure(IllegalStateException("„WireGuard für Windows“ ist nicht installiert"))
        _error.value = null
        _state.value = VpnState.CONNECTING
        runCatching {
            // Konfiguration nur kurz im Klartext ablegen – WireGuard uebernimmt sie verschluesselt in den eigenen Speicher
            val conf = File(File(AppDirs.root, "vpn").apply { mkdirs() }, "$TUNNEL.conf")
            conf.writeText(text)
            try {
                runElevated(exe, "/installtunnelservice \"${conf.absolutePath}\"")
                repeat(20) { if (tunnelUp()) return@repeat; delay(500) }
            } finally {
                conf.delete()
            }
            if (!tunnelUp()) error("Tunnel wurde nicht gestartet (Administrator-Abfrage abgelehnt?)")
            _state.value = VpnState.CONNECTED
        }.onFailure {
            _state.value = VpnState.ERROR
            _error.value = it.message ?: it.javaClass.simpleName
        }.map { }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        wireGuardExe?.let { exe -> runCatching { runElevated(exe, "/uninstalltunnelservice $TUNNEL") } }
        repeat(10) { if (!tunnelUp()) return@repeat; delay(300) }
        _state.value = if (tunnelUp()) VpnState.CONNECTED else VpnState.DISCONNECTED
    }

    /** Startet WireGuard mit Administrator-Rechten (Windows zeigt die Benutzerkontensteuerung). */
    private fun runElevated(exe: File, args: String) {
        val ps = "Start-Process -FilePath '${exe.absolutePath}' -ArgumentList '${args.replace("'", "''")}' -Verb RunAs -Wait -WindowStyle Hidden"
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) error(out.lines().firstOrNull { it.isNotBlank() } ?: "WireGuard-Befehl fehlgeschlagen")
    }

    companion object {
        private const val CONFIG = "wireguard"
        private const val TUNNEL = "portiva"
        const val DOWNLOAD_URL = "https://download.wireguard.com/windows-client/wireguard-installer.exe"
    }
}
