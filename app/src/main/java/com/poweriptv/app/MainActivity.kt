package com.poweriptv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.poweriptv.app.ui.AppNavigation
import com.poweriptv.app.ui.components.SplashScreen
import com.poweriptv.app.ui.theme.PowerTheme
import com.poweriptv.app.vpn.VpnState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null &&
            container.settings.vpnAutoConnect.value &&
            container.vpn.hasConfig.value &&
            container.vpn.state.value != VpnState.CONNECTED
        ) {
            connectVpn()
        }
        setContent {
            PowerTheme {
                var splash by rememberSaveable { mutableStateOf(true) }
                if (splash) SplashScreen(onFinished = { splash = false })
                else AppNavigation(container = container, onConnectVpn = ::connectVpn)
            }
        }
    }
}
