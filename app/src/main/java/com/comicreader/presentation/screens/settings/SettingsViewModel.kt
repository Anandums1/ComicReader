package com.comicreader.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesDataStore.preferencesFlow.collect { prefs ->
                _uiState.update {
                    it.copy(
                        theme = prefs.theme,
                        readingMode = prefs.readingMode,
                        readingDirection = prefs.readingDirection,
                        pageAnimation = prefs.pageTurnAnimation,
                        volumeKeyNav = prefs.volumeKeyNav,
                        keepScreenAwake = prefs.keepScreenAwake,
                        watchedDirectory = prefs.watchedDirectory
                    )
                }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        _uiState.update { it.copy(theme = theme) }
        viewModelScope.launch {
            userPreferencesDataStore.setTheme(theme)
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        _uiState.update { it.copy(readingMode = mode) }
        viewModelScope.launch {
            userPreferencesDataStore.setReadingMode(mode)
        }
    }

    fun setReadingDirection(direction: ReadingDirection) {
        _uiState.update { it.copy(readingDirection = direction) }
        viewModelScope.launch {
            userPreferencesDataStore.setReadingDirection(direction)
        }
    }

    fun setVolumeKeyNav(enabled: Boolean) {
        _uiState.update { it.copy(volumeKeyNav = enabled) }
        viewModelScope.launch {
            userPreferencesDataStore.setVolumeKeyNav(enabled)
        }
    }

    fun setKeepScreenAwake(keepAwake: Boolean) {
        _uiState.update { it.copy(keepScreenAwake = keepAwake) }
        viewModelScope.launch {
            userPreferencesDataStore.setKeepScreenAwake(keepAwake)
        }
    }

    fun setWatchedDirectory(path: String) {
        _uiState.update { it.copy(watchedDirectory = path) }
        viewModelScope.launch {
            userPreferencesDataStore.setWatchedDirectory(path)
        }
    }

    fun setPageAnimation(animation: PageAnimation) {
        _uiState.update { it.copy(pageAnimation = animation) }
        viewModelScope.launch {
            userPreferencesDataStore.setPageAnimation(animation)
        }
    }
}
