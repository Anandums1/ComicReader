package com.comicreader.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.repository.BookmarkRepositoryImpl
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.data.repository.StatsRepositoryImpl
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import com.comicreader.format.CbrParser
import com.comicreader.format.CbzParser
import com.comicreader.format.ComicParserFactory
import com.comicreader.format.EpubParser
import com.comicreader.format.PdfParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val directExecutor = Executor { it.run() }

    private lateinit var database: ComicDatabase
    private lateinit var comicRepository: ComicRepositoryImpl
    private lateinit var bookmarkRepository: BookmarkRepositoryImpl
    private lateinit var statsRepository: StatsRepositoryImpl
    private lateinit var dataStore: UserPreferencesDataStore
    private lateinit var parserFactory: ComicParserFactory

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
        statsRepository = StatsRepositoryImpl(database.readingStatsDao(), database.comicDao())
        dataStore = UserPreferencesDataStore(ApplicationProvider.getApplicationContext())

        runBlocking {
            dataStore.setReadingMode(ReadingMode.HORIZONTAL)
            dataStore.setReadingDirection(ReadingDirection.LTR)
        }

        parserFactory = ComicParserFactory(
            cbzParser = CbzParser(),
            cbrParser = CbrParser(),
            pdfParser = PdfParser(),
            epubParser = EpubParser()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private suspend fun insertTestComic(
        title: String = "Test Comic",
        pageCount: Int = 10,
        currentPage: Int = 0
    ): Long {
        return comicRepository.insertOrUpdateComic(
            Comic(
                title = title,
                filePath = "/storage/emulated/0/Downloads/Comics/test.cbz",
                format = ComicFormat.CBZ,
                pageCount = pageCount,
                currentPage = currentPage
            )
        )
    }

    @Test
    fun testInitialization_loadsComicAndInitialState() = runTest(testDispatcher) {
        val comicId = insertTestComic(title = "Watchmen", pageCount = 12, currentPage = 3)
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.comic)
        assertEquals(comicId, state.comic?.id)
        assertEquals(3, state.currentPage)
        assertEquals(12, state.totalPages)
        assertFalse(state.isLoading)
    }

    @Test
    fun testOnPageChanged_updatesReadingProgressInDatabase() = runTest(testDispatcher) {
        val comicId = insertTestComic(title = "Saga", pageCount = 20, currentPage = 0)
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        // Turn to page 7
        viewModel.onPageChanged(7)
        advanceUntilIdle()

        assertEquals(7, viewModel.uiState.value.currentPage)

        // Verify Room persistence
        val savedComic = comicRepository.getComicByIdOnce(comicId)
        assertNotNull(savedComic)
        assertEquals(7, savedComic!!.currentPage)
        assertTrue(savedComic.lastReadTimestamp > 0L)
    }

    @Test
    fun testOnPageChanged_boundaryClamping() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 10, currentPage = 5)
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        // Negative clamp to 0
        viewModel.onPageChanged(-10)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        // Over-max clamp to totalPages - 1 (9)
        viewModel.onPageChanged(100)
        advanceUntilIdle()
        assertEquals(9, viewModel.uiState.value.currentPage)
    }

    @Test
    fun testNextAndPreviousPageHelpers() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 10, currentPage = 4)
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.currentPage)

        viewModel.previousPage()
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.currentPage)
    }

    @Test
    fun testBookmarkToggle_addsAndRemovesBookmark() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 10, currentPage = 6)
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCurrentPageBookmarked)

        // Add bookmark
        viewModel.toggleBookmark()
        advanceUntilIdle()

        val bookmarksAfterAdd = bookmarkRepository.getBookmarksForComic(comicId).first()
        assertEquals(1, bookmarksAfterAdd.size)
        assertEquals(6, bookmarksAfterAdd.first().pageNumber)
        assertTrue(viewModel.uiState.value.isCurrentPageBookmarked)

        // Toggle again to remove bookmark
        viewModel.toggleBookmark()
        advanceUntilIdle()

        val bookmarksAfterRemove = bookmarkRepository.getBookmarksForComic(comicId).first()
        assertTrue(bookmarksAfterRemove.isEmpty())
        assertFalse(viewModel.uiState.value.isCurrentPageBookmarked)
    }

    @Test
    fun testReadingModeChange_persistsToPreferences() = runTest(testDispatcher) {
        val comicId = insertTestComic()
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle,
            mainDispatcher = testDispatcher
        )

        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        viewModel.setReadingMode(ReadingMode.VERTICAL)
        dataStore.readingModeFlow.first { it == ReadingMode.VERTICAL }
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        assertEquals(ReadingMode.VERTICAL, viewModel.uiState.value.readingMode)
    }

    @Test
    fun testReadingDirectionChange_persistsToPreferences() = runTest(testDispatcher) {
        val comicId = insertTestComic()
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle,
            mainDispatcher = testDispatcher
        )

        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        viewModel.setReadingDirection(ReadingDirection.RTL)
        dataStore.readingDirectionFlow.first { it == ReadingDirection.RTL }
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        assertEquals(ReadingDirection.RTL, viewModel.uiState.value.readingDirection)
    }

    @Test
    fun testVolumeKeyNavigation_flipsPages() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 10, currentPage = 5)
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        // Volume Down (25) -> Advance to 6
        val downHandled = viewModel.onVolumeKey(25)
        advanceUntilIdle()
        assertTrue(downHandled)
        assertEquals(6, viewModel.uiState.value.currentPage)

        // Volume Up (24) -> Go back to 5
        val upHandled = viewModel.onVolumeKey(24)
        advanceUntilIdle()
        assertTrue(upHandled)
        assertEquals(5, viewModel.uiState.value.currentPage)

        // Irrelevant key (e.g. 66) -> false
        val otherHandled = viewModel.onVolumeKey(66)
        assertFalse(otherHandled)
        assertEquals(5, viewModel.uiState.value.currentPage)
    }

    @Test
    fun testOverlayVisibilityToggle() = runTest(testDispatcher) {
        val comicId = insertTestComic()
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isOverlayVisible)

        viewModel.toggleOverlay()
        assertTrue(viewModel.uiState.value.isOverlayVisible)

        viewModel.toggleOverlay()
        assertFalse(viewModel.uiState.value.isOverlayVisible)
    }

    @Test
    fun testSessionEnd_recordsReadingSessionInStatsRepository() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 15, currentPage = 0)
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))

        val viewModel = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        // Read through pages 1, 2, 3
        viewModel.onPageChanged(1)
        advanceUntilIdle()
        viewModel.onPageChanged(2)
        advanceUntilIdle()
        viewModel.onPageChanged(3)
        advanceUntilIdle()

        viewModel.onSessionEnd()
        advanceUntilIdle()

        val sessions = statsRepository.getAllSessions().first()
        assertTrue(sessions.isNotEmpty())
        val lastSession = sessions.first()
        assertEquals(comicId, lastSession.comicId)
        assertTrue(lastSession.pagesRead >= 3)
    }
}
