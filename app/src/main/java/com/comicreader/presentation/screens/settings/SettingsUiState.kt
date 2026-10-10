package com.comicreader.presentation.screens.settings

import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode

/**
 * UI State for the Settings Screen.
 */
data class SettingsUiState(
    val theme: AppTheme = AppTheme.DARK,
    val readingMode: ReadingMode = ReadingMode.HORIZONTAL,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val pageAnimation: PageAnimation = PageAnimation.SLIDE,
    val volumeKeyNav: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val watchedDirectory: String = "/storage/emulated/0/Downloads/Comics"
)
