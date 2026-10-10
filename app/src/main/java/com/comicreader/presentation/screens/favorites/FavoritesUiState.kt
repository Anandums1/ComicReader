package com.comicreader.presentation.screens.favorites

import com.comicreader.domain.model.Comic

/**
 * UI State for the Favorites Screen.
 */
data class FavoritesUiState(
    val comics: List<Comic> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
