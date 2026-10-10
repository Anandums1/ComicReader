package com.comicreader.presentation.screens.details

import com.comicreader.domain.model.Bookmark
import com.comicreader.domain.model.Comic

/**
 * UI State for Comic Detail Screen.
 */
data class ComicDetailUiState(
    val comic: Comic? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
