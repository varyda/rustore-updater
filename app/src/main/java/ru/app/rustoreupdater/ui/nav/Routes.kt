package ru.app.rustoreupdater.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Route(val route: String, val label: String, val icon: ImageVector) {
    data object Feed : Route("feed", "Лента", Icons.Outlined.Explore)
    data object Tracked : Route("tracked", "Мои", Icons.Outlined.Apps)
    data object Search : Route("search", "Поиск", Icons.Outlined.Search)
    data object Settings : Route("settings", "Настройки", Icons.Outlined.Settings)
    // Full results for a feed category: reuses SearchScreen with a pre-filled, read-only query.
    data object SearchResults : Route("searchResults/{query}", "", Icons.Outlined.Search) {
        /**
         * Builds the route with [android.net.Uri.encode] rather than `URLEncoder`:
         * the latter writes spaces as `+`, which navigation does not turn back into
         * spaces, so a category like "социальные сети" would be searched literally.
         * [ru.app.rustoreupdater.ui.screens.SearchViewModel] also decodes defensively.
         */
        fun build(query: String): String = "searchResults/${android.net.Uri.encode(query)}"
        const val ARG_QUERY = "query"
    }
    // Detail takes the numeric appId (path) and the package name (query) so it can
    // load overallInfo even for apps that aren't tracked yet.
    data object Detail : Route("detail/{appId}?packageName={packageName}", "", Icons.Outlined.Apps) {
        fun build(appId: String, packageName: String?): String =
            if (packageName.isNullOrBlank()) "detail/$appId"
            else "detail/$appId?packageName=${android.net.Uri.encode(packageName)}"
        const val ARG_APP_ID = "appId"
        const val ARG_PACKAGE = "packageName"
        const val BASE = "detail"
    }
}

val bottomNavRoutes = listOf(Route.Feed, Route.Tracked, Route.Search, Route.Settings)
