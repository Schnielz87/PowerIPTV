package com.poweriptv.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

@Serializable
data class Reminder(
    val title: String,
    val channelName: String,
    /** Schluessel des Senders (ContentItem.key), damit er abgespielt werden kann. */
    val channelKey: String,
    val start: Long,
    val logo: String? = null,
)

/** EPG-Erinnerungen (5 Minuten vor Beginn) – wie in der Android-App. */
class ReminderStore {
    private val file = JsonFile(AppDirs.file("reminders.json"), ListSerializer(Reminder.serializer())) { emptyList() }
    private val _items = MutableStateFlow(file.read().filter { it.start > System.currentTimeMillis() - 3600_000L })
    val items: StateFlow<List<Reminder>> = _items.asStateFlow()

    fun has(channelName: String, start: Long) = _items.value.any { it.channelName == channelName && it.start == start }

    fun add(r: Reminder) {
        _items.update { list -> list.filterNot { it.channelName == r.channelName && it.start == r.start } + r }
        file.write(_items.value)
    }

    fun remove(channelName: String, start: Long) {
        _items.update { list -> list.filterNot { it.channelName == channelName && it.start == start } }
        file.write(_items.value)
    }

    /** Liefert faellige Erinnerungen (Beginn in <= 5 Minuten) und entfernt sie. */
    fun takeDue(now: Long = System.currentTimeMillis()): List<Reminder> {
        val due = _items.value.filter { it.start - now <= LEAD }
        if (due.isNotEmpty()) {
            _items.update { list -> list - due.toSet() }
            file.write(_items.value)
        }
        return due
    }

    companion object {
        const val LEAD = 5 * 60_000L
    }
}

/** Windows-Benachrichtigung unten rechts (Infobereich). */
object DesktopNotifier {
    private val tray: TrayIcon? by lazy {
        runCatching {
            if (!SystemTray.isSupported()) return@runCatching null
            val img = AppDirs::class.java.getResource("/tray_icon.png")?.let { Toolkit.getDefaultToolkit().getImage(it) }
                ?: return@runCatching null
            TrayIcon(img, "Portiva – PowerIPTV").apply { isImageAutoSize = true }.also { SystemTray.getSystemTray().add(it) }
        }.getOrNull()
    }

    fun show(title: String, text: String) {
        runCatching { tray?.displayMessage(title, text, TrayIcon.MessageType.INFO) }
    }

    fun dispose() {
        runCatching { tray?.let { SystemTray.getSystemTray().remove(it) } }
    }
}
