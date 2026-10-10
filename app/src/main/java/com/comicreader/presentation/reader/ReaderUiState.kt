package com.comicreader.presentation.reader

import com.comicreader.domain.model.Bookmark
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicPage
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode

/**
 * UI State for the Comic Reader Screen.
 */
data class ReaderUiState(
    val comic: Comic? = null,
    val pages: List<ComicPage> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val readingMode: ReadingMode = ReadingMode.HORIZONTAL,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val pageAnimation: PageAnimation = PageAnimation.SLIDE,
    val isOverlayVisible: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val isCurrentPageBookmarked: Boolean = false,
    val zoomScale: Float = 1.0f,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val keepScreenAwake: Boolean = true,
    val brightness: Float = -1.0f,
    val isBookmarksPanelVisible: Boolean = false
) {
    val currentComicPage: ComicPage?
        get() = pages.getOrNull(currentPage)

    val progress: Float
        get() = if (totalPages > 1) {
            currentPage.toFloat() / (totalPages - 1).coerceAtLeast(1)
        } else if (totalPages == 1 && currentPage >= 1) {
            1f
        } else {
            0f
        }

    val isFirstPage: Boolean
        get() = currentPage <= 0

    val isLastPage: Boolean
        get() = totalPages > 0 && currentPage >= (totalPages - 1)
}

