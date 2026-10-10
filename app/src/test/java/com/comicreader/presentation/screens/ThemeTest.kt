package com.comicreader.presentation.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.comicreader.domain.model.AppTheme
import com.comicreader.ui.theme.ComicBackground
import com.comicreader.ui.theme.ComicPrimary
import com.comicreader.ui.theme.ComicSecondary
import com.comicreader.ui.theme.ComicStyleColorScheme
import com.comicreader.ui.theme.DarkAmoledBackground
import com.comicreader.ui.theme.DarkAmoledColorScheme
import com.comicreader.ui.theme.DarkAmoledPrimary
import com.comicreader.ui.theme.DarkAmoledSecondary
import com.comicreader.ui.theme.LightBackground
import com.comicreader.ui.theme.LightColorScheme
import com.comicreader.ui.theme.LightPrimary
import com.comicreader.ui.theme.LightSecondary
import com.comicreader.ui.theme.comicHalftonePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeTest {

    @Test
    fun testDarkAmoledTheme_hasTrueBlackBackgroundAndVioletAccents() {
        assertEquals(Color(0xFF000000), DarkAmoledBackground)
        assertEquals(Color(0xFF7C4DFF), DarkAmoledPrimary)
        assertEquals(Color(0xFF00E5FF), DarkAmoledSecondary)

        assertEquals(Color(0xFF000000), DarkAmoledColorScheme.background)
        assertEquals(Color(0xFF7C4DFF), DarkAmoledColorScheme.primary)
        assertEquals(Color(0xFF00E5FF), DarkAmoledColorScheme.secondary)
    }

    @Test
    fun testLightTheme_hasPaperWhiteBackgroundAndCobaltBluePrimary() {
        assertEquals(Color(0xFFFAFAFA), LightBackground)
        assertEquals(Color(0xFF1976D2), LightPrimary)
        assertEquals(Color(0xFFFF4081), LightSecondary)

        assertEquals(Color(0xFFFAFAFA), LightColorScheme.background)
        assertEquals(Color(0xFF1976D2), LightColorScheme.primary)
        assertEquals(Color(0xFFFF4081), LightColorScheme.secondary)
    }

    @Test
    fun testComicStyleTheme_hasNewsprintBackgroundAndPopArtColors() {
        assertEquals(Color(0xFFFFFDF0), ComicBackground)
        assertEquals(Color(0xFFD32F2F), ComicPrimary)
        assertEquals(Color(0xFFFFD600), ComicSecondary)

        assertEquals(Color(0xFFFFFDF0), ComicStyleColorScheme.background)
        assertEquals(Color(0xFFD32F2F), ComicStyleColorScheme.primary)
        assertEquals(Color(0xFFFFD600), ComicStyleColorScheme.secondary)
    }

    @Test
    fun testComicHalftoneModifier_attachesCleanly() {
        val modifier = Modifier.comicHalftonePattern(
            dotColor = Color.Black.copy(alpha = 0.08f),
            dotRadius = 2.dp,
            spacing = 10.dp
        )
        assertNotNull(modifier)
    }

    @Test
    fun testAppThemeEnum_containsAllFourRequiredThemes() {
        val themes = AppTheme.entries
        assertEquals(4, themes.size)
        assertEquals(listOf(AppTheme.DARK, AppTheme.LIGHT, AppTheme.ADAPTIVE, AppTheme.COMIC), themes)
    }
}
