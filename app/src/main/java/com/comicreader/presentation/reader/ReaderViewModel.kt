package com.comicreader.presentation.reader

import android.view.KeyEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import com.comicreader.domain.repository.BookmarkRepository
import com.comicreader.domain.repository.ComicRepository
import com.comicreader.domain.repository.StatsRepository
import com.comicreader.format.ComicParser
import com.comicreader.di.MainDispatcher
import com.comicreader.format.ComicParserFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val comicRepository: ComicRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val statsRepository: StatsRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val comicParserFactory: ComicParserFactory,
    savedStateHandle: SavedStateHandle,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState(isLoading = true))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentComicId: Long = 0L
    private var activeParser: ComicParser? = null
    private var volumeKeyNavEnabled: Boolean = true
    private val sessionStartTime: Long = System.currentTimeMillis()
    private val pagesReadInSession: MutableSet<Int> = mutableSetOf()
    private var pagesTurnedCount: Int = 0

    init {
        val rawId = savedStateHandle.get<Any>("comicId")
        val initialId = when (rawId) {
            is Long -> rawId
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }

        if (initialId != null && initialId > 0L) {
            loadComic(initialId)
        }

        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch(mainDispatcher) {
            userPreferencesDataStore.preferencesFlow.collectLatest { prefs ->
                volumeKeyNavEnabled = prefs.volumeKeyNav
                _uiState.update { current ->
                    current.copy(
                        readingMode = prefs.readingMode,
                        pageAnimation = prefs.pageTurnAnimation,
                        readingDirection = prefs.readingDirection,
                        keepScreenAwake = prefs.keepScreenAwake,
                        brightness = prefs.brightness
                    )
                }
            }
        }
    }

    fun loadComic(comicId: Long) {
        currentComicId = comicId
        pagesReadInSession.clear()
        pagesTurnedCount = 0

        viewModelScope.launch(mainDispatcher) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val comic = comicRepository.getComicByIdOnce(comicId)
                if (comic == null) {
                    currentComicId = 0L
                    _uiState.update {
                        it.copy(
                            comic = null,
                            isLoading = false,
                            errorMessage = "Comic not found (id: $comicId)"
                        )
                    }
                    return@launch
                }

                activeParser = comicParserFactory.getParser(comic.filePath)

                val initialPage = comic.currentPage.coerceAtLeast(0)
                pagesReadInSession.add(initialPage)

                _uiState.update {
                    it.copy(
                        comic = comic,
                        currentPage = initialPage,
                        totalPages = comic.pageCount.coerceAtLeast(1),
                        isLoading = false
                    )
                }

                // Observe bookmarks for this comic
                launch(mainDispatcher) {
                    bookmarkRepository.getBookmarksForComic(comicId).collectLatest { bookmarks ->
                        _uiState.update { state ->
                            state.copy(
                                bookmarks = bookmarks,
                                isCurrentPageBookmarked = bookmarks.any { it.pageNumber == state.currentPage }
                            )
                        }
                    }
                }

                // Load pages via active parser if physical file exists
                val parser = activeParser
                if (parser != null) {
                    val file = File(comic.filePath)
                    if (file.exists() && file.length() > 0L) {
                        parser.getPages(file).collectLatest { pages ->
                            _uiState.update { state ->
                                val total = if (pages.isNotEmpty()) pages.size else state.totalPages
                                val safeCurrent = state.currentPage.coerceIn(0, (total - 1).coerceAtLeast(0))
                                state.copy(
                                    pages = pages,
                                    totalPages = total,
                                    currentPage = safeCurrent,
                                    isCurrentPageBookmarked = state.bookmarks.any { it.pageNumber == safeCurrent }
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                currentComicId = 0L
                _uiState.update {
                    it.copy(
                        comic = null,
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load comic"
                    )
                }
            }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val total = _uiState.value.totalPages
        val maxPage = (total - 1).coerceAtLeast(0)
        val clampedPage = pageIndex.coerceIn(0, maxPage)

        val previousPage = _uiState.value.currentPage
        if (clampedPage != previousPage) {
            pagesTurnedCount++
            pagesReadInSession.add(clampedPage)
        }

        _uiState.update { current ->
            current.copy(
                currentPage = clampedPage,
                isCurrentPageBookmarked = current.bookmarks.any { it.pageNumber == clampedPage }
            )
        }

        val comicId = currentComicId
        if (comicId > 0L && _uiState.value.comic != null) {
            viewModelScope.launch(mainDispatcher) {
                comicRepository.updateReadingProgress(
                    comicId = comicId,
                    page = clampedPage,
                    totalPages = total
                )
            }
        }
    }

    fun nextPage() {
        val maxPage = (_uiState.value.totalPages - 1).coerceAtLeast(0)
        val target = (_uiState.value.currentPage + 1).coerceAtMost(maxPage)
        onPageChanged(target)
    }

    fun previousPage() {
        val target = (_uiState.value.currentPage - 1).coerceAtLeast(0)
        onPageChanged(target)
    }

    fun jumpToPage(pageIndex: Int) {
        onPageChanged(pageIndex)
    }

    fun toggleOverlay() {
        _uiState.update { it.copy(isOverlayVisible = !it.isOverlayVisible) }
    }

    fun setOverlayVisible(visible: Boolean) {
        _uiState.update { it.copy(isOverlayVisible = visible) }
    }

    fun setReadingMode(mode: ReadingMode) {
        _uiState.update { it.copy(readingMode = mode) }
        viewModelScope.launch(mainDispatcher) {
            userPreferencesDataStore.setReadingMode(mode)
        }
    }

    fun setReadingDirection(direction: ReadingDirection) {
        _uiState.update { it.copy(readingDirection = direction) }
        viewModelScope.launch(mainDispatcher) {
            userPreferencesDataStore.setReadingDirection(direction)
        }
    }

    fun setPageAnimation(animation: PageAnimation) {
        _uiState.update { it.copy(pageAnimation = animation) }
        viewModelScope.launch(mainDispatcher) {
            userPreferencesDataStore.setPageAnimation(animation)
        }
    }

    fun toggleBookmark() {
        val comicId = currentComicId
        if (comicId <= 0L || _uiState.value.comic == null) return

        val currentPage = _uiState.value.currentPage
        val isBookmarked = _uiState.value.isCurrentPageBookmarked

        _uiState.update { it.copy(isCurrentPageBookmarked = !isBookmarked) }

        viewModelScope.launch(mainDispatcher) {
            if (isBookmarked) {
                bookmarkRepository.deleteBookmark(comicId = comicId, pageNumber = currentPage)
            } else {
                bookmarkRepository.addBookmark(
                    comicId = comicId,
                    pageNumber = currentPage,
                    note = null
                )
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch(mainDispatcher) {
            bookmarkRepository.deleteBookmark(bookmarkId)
        }
    }

    fun setZoomScale(scale: Float) {
        _uiState.update { it.copy(zoomScale = clampZoom(scale)) }
    }

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness) }
        viewModelScope.launch(mainDispatcher) {
            userPreferencesDataStore.setBrightness(brightness)
        }
    }

    fun setKeepScreenAwake(keepAwake: Boolean) {
        _uiState.update { it.copy(keepScreenAwake = keepAwake) }
        viewModelScope.launch(mainDispatcher) {
            userPreferencesDataStore.setKeepScreenAwake(keepAwake)
        }
    }

    fun showBookmarksPanel(show: Boolean) {
        _uiState.update { it.copy(isBookmarksPanelVisible = show) }
    }

    fun onVolumeKey(keyCode: Int): Boolean {
        if (!volumeKeyNavEnabled) return false
        val total = _uiState.value.totalPages
        if (total <= 0) return false

        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, 25 -> {
                nextPage()
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP, 24 -> {
                previousPage()
                true
            }
            else -> false
        }
    }

    fun onSessionEnd() {
        val comicId = currentComicId
        if (comicId <= 0L || _uiState.value.comic == null) return

        val durationSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000L).coerceAtLeast(0L)
        val pagesTurned = maxOf(pagesReadInSession.size, pagesTurnedCount)

        viewModelScope.launch(mainDispatcher) {
            statsRepository.recordReadingSession(
                comicId = comicId,
                pagesRead = pagesTurned,
                durationSeconds = durationSeconds
            )
        }
    }

    fun getActiveParser(): ComicParser? = activeParser

    override fun onCleared() {
        super.onCleared()
        onSessionEnd()
    }
}
