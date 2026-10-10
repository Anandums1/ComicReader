package com.comicreader.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comicreader.presentation.reader.ReaderScreen
import com.comicreader.presentation.screens.details.ComicDetailScreen
import com.comicreader.presentation.screens.favorites.FavoritesScreen
import com.comicreader.presentation.screens.history.HistoryScreen
import com.comicreader.presentation.screens.library.LibraryScreen
import com.comicreader.presentation.screens.search.SearchScreen
import com.comicreader.presentation.screens.settings.SettingsScreen
import com.comicreader.presentation.screens.stats.StatsScreen

/**
 * Top-level application navigation graph linking Library, Reader,
 * Details, Search, Favorites, History, Stats, and Settings screens.
 */
@Composable
fun ComicReaderNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Library.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 1. Library Screen
        composable(route = Screen.Library.route) {
            LibraryScreen(
                onNavigateToComicDetail = { comicId ->
                    navController.navigate(Screen.Detail.createRoute(comicId))
                },
                onNavigateToReader = { comicId ->
                    navController.navigate(Screen.Reader.createRoute(comicId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.Favorites.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToStats = {
                    navController.navigate(Screen.Stats.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // 2. Comic Detail Screen
        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument(Screen.Detail.ARG_COMIC_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val comicId = backStackEntry.arguments?.getLong(Screen.Detail.ARG_COMIC_ID) ?: 0L
            ComicDetailScreen(
                comicId = comicId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReader = { id ->
                    navController.navigate(Screen.Reader.createRoute(id))
                }
            )
        }

        // 3. Comic Reader Screen
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument(Screen.Reader.ARG_COMIC_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val comicId = backStackEntry.arguments?.getLong(Screen.Reader.ARG_COMIC_ID) ?: 0L
            ReaderScreen(
                comicId = comicId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 4. Search Screen
        composable(route = Screen.Search.route) {
            SearchScreen(
                onNavigateToComicDetail = { comicId ->
                    navController.navigate(Screen.Detail.createRoute(comicId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 5. Favorites Screen
        composable(route = Screen.Favorites.route) {
            FavoritesScreen(
                onNavigateToComicDetail = { comicId ->
                    navController.navigate(Screen.Detail.createRoute(comicId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 6. History Screen
        composable(route = Screen.History.route) {
            HistoryScreen(
                onNavigateToComicDetail = { comicId ->
                    navController.navigate(Screen.Detail.createRoute(comicId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 7. Stats Dashboard Screen
        composable(route = Screen.Stats.route) {
            StatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 8. Settings Screen
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
