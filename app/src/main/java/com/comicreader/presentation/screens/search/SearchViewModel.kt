package com.comicreader.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
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
class SearchViewModel @Inject constructor(
    private val comicRepository: ComicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        performSearch()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        performSearch()
    }

    fun setFormat(format: ComicFormat?) {
        _uiState.update { it.copy(selectedFormat = format) }
        performSearch()
    }

    fun setOnlyFavorites(onlyFavorites: Boolean) {
        _uiState.update { it.copy(onlyFavorites = onlyFavorites) }
        performSearch()
    }

    fun setOnlyUnread(onlyUnread: Boolean) {
        _uiState.update { it.copy(onlyUnread = onlyUnread) }
        performSearch()
    }

    fun toggleFavorite(comicId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                comicRepository.toggleFavorite(comicId, isFavorite)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Failed to update favorite")
                }
            }
        }
    }

    private fun performSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            comicRepository.filterComics(
                query = currentState.query.ifBlank { null },
                format = currentState.selectedFormat,
                onlyFavorites = currentState.onlyFavorites
            )
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.localizedMessage ?: "Search failed"
                        )
                    }
                }
                .collect { comics ->
                    val filtered = if (_uiState.value.onlyUnread) {
                        comics.filter { it.currentPage == 0 && !it.isCompleted }
                    } else {
                        comics
                    }
                    _uiState.update {
                        it.copy(
                            results = filtered,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
