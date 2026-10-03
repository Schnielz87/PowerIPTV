package com.poweriptv.desktop.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.player.renderer.RendererDiscoverer
import uk.co.caprica.vlcj.player.renderer.RendererDiscovererEventListener
import uk.co.caprica.vlcj.player.renderer.RendererItem

/**
 * „Auf Fernseher übertragen“ (wie Google Cast in der Android-App): VLC findet Chromecast-Geräte
 * (und Fernseher mit Chromecast built-in) im Heimnetz und spielt den Stream direkt dort ab.
 */
object CastDiscovery {
    private val _devices = MutableStateFlow<List<RendererItem>>(emptyList())
    val devices: StateFlow<List<RendererItem>> = _devices.asStateFlow()
    private val discoverers = mutableListOf<RendererDiscoverer>()

    @Synchronized
    fun start() {
        if (discoverers.isNotEmpty()) return
        val factory = Vlc.factory ?: return
        runCatching {
            factory.renderers().discoverers().forEach { d ->
                val disc = factory.renderers().discoverer(d.name()) ?: return@forEach
                disc.events().addRendererDiscovererEventListener(object : RendererDiscovererEventListener {
                    override fun rendererDiscovererItemAdded(rd: RendererDiscoverer, item: RendererItem) {
                        if (item.canVideo()) { item.hold(); _devices.value = _devices.value.filterNot { it.name() == item.name() } + item }
                    }
                    override fun rendererDiscovererItemDeleted(rd: RendererDiscoverer, item: RendererItem) {
                        _devices.value = _devices.value.filterNot { it.name() == item.name() }
                    }
                })
                if (disc.start()) discoverers += disc else disc.release()
            }
        }
    }

    @Synchronized
    fun release() {
        discoverers.forEach { runCatching { it.stop(); it.release() } }
        discoverers.clear()
    }
}
