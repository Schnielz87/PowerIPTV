package com.poweriptv.desktop.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.LinuxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.OsxNativeDiscoveryStrategy
import uk.co.caprica.vlcj.factory.discovery.strategy.WindowsNativeDiscoveryStrategy
import java.io.File

/**
 * Der Windows-Installer bringt VLC (libvlc.dll + plugins) im App-Ordner mit ("vlc"),
 * damit nichts zusaetzlich installiert werden muss. Fallback: installiertes VLC.
 */
object Vlc {
    @Volatile var error: String? = null
        private set

    private fun bundledDir(): File? {
        val res = System.getProperty("compose.application.resources.dir")?.let(::File)
        val candidates = listOfNotNull(
            res?.let { File(it, "vlc") },
            File(System.getProperty("user.dir"), "vlc"),
        )
        return candidates.firstOrNull { File(it, "libvlc.dll").exists() || File(it, "libvlc.so").exists() }
    }

    private class Bundled(private val dir: File) : NativeDiscoveryStrategy {
        private val windows = WindowsNativeDiscoveryStrategy()
        override fun supported() = true
        override fun discover(): String = dir.absolutePath
        override fun onFound(path: String) = true
        override fun onSetPluginPath(path: String) = if (windows.supported()) windows.onSetPluginPath(path) else true
    }

    private fun create(): MediaPlayerFactory? =
        runCatching {
            val strategies = listOfNotNull<NativeDiscoveryStrategy>(
                bundledDir()?.let(::Bundled),
                WindowsNativeDiscoveryStrategy(), LinuxNativeDiscoveryStrategy(), OsxNativeDiscoveryStrategy(),
            )
            if (!NativeDiscovery(*strategies.toTypedArray()).discover()) {
                error = "VLC-Bibliothek nicht gefunden"
                return@runCatching null
            }
            MediaPlayerFactory(
                "--no-video-title-show",
                "--no-snapshot-preview",
                "--no-osd",
                "--quiet",
                "--audio-language=de,deu,ger",
                "--http-reconnect",
            )
        }.onFailure { error = it.message ?: it.toString() }.getOrNull()

    private val lazyFactory = lazy { create() }
    val factory: MediaPlayerFactory? get() = lazyFactory.value

    private val players = java.util.Collections.synchronizedSet(LinkedHashSet<PlayerController>())
    fun register(p: PlayerController) { players += p }
    fun unregister(p: PlayerController) { players -= p }

    /** Beim Beenden: erst alle Player, dann die VLC-Instanz freigeben. */
    fun release() {
        players.toList().forEach { it.release() }
        CastDiscovery.release()
        if (lazyFactory.isInitialized()) runCatching { lazyFactory.value?.release() }
    }
}
