package com.comicreader.presentation.screens

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.repository.BookmarkRepositoryImpl
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.presentation.screens.details.ComicDetailViewModel
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
import org.junit.Assert.assertNotNull
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
class ComicDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val directExecutor = Executor { it.run() }

    private lateinit var database: ComicDatabase
    private lateinit var comicRepository: ComicRepositoryImpl
    private lateinit var bookmarkRepository: BookmarkRepositoryImpl

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
        bookmarkRepository = BookmarkRepositoryImpl(database.bookmarkDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testLoadComic_loadsMetadataAndBookmarks() = runTest {
        val comic = Comic(
            title = "Action Comics #1",
            filePath = "/comics/action1.cbz",
            format = ComicFormat.CBZ,
            pageCount = 64,
            currentPage = 10,
            fileSizeBytes = 2048000L
        )
        val comicId = comicRepository.insertOrUpdateComic(comic)
        val bookmarkId = bookmarkRepository.addBookmark(comicId = comicId, pageNumber = 5, note = "Key scene")

        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))
        val viewModel = ComicDetailViewModel(savedStateHandle, comicRepository, bookmarkRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.comic)
        assertEquals("Action Comics #1", state.comic!!.title)
        assertEquals(64, state.comic!!.pageCount)
        assertEquals(1, state.bookmarks.size)
        assertEquals("Key scene", state.bookmarks[0].note)
    }

    @Test
    fun testLoadComic_savedStateHandle_supportsStringId() = runTest {
        val comic = Comic(
            title = "Detective Comics #27",
            filePath = "/comics/tec27.cbz",
            format = ComicFormat.CBZ
        )
        val comicId = comicRepository.insertOrUpdateComic(comic)

        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId.toString()))
        val viewModel = ComicDetailViewModel(savedStateHandle, comicRepository, bookmarkRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.comic)
        assertEquals("Detective Comics #27", state.comic!!.title)
    }

    @Test
    fun testLoadComic_nonExistentComic_handlesGracefully() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to 999999L))
        val viewModel = ComicDetailViewModel(savedStateHandle, comicRepository, bookmarkRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.comic == null)
        assertTrue(state.errorMessage?.contains("not found") == true)
    }

    @Test
    fun testToggleFavorite_updatesRepositoryAndState() = runTest {
        val comic = Comic(
            title = "Wonder Woman",
            filePath = "/comics/ww.cbz",
            format = ComicFormat.CBZ,
            isFavorite = false
        )
        val comicId = comicRepository.insertOrUpdateComic(comic)

        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))
        val viewModel = ComicDetailViewModel(savedStateHandle, comicRepository, bookmarkRepository)
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        val updated = comicRepository.getComicByIdOnce(comicId)
        assertNotNull(updated)
        assertTrue(updated!!.isFavorite)
    }

    @Test
    fun testDeleteBookmark_removesBookmarkFromList() = runTest {
        val comic = Comic(
            title = "Flash #1",
            filePath = "/comics/flash.cbz",
            format = ComicFormat.CBZ
        )
        val comicId = comicRepository.insertOrUpdateComic(comic)
        val bmId = bookmarkRepository.addBookmark(comicId = comicId, pageNumber = 12)

        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))
        val viewModel = ComicDetailViewModel(savedStateHandle, comicRepository, bookmarkRepository)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.bookmarks.size)

        viewModel.deleteBookmark(bmId)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.bookmarks.size)
    }
}
