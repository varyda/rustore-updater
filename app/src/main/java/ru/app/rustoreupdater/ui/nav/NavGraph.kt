package ru.app.rustoreupdater.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.app.rustoreupdater.ui.screens.DetailScreen
import ru.app.rustoreupdater.ui.screens.FeedScreen
import ru.app.rustoreupdater.ui.screens.SearchScreen
import ru.app.rustoreupdater.ui.screens.SettingsScreen
import ru.app.rustoreupdater.ui.screens.TrackedScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBottomBar = currentRoute in bottomNavRoutes.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavRoutes.forEach { route ->
                        NavigationBarItem(
                            selected = currentRoute == route.route,
                            onClick = {
                                nav.navigate(route.route) {
                                    popUpTo(Route.Feed.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(route.icon, contentDescription = route.label) },
                            label = { Text(route.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Route.Feed.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Route.Feed.route) {
                FeedScreen(
                    onOpenDetail = { id, pkg -> nav.navigate(Route.Detail.build(id, pkg)) },
                    onOpenAll = { query -> nav.navigate(Route.SearchResults.build(query)) },
                )
            }
            composable(Route.Tracked.route) {
                TrackedScreen(onOpenDetail = { id, pkg -> nav.navigate(Route.Detail.build(id, pkg)) })
            }
            composable(Route.Search.route) {
                SearchScreen(onOpenDetail = { id, pkg -> nav.navigate(Route.Detail.build(id, pkg)) })
            }
            composable(
                route = Route.SearchResults.route,
                arguments = listOf(
                    navArgument(Route.SearchResults.ARG_QUERY) { type = NavType.StringType },
                ),
            ) {
                SearchScreen(
                    onOpenDetail = { id, pkg -> nav.navigate(Route.Detail.build(id, pkg)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Route.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Route.Detail.route,
                arguments = listOf(
                    navArgument(Route.Detail.ARG_APP_ID) { type = NavType.StringType },
                    navArgument(Route.Detail.ARG_PACKAGE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                DetailScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
