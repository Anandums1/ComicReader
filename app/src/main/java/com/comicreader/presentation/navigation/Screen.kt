package com.comicreader.presentation.navigation

/**
 * Type-safe destination routes for application navigation.
 */
sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Search : Screen("search")
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Stats : Screen("stats")
    data object Settings : Screen("settings")

    data object Detail : Screen("detail/{comicId}") {
        const val ARG_COMIC_ID = "comicId"
        fun createRoute(comicId: Long): String = "detail/$comicId"
    }

    data object Reader : Screen("reader/{comicId}") {
        const val ARG_COMIC_ID = "comicId"
        fun createRoute(comicId: Long): String = "reader/$comicId"
    }
}
