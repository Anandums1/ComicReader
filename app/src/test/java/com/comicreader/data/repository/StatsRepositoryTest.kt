package com.comicreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.domain.repository.StatsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsRepositoryTest {

    private lateinit var database: ComicDatabase
    private lateinit var repository: StatsRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StatsRepositoryImpl(database.readingStatsDao(), database.comicDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordSessionAndVerifyReadingStats() = runBlocking {
        repository.recordReadingSession(
            comicId = 1L,
            pagesRead = 12,
            durationSeconds = 600L
        )
        repository.recordReadingSession(
            comicId = 2L,
            pagesRead = 18,
            durationSeconds = 900L
        )

        val stats = repository.getReadingStats().first()
        assertThat(stats.totalPagesRead).isEqualTo(30)
        assertThat(stats.totalReadingTimeSeconds).isEqualTo(1500L)
        assertThat(stats.recentSessions).hasSize(2)
    }

    @Test
    fun weeklyStatsAggregation() = runBlocking {
        repository.recordReadingSession(
            comicId = 1L,
            pagesRead = 10,
            durationSeconds = 300L
        )

        val weeklyStats = repository.getWeeklyStats().first()
        assertThat(weeklyStats).hasSize(7) // 7 days of the week

        val totalPagesInWeek = weeklyStats.sumOf { it.pagesRead }
        assertThat(totalPagesInWeek).isEqualTo(10)
    }
}

