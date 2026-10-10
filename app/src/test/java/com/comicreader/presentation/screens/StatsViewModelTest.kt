package com.comicreader.presentation.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.data.repository.StatsRepositoryImpl
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.presentation.screens.stats.StatsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val directExecutor = Executor { it.run() }

    private lateinit var database: ComicDatabase
    private lateinit var comicRepository: ComicRepositoryImpl
    private lateinit var statsRepository: StatsRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ComicDatabase::class.java
        )
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        comicRepository = ComicRepositoryImpl(database.comicDao())
        statsRepository = StatsRepositoryImpl(database.readingStatsDao(), database.comicDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testInitialStats_whenNoSessions_returnsZeroedState() = runTest {
        val viewModel = StatsViewModel(statsRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.totalPagesRead)
        assertEquals(0, state.readingTimeHours)
        assertEquals(0, state.readingTimeMinutes)
        assertEquals("0h 0m", state.formattedReadingTime)
        assertEquals(0, state.completedComics)
        assertEquals(0, state.inProgressComics)
    }

    @Test
    fun testStatsCalculation_withReadingSessionsAndComics() = runTest {
        val comicId = comicRepository.insertOrUpdateComic(
            Comic(title = "Comic 1", filePath = "/c1.cbz", format = ComicFormat.CBZ, pageCount = 100, currentPage = 50)
        )
        // Record 2 reading sessions:
        // Session 1: 30 pages, 3720 seconds (1 hour, 2 minutes)
        // Session 2: 20 pages, 1800 seconds (30 minutes)
        // Total: 50 pages, 5520 seconds = 1 hour, 32 minutes
        statsRepository.recordReadingSession(comicId = comicId, pagesRead = 30, durationSeconds = 3720L)
        statsRepository.recordReadingSession(comicId = comicId, pagesRead = 20, durationSeconds = 1800L)

        val viewModel = StatsViewModel(statsRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(50, state.totalPagesRead)
        assertEquals(1, state.readingTimeHours)
        assertEquals(32, state.readingTimeMinutes)
        assertEquals("1h 32m", state.formattedReadingTime)
        assertEquals(1, state.inProgressComics)
        assertEquals(0, state.completedComics)
    }
}
