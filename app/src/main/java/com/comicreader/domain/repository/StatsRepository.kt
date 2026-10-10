package com.comicreader.domain.repository

import com.comicreader.domain.model.DailyReadingStat
import com.comicreader.domain.model.ReadingSession
import com.comicreader.domain.model.ReadingStats
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    fun getReadingStats(): Flow<ReadingStats>
    suspend fun recordReadingSession(comicId: Long?, pagesRead: Int, durationSeconds: Long): Long
    fun getAllSessions(): Flow<List<ReadingSession>>
    fun getWeeklyStats(): Flow<List<DailyReadingStat>>
}

