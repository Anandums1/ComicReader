package com.comicreader.presentation.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.presentation.screens.history.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val directExecutor = Executor { it.run() }

    private lateinit var database: ComicDatabase
    private lateinit var comicRepository: ComicRepositoryImpl

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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testHistory_ordersByLastReadTimestampDesc() = runTest {
        val now = System.currentTimeMillis()
        comicRepository.insertOrUpdateComic(Comic(title = "Old Read", filePath = "/old.cbz", format = ComicFormat.CBZ, lastReadTimestamp = now - 10000))
        comicRepository.insertOrUpdateComic(Comic(title = "Never Read", filePath = "/never.cbz", format = ComicFormat.CBZ, lastReadTimestamp = 0L))
        comicRepository.insertOrUpdateComic(Comic(title = "Recent Read", filePath = "/recent.cbz", format = ComicFormat.CBZ, lastReadTimestamp = now))

        val viewModel = HistoryViewModel(comicRepository)
        advanceUntilIdle()

        val history = viewModel.uiState.value.comics
        assertEquals(2, history.size)
        assertEquals("Recent Read", history[0].title)
        assertEquals("Old Read", history[1].title)
    }
}
