package com.comicreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.comicreader.domain.model.AppTheme

/**
 * Main application theme wrapper for the Comic Book Reader.
 * Switches between Dark AMOLED, Light, Adaptive Material You, and Comic Halftone styles.
 */
@Composable
fun ComicReaderTheme(
    theme: AppTheme = AppTheme.DARK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme: ColorScheme = when (theme) {
        AppTheme.DARK -> DarkAmoledColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.COMIC -> ComicStyleColorScheme
        AppTheme.ADAPTIVE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                if (darkTheme) DarkAmoledColorScheme else LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
