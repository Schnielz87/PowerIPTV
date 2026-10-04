package com.poweriptv.app

object BuildConfigInfo {
    const val VERSION = "1.0.0"

    /** Google-Play-Variante: Updates nur ueber den Play Store, kein geraeteweiter VPN-Tunnel (Play-Richtlinien). */
    val PLAY_STORE: Boolean = BuildConfig.PLAY_STORE

    /** Eigene Update-Funktion (APK von GitHub) und VPN gibt es nur in der GitHub-Variante. */
    val SELF_UPDATE: Boolean = !PLAY_STORE
    val VPN_AVAILABLE: Boolean = !PLAY_STORE
}
