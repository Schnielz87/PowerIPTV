package com.poweriptv.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Bundle
import com.poweriptv.app.data.Orientation
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poweriptv.app.ui.AppNavigation
import com.poweriptv.app.ui.theme.Background
import com.poweriptv.app.util.DeviceInfo
import com.poweriptv.app.util.LocalIsTv
import com.poweriptv.app.ui.components.SplashScreen
import com.poweriptv.app.ui.theme.PowerTheme
import com.poweriptv.app.vpn.VpnState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PLAY_URL = "play_url"
        const val EXTRA_PLAY_TITLE = "play_title"
    }

    private val container get() = (application as PowerIptvApp).container

    private var afterVpnPermission: (() -> Unit)? = null
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) afterVpnPermission?.invoke()
        afterVpnPermission = null
    }

    /** Fragt bei Bedarf die VPN-Berechtigung an und verbindet anschliessend. */
    fun connectVpn() {
        val intent = container.vpn.permissionIntent()
        val doConnect = { lifecycleScope.launch { container.vpn.connect() }; Unit }
        if (intent != null) {
            afterVpnPermission = doConnect
            vpnPermission.launch(intent)
        } else doConnect()
    }

    /** Im Querformat Status- und Navigationsleiste ausblenden (per Wischen wieder sichtbar). */
    private val isTv by lazy { DeviceInfo.isTv(this) }

    private fun applySystemBars(orientation: Int) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isTv || orientation == Configuration.ORIENTATION_LANDSCAPE) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars(newConfig.orientation)
    }

    /** Aus einer Erinnerung: Sender direkt starten. */
    private fun handlePlayIntent(intent: android.content.Intent?) {
        val url = intent?.getStringExtra(EXTRA_PLAY_URL) ?: return
        val title = intent.getStringExtra(EXTRA_PLAY_TITLE) ?: "Live TV"
        intent.removeExtra(EXTRA_PLAY_URL)
        com.poweriptv.app.ui.components.startPlayback(this, container, listOf(com.poweriptv.app.PlayEntry(title, url, null, live = true)), 0)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handlePlayIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(resources.configuration.orientation)
        if (savedInstanceState == null) handlePlayIntent(intent)
        container.prewarmVlc()
        // Bildschirmausrichtung aus den Einstellungen (Standard: Querformat)
        lifecycleScope.launch {
            container.settings.orientation.collect {
                // Fernseher: immer Querformat
                requestedOrientation = if (isTv) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else when (container.settings.orientationEnum()) {
                    Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    Orientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
            }
        }
        if (savedInstanceState == null &&
            container.settings.vpnAutoConnect.value &&
            container.vpn.hasConfig.value &&
            container.vpn.state.value != VpnState.CONNECTED
        ) {
            connectVpn()
        }
        setContent {
            PowerTheme {
                CompositionLocalProvider(LocalIsTv provides isTv) {
                    // TV: Sicherheitsrand gegen Overscan (auf manchen Fernsehern werden die Raender abgeschnitten)
                    val safeArea = if (isTv) Modifier.padding(horizontal = 24.dp, vertical = 12.dp) else Modifier
                    Box(Modifier.fillMaxSize().background(Background).then(safeArea)) {
                        var splash by rememberSaveable { mutableStateOf(true) }
                        if (splash) SplashScreen(onFinished = { splash = false })
                        else AppNavigation(container = container, onConnectVpn = ::connectVpn)
                        com.poweriptv.app.ui.components.ResumePromptDialog(container)
                    }
                }
            }
        }
    }
}
