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
    fun onLeave(activity: Activity, isTv: Boolean, width: Int, height: Int) {
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
        enter(activity, isTv, width, height)
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
    fun update(activity: Activity, isTv: Boolean, width: Int, height: Int, autoEnter: Boolean) {
        if (!supported(activity, isTv)) return
        runCatching { activity.setPictureInPictureParams(params(width, height, autoEnter)) }
    }

    /** Sofort ins Mini-Fenster (Android 8–11; ab 12 erledigt das autoEnter). */
    fun enter(activity: Activity, isTv: Boolean, width: Int, height: Int) {
        if (!supported(activity, isTv)) return
        runCatching { activity.enterPictureInPictureMode(params(width, height, true)) }
    }

    private fun params(width: Int, height: Int, autoEnter: Boolean): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio(width, height))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        return b.build()
    }
}
