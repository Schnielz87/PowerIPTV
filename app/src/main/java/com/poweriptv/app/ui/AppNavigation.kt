package com.poweriptv.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.screens.AddProfileScreen
import com.poweriptv.app.ui.screens.BrowseScreen
import com.poweriptv.app.ui.screens.DownloadsScreen
import com.poweriptv.app.ui.screens.FavoritesScreen
import com.poweriptv.app.ui.screens.HomeScreen
import com.poweriptv.app.ui.screens.MovieDetailScreen
import com.poweriptv.app.ui.screens.ProfilesScreen
import com.poweriptv.app.ui.screens.SeriesDetailScreen
import com.poweriptv.app.ui.screens.SettingsScreen
import com.poweriptv.app.ui.screens.VpnScreen

object Routes {
    const val PROFILES = "profiles"
    const val ADD_PROFILE = "addProfile?id={id}"
    const val HOME = "home"
    const val BROWSE = "browse/{type}"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val VPN = "vpn"
    const val DOWNLOADS = "downloads"

    fun addProfile(id: String? = null) = if (id == null) "addProfile" else "addProfile?id=$id"
    fun browse(type: ContentType) = "browse/${type.name}"
}

@Composable
fun AppNavigation(container: AppContainer, onConnectVpn: () -> Unit) {
    val nav = rememberNavController()
    val start = if (container.source != null) Routes.HOME else Routes.PROFILES

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.PROFILES) {
            ProfilesScreen(
                container = container,
                onSelected = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.PROFILES) { inclusive = true } }
                },
                onAdd = { nav.navigate(Routes.addProfile()) },
                onEdit = { nav.navigate(Routes.addProfile(it)) },
                onVpn = { nav.navigate(Routes.VPN) },
                onDownloads = { nav.navigate(Routes.DOWNLOADS) },
            )
        }
        composable(
            Routes.ADD_PROFILE,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            AddProfileScreen(
                container = container,
                editId = entry.arguments?.getString("id"),
                onDone = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpen = { nav.navigate(Routes.browse(it)) },
                onFavorites = { nav.navigate(Routes.FAVORITES) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onVpn = { nav.navigate(Routes.VPN) },
                onDownloads = { nav.navigate(Routes.DOWNLOADS) },
                onSwitchProfile = {
                    nav.navigate(Routes.PROFILES) { popUpTo(Routes.HOME) { inclusive = true } }
                },
            )
        }
        composable(Routes.BROWSE, arguments = listOf(navArgument("type") { type = NavType.StringType })) { entry ->
            val type = ContentType.valueOf(entry.arguments?.getString("type") ?: ContentType.LIVE.name)
            BrowseScreen(
                container = container,
                type = type,
                onBack = { nav.popBackStack() },
                onOpenDetail = { item ->
                    container.selectedItem = item
                    nav.navigate(if (item.type == ContentType.SERIES) Routes.SERIES else Routes.MOVIE)
                },
            )
        }
        composable(Routes.MOVIE) { MovieDetailScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.SERIES) { SeriesDetailScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.FAVORITES) {
            FavoritesScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenDetail = { item ->
                    container.selectedItem = item
                    nav.navigate(if (item.type == ContentType.SERIES) Routes.SERIES else Routes.MOVIE)
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container, onBack = { nav.popBackStack() }, onVpn = { nav.navigate(Routes.VPN) })
        }
        composable(Routes.DOWNLOADS) { DownloadsScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.VPN) {
            VpnScreen(container, onBack = { nav.popBackStack() }, onConnect = onConnectVpn)
        }
    }
}
