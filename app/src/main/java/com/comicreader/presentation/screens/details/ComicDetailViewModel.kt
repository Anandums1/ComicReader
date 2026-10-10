package com.comicreader.presentation.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicreader.domain.repository.BookmarkRepository
import com.comicreader.domain.repository.ComicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComicDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val comicRepository: ComicRepository,
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComicDetailUiState())
    val uiState: StateFlow<ComicDetailUiState> = _uiState.asStateFlow()

    private var comicJob: Job? = null
    private var bookmarksJob: Job? = null

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
        } else {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid comic ID") }
        }
    }

    fun loadComic(comicId: Long) {
        comicJob?.cancel()
        bookmarksJob?.cancel()

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        comicJob = viewModelScope.launch {
            comicRepository.getComicById(comicId)
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.localizedMessage ?: "Failed to load comic"
                        )
                    }
                }
                .collect { comic ->
                    if (comic == null) {
                        _uiState.update {
                            it.copy(
                                comic = null,
                                isLoading = false,
                                errorMessage = "Comic not found (id: $comicId)"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                comic = comic,
                                isLoading = false
                            )
                        }
                    }
                }
        }

        bookmarksJob = viewModelScope.launch {
            bookmarkRepository.getBookmarksForComic(comicId)
                .catch { /* ignore bookmark errors gracefully */ }
                .collect { bookmarks ->
                    _uiState.update { it.copy(bookmarks = bookmarks) }
                }
        }
    }

    fun toggleFavorite() {
        val comic = _uiState.value.comic ?: return
        viewModelScope.launch {
            try {
                comicRepository.toggleFavorite(comic.id, !comic.isFavorite)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Failed to update favorite")
                }
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            try {
                bookmarkRepository.deleteBookmark(bookmarkId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Failed to delete bookmark")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
