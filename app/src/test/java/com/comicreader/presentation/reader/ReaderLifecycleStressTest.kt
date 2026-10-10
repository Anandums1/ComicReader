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

/**
 * Empirical Stress Test Suite for Reader Lifecycle, Activity Recreation, and State Persistence.
 * Validates resilience against rapid configuration changes, simulated process/activity recreation,
 * concurrent navigation, and boundary conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderLifecycleStressTest {

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
        title: String = "Stress Test Comic",
        pageCount: Int = 100,
        currentPage: Int = 0
    ): Long {
        return comicRepository.insertOrUpdateComic(
            Comic(
                title = title,
                filePath = "/storage/emulated/0/Downloads/Comics/stress.cbz",
                format = ComicFormat.CBZ,
                pageCount = pageCount,
                currentPage = currentPage
            )
        )
    }

    private fun createViewModel(comicId: Long): ReaderViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("comicId" to comicId))
        return ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandle,
            mainDispatcher = testDispatcher
        )
    }

    @Test
    fun testRapidConfigurationChanges_duringSequentialPageTurns() = runTest(testDispatcher) {
        // Arrange: 100-page comic starting at 0
        val comicId = insertTestComic(pageCount = 100, currentPage = 0)

        var currentVm = createViewModel(comicId)
        advanceUntilIdle()
        assertEquals(0, currentVm.uiState.value.currentPage)

        // Act: Simulate 15 consecutive cycles of (page turn -> activity recreation)
        for (step in 1..15) {
            currentVm.nextPage()
            advanceUntilIdle()
            assertEquals(step, currentVm.uiState.value.currentPage)

            // Simulate Activity recreation / ViewModel re-instantiation
            currentVm = createViewModel(comicId)
            advanceUntilIdle()

            // Assert: Restored state matches turned page exactly
            assertEquals("Failed to restore page after recreation at step $step", step, currentVm.uiState.value.currentPage)
        }

        // Final DB check
        val finalComic = comicRepository.getComicByIdOnce(comicId)
        assertNotNull(finalComic)
        assertEquals(15, finalComic!!.currentPage)
    }

    @Test
    fun testBurstPageTurns_followedByImmediateActivityRecreation() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 50, currentPage = 0)

        val vm1 = createViewModel(comicId)
        advanceUntilIdle()

        // Rapid burst: jump through 25 pages without waiting
        for (page in 1..25) {
            vm1.onPageChanged(page)
        }
        advanceUntilIdle()

        assertEquals(25, vm1.uiState.value.currentPage)

        // Simulate activity destruction: call session end and drop vm1
        vm1.onSessionEnd()
        advanceUntilIdle()

        // Activity recreated: new VM initialized with SavedStateHandle
        val vm2 = createViewModel(comicId)
        advanceUntilIdle()

        assertEquals("New ViewModel must restore to last burst page", 25, vm2.uiState.value.currentPage)
        val persistedComic = comicRepository.getComicByIdOnce(comicId)
        assertEquals(25, persistedComic?.currentPage)
    }

    @Test
    fun testActivityRecreation_preservesBookmarksAndPreferencesContinuity() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 50, currentPage = 10)

        var vm = createViewModel(comicId)
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        // Turn to page 12 and bookmark it
        vm.onPageChanged(12)
        advanceUntilIdle()
        vm.toggleBookmark()
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()
        assertTrue(vm.uiState.value.isCurrentPageBookmarked)

        // Change preferences
        vm.setReadingMode(ReadingMode.VERTICAL)
        dataStore.readingModeFlow.first { it == ReadingMode.VERTICAL }
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        vm.setReadingDirection(ReadingDirection.RTL)
        dataStore.readingDirectionFlow.first { it == ReadingDirection.RTL }
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        // Simulate activity recreation
        val recreatedVm = createViewModel(comicId)
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        // Wait for reactive preferences flow to emit into recreated ViewModel
        recreatedVm.uiState.first { !it.isLoading && it.readingMode == ReadingMode.VERTICAL && it.readingDirection == ReadingDirection.RTL }
        ShadowLooper.idleMainLooper()

        // Assert: Page 12 restored, bookmark recognized, preferences preserved
        assertEquals(12, recreatedVm.uiState.value.currentPage)
        assertTrue(recreatedVm.uiState.value.isCurrentPageBookmarked)
        assertEquals(1, recreatedVm.uiState.value.bookmarks.size)
        assertEquals(12, recreatedVm.uiState.value.bookmarks.first().pageNumber)
        assertEquals(ReadingMode.VERTICAL, recreatedVm.uiState.value.readingMode)
        assertEquals(ReadingDirection.RTL, recreatedVm.uiState.value.readingDirection)
    }

    @Test
    fun testBoundaryPages_preservesCompletionAndProgressAcrossRecreation() = runTest(testDispatcher) {
        // Boundary 1: Initial zero progress
        val comicId = insertTestComic(pageCount = 20, currentPage = 0)
        var vm = createViewModel(comicId)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.currentPage)
        var comicDb = comicRepository.getComicByIdOnce(comicId)
        assertEquals(0.0f, comicDb!!.progress, 0.001f)
        assertFalse(comicDb.isCompleted)

        // Recreate at page 0
        vm = createViewModel(comicId)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.currentPage)

        // Turn to final page (19 out of 20)
        vm.onPageChanged(19)
        advanceUntilIdle()

        comicDb = comicRepository.getComicByIdOnce(comicId)
        assertEquals(1.0f, comicDb!!.progress, 0.001f)
        assertTrue(comicDb.isCompleted)

        // Recreate at final completed page
        val finalVm = createViewModel(comicId)
        advanceUntilIdle()

        assertEquals(19, finalVm.uiState.value.currentPage)
        val restoredComic = comicRepository.getComicByIdOnce(comicId)
        assertEquals(19, restoredComic!!.currentPage)
        assertTrue("Comic must remain marked as completed across recreation", restoredComic.isCompleted)
        assertEquals(1.0f, restoredComic.progress, 0.001f)
    }

    @Test
    fun testMultipleReadingSessions_accumulatesStatsAcrossRecreations() = runTest(testDispatcher) {
        val comicId = insertTestComic(pageCount = 100, currentPage = 0)

        // Session 1: Read pages 0 to 5
        var vm = createViewModel(comicId)
        advanceUntilIdle()
        for (p in 1..5) {
            vm.onPageChanged(p)
            advanceUntilIdle()
        }
        vm.onSessionEnd()
        advanceUntilIdle()

        // Session 2: Activity recreated, read pages 5 to 15
        vm = createViewModel(comicId)
        advanceUntilIdle()
        assertEquals(5, vm.uiState.value.currentPage)
        for (p in 6..15) {
            vm.onPageChanged(p)
            advanceUntilIdle()
        }
        vm.onSessionEnd()
        advanceUntilIdle()

        // Session 3: Activity recreated, read pages 15 to 30
        vm = createViewModel(comicId)
        advanceUntilIdle()
        assertEquals(15, vm.uiState.value.currentPage)
        for (p in 16..30) {
            vm.onPageChanged(p)
            advanceUntilIdle()
        }
        vm.onSessionEnd()
        advanceUntilIdle()

        // Verify accumulated reading stats
        val allSessions = statsRepository.getAllSessions().first()
        assertEquals("Must record 3 reading sessions across recreations", 3, allSessions.size)
        assertTrue(allSessions.all { it.comicId == comicId })
    }

    @Test
    fun testSavedStateHandle_longIdVariantSupportedAcrossRecreation() = runTest(testDispatcher) {
        val comicId = insertTestComic(title = "Navigation Route Test", pageCount = 30, currentPage = 8)

        // Standard Long argument (from AndroidX navigation NavType.LongType)
        val vmLong = createViewModel(comicId)
        advanceUntilIdle()
        assertEquals(8, vmLong.uiState.value.currentPage)
        assertEquals(comicId, vmLong.uiState.value.comic?.id)
    }

    @Test
    fun testSavedStateHandle_stringAndLongIdVariantsSupportedAcrossRecreation() = runTest(testDispatcher) {
        val comicId = insertTestComic(title = "Navigation Route Test", pageCount = 30, currentPage = 8)

        // 1. Long argument variant
        val vmLong = createViewModel(comicId)
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()
        assertEquals(8, vmLong.uiState.value.currentPage)
        assertEquals(comicId, vmLong.uiState.value.comic?.id)

        // 2. String argument variant (standard for Jetpack Compose Navigation route arguments: "reader/{comicId}")
        val savedStateHandleStr = SavedStateHandle(mapOf("comicId" to comicId.toString()))
        val vmString = ReaderViewModel(
            comicRepository = comicRepository,
            bookmarkRepository = bookmarkRepository,
            statsRepository = statsRepository,
            userPreferencesDataStore = dataStore,
            comicParserFactory = parserFactory,
            savedStateHandle = savedStateHandleStr,
            mainDispatcher = testDispatcher
        )
        advanceUntilIdle()
        ShadowLooper.idleMainLooper()
        assertEquals(8, vmString.uiState.value.currentPage)
        assertEquals(comicId, vmString.uiState.value.comic?.id)
        assertFalse(vmString.uiState.value.isLoading)
    }

    @Test
    fun testInvalidComicId_doesNotCrashAcrossRecreation() = runTest(testDispatcher) {
        // Recreate with non-existent comic ID
        val vmNonExistent = createViewModel(999999L)
        advanceUntilIdle()

        assertFalse(vmNonExistent.uiState.value.isLoading)
        assertNotNull(vmNonExistent.uiState.value.errorMessage)
        assertTrue(vmNonExistent.uiState.value.errorMessage!!.contains("Comic not found"))

        // Recreate with 0 / negative ID
        val vmZero = createViewModel(0L)
        advanceUntilIdle()
        // Default uninitialized state, no crash
        assertTrue(vmZero.uiState.value.isLoading)
    }
}
