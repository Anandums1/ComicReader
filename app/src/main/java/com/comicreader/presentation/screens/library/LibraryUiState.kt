package com.comicreader.presentation.screens.library

import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.SortOrder

/**
 * UI State for the Comic Library Screen.
 */
data class LibraryUiState(
    val comics: List<Comic> = emptyList(),
    val sortOrder: SortOrder = SortOrder.TITLE,
    val isScanning: Boolean = false,
    val scanMessage: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
