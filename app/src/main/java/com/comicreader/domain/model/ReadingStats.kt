package com.comicreader.domain.model

data class ReadingStats(
    val totalPagesRead: Int = 0,
    val totalReadingTimeSeconds: Long = 0L,
    val completedComics: Int = 0,
    val inProgressComics: Int = 0,
    val recentSessions: List<ReadingSession> = emptyList(),
    val weeklyPageCounts: Map<String, Int> = emptyMap()
)

data class ReadingSession(
    val id: Long = 0,
    val comicId: Long? = null,
    val comicTitle: String? = null,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val pagesRead: Int
)

data class DailyReadingStat(
    val dayOfWeek: String,
    val pagesRead: Int,
    val durationSeconds: Long
)

