package com.poweriptv.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Bild-in-Bild (Mini-Fenster) fuer beide Player: Beim Verlassen der App (Home-Taste/-Geste, "Kreis")
 * laeuft das Video in einem kleinen Fenster weiter. Ab Android 12 automatisch und ruckelfrei.
 */
object Pip {
    fun supported(activity: Activity, isTv: Boolean): Boolean =
        !isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Hat der Nutzer Bild-in-Bild fuer die App in den Android-Einstellungen ausgeschaltet? */
    fun allowed(activity: Activity): Boolean = runCatching {
        val ops = activity.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val uid = android.os.Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, uid, activity.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, uid, activity.packageName)
        mode == android.app.AppOpsManager.MODE_ALLOWED || mode == android.app.AppOpsManager.MODE_DEFAULT
    }.getOrDefault(true)

    /** Beim Verlassen ("Kreis"/Home): ins Mini-Fenster – oder Hinweis, falls es in den Einstellungen ausgeschaltet ist. */
    fun onLeave(activity: Activity, isTv: Boolean, width: Int, height: Int, live: Boolean = false) {
        if (!supported(activity, isTv)) return
        if (!allowed(activity)) {
            android.widget.Toast.makeText(activity,
                "Bild-in-Bild ist für PowerIPTV ausgeschaltet – Einstellungen → Apps → PowerIPTV → Bild-in-Bild einschalten",
                android.widget.Toast.LENGTH_LONG).show()
            return
        }
        // Ab Android 12 sollte autoEnter das erledigen; manche Hersteller (z.B. bei Gestensteuerung) loesen es aber
        // nicht aus -> zusaetzlich direkt umschalten (ist es schon unterwegs, passiert nichts doppelt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode) return
        enter(activity, isTv, width, height, live)
    }

    /** Seitenverhaeltnis des Videos (Android erlaubt nur 1:2,39 bis 2,39:1). */
    private fun ratio(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) return Rational(16, 9)
        val r = width.toFloat() / height
        return when {
            r > 2.39f -> Rational(239, 100)
            r < 1 / 2.39f -> Rational(100, 239)
            else -> Rational(width, height)
        }
    }

    /** Einstellungen hinterlegen: autoEnter = beim Verlassen automatisch ins Mini-Fenster (nur waehrend der Wiedergabe). */
    fun update(activity: Activity, isTv: Boolean, width: Int, height: Int, autoEnter: Boolean, playing: Boolean = true, live: Boolean = false) {
        if (!supported(activity, isTv)) return
        runCatching { activity.setPictureInPictureParams(params(activity, width, height, autoEnter, playing, live)) }
    }

    // ---------- Knoepfe im Mini-Fenster: 10 s zurueck, Pause/Weiter, 10 s vor (Live: nur Pause/Weiter) ----------
    const val ACTION = "com.poweriptv.app.PIP_CONTROL"
    const val EXTRA_CMD = "cmd"
    const val CMD_BACK = 1
    const val CMD_TOGGLE = 2
    const val CMD_FORWARD = 3

    private fun action(activity: Activity, cmd: Int, icon: Int, title: String): android.app.RemoteAction {
        val intent = android.content.Intent(ACTION).setPackage(activity.packageName).putExtra(EXTRA_CMD, cmd)
        val pi = android.app.PendingIntent.getBroadcast(
            activity, cmd, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(activity, icon), title, title, pi)
    }

    private fun actions(activity: Activity, playing: Boolean, live: Boolean): List<android.app.RemoteAction> {
        val toggle = if (playing) action(activity, CMD_TOGGLE, android.R.drawable.ic_media_pause, "Pause")
        else action(activity, CMD_TOGGLE, android.R.drawable.ic_media_play, "Weiter")
        if (live) return listOf(toggle)
        return listOf(
            action(activity, CMD_BACK, android.R.drawable.ic_media_rew, "10 Sekunden zurück"),
            toggle,
            action(activity, CMD_FORWARD, android.R.drawable.ic_media_ff, "10 Sekunden vor"),
        )
    }

    /** Empfaenger fuer die Knoepfe im Mini-Fenster registrieren (in onCreate; Rueckgabe in onDestroy abmelden). */
    fun register(activity: Activity, onCommand: (Int) -> Unit): android.content.BroadcastReceiver {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                if (i?.action == ACTION) onCommand(i.getIntExtra(EXTRA_CMD, 0))
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            activity, r, android.content.IntentFilter(ACTION), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return r
    }

    /** Sofort ins Mini-Fenster (Android 8–11; ab 12 erledigt das autoEnter). */
    fun enter(activity: Activity, isTv: Boolean, width: Int, height: Int, live: Boolean = false) {
        if (!supported(activity, isTv)) return
        runCatching { activity.enterPictureInPictureMode(params(activity, width, height, true, true, live)) }
    }

    private fun params(activity: Activity, width: Int, height: Int, autoEnter: Boolean, playing: Boolean, live: Boolean): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio(width, height)).setActions(actions(activity, playing, live))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        return b.build()
    }
}
