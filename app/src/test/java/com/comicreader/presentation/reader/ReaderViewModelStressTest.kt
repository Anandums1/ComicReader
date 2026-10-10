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
import kotlin.random.Random

/**
 * Empirical stress test suite for ReaderViewModel.
 * Stresses:
 * 1. Rapid page changes (1,000 sequential and random flips).
 * 2. Boundary navigation across extreme indices (-100 to 10,000, Int.MIN_VALUE, Int.MAX_VALUE).
 * 3. Rapid concurrent bookmark toggling on single and multiple pages.
 * 4. Single-page comic boundaries and non-existent comic ID handling.
 * 5. Extreme zoom scale inputs.
 * 6. High-frequency volume key navigation bursts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelStressTest {

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
        title: String = "Stress Comic",
        pageCount: Int = 50,
        currentPage: Int = 0
    ): Long {
        return comicRepository.insertOrUpdateComic(
            Comic(
                title = title,
                filePath = "/storage/emulated/0/Comics/stress.cbz",
                format = ComicFormat.CBZ,
                pageCount = pageCount,
                currentPage = currentPage
            )
        )
    }

    @Test
    fun testRapidPageChanges_1000SequentialAndRandomFlips() = runTest(testDispatcher) {
        val totalPages = 100
        val comicId = insertTestComic(title = "Massive Comic", pageCount = totalPages, currentPage = 0)
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

        val random = Random(12345)
        for (i in 0 until 1_000) {
            when (random.nextInt(3)) {
                0 -> viewModel.nextPage()
                1 -> viewModel.previousPage()
                2 -> viewModel.onPageChanged(random.nextInt(-20, totalPages + 20))
            }
            // State must invariant: 0 <= currentPage <= totalPages - 1
            val current = viewModel.uiState.value.currentPage
            assertTrue("Current page $current must be >= 0", current >= 0)
            assertTrue("Current page $current must be < $totalPages", current < totalPages)
        }

        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        // Room must match final clamped state
        val savedComic = comicRepository.getComicByIdOnce(comicId)
        assertNotNull(savedComic)
        assertEquals(viewModel.uiState.value.currentPage, savedComic!!.currentPage)
        assertTrue(savedComic.currentPage in 0 until totalPages)
    }

    @Test
    fun testBoundaryNavigation_extremePageIndicesMinus100To10000() = runTest(testDispatcher) {
        val totalPages = 50
        val maxPage = totalPages - 1 // 49
        val comicId = insertTestComic(pageCount = totalPages, currentPage = 25)
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

        // 1. Extreme negative jumps
        val negativeTests = listOf(-1, -2, -10, -100, -99999, Int.MIN_VALUE)
        for (neg in negativeTests) {
            viewModel.onPageChanged(neg)
            advanceUntilIdle()
            assertEquals("Jump to $neg must clamp to 0", 0, viewModel.uiState.value.currentPage)
        }

        // Previous page at 0 stays at 0
        viewModel.previousPage()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        // 2. Extreme positive jumps
        val positiveTests = listOf(50, 51, 100, 10000, 999999, Int.MAX_VALUE)
        for (pos in positiveTests) {
            viewModel.onPageChanged(pos)
            advanceUntilIdle()
            assertEquals("Jump to $pos must clamp to $maxPage", maxPage, viewModel.uiState.value.currentPage)
        }

        // Next page at max stays at max
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(maxPage, viewModel.uiState.value.currentPage)

        // 3. Jump to exact boundaries
        viewModel.jumpToPage(0)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        viewModel.jumpToPage(maxPage)
        advanceUntilIdle()
        assertEquals(maxPage, viewModel.uiState.value.currentPage)

        // Verify database persistence of final state
        val comic = comicRepository.getComicByIdOnce(comicId)
        assertNotNull(comic)
        assertEquals(maxPage, comic!!.currentPage)
    }

    @Test
    fun testRapidBookmarkToggling_burstOnSamePage() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 20, currentPage = 5)
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

        // Burst toggle 50 times sequentially
        for (i in 1..50) {
            viewModel.toggleBookmark()
            advanceUntilIdle()
            ShadowLooper.idleMainLooper()
        }

        // After 50 toggles (even number), bookmark state should be false and repository empty
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val bookmarks = bookmarkRepository.getBookmarksForComic(comicId).first()
        assertTrue("After 50 toggles, bookmarks should be empty", bookmarks.isEmpty())
        assertFalse("uiState bookmark flag should be false", viewModel.uiState.value.isCurrentPageBookmarked)

        // 51st toggle -> should add bookmark
        viewModel.toggleBookmark()
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val bookmarksAfter51 = bookmarkRepository.getBookmarksForComic(comicId).first()
        assertEquals(1, bookmarksAfter51.size)
        assertEquals(5, bookmarksAfter51[0].pageNumber)
        assertTrue(viewModel.uiState.value.isCurrentPageBookmarked)
    }

    @Test
    fun testRapidBookmarkingAcrossMultiplePages() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 20, currentPage = 0)
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

        // Bookmark every even page from 0 to 18 (10 bookmarks total)
        for (page in 0 until 20 step 2) {
            viewModel.onPageChanged(page)
            advanceUntilIdle()
            viewModel.toggleBookmark()
            advanceUntilIdle()
            ShadowLooper.idleMainLooper()
        }

        val bookmarks = bookmarkRepository.getBookmarksForComic(comicId).first()
        assertEquals(10, bookmarks.size)
        for (i in 0 until 10) {
            assertEquals(i * 2, bookmarks[i].pageNumber)
        }

        // Verify bookmark drawer deletion
        val firstBookmark = bookmarks.first()
        viewModel.deleteBookmark(firstBookmark.id)
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val remainingBookmarks = bookmarkRepository.getBookmarksForComic(comicId).first()
        assertEquals(9, remainingBookmarks.size)
        assertFalse(remainingBookmarks.any { it.pageNumber == 0 })
    }

    @Test
    fun testSinglePageComic_boundaries() = runTest(testDispatcher) {
        // Comic with only 1 page (pageCount = 1)
        val comicId = insertTestComic(pageCount = 1, currentPage = 0)
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

        assertEquals(0, viewModel.uiState.value.currentPage)
        assertEquals(1, viewModel.uiState.value.totalPages)

        // Next page does not exceed 0
        viewModel.nextPage()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        // Previous page does not go below 0
        viewModel.previousPage()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        // Arbitrary jump clamps to 0
        viewModel.jumpToPage(10)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        viewModel.jumpToPage(-10)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)
    }

    @Test
    fun testNonExistentComic_gracefulErrorHandling() = runTest(testDispatcher) {
        val nonExistentId = 999999L
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to nonExistentId))

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

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Comic not found"))

        // Invoking navigation methods must not crash
        viewModel.nextPage()
        viewModel.previousPage()
        viewModel.jumpToPage(5)
        viewModel.toggleBookmark()
        viewModel.toggleOverlay()
        viewModel.onVolumeKey(25)
        viewModel.onSessionEnd()
        advanceUntilIdle()
    }

    @Test
    fun testExtremeZoomScales_appliedSafely() = runTest(testDispatcher) {
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

        viewModel.setZoomScale(-100f)
        assertEquals(1.0f, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.setZoomScale(100f)
        assertEquals(5.0f, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.setZoomScale(Float.MAX_VALUE)
        assertEquals(5.0f, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.setZoomScale(Float.POSITIVE_INFINITY)
        assertEquals(5.0f, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.setZoomScale(Float.NEGATIVE_INFINITY)
        assertEquals(1.0f, viewModel.uiState.value.zoomScale, 0.001f)

        viewModel.setZoomScale(2.5f)
        assertEquals(2.5f, viewModel.uiState.value.zoomScale, 0.001f)
    }

    @Test
    fun testVolumeKeyNavigation_stressBurst() = runTest(testDispatcher) {
        val totalPages = 30
        val maxPage = 29
        val comicId = insertTestComic(pageCount = totalPages, currentPage = 0)
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

        // 100 Volume Down presses (KEY 25) -> clamps to maxPage (29)
        for (i in 0 until 100) {
            val handled = viewModel.onVolumeKey(25)
            assertTrue(handled)
        }
        advanceUntilIdle()
        assertEquals(maxPage, viewModel.uiState.value.currentPage)

        // 100 Volume Up presses (KEY 24) -> clamps to 0
        for (i in 0 until 100) {
            val handled = viewModel.onVolumeKey(24)
            assertTrue(handled)
        }
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentPage)

        // Arbitrary keys ignored
        val unhandled = viewModel.onVolumeKey(12345)
        assertFalse(unhandled)
        assertEquals(0, viewModel.uiState.value.currentPage)
    }

    @Test
    fun testSessionTracking_multipleRapidFlips() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 40, currentPage = 0)
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

        // Flip through 15 distinct pages
        for (i in 1..15) {
            viewModel.onPageChanged(i)
        }
        advanceUntilIdle()

        viewModel.onSessionEnd()
        advanceUntilIdle()

        val sessions = statsRepository.getAllSessions().first()
        assertTrue("Session must be recorded", sessions.isNotEmpty())
        val lastSession = sessions.first()
        assertEquals(comicId, lastSession.comicId)
        assertTrue("pagesRead should reflect distinct pages read (>= 15)", lastSession.pagesRead >= 15)
    }
}
