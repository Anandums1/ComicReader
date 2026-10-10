package com.comicreader.presentation.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.presentation.screens.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class SearchViewModelTest {

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
    fun testSearchByQuery_filtersMatchingTitles() = runTest {
        comicRepository.insertOrUpdateComic(Comic(title = "Spider-Man Vol 1", filePath = "/sm1.cbz", format = ComicFormat.CBZ))
        comicRepository.insertOrUpdateComic(Comic(title = "Spider-Man Vol 2", filePath = "/sm2.cbz", format = ComicFormat.CBZ))
        comicRepository.insertOrUpdateComic(Comic(title = "Batman Year One", filePath = "/bm.cbz", format = ComicFormat.CBZ))

        val viewModel = SearchViewModel(comicRepository)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.results.size)

        viewModel.setQuery("Spider")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.results.size)
        assertTrue(viewModel.uiState.value.results.all { it.title.contains("Spider") })
    }

    @Test
    fun testFilterByFormat_returnsOnlySelectedFormat() = runTest {
        comicRepository.insertOrUpdateComic(Comic(title = "Comic CBZ", filePath = "/c.cbz", format = ComicFormat.CBZ))
        comicRepository.insertOrUpdateComic(Comic(title = "Comic PDF", filePath = "/c.pdf", format = ComicFormat.PDF))
        comicRepository.insertOrUpdateComic(Comic(title = "Comic EPUB", filePath = "/c.epub", format = ComicFormat.EPUB))

        val viewModel = SearchViewModel(comicRepository)
        advanceUntilIdle()

        viewModel.setFormat(ComicFormat.PDF)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals(ComicFormat.PDF, viewModel.uiState.value.results[0].format)
    }

    @Test
    fun testFilterByFavoritesOnly_returnsFavorites() = runTest {
        comicRepository.insertOrUpdateComic(Comic(title = "Fav Comic", filePath = "/fav.cbz", format = ComicFormat.CBZ, isFavorite = true))
        comicRepository.insertOrUpdateComic(Comic(title = "Regular Comic", filePath = "/reg.cbz", format = ComicFormat.CBZ, isFavorite = false))

        val viewModel = SearchViewModel(comicRepository)
        advanceUntilIdle()

        viewModel.setOnlyFavorites(true)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("Fav Comic", viewModel.uiState.value.results[0].title)
    }

    @Test
    fun testFilterByUnreadOnly_returnsComicsWithZeroProgress() = runTest {
        comicRepository.insertOrUpdateComic(Comic(title = "Unread", filePath = "/u.cbz", format = ComicFormat.CBZ, pageCount = 20, currentPage = 0))
        comicRepository.insertOrUpdateComic(Comic(title = "Reading", filePath = "/r.cbz", format = ComicFormat.CBZ, pageCount = 20, currentPage = 5))

        val viewModel = SearchViewModel(comicRepository)
        advanceUntilIdle()

        viewModel.setOnlyUnread(true)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("Unread", viewModel.uiState.value.results[0].title)
    }
}
