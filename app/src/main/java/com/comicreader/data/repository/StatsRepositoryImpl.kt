package com.comicreader.data.repository

import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.dao.ReadingStatsDao
import com.comicreader.data.local.db.entity.ReadingStatsEntity
import com.comicreader.domain.model.DailyReadingStat
import com.comicreader.domain.model.ReadingSession
import com.comicreader.domain.model.ReadingStats
import com.comicreader.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val readingStatsDao: ReadingStatsDao,
    private val comicDao: ComicDao
) : StatsRepository {

    override fun getReadingStats(): Flow<ReadingStats> {
        return combine(
            readingStatsDao.getTotalPagesRead(),
            readingStatsDao.getTotalReadingTimeSeconds(),
            readingStatsDao.getCompletedComicsCount(),
            readingStatsDao.getInProgressComicsCount(),
            readingStatsDao.getAllSessions()
        ) { pages, seconds, completed, inProgress, sessionEntities ->
            val sessions = sessionEntities.map { entity ->
                entity.toDomain()
            }

            // Calculate weekly page counts by day of week
            val dayFormat = SimpleDateFormat("EEE", Locale.US)
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val weeklyMap = mutableMapOf(
                "Mon" to 0, "Tue" to 0, "Wed" to 0, "Thu" to 0, "Fri" to 0, "Sat" to 0, "Sun" to 0
            )

            sessionEntities
                .filter { it.sessionStartTimestamp >= oneWeekAgo }
                .forEach { session ->
                    val dayStr = dayFormat.format(Date(session.sessionStartTimestamp))
                    weeklyMap[dayStr] = (weeklyMap[dayStr] ?: 0) + session.pagesRead
                }

            ReadingStats(
                totalPagesRead = pages,
                totalReadingTimeSeconds = seconds,
                completedComics = completed,
                inProgressComics = inProgress,
                recentSessions = sessions.take(20),
                weeklyPageCounts = weeklyMap
            )
        }
    }

    override suspend fun recordReadingSession(
        comicId: Long?,
        pagesRead: Int,
        durationSeconds: Long
    ): Long {
        val now = System.currentTimeMillis()
        val start = now - (durationSeconds * 1000L).coerceAtLeast(0L)
        val validComicId = if (comicId != null) {
            val exists = comicDao.getComicByIdOnce(comicId) != null
            if (exists) comicId else null
        } else {
            null
        }
        val entity = ReadingStatsEntity(
            comicId = validComicId,
            sessionStartTimestamp = start,
            sessionEndTimestamp = now,
            durationSeconds = durationSeconds,
            pagesRead = pagesRead
        )
        return readingStatsDao.insertSession(entity)
    }

    override fun getAllSessions(): Flow<List<ReadingSession>> {
        return readingStatsDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getWeeklyStats(): Flow<List<DailyReadingStat>> {
        val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        return readingStatsDao.getSessionsSince(oneWeekAgo).map { sessions ->
            val dayFormat = SimpleDateFormat("EEE", Locale.US)
            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val pagesByDay = mutableMapOf<String, Int>()
            val durationByDay = mutableMapOf<String, Long>()

            days.forEach { day ->
                pagesByDay[day] = 0
                durationByDay[day] = 0L
            }

            sessions.forEach { s ->
                val day = dayFormat.format(Date(s.sessionStartTimestamp))
                pagesByDay[day] = (pagesByDay[day] ?: 0) + s.pagesRead
                durationByDay[day] = (durationByDay[day] ?: 0L) + s.durationSeconds
            }

            days.map { day ->
                DailyReadingStat(
                    dayOfWeek = day,
                    pagesRead = pagesByDay[day] ?: 0,
                    durationSeconds = durationByDay[day] ?: 0L
                )
            }
        }
    }
}

