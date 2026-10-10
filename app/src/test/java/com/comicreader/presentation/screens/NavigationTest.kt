package com.comicreader.presentation.screens

import com.comicreader.presentation.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {

    @Test
    fun testScreenRoutes_matchExpectedPatterns() {
        assertEquals("library", Screen.Library.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("favorites", Screen.Favorites.route)
        assertEquals("history", Screen.History.route)
        assertEquals("stats", Screen.Stats.route)
        assertEquals("settings", Screen.Settings.route)
        assertEquals("detail/{comicId}", Screen.Detail.route)
        assertEquals("reader/{comicId}", Screen.Reader.route)
    }

    @Test
    fun testParameterizedRouteCreation_producesCorrectUri() {
        assertEquals("detail/42", Screen.Detail.createRoute(42L))
        assertEquals("reader/108", Screen.Reader.createRoute(108L))
    }
}
