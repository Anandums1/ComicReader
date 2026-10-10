package com.comicreader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 1. Dark AMOLED Theme Palette (True Black & Neon Accents)
val DarkAmoledBackground = Color(0xFF000000)
val DarkAmoledPrimary = Color(0xFF7C4DFF)        // Deep Violet Accent
val DarkAmoledOnPrimary = Color(0xFFFFFFFF)
val DarkAmoledPrimaryContainer = Color(0xFF311B92)
val DarkAmoledOnPrimaryContainer = Color(0xFFEDE7F6)
val DarkAmoledSecondary = Color(0xFF00E5FF)      // Cyber Cyan
val DarkAmoledOnSecondary = Color(0xFF000000)
val DarkAmoledSecondaryContainer = Color(0xFF006064)
val DarkAmoledOnSecondaryContainer = Color(0xFFE0F7FA)
val DarkAmoledSurface = Color(0xFF121212)        // Deep Charcoal
val DarkAmoledOnSurface = Color(0xFFEDEDED)
val DarkAmoledSurfaceVariant = Color(0xFF1E1E1E)
val DarkAmoledOnSurfaceVariant = Color(0xFFB0B0B0)
val DarkAmoledOnBackground = Color(0xFFEDEDED)
val DarkAmoledOutline = Color(0xFF2C2C2C)

val DarkAmoledColorScheme = darkColorScheme(
    primary = DarkAmoledPrimary,
    onPrimary = DarkAmoledOnPrimary,
    primaryContainer = DarkAmoledPrimaryContainer,
    onPrimaryContainer = DarkAmoledOnPrimaryContainer,
    secondary = DarkAmoledSecondary,
    onSecondary = DarkAmoledOnSecondary,
    secondaryContainer = DarkAmoledSecondaryContainer,
    onSecondaryContainer = DarkAmoledOnSecondaryContainer,
    background = DarkAmoledBackground,
    onBackground = DarkAmoledOnBackground,
    surface = DarkAmoledSurface,
    onSurface = DarkAmoledOnSurface,
    surfaceVariant = DarkAmoledSurfaceVariant,
    onSurfaceVariant = DarkAmoledOnSurfaceVariant,
    outline = DarkAmoledOutline
)

// 2. Light Theme Palette (Paper White & Cobalt Blue)
val LightBackground = Color(0xFFFAFAFA)          // Paper White
val LightPrimary = Color(0xFF1976D2)             // Cobalt Blue
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFBBDEFB)
val LightOnPrimaryContainer = Color(0xFF0D47A1)
val LightSecondary = Color(0xFFFF4081)           // Vivid Pink Accent
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFCE4EC)
val LightOnSecondaryContainer = Color(0xFF880E4F)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1A1A)
val LightSurfaceVariant = Color(0xFFF0F0F0)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOnBackground = Color(0xFF1A1A1A)
val LightOutline = Color(0xFFE0E0E0)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

// 3. Comic Style Halftone Palette (Vintage Newsprint & Action Colors)
val ComicBackground = Color(0xFFFFFDF0)          // Vintage Comic Newsprint Cream
val ComicPrimary = Color(0xFFD32F2F)             // Vintage Action Red
val ComicOnPrimary = Color(0xFFFFFFFF)
val ComicPrimaryContainer = Color(0xFFFFCDD2)
val ComicOnPrimaryContainer = Color(0xFFB71C1C)
val ComicSecondary = Color(0xFFFFD600)           // Comic Book Yellow
val ComicOnSecondary = Color(0xFF000000)
val ComicSecondaryContainer = Color(0xFFFFF9C4)
val ComicOnSecondaryContainer = Color(0xFFF57F17)
val ComicTertiary = Color(0xFF1976D2)            // Classic Hero Blue
val ComicOnTertiary = Color(0xFFFFFFFF)
val ComicSurface = Color(0xFFFFF8E7)             // Aged Paper
val ComicOnSurface = Color(0xFF000000)
val ComicSurfaceVariant = Color(0xFFFFE0B2)
val ComicOnSurfaceVariant = Color(0xFF212121)
val ComicOnBackground = Color(0xFF000000)        // Jet Black Ink
val ComicOutline = Color(0xFF000000)             // Bold Inked Outlines

val ComicStyleColorScheme = lightColorScheme(
    primary = ComicPrimary,
    onPrimary = ComicOnPrimary,
    primaryContainer = ComicPrimaryContainer,
    onPrimaryContainer = ComicOnPrimaryContainer,
    secondary = ComicSecondary,
    onSecondary = ComicOnSecondary,
    secondaryContainer = ComicSecondaryContainer,
    onSecondaryContainer = ComicOnSecondaryContainer,
    tertiary = ComicTertiary,
    onTertiary = ComicOnTertiary,
    background = ComicBackground,
    onBackground = ComicOnBackground,
    surface = ComicSurface,
    onSurface = ComicOnSurface,
    surfaceVariant = ComicSurfaceVariant,
    onSurfaceVariant = ComicOnSurfaceVariant,
    outline = ComicOutline
)
