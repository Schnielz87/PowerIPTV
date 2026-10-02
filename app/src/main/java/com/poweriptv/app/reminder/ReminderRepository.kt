package com.poweriptv.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.poweriptv.app.MainActivity
import com.poweriptv.app.PowerIptvApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val channelName: String,
    val url: String,
    val start: Long,
    val logo: String? = null,
)

/**
 * EPG-Erinnerungen: 5 Minuten vor Sendungsbeginn eine Benachrichtigung
 * ("Jetzt ansehen" startet den Sender direkt).
 */
class ReminderRepository(private val context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(Reminder.serializer())
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Reminder>> = _items

    private fun load(): List<Reminder> = runCatching {
        json.decodeFromString(serializer, prefs.getString("list", null) ?: "[]")
    }.getOrDefault(emptyList()).filter { it.start > System.currentTimeMillis() - 3_600_000L }

    private fun save(list: List<Reminder>) {
        _items.value = list
        prefs.edit().putString("list", json.encodeToString(serializer, list)).apply()
    }

    fun idFor(channelName: String, start: Long) = "${channelName}|$start"
    fun has(channelName: String, start: Long) = _items.value.any { it.id == idFor(channelName, start) }

    fun add(title: String, channelName: String, url: String, start: Long, logo: String?) {
        val r = Reminder(idFor(channelName, start), title, channelName, url, start, logo)
        save(_items.value.filterNot { it.id == r.id } + r)
        arm(r)
    }

    fun remove(channelName: String, start: Long) {
        val id = idFor(channelName, start)
        save(_items.value.filterNot { it.id == id })
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarm(id))
    }

    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    /** Nach Neustart des Geraets erneut stellen. */
    fun rearmAll() = _items.value.forEach { arm(it) }

    private fun arm(r: Reminder) {
        val at = (r.start - LEAD).coerceAtLeast(System.currentTimeMillis() + 1000)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarm(r.id)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun alarm(id: String) = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notify(r: Reminder) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Erinnerungen", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            context, r.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_PLAY_URL, r.url)
                .putExtra(MainActivity.EXTRA_PLAY_TITLE, r.channelName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(r.start))
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Gleich: ${r.title}")
            .setContentText("${r.channelName} · $time Uhr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_play, "Jetzt ansehen", open)
            .build()
        runCatching { nm.notify(r.id.hashCode(), n) }
        save(_items.value.filterNot { it.id == r.id })
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        private const val CHANNEL = "reminders"
        /** Vorlauf: 5 Minuten vor Beginn erinnern. */
        const val LEAD = 5 * 60_000L
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderRepository.EXTRA_ID) ?: return
        val repo = (context.applicationContext as PowerIptvApp).container.reminders
        repo.get(id)?.let { repo.notify(it) }
    }
}
