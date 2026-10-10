package com.comicreader.presentation.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicreader.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState(isLoading = true))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            statsRepository.getReadingStats()
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.localizedMessage ?: "Failed to load stats"
                        )
                    }
                }
                .collect { stats ->
                    val totalSec = stats.totalReadingTimeSeconds
                    val hours = (totalSec / 3600L).toInt()
                    val minutes = ((totalSec % 3600L) / 60L).toInt()

                    _uiState.update {
                        it.copy(
                            totalPagesRead = stats.totalPagesRead,
                            readingTimeHours = hours,
                            readingTimeMinutes = minutes,
                            completedComics = stats.completedComics,
                            inProgressComics = stats.inProgressComics,
                            isLoading = false
                        )
                    }
                }
        }

        viewModelScope.launch {
            statsRepository.getWeeklyStats()
                .catch { /* ignore weekly errors gracefully */ }
                .collect { weekly ->
                    _uiState.update {
                        it.copy(weeklyStats = weekly)
                    }
                }
        }
    }
}
