package com.poweriptv.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.poweriptv.desktop.player.PlayerScreen
import com.poweriptv.desktop.player.Vlc
import com.poweriptv.desktop.ui.Background
import com.poweriptv.desktop.ui.BrandWordmark
import com.poweriptv.desktop.ui.NavRail
import com.poweriptv.desktop.ui.PortivaLogo
import com.poweriptv.desktop.ui.PowerTheme
import com.poweriptv.desktop.ui.screens.BrowseScreen
import com.poweriptv.desktop.ui.screens.DetailScreen
import com.poweriptv.desktop.ui.screens.FavoritesScreen
import com.poweriptv.desktop.ui.screens.HomeScreen
import com.poweriptv.desktop.ui.screens.ProfilesScreen
import com.poweriptv.desktop.ui.screens.SearchScreen
import com.poweriptv.desktop.ui.screens.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem

val AppVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

@Suppress("DEPRECATION")
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp), position = WindowPosition(Alignment.Center))
    val app = remember { AppState(windowState) }
    val icon = painterResource("app_logo.xml")
    val title = "Portiva – PowerIPTV"
    val quit = {
        app.shutdown()
        exitApplication()
    }
    // F11 schaltet ueberall ins Vollbild
    val globalKeys: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.F11) { app.toggleFullscreen(); true } else false
    }

    Window(onCloseRequest = quit, state = windowState, title = title, icon = icon, onPreviewKeyEvent = globalKeys) {
        LaunchedEffect(Unit) {
            app.mainWindow = window
            window.minimumSize = Dimension(980, 620)
            if (app.settings.value.startFullscreen) app.setFullscreen(true)
        }
        PowerTheme {
            Surface(color = Background, contentColor = Color.White) {
                // Im Vollbild zeigt das randlose Fenster den Inhalt (hier nur schwarz, damit nichts doppelt laeuft)
                if (!app.isFullscreen) Root(app) else Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }

    if (app.isFullscreen) {
        // Echtes Vollbild: randloses Fenster exakt ueber dem Bildschirm des Hauptfensters (deckt Titel- und Taskleiste ab)
        val screen = remember {
            (app.mainWindow?.graphicsConfiguration ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration).bounds
        }
        val fsState = rememberWindowState(
            position = WindowPosition(screen.x.dp, screen.y.dp),
            size = DpSize(screen.width.dp, screen.height.dp),
        )
        Window(
            onCloseRequest = { app.setFullscreen(false) },
            state = fsState, title = title, icon = icon,
            undecorated = true, resizable = false,
            onPreviewKeyEvent = globalKeys,
        ) {
            LaunchedEffect(Unit) {
                window.setBounds(screen)
                window.toFront()
                window.requestFocus()
            }
            PowerTheme {
                Surface(color = Background, contentColor = Color.White) { Root(app) }
            }
        }
    }
}

@Composable
private fun Root(app: AppState) {
    if (!app.splashDone) {
        SplashScreen(playSound = app.settings.value.introSound) { app.splashDone = true }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (app.profile != null) NavRail(app)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val protectedScreen = app.screen in listOf(Screen.Settings, Screen.Profiles, Screen.Vpn) && app.profile != null
                var settingsUnlocked by androidx.compose.runtime.remember(app.screen) { androidx.compose.runtime.mutableStateOf(false) }
                if (protectedScreen && !settingsUnlocked && app.parental.settingsNeedPin()) {
                    com.poweriptv.desktop.ui.PinDialog(app.parental, onDismiss = { app.back() }) { settingsUnlocked = true }
                } else when (val s = app.screen) {
                    Screen.Home -> HomeScreen(app)
                    is Screen.Browse -> BrowseScreen(app, s.type)
                    Screen.Favorites -> FavoritesScreen(app)
                    Screen.Search -> SearchScreen(app)
                    Screen.Settings -> SettingsScreen(app)
                    Screen.Profiles -> ProfilesScreen(app)
                    Screen.Epg -> com.poweriptv.desktop.ui.screens.EpgScreen(app)
                    Screen.Recordings -> com.poweriptv.desktop.ui.screens.RecordingsScreen(app)
                    Screen.Downloads -> com.poweriptv.desktop.ui.screens.DownloadsScreen(app)
                    Screen.Recommendations -> com.poweriptv.desktop.ui.screens.RecommendationsScreen(app)
                    Screen.Parental -> com.poweriptv.desktop.ui.screens.ParentalScreen(app)
                    Screen.Vpn -> com.poweriptv.desktop.ui.screens.VpnScreen(app)
                    Screen.MultiView -> com.poweriptv.desktop.ui.screens.MultiViewScreen(app)
                    is Screen.Detail -> DetailScreen(app, s.item)
                }
            }
        }
        app.playing?.let { req -> PlayerScreen(app, req) }
        app.listPickerFor?.let { item -> com.poweriptv.desktop.ui.ListPickerDialog(app, item) { app.listPickerFor = null } }
        app.pinRequest?.let { action ->
            com.poweriptv.desktop.ui.PinDialog(app.parental, onDismiss = { app.pinRequest = null }) { app.pinRequest = null; action() }
        }
        app.dueReminder?.let { r ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { app.dueReminder = null },
                title = { androidx.compose.material3.Text("Gleich auf ${r.channelName}") },
                text = { androidx.compose.material3.Text(r.title + " beginnt in wenigen Minuten.") },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = { app.dueReminder = null; app.playReminder(r) }) { androidx.compose.material3.Text("Jetzt ansehen") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { app.dueReminder = null }) { androidx.compose.material3.Text("Später") } },
            )
        }
    }
}

/** Startbildschirm mit Kino-Klang: Logo blendet waehrend des Anlaufs ein und springt auf den Schlag. */
@Composable
private fun SplashScreen(playSound: Boolean, onFinished: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (playSound) playIntroSound()
        alpha.animateTo(0.45f, tween(720))
        launch { alpha.animateTo(1f, tween(120)) }
        scale.animateTo(1.08f, tween(140))
        scale.animateTo(1f, tween(260))
        delay(1350)
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0F2A4D), Background), radius = 1600f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha.value).scale(scale.value)) {
            PortivaLogo(Modifier.size(150.dp))
            Spacer(Modifier.height(18.dp))
            BrandWordmark(large = true, centered = true)
        }
    }
}

private fun playIntroSound() {
    Thread {
        runCatching {
            val res = AppState::class.java.getResourceAsStream("/intro_sound.wav") ?: return@runCatching
            AudioSystem.getAudioInputStream(BufferedInputStream(res)).use { stream ->
                val clip = AudioSystem.getClip()
                clip.open(stream)
                clip.start()
                Thread.sleep(clip.microsecondLength / 1000 + 200)
                clip.close()
            }
        }
    }.apply { isDaemon = true }.start()
}

fun AppState.shutdown() {
    closePlayer()
    com.poweriptv.desktop.data.DesktopNotifier.dispose()
    Vlc.release()
}
