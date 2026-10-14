package com.poweriptv.app.player

import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry

/**
 * Rettungsleiter, wenn ein Stream abbricht (Standard-Player und VLC):
 *  1.-2. neu verbinden
 *  3.-4. Live: anderes Format (Xtream .ts <-> .m3u8) bzw. Ausweich-Sender gleichen Namens (z.B. "Sky Sport HD" -> "Sky Sport HD Backup")
 *  5.    nochmal neu verbinden, danach Fehler – mit Hinweis, falls alle Verbindungen des Zugangs belegt sind.
 */
class StreamRescue(private val container: AppContainer) {
    sealed class Step {
        data class Reconnect(val attempt: Int) : Step()
        data class Format(val index: Int) : Step()
        data class Backup(val index: Int, val title: String) : Step()
        object GiveUp : Step()
    }

    var attempts = 0
        private set
    private val tried = mutableSetOf<String>()

    /** Nach erfolgreichem Start oder bei Senderwechsel durch den Nutzer. */
    fun reset() { attempts = 0; tried.clear() }

    /** Bild laeuft wieder -> Zaehler zuruecksetzen, aber bereits probierte Adressen merken. */
    fun recovered() { attempts = 0 }

    fun next(): Step {
        attempts++
        val queue = container.playQueue
        val i = container.playIndex
        val cur = queue.getOrNull(i) ?: return Step.GiveUp
        tried += cur.url
        if (cur.live && attempts in 3..4) {
            altFormatUrl(cur.url)?.takeIf { it !in tried }?.let { alt ->
                tried += alt
                container.playQueue = queue.toMutableList().also { it[i] = cur.copy(url = alt) }
                return Step.Format(i)
            }
            backupIndex(cur, queue)?.let { b -> tried += queue[b].url; return Step.Backup(b, queue[b].title) }
        }
        return if (attempts <= 5) Step.Reconnect(attempts) else Step.GiveUp
    }

    private fun backupIndex(cur: PlayEntry, queue: List<PlayEntry>): Int? {
        val key = StreamRules.baseName(cur.title).ifEmpty { return null }
        return queue.indices.firstOrNull { j ->
            val e = queue[j]
            e.live && e.url !in tried && e.url != cur.url && StreamRules.baseName(e.title) == key
        }
    }

    companion object {
        fun altFormatUrl(url: String): String? = StreamRules.altFormatUrl(url)

        /** Hinweis, wenn der Zugang gerade keine freie Verbindung hat. */
        suspend fun connectionsFullMessage(container: AppContainer): String? = runCatching {
            val info = container.source?.accountInfo() ?: return null
            StreamRules.connectionsFullText(info.activeConnections, info.maxConnections)
        }.getOrNull()

        fun message(step: Step): String? = when (step) {
            is Step.Reconnect -> StreamRules.msgReconnect(step.attempt)
            is Step.Format -> StreamRules.MSG_FORMAT
            is Step.Backup -> StreamRules.msgBackup(step.title)
            Step.GiveUp -> null
        }
    }
}
