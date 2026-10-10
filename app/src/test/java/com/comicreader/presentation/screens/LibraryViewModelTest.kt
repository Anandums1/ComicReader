package com.comicreader.presentation.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.data.scanner.ComicFileScanner
import com.comicreader.data.scanner.ScanProgress
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.SortOrder
import com.comicreader.presentation.screens.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
import java.io.File
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val directExecutor = Executor { it.run() }

    private lateinit var database: ComicDatabase
    private lateinit var comicRepository: ComicRepositoryImpl
    private lateinit var dataStore: UserPreferencesDataStore
    private lateinit var fakeScanner: FakeComicFileScanner

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
        dataStore = UserPreferencesDataStore(ApplicationProvider.getApplicationContext())
        fakeScanner = FakeComicFileScanner()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testInitialLibrary_loadsComicsInTitleOrder() = runTest {
        // Arrange: Insert comics
        val c1 = Comic(title = "Spider-Man", filePath = "/path/spiderman.cbz", format = ComicFormat.CBZ)
        val c2 = Comic(title = "Batman", filePath = "/path/batman.cbz", format = ComicFormat.CBZ)
        val c3 = Comic(title = "Avengers", filePath = "/path/avengers.cbz", format = ComicFormat.CBZ)
        comicRepository.insertOrUpdateComic(c1)
        comicRepository.insertOrUpdateComic(c2)
        comicRepository.insertOrUpdateComic(c3)

        val viewModel = LibraryViewModel(comicRepository, dataStore, fakeScanner)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SortOrder.TITLE, state.sortOrder)
        assertEquals(3, state.comics.size)
        assertEquals("Avengers", state.comics[0].title)
        assertEquals("Batman", state.comics[1].title)
        assertEquals("Spider-Man", state.comics[2].title)
    }

    @Test
    fun testSetSortOrder_reordersComics() = runTest {
        val now = System.currentTimeMillis()
        val c1 = Comic(title = "B", filePath = "/b.cbz", format = ComicFormat.CBZ, addedTimestamp = now - 5000)
        val c2 = Comic(title = "A", filePath = "/a.cbz", format = ComicFormat.CBZ, addedTimestamp = now)
        comicRepository.insertOrUpdateComic(c1)
        comicRepository.insertOrUpdateComic(c2)

        val viewModel = LibraryViewModel(comicRepository, dataStore, fakeScanner)
        advanceUntilIdle()

        // Change sort order to RECENTLY_ADDED
        viewModel.setSortOrder(SortOrder.RECENTLY_ADDED)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SortOrder.RECENTLY_ADDED, state.sortOrder)
        assertEquals(2, state.comics.size)
        assertEquals("A", state.comics[0].title)
    }

    @Test
    fun testDeleteComic_removesFromRepository() = runTest {
        val c1 = Comic(title = "Comic to delete", filePath = "/del.cbz", format = ComicFormat.CBZ)
        val id = comicRepository.insertOrUpdateComic(c1)
        val inserted = comicRepository.getComicByIdOnce(id)
        assertNotNull(inserted)

        val viewModel = LibraryViewModel(comicRepository, dataStore, fakeScanner)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.comics.size)

        viewModel.deleteComic(inserted!!)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.comics.size)
        val postDelete = comicRepository.getComicByIdOnce(id)
        assertTrue(postDelete == null)
    }

    @Test
    fun testToggleFavorite_updatesComicState() = runTest {
        val c1 = Comic(title = "Fav Comic", filePath = "/fav.cbz", format = ComicFormat.CBZ, isFavorite = false)
        val id = comicRepository.insertOrUpdateComic(c1)

        val viewModel = LibraryViewModel(comicRepository, dataStore, fakeScanner)
        advanceUntilIdle()

        viewModel.toggleFavorite(id, true)
        advanceUntilIdle()

        val updated = comicRepository.getComicByIdOnce(id)
        assertNotNull(updated)
        assertTrue(updated!!.isFavorite)
    }

    @Test
    fun testTriggerScan_updatesScanProgressAndCompletes() = runTest {
        val viewModel = LibraryViewModel(comicRepository, dataStore, fakeScanner)
        advanceUntilIdle()

        val tempDir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "test_scan")
        tempDir.mkdirs()

        viewModel.triggerScan(tempDir)
        advanceUntilIdle()

        assertTrue(fakeScanner.scanDirectoryInvoked)
        assertFalse(viewModel.uiState.value.isScanning)
        assertTrue(viewModel.uiState.value.scanMessage?.contains("Scan complete") == true)

        viewModel.clearScanMessage()
        assertTrue(viewModel.uiState.value.scanMessage == null)
    }

    class FakeComicFileScanner : ComicFileScanner {
        var scanDirectoryInvoked = false
        var emitFail = false

        override suspend fun scanAll(directories: List<File>): Flow<ScanProgress> = flow { }

        override suspend fun scanDirectory(directory: File): Flow<ScanProgress> = flow {
            scanDirectoryInvoked = true
            if (emitFail) {
                emit(ScanProgress.Failed(RuntimeException("Disk error")))
            } else {
                emit(ScanProgress.Started(2))
                emit(ScanProgress.Processing("comic1.cbz", 1, 2))
                emit(ScanProgress.Finished(addedCount = 1, updatedCount = 0, removedCount = 0))
            }
        }

        override suspend fun scanSingleFile(file: File): ComicEntity? = null
        override suspend fun reconcileLibrary(): Int = 0
    }
}
