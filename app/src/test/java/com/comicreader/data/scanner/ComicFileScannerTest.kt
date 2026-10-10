package com.comicreader.data.scanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.e2e.TestComicGenerator
import com.comicreader.format.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ComicFileScannerTest {

    private lateinit var context: Context
    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var parserFactory: ComicParserFactory
    private lateinit var scanner: ComicFileScannerImpl
    private lateinit var scanDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = database.comicDao()

        val cbzParser = CbzParser()
        val cbrParser = CbrParser()
        val pdfParser = PdfParser()
        val epubParser = EpubParser()
        parserFactory = ComicParserFactory(cbzParser, cbrParser, pdfParser, epubParser)

        scanner = ComicFileScannerImpl(context, comicDao, parserFactory)

        scanDir = File(context.cacheDir, "scanner_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        scanDir.deleteRecursively()
    }

    @Test
    fun scanSingleFileExtractsMetadataCachesCoverAndInsertsToRoom() {
        runBlocking {
            val file = File(scanDir, "spider_man.cbz")
            TestComicGenerator.createMockCbz(
                file,
                pageCount = 4,
                title = "Spider-Man #100",
                series = "The Amazing Spider-Man"
            )

            val entity = scanner.scanSingleFile(file)

            assertThat(entity).isNotNull()
            assertThat(entity!!.title).isEqualTo("Spider-Man #100")
            assertThat(entity.pageCount).isEqualTo(4)
            assertThat(entity.filePath).isEqualTo(file.absolutePath)
            assertThat(entity.coverPath).isNotNull()
            assertThat(File(entity.coverPath!!).exists()).isTrue()

            // Verify in Room database
            val inDb = comicDao.getComicByPath(file.absolutePath)
            assertThat(inDb).isNotNull()
            assertThat(inDb!!.title).isEqualTo("Spider-Man #100")
            assertThat(inDb.id).isEqualTo(entity.id)
        }
    }

    @Test
    fun scanDirectoryRecursivelyDiscoversAndIndexesMultipleFormats() {
        runBlocking {
            val subDir = File(scanDir, "subfolder").apply { mkdirs() }

            val cbz = File(scanDir, "comic1.cbz")
            TestComicGenerator.createMockCbz(cbz, pageCount = 2, title = "CBZ Comic")

            val pdf = File(subDir, "comic2.pdf")
            TestComicGenerator.createMockPdf(pdf, pageCount = 3, title = "PDF Comic")

            val epub = File(subDir, "comic3.epub")
            TestComicGenerator.createMockEpub(epub, pageCount = 2, title = "EPUB Comic")

            // Include an unsupported file that should be ignored
            val txt = File(scanDir, "notes.txt")
            txt.writeText("Not a comic")

            val progressList = scanner.scanDirectory(scanDir).toList()

            assertThat(progressList.first()).isInstanceOf(ScanProgress.Started::class.java)
            val started = progressList.first() as ScanProgress.Started
            assertThat(started.totalEstimatedFiles).isEqualTo(3)

            val finished = progressList.last() as ScanProgress.Finished
            assertThat(finished.addedCount).isEqualTo(3)
            assertThat(finished.updatedCount).isEqualTo(0)
            assertThat(finished.removedCount).isEqualTo(0)

            // Verify Room contains all 3 comics
            val allComics = comicDao.getAllComics().first()
            assertThat(allComics).hasSize(3)
            val titles = allComics.map { it.title }
            assertThat(titles).containsExactly("CBZ Comic", "PDF Comic", "EPUB Comic")
        }
    }

    @Test
    fun skipsUnmodifiedFilesOnSubsequentScan() {
        runBlocking {
            val file = File(scanDir, "unchanged.cbz")
            TestComicGenerator.createMockCbz(file, pageCount = 3, title = "Unchanged Comic")

            // First scan: inserts
            val firstScan = scanner.scanSingleFile(file)
            assertThat(firstScan).isNotNull()

            // Second scan: unchanged file size and cover exists -> skips re-parsing
            val secondScan = scanner.scanSingleFile(file)
            assertThat(secondScan).isNotNull()
            assertThat(secondScan!!.id).isEqualTo(firstScan!!.id)
            assertThat(secondScan.title).isEqualTo(firstScan.title)
        }
    }

    @Test
    fun reconcilesDeletedFilesFromLibraryAndRemovesFromDatabase() {
        runBlocking {
            val file = File(scanDir, "to_delete.cbz")
            TestComicGenerator.createMockCbz(file, pageCount = 2, title = "Ephemeral Comic")

            val entity = scanner.scanSingleFile(file)
            assertThat(entity).isNotNull()
            assertThat(comicDao.getComicByPath(file.absolutePath)).isNotNull()

            // Delete the physical file
            file.delete()
            assertThat(file.exists()).isFalse()

            // Reconcile
            val removed = scanner.reconcileLibrary()
            assertThat(removed).isEqualTo(1)

            // Room entry must be deleted
            val inDb = comicDao.getComicByPath(file.absolutePath)
            assertThat(inDb).isNull()
        }
    }
}
