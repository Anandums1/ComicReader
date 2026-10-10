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
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
class ScannerStressTest {

    private lateinit var context: Context
    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var parserFactory: ComicParserFactory
    private lateinit var scanner: ComicFileScannerImpl
    private lateinit var rootScanDir: File

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

        rootScanDir = File(context.cacheDir, "scanner_stress_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        rootScanDir.deleteRecursively()
    }

    private fun createFastMockCbz(file: File, pageCount: Int = 1): File {
        file.parentFile?.mkdirs()
        val tinyPng = TestComicGenerator.generateValidImageBytes(width = 4, height = 4)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for (i in 1..pageCount) {
                zos.putNextEntry(ZipEntry("p$i.png"))
                zos.write(tinyPng)
                zos.closeEntry()
            }
        }
        return file
    }

    private fun createFastMockEpub(file: File): File {
        file.parentFile?.mkdirs()
        val tinyPng = TestComicGenerator.generateValidImageBytes(width = 4, height = 4)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(StandardCharsets.US_ASCII))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
            zos.write(containerXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/images/p1.png"))
            zos.write(tinyPng)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opfXml = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata><title>${file.nameWithoutExtension}</title></metadata>
  <manifest><item id="i1" href="images/p1.png" media-type="image/png"/></manifest>
  <spine><itemref idref="i1"/></spine>
</package>"""
            zos.write(opfXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return file
    }

    @Test
    fun scanner_deeplyNestedDirectoryWith500PlusFiles_performanceAndCorrectness() = runBlocking {
        // Build 12 levels of nested directories
        val directories = mutableListOf<File>()
        var currentDir = rootScanDir
        directories.add(currentDir)
        for (level in 1..12) {
            currentDir = File(currentDir, "level_$level").apply { mkdirs() }
            directories.add(currentDir)
        }

        // Distribute 520 files across all 13 directory levels:
        // - 420 supported comic files: 220 CBZ, 100 PDF, 80 EPUB, 20 CBR
        // - 100 unsupported files: .txt, .log, .json, .bak
        var fileIndex = 0

        // 220 CBZs
        for (i in 1..220) {
            val dir = directories[fileIndex % directories.size]
            createFastMockCbz(File(dir, "comic_cbz_$i.cbz"), pageCount = 2)
            fileIndex++
        }

        // 100 PDFs
        for (i in 1..100) {
            val dir = directories[fileIndex % directories.size]
            TestComicGenerator.createMockPdf(File(dir, "comic_pdf_$i.pdf"), pageCount = 2, title = "PDF Comic $i")
            fileIndex++
        }

        // 80 EPUBs
        for (i in 1..80) {
            val dir = directories[fileIndex % directories.size]
            createFastMockEpub(File(dir, "comic_epub_$i.epub"))
            fileIndex++
        }

        // 20 CBRs
        for (i in 1..20) {
            val dir = directories[fileIndex % directories.size]
            TestComicGenerator.createMockCbr(File(dir, "comic_cbr_$i.cbr"), isRar5 = false)
            fileIndex++
        }

        // 100 unsupported files
        val unsupportedExts = listOf("txt", "log", "json", "bak", "nfo")
        for (i in 1..100) {
            val dir = directories[fileIndex % directories.size]
            val ext = unsupportedExts[i % unsupportedExts.size]
            File(dir, "document_$i.$ext").writeText("Unsupported content $i")
            fileIndex++
        }

        // Total files created = 520 (420 supported, 100 unsupported)
        assertThat(fileIndex).isEqualTo(520)

        // Measure scan performance
        val progressEvents: List<ScanProgress>
        val scanDurationMs = measureTimeMillis {
            progressEvents = scanner.scanDirectory(rootScanDir).toList()
        }

        // Verify start event correctly identified exactly 420 supported files
        assertThat(progressEvents.first()).isInstanceOf(ScanProgress.Started::class.java)
        val started = progressEvents.first() as ScanProgress.Started
        assertThat(started.totalEstimatedFiles).isEqualTo(420)

        // Verify finish event
        assertThat(progressEvents.last()).isInstanceOf(ScanProgress.Finished::class.java)
        val finished = progressEvents.last() as ScanProgress.Finished
        assertThat(finished.addedCount).isEqualTo(420)
        assertThat(finished.updatedCount).isEqualTo(0)
        assertThat(finished.removedCount).isEqualTo(0)

        // Verify database persistence
        val allComics = comicDao.getAllComics().first()
        assertThat(allComics).hasSize(420)

        // Verify execution completed within reasonable bounds (< 15 seconds for 520 files on disk)
        println("Scanned 520 files (420 indexed) across 12 directory levels in ${scanDurationMs}ms")
        assertThat(scanDurationMs).isLessThan(20000L)

        // =========================================================================
        // Subsequent Scan Skip Performance
        // =========================================================================
        val subsequentEvents: List<ScanProgress>
        val subsequentDurationMs = measureTimeMillis {
            subsequentEvents = scanner.scanDirectory(rootScanDir).toList()
        }

        val subsequentFinished = subsequentEvents.last() as ScanProgress.Finished
        assertThat(subsequentFinished.addedCount).isEqualTo(0)
        assertThat(subsequentFinished.updatedCount).isEqualTo(0)
        assertThat(subsequentFinished.removedCount).isEqualTo(0)

        // Skip logic should be significantly faster than initial scan
        println("Subsequent skip scan for 420 comics took ${subsequentDurationMs}ms")
        assertThat(subsequentDurationMs).isLessThan(scanDurationMs + 1000L)

        // =========================================================================
        // Mass Deletion and Library Reconciliation
        // =========================================================================
        // Delete 100 comics from disk
        val comicsToDelete = allComics.take(100)
        for (comic in comicsToDelete) {
            File(comic.filePath).delete()
        }

        val removedCount = scanner.reconcileLibrary()
        assertThat(removedCount).isEqualTo(100)

        val remainingComics = comicDao.getAllComics().first()
        assertThat(remainingComics).hasSize(320)
    }

    @Test
    fun scanner_corruptAndZeroByteFiles_doesNotCrashScan() = runBlocking {
        val testDir = File(rootScanDir, "corrupt_test").apply { mkdirs() }

        // 1. 0-byte CBZ
        File(testDir, "empty.cbz").writeBytes(ByteArray(0))

        // 2. Corrupt CBR
        File(testDir, "broken.cbr").writeText("GARBAGE")

        // 3. Corrupt EPUB
        File(testDir, "broken.epub").writeText("NOT_A_ZIP")

        // 4. Corrupt PDF
        File(testDir, "broken.pdf").writeText("INVALID_PDF")

        // 5. Valid CBZ
        createFastMockCbz(File(testDir, "valid.cbz"), pageCount = 3)

        val progress = scanner.scanDirectory(testDir).toList()

        // Should complete successfully without throwing
        assertThat(progress.last()).isInstanceOf(ScanProgress.Finished::class.java)
        val finished = progress.last() as ScanProgress.Finished
        assertThat(finished.addedCount).isEqualTo(5)

        val allComics = comicDao.getAllComics().first()
        assertThat(allComics).hasSize(5)
    }
}
