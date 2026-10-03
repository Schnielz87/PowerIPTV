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
import com.poweriptv.app.ui.screens.EpgGridScreen
import com.poweriptv.app.ui.screens.ParentalScreen
import com.poweriptv.app.ui.screens.RecommendationsScreen
import com.poweriptv.app.ui.screens.RecordingsScreen
import com.poweriptv.app.ui.screens.SearchScreen
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
    const val UPDATE = "update"
    const val VPN = "vpn"
    const val DOWNLOADS = "downloads"
    const val EPG = "epg"
    const val RECORDINGS = "recordings"
    const val PARENTAL = "parental"
    const val RECOMMENDATIONS = "recommendations"
    const val SEARCH = "search"
    const val RECENT = "recent/{type}"

    fun addProfile(id: String? = null) = if (id == null) "addProfile" else "addProfile?id=$id"
    fun browse(type: ContentType) = "browse/${type.name}"
    fun recent(type: ContentType) = "recent/${type.name}"
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
                    // Startseite mit dem neuen Zugang frisch aufbauen (alter Verlauf wird verworfen)
                    nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
                },
                onBack = { nav.popBackStack() },
                canGoBack = container.source != null,
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
                onUpdate = { nav.navigate(Routes.UPDATE) },
                onVpn = { nav.navigate(Routes.VPN) },
                onDownloads = { nav.navigate(Routes.DOWNLOADS) },
                onEpg = { nav.navigate(Routes.EPG) },
                onRecordings = { nav.navigate(Routes.RECORDINGS) },
                onRecommendations = { nav.navigate(Routes.RECOMMENDATIONS) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onOpenRecent = { nav.navigate(Routes.recent(it)) },
                onOpenDetail = { item ->
                    container.selectedItem = item
                    nav.navigate(if (item.type == ContentType.SERIES) Routes.SERIES else Routes.MOVIE)
                },
                // Startseite bleibt im Verlauf -> mit "Zurueck" kommt man wieder hierher
                onSwitchProfile = { nav.navigate(Routes.PROFILES) },
                onProfileSwitched = {
                    nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
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
        composable(Routes.RECENT, arguments = listOf(navArgument("type") { type = NavType.StringType })) { entry ->
            val type = ContentType.valueOf(entry.arguments?.getString("type") ?: ContentType.MOVIE.name)
            com.poweriptv.app.ui.screens.RecentScreen(
                container = container,
                initialType = type,
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
        composable(Routes.UPDATE) {
            com.poweriptv.app.ui.screens.UpdateScreen(container, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container,
                onBack = { nav.popBackStack() },
                onVpn = { nav.navigate(Routes.VPN) },
                onParental = { nav.navigate(Routes.PARENTAL) },
                onRecordings = { nav.navigate(Routes.RECORDINGS) },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(container, onBack = { nav.popBackStack() }, onOpenDetail = { item ->
                container.selectedItem = item
                nav.navigate(if (item.type == ContentType.SERIES) Routes.SERIES else Routes.MOVIE)
            })
        }
        composable(Routes.EPG) { EpgGridScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.RECORDINGS) { RecordingsScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.PARENTAL) { ParentalScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.RECOMMENDATIONS) {
            RecommendationsScreen(
                container,
                onBack = { nav.popBackStack() },
                onOpenDetail = { item ->
                    container.selectedItem = item
                    nav.navigate(if (item.type == ContentType.SERIES) Routes.SERIES else Routes.MOVIE)
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.DOWNLOADS) { DownloadsScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.VPN) {
            VpnScreen(container, onBack = { nav.popBackStack() }, onConnect = onConnectVpn)
        }
    }
}
