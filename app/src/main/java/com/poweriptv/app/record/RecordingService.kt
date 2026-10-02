package com.poweriptv.app.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.poweriptv.app.PowerIptvApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Vordergrund-Dienst fuer laufende Aufnahmen (mehrere parallel moeglich). */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val container get() = (application as PowerIptvApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Aufnahmen", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Aufnahme wird gestartet"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        val id = intent?.getStringExtra(EXTRA_ID)
        if (id != null && jobs[id]?.isActive != true) {
            jobs[id] = scope.launch { record(id) }
        }
        if (jobs.values.none { it.isActive }) finish()
        return START_NOT_STICKY
    }

    private suspend fun record(id: String) {
        val repo = container.recordings
        val rec = repo.get(id) ?: return
        if (rec.status != RecStatus.SCHEDULED) return
        // Falls der Wecker etwas zu frueh kam
        val wait = rec.start - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        if (repo.get(id)?.status != RecStatus.SCHEDULED) return

        repo.patch(id) { it.copy(status = RecStatus.RECORDING, error = null) }
        updateNotification()
        val file = File(rec.filePath)
        var attempts = 0
        var lastUpdate = 0L
        try {
            FileOutputStream(file, true).use { out ->
                // Bei Abbruechen bis Sendungsende automatisch neu verbinden
                while (System.currentTimeMillis() < (repo.get(id)?.end ?: 0L)) {
                    try {
                        StreamCapture(container.http).capture(
                            url = rec.url,
                            out = out,
                            keepGoing = { System.currentTimeMillis() < (repo.get(id)?.end ?: 0L) },
                            onBytes = {
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 3000) {
                                    lastUpdate = now
                                    repo.patch(id) { r -> r.copy(bytes = file.length()) }
                                }
                            },
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (++attempts > 20) throw e
                        delay(3000)
                    }
                    repo.patch(id) { it.copy(bytes = file.length()) }
                }
            }
            repo.patch(id) { it.copy(status = RecStatus.COMPLETED, bytes = file.length()) }
        } catch (e: CancellationException) {
            repo.patch(id) { it.copy(status = RecStatus.COMPLETED, bytes = file.length()) }
        } catch (e: Exception) {
            repo.patch(id) {
                it.copy(
                    status = if (file.length() > 0) RecStatus.COMPLETED else RecStatus.FAILED,
                    error = e.message, bytes = file.length(),
                )
            }
        } finally {
            jobs.remove(id)
            if (jobs.values.none { it.isActive }) finish() else updateNotification()
        }
    }

    private fun updateNotification() {
        val running = container.recordings.entries.value.filter { it.status == RecStatus.RECORDING }
        val text = if (running.isEmpty()) "Aufnahme" else running.joinToString { "${it.channelName}: ${it.title}" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(text))
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("● Aufnahme laeuft")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "recording_id"
        private const val CHANNEL = "recordings"
        private const val NOTIFICATION_ID = 4712
    }
}

/** Wecker: startet eine geplante Aufnahme. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RecordingService.EXTRA_ID) ?: return
        (context.applicationContext as PowerIptvApp).container.recordings.startService(id)
    }
}

/** Nach einem Neustart des Geraets alle geplanten Aufnahmen neu stellen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            (context.applicationContext as PowerIptvApp).container.recordings.rearmAll()
        }
    }
}
