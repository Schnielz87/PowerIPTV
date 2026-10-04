package com.poweriptv.app.vpn

import com.poweriptv.app.data.SettingsRepository
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class VpnRequiredException : IOException(
    "Kill-Switch aktiv: Verbindung blockiert, weil kein VPN verbunden ist. " +
        "Bitte in den Einstellungen das VPN verbinden."
)

/**
 * Kill-Switch: Jede Anfrage der App (API, Playlist, Streams, Bilder) wird blockiert,
 * solange kein VPN aktiv ist und die Option "VPN erzwingen" eingeschaltet ist.
 */
class VpnGuardInterceptor(
    private val vpn: () -> VpnManager,
    private val settings: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (settings.vpnRequired.value && !vpn().isProtected()) throw VpnRequiredException()
        return chain.proceed(chain.request())
    }
}
