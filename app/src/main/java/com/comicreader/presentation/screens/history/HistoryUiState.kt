package com.comicreader.presentation.screens.history

import com.comicreader.domain.model.Comic

/**
 * UI State for the Reading History Screen.
 */
data class HistoryUiState(
    val comics: List<Comic> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
