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
