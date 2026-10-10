package com.comicreader.data.scanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.format.*
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FolderWatcherServiceTest {

    private lateinit var context: Context
    private lateinit var database: ComicDatabase
    private lateinit var service: FolderWatcherService
    private lateinit var watchDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val cbzParser = CbzParser()
        val cbrParser = CbrParser()
        val pdfParser = PdfParser()
        val epubParser = EpubParser()
        val parserFactory = ComicParserFactory(cbzParser, cbrParser, pdfParser, epubParser)
        val scanner = ComicFileScannerImpl(context, database.comicDao(), parserFactory)
        val dataStore = UserPreferencesDataStore(context)

        service = FolderWatcherService().apply {
            this.scanner = scanner
            this.parserFactory = parserFactory
            this.userPreferencesDataStore = dataStore
        }

        watchDir = File(context.cacheDir, "folder_watcher_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        service.stopWatching()
        database.close()
        watchDir.deleteRecursively()
    }

    @Test
    fun watchDirectoryStartsAndStopsObserverCleanly() {
        assertThat(service.isWatching()).isFalse()

        service.watchDirectory(watchDir)
        assertThat(service.isWatching()).isTrue()

        service.stopWatching()
        assertThat(service.isWatching()).isFalse()
    }

    @Test
    fun ignoresNonExistentDirectory() {
        val nonExistent = File(watchDir, "missing_folder")
        service.watchDirectory(nonExistent)
        assertThat(service.isWatching()).isFalse()
    }
}
