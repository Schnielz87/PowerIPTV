package com.poweriptv.app.player

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.record.RecStatus
import com.poweriptv.app.record.Recording
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.Danger
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Aufnahme + gleichzeitiges Schauen mit nur EINER Verbindung zum Anbieter:
 * Laeuft fuer den Sender eine Aufnahme, spielt der Player aus der wachsenden Aufnahmedatei
 * (statt eine zweite Verbindung zu oeffnen, die der Anbieter sonst abwuergt).
 */
object RecordingLive {
    /** Kurz vor dem aktuellen Ende der Datei beginnen (~ wenige Sekunden hinter "live"), an TS-Pakete ausgerichtet. */
    fun liveOffset(file: File, back: Long = 3L * 1024 * 1024): Long {
        val len = file.length()
        val off = (len - back).coerceAtLeast(0)
        return off - off % 188
    }

    fun isGrowing(container: AppContainer, id: String) =
        container.recordings.get(id)?.status == RecStatus.RECORDING

    /** Wartet (max. ~15 s), bis die Aufnahme laeuft und Daten geschrieben wurden. */
    suspend fun awaitData(container: AppContainer, url: String): Recording? {
        repeat(75) {
            val rec = container.recordings.activeFor(url)
            if (rec != null && File(rec.filePath).length() > 256 * 1024) return rec
            kotlinx.coroutines.delay(200)
        }
        return container.recordings.activeFor(url)?.takeIf { File(it.filePath).exists() }
    }

    /** Pipe fuer VLC: liest die wachsende Datei und reicht die Daten weiter. */
    fun pipe(container: AppContainer, rec: Recording): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createPipe()
        Thread {
            runCatching {
                RandomAccessFile(File(rec.filePath), "r").use { raf ->
                    raf.seek(liveOffset(File(rec.filePath)))
                    FileOutputStream(write.fileDescriptor).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var idle = 0
                        while (true) {
                            val n = raf.read(buf)
                            if (n > 0) { out.write(buf, 0, n); idle = 0; continue }
                            if (!isGrowing(container, rec.id) || idle > 15_000) break
                            Thread.sleep(50); idle += 50
                        }
                    }
                }
            }
            runCatching { write.close() }
        }.apply { isDaemon = true; name = "rec-pipe" }.start()
        return read
    }

    /**
     * Sender wechseln, waehrend Aufnahmen laufen: reicht die Verbindungszahl des Accounts nicht,
     * liefert dies einen Hinweis (statt dass alle Streams stehen bleiben).
     */
    fun blockedBy(container: AppContainer, entry: PlayEntry): Recording? {
        if (!entry.live) return null
        val running = container.recordings.running()
        if (running.isEmpty() || running.any { it.url == entry.url }) return null
        val max = container.maxConnections ?: return null
        return running.takeIf { it.size >= max }?.first()
    }

    fun blockedMessage(container: AppContainer, rec: Recording): String {
        val max = container.maxConnections ?: 1
        return "Dein Zugang erlaubt nur $max gleichzeitige Verbindung${if (max == 1) "" else "en"}. " +
            "Sie wird gerade fuer die Aufnahme von \"${rec.channelName}\" genutzt.\n\n" +
            "Waehrend der Aufnahme kannst du diesen Sender schauen – oder die Aufnahme stoppen."
    }
}

/** Dialog "Aufnahme starten" (Live-TV). Laeuft schon eine Aufnahme des Senders: "Aufnahme stoppen". */
@Composable
fun RecordDialog(
    container: AppContainer,
    entry: PlayEntry?,
    onMessage: (String) -> Unit,
    onStarted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val item = entry?.item
    val now = System.currentTimeMillis()
    val active = entry?.let { container.recordings.activeFor(it.url) }
    val currentProgramme = item?.let { container.epg.current(it, now) }
    fun start(minutes: Int?) {
        if (entry == null) return
        onStarted() // erst die Live-Verbindung freigeben, dann nimmt die Aufnahme sie
        onMessage(
            if (minutes == null && currentProgramme != null) {
                container.recordings.schedule(currentProgramme.title, entry.title, entry.url, now, currentProgramme.end, item?.logo)
            } else {
                container.recordings.recordNow(entry.title, entry.title, entry.url, minutes ?: 60, item?.logo)
            },
        )
        onDismiss()
    }
    val btn = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (active != null) "● Aufnahme laeuft" else "Aufnahme starten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active != null) {
                    Text("\"${active.title}\" wird aufgenommen.")
                    Button(
                        onClick = { container.recordings.stop(active.id); onMessage("Aufnahme gestoppt und gespeichert"); onDismiss() },
                        modifier = btn,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    ) { Text("■ Aufnahme stoppen") }
                } else {
                    if (currentProgramme != null) {
                        Button(modifier = btn, onClick = { start(null) }) { Text("Bis Sendungsende: ${currentProgramme.title}", maxLines = 1) }
                    }
                    listOf(30, 60, 120, 180).forEach { m ->
                        OutlinedButton(modifier = btn, onClick = { start(m) }) { Text("$m Minuten") }
                    }
                }
            }
        },
        confirmButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50)), onClick = onDismiss) { Text(if (active != null) "Weiter schauen" else "Abbrechen") } },
    )
}
