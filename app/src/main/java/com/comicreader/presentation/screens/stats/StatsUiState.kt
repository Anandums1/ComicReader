package com.comicreader.presentation.screens.stats

import com.comicreader.domain.model.DailyReadingStat

/**
 * UI State for the Reading Statistics Dashboard.
 */
data class StatsUiState(
    val totalPagesRead: Int = 0,
    val readingTimeHours: Int = 0,
    val readingTimeMinutes: Int = 0,
    val completedComics: Int = 0,
    val inProgressComics: Int = 0,
    val weeklyStats: List<DailyReadingStat> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val formattedReadingTime: String
        get() = "${readingTimeHours}h ${readingTimeMinutes}m"
}
