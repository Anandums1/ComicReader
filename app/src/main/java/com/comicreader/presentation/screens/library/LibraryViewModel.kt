package com.comicreader.presentation.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.data.scanner.ComicFileScanner
import com.comicreader.data.scanner.ScanProgress
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.SortOrder
import com.comicreader.domain.repository.ComicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val comicRepository: ComicRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val scanner: ComicFileScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var comicsCollectionJob: Job? = null

    init {
        observeComics(SortOrder.TITLE)
    }

    fun setSortOrder(sortOrder: SortOrder) {
        if (_uiState.value.sortOrder == sortOrder) return
        _uiState.update { it.copy(sortOrder = sortOrder) }
        observeComics(sortOrder)
    }

    private fun observeComics(sortOrder: SortOrder) {
        comicsCollectionJob?.cancel()
        comicsCollectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            comicRepository.getAllComics(sortOrder)
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.localizedMessage ?: "Failed to load library"
                        )
                    }
                }
                .collect { comics ->
                    _uiState.update {
                        it.copy(
                            comics = comics,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun deleteComic(comic: Comic) {
        viewModelScope.launch {
            try {
                comicRepository.deleteComic(comic)
                comic.coverPath?.let { path ->
                    val coverFile = File(path)
                    if (coverFile.exists()) {
                        coverFile.delete()
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Failed to delete comic")
                }
            }
        }
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

    fun triggerScan(directory: File? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isScanning = true, scanMessage = "Scanning started...") }
                val targetDir = directory ?: run {
                    val path = userPreferencesDataStore.watchedDirectoryFlow.first()
                    File(path)
                }

                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                scanner.scanDirectory(targetDir).collect { progress ->
                    when (progress) {
                        is ScanProgress.Started -> {
                            _uiState.update {
                                it.copy(scanMessage = "Found ${progress.totalEstimatedFiles} files to check...")
                            }
                        }
                        is ScanProgress.Processing -> {
                            _uiState.update {
                                it.copy(scanMessage = "Scanning (${progress.processedCount}/${progress.total}): ${progress.currentFile}")
                            }
                        }
                        is ScanProgress.FileIndexed -> {
                            // Comic added/indexed
                        }
                        is ScanProgress.Finished -> {
                            _uiState.update {
                                it.copy(
                                    isScanning = false,
                                    scanMessage = "Scan complete. Added: ${progress.addedCount}, Updated: ${progress.updatedCount}"
                                )
                            }
                        }
                        is ScanProgress.Failed -> {
                            _uiState.update {
                                it.copy(
                                    isScanning = false,
                                    scanMessage = null,
                                    errorMessage = progress.error.localizedMessage ?: "Scan failed"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = e.localizedMessage ?: "Scan failed"
                    )
                }
            }
        }
    }

    fun clearScanMessage() {
        _uiState.update { it.copy(scanMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
