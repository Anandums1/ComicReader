package com.comicreader.presentation.screens.search

import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat

/**
 * UI State for the Search Screen.
 */
data class SearchUiState(
    val query: String = "",
    val selectedFormat: ComicFormat? = null, // null represents ALL formats
    val onlyFavorites: Boolean = false,
    val onlyUnread: Boolean = false,
    val results: List<Comic> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
