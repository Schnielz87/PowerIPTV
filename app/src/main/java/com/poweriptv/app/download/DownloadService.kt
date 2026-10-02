package com.poweriptv.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.poweriptv.app.PowerIptvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Vordergrund-Dienst, der die Download-Warteschlange abarbeitet. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    private val repo get() = (application as PowerIptvApp).container.downloads

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Download wird vorbereitet", null, 0f),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (worker?.isActive != true) {
            worker = scope.launch {
                while (true) {
                    val next = repo.nextQueued() ?: break
                    repo.run(next) { e ->
                        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(NOTIFICATION_ID, notification(e.title, e, e.progress))
                    }
                }
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(title: String, entry: DownloadEntry?, progress: Float) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(entry?.let { "${(progress * 100).toInt()} %" } ?: "")
            .setProgress(100, (progress * 100).toInt(), entry == null || entry.total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 4711
    }
}
