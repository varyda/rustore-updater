package ru.app.rustoreupdater.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Route(val route: String, val label: String, val icon: ImageVector) {
    data object Tracked : Route("tracked", "Мои приложения", Icons.Outlined.Apps)
    data object Search : Route("search", "Поиск", Icons.Outlined.Search)
    data object Settings : Route("settings", "Настройки", Icons.Outlined.Settings)
    // Detail takes the numeric appId (path) and the package name (query) so it can
    // load overallInfo even for apps that aren't tracked yet.
    data object Detail : Route("detail/{appId}?packageName={packageName}", "", Icons.Outlined.Apps) {
        fun build(appId: String, packageName: String?): String =
            if (packageName != null) "detail/$appId?packageName=$packageName"
            else "detail/$appId?packageName="
        const val ARG_APP_ID = "appId"
        const val ARG_PACKAGE = "packageName"
        const val BASE = "detail"
    }
}

val bottomNavRoutes = listOf(Route.Tracked, Route.Search, Route.Settings)
