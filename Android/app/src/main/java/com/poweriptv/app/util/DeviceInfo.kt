package com.poweriptv.app.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/** Erkennt, ob die App auf einem Fernseher / TV-Stick laeuft. */
object DeviceInfo {
    @Volatile private var cached: Boolean? = null

    fun isTv(context: Context): Boolean = cached ?: run {
        val pm = context.packageManager
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
        val tv = uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("amazon.hardware.fire_tv") ||              // Amazon Fire TV
            (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
                pm.hasSystemFeature("android.hardware.type.television"))
        cached = tv
        tv
    }

    fun isFireTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
}

/** In Compose verfuegbar: laeuft die App auf einem TV? */
val LocalIsTv = staticCompositionLocalOf { false }
