package com.comicreader.stress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.scanner.ComicFileScannerImpl
import com.comicreader.data.scanner.ScanProgress
import com.comicreader.domain.model.ComicFormat
import com.comicreader.e2e.TestComicGenerator
import com.comicreader.format.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.sign

@RunWith(RobolectricTestRunner::class)
class NaturalOrderAndConcurrencyStressTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var context: Context
    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var parserFactory: ComicParserFactory

    private val cbzParser = CbzParser()
    private val cbrParser = CbrParser()
    private val pdfParser = PdfParser()
    private val epubParser = EpubParser()

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "stress_m2_${System.nanoTime()}").apply { mkdirs() }
        cacheDir = File(tempDir, "cache").apply { mkdirs() }
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = database.comicDao()

        parserFactory = ComicParserFactory(cbzParser, cbrParser, pdfParser, epubParser)
    }

    @After
    fun tearDown() {
        database.close()
        // On Windows, deleting tempDir verifies that all file descriptors, streams, and archives were properly closed
        val deleted = tempDir.deleteRecursively()
        assertThat(deleted).isTrue()
    }

    // =========================================================================
    // SECTION 1: NaturalOrderComparator EMPIRICAL STRESS TESTS
    // =========================================================================

    @Test
    fun testNaturalSorting_ExtremeAlphanumericPatterns() {
        // Required pattern from dispatch: p001.jpg, p10.jpg, p2.jpg, p02a.jpg
        val input = listOf("p001.jpg", "p10.jpg", "p2.jpg", "p02a.jpg")
        val sorted = input.sortedWith(NaturalOrderComparator)

        // p001.jpg has numeric value 1 (len 3)
        // p2.jpg has numeric value 2 (len 1)
        // p02a.jpg has numeric value 2 (len 2) followed by "a.jpg"
        // p10.jpg has numeric value 10
        assertThat(sorted).containsExactly(
            "p001.jpg",
            "p2.jpg",
            "p02a.jpg",
            "p10.jpg"
        ).inOrder()

        // Test varying zero padding on identical numeric value
        val zeroPaddingList = listOf("p0002.jpg", "p2.jpg", "p02.jpg", "p002.jpg")
        val sortedZeroPadding = zeroPaddingList.sortedWith(NaturalOrderComparator)
        assertThat(sortedZeroPadding).containsExactly(
            "p2.jpg",
            "p02.jpg",
            "p002.jpg",
            "p0002.jpg"
        ).inOrder()

        // Test numeric ties broken by alphabetical suffix
        val suffixList = listOf("p02c.jpg", "p02.jpg", "p02a.jpg", "p02b.jpg")
        val sortedSuffix = suffixList.sortedWith(NaturalOrderComparator)
        assertThat(sortedSuffix).containsExactly(
            "p02.jpg",
            "p02a.jpg",
            "p02b.jpg",
            "p02c.jpg"
        ).inOrder()
    }

    @Test
    fun testNaturalSorting_MultiNumberFilenames() {
        // Multi-number Chapter and Page patterns
        val input = listOf(
            "Chapter 10 - Page 1.jpg",
            "Chapter 1 - Page 2.jpg",
            "Chapter 1 - Page 10.jpg",
            "Chapter 2 - Page 1.jpg",
            "Chapter 1 - Page 1.jpg",
            "Chapter 10 - Page 2.jpg"
        )
        val sorted = input.sortedWith(NaturalOrderComparator)

        assertThat(sorted).containsExactly(
            "Chapter 1 - Page 1.jpg",
            "Chapter 1 - Page 2.jpg",
            "Chapter 1 - Page 10.jpg",
            "Chapter 2 - Page 1.jpg",
            "Chapter 10 - Page 1.jpg",
            "Chapter 10 - Page 2.jpg"
        ).inOrder()

        // Dotted version / volume numbering
        val dottedVersions = listOf(
            "v1.10.0.cbz",
            "v1.2.0.cbz",
            "v1.0.2.cbz",
            "v1.0.1.cbz",
            "v1.0.10.cbz",
            "v2.0.0.cbz"
        )
        val sortedVersions = dottedVersions.sortedWith(NaturalOrderComparator)

        assertThat(sortedVersions).containsExactly(
            "v1.0.1.cbz",
            "v1.0.2.cbz",
            "v1.0.10.cbz",
            "v1.2.0.cbz",
            "v1.10.0.cbz",
            "v2.0.0.cbz"
        ).inOrder()

        // Underscore delimited tokens
        val tokens = listOf(
            "comic_v1_c10_p01.png",
            "comic_v1_c1_p02.png",
            "comic_v1_c1_p01.png",
            "comic_v2_c1_p01.png"
        )
        val sortedTokens = tokens.sortedWith(NaturalOrderComparator)
        assertThat(sortedTokens).containsExactly(
            "comic_v1_c1_p01.png",
            "comic_v1_c1_p02.png",
            "comic_v1_c10_p01.png",
            "comic_v2_c1_p01.png"
        ).inOrder()
    }

    @Test
    fun testNaturalSorting_UnicodeAndInternationalNames() {
        // Japanese / CJK Chapter numbering
        val cjkInput = listOf("第100話.jpg", "第1話.jpg", "第2話.jpg", "第10話.jpg", "第20話.jpg")
        val sortedCjk = cjkInput.sortedWith(NaturalOrderComparator)
        assertThat(sortedCjk).containsExactly(
            "第1話.jpg",
            "第2話.jpg",
            "第10話.jpg",
            "第20話.jpg",
            "第100話.jpg"
        ).inOrder()

        // French accented titles
        val frenchInput = listOf("épisode 10.cbz", "épisode 1.cbz", "épisode 2.cbz")
        val sortedFrench = frenchInput.sortedWith(NaturalOrderComparator)
        assertThat(sortedFrench).containsExactly(
            "épisode 1.cbz",
            "épisode 2.cbz",
            "épisode 10.cbz"
        ).inOrder()

        // Emojis embedded in filenames
        val emojiInput = listOf("🦸100.png", "🦸1.png", "🦸2.png", "🦸10.png")
        val sortedEmoji = emojiInput.sortedWith(NaturalOrderComparator)
        assertThat(sortedEmoji).containsExactly(
            "🦸1.png",
            "🦸2.png",
            "🦸10.png",
            "🦸100.png"
        ).inOrder()

        // Cyrillic chapter names
        val cyrillicInput = listOf("Том 10 Глава 1.cbz", "Том 2 Глава 1.cbz", "Том 1 Глава 2.cbz", "Том 1 Глава 1.cbz")
        val sortedCyrillic = cyrillicInput.sortedWith(NaturalOrderComparator)
        assertThat(sortedCyrillic).containsExactly(
            "Том 1 Глава 1.cbz",
            "Том 1 Глава 2.cbz",
            "Том 2 Глава 1.cbz",
            "Том 10 Глава 1.cbz"
        ).inOrder()
    }

    @Test
    fun testNaturalSorting_ArbitraryPrecisionExtremeNumbers() {
        // Numbers with 120+ digits exceeding Long.MAX_VALUE and Double capacity
        val num120Nines = "page" + "9".repeat(120) + ".jpg"
        val num121Digits = "page1" + "0".repeat(120) + ".jpg"
        val numSmall = "page1.jpg"

        val list = listOf(num121Digits, numSmall, num120Nines)
        val sorted = list.sortedWith(NaturalOrderComparator)

        assertThat(sorted).containsExactly(
            numSmall,
            num120Nines,
            num121Digits
        ).inOrder()
    }

    @Test
    fun testNaturalOrderComparator_MathematicalContractFuzzing() {
        // Verify comparator contract:
        // 1. Reflexivity: compare(x, x) == 0
        // 2. Anti-symmetry: sgn(compare(x, y)) == -sgn(compare(y, x))
        // 3. Transitivity: if compare(x, y) <= 0 and compare(y, z) <= 0, then compare(x, z) <= 0
        val testPool = listOf(
            "", "a", "A", "b", "0", "00", "01", "1", "2", "10", "010",
            "p001.jpg", "p01.jpg", "p1.jpg", "p2.jpg", "p02.jpg", "p02a.jpg", "p02b.jpg", "p10.jpg",
            "第1話", "第2話", "第10話", "épisode 1", "épisode 10",
            "page-1", "page-01", "page_1", "page_2",
            "1.0.0", "1.0.1", "1.1.0", "1.10.0", "2.0.0"
        )

        // 1. Test Reflexivity & Anti-symmetry
        for (i in testPool.indices) {
            val a = testPool[i]
            assertThat(NaturalOrderComparator.compare(a, a)).isEqualTo(0)

            for (j in testPool.indices) {
                val b = testPool[j]
                val cmpAB = NaturalOrderComparator.compare(a, b)
                val cmpBA = NaturalOrderComparator.compare(b, a)
                assertThat(cmpAB.sign).isEqualTo(-cmpBA.sign)
            }
        }

        // 2. Test Transitivity
        for (i in testPool.indices) {
            val a = testPool[i]
            for (j in testPool.indices) {
                val b = testPool[j]
                val cmpAB = NaturalOrderComparator.compare(a, b)
                if (cmpAB <= 0) {
                    for (k in testPool.indices) {
                        val c = testPool[k]
                        val cmpBC = NaturalOrderComparator.compare(b, c)
                        if (cmpBC <= 0) {
                            val cmpAC = NaturalOrderComparator.compare(a, c)
                            assertThat(cmpAC).isAtMost(0)
                        }
                    }
                }
            }
        }

        // 3. Large randomized shuffle test to ensure Java TimSort never throws IllegalArgumentException
        val largeList = (0..500).map { i ->
            val prefix = if (i % 2 == 0) "page" else "ch_${i % 5}_p"
            val num = (i * 37) % 150
            "$prefix${num}.jpg"
        }
        val sortedLarge = largeList.sortedWith(NaturalOrderComparator)
        assertThat(sortedLarge).hasSize(501)
    }

    // =========================================================================
    // SECTION 2: CONCURRENCY & RESOURCE LEAK EMPIRICAL STRESS TESTS
    // =========================================================================

    @Test
    fun testConcurrentCbzParsing_WithoutDeadlockOrFdLeaks() = runBlocking {
        // Create 5 distinct CBZ archives with 10 pages each
        val cbzFiles = (1..5).map { i ->
            val file = File(tempDir, "concurrent_cbz_$i.cbz")
            TestComicGenerator.createMockCbz(
                file,
                pageCount = 10,
                title = "Concurrent Comic #$i",
                series = "Stress Series",
                writer = "Stress Writer"
            )
            file
        }

        // Spawn 30 concurrent coroutines hammering the parser
        val deferreds = (1..30).map { workerId ->
            async(Dispatchers.IO) {
                val targetFile = cbzFiles[workerId % cbzFiles.size]

                // Concurrently extract metadata
                val metadata = cbzParser.extractMetadata(targetFile)
                assertThat(metadata.pageCount).isEqualTo(10)
                assertThat(metadata.format).isEqualTo(ComicFormat.CBZ)

                // Concurrently extract pages flow
                val pages = cbzParser.getPages(targetFile).first()
                assertThat(pages).hasSize(10)

                // Concurrently extract cover
                val workerCache = File(cacheDir, "cbz_worker_$workerId").apply { mkdirs() }
                val cover = cbzParser.extractCover(targetFile, workerCache)
                assertThat(cover).isNotNull()
                assertThat(cover!!.exists()).isTrue()

                // Concurrently load sampled bitmap
                val bitmap = cbzParser.loadPageBitmap(targetFile, 0, 100, 100)
                assertThat(bitmap).isNotNull()
                workerId
            }
        }

        withTimeout(20_000L) {
            val results = deferreds.awaitAll()
            assertThat(results).hasSize(30)
        }
    }

    @Test
    fun testConcurrentEpubParsing_WithoutDeadlockOrFdLeaks() = runBlocking {
        // Create 3 distinct EPUB archives with fixed-layout XHTML pages
        val epubFiles = (1..3).map { i ->
            val file = File(tempDir, "concurrent_epub_$i.epub")
            TestComicGenerator.createMockEpub(
                file,
                pageCount = 5,
                title = "Concurrent EPUB Comic #$i",
                fixedLayout = true
            )
            file
        }

        // Spawn 24 concurrent coroutines hammering the EPUB parser
        val deferreds = (1..24).map { workerId ->
            async(Dispatchers.IO) {
                val targetFile = epubFiles[workerId % epubFiles.size]

                val metadata = epubParser.extractMetadata(targetFile)
                assertThat(metadata.pageCount).isEqualTo(5)
                assertThat(metadata.format).isEqualTo(ComicFormat.EPUB)

                val pages = epubParser.getPages(targetFile).first()
                assertThat(pages).hasSize(5)

                val workerCache = File(cacheDir, "epub_worker_$workerId").apply { mkdirs() }
                val cover = epubParser.extractCover(targetFile, workerCache)
                assertThat(cover).isNotNull()

                val bitmap = epubParser.loadPageBitmap(targetFile, 0, 100, 100)
                assertThat(bitmap).isNotNull()
                workerId
            }
        }

        withTimeout(20_000L) {
            val results = deferreds.awaitAll()
            assertThat(results).hasSize(24)
        }
    }

    @Test
    fun testConcurrentPdfParsing_UnderMutexWithoutStarvation() = runBlocking {
        val pdfFiles = (1..3).map { i ->
            val file = File(tempDir, "concurrent_pdf_$i.pdf")
            TestComicGenerator.createMockPdf(
                file,
                pageCount = 6,
                title = "Concurrent PDF Comic #$i"
            )
            file
        }

        val deferreds = (1..18).map { workerId ->
            async(Dispatchers.IO) {
                val targetFile = pdfFiles[workerId % pdfFiles.size]

                val metadata = pdfParser.extractMetadata(targetFile)
                assertThat(metadata.pageCount).isEqualTo(6)
                assertThat(metadata.format).isEqualTo(ComicFormat.PDF)

                val pages = pdfParser.getPages(targetFile).first()
                assertThat(pages).hasSize(6)
                workerId
            }
        }

        withTimeout(20_000L) {
            val results = deferreds.awaitAll()
            assertThat(results).hasSize(18)
        }
    }

    @Test
    fun testConcurrentCbrParsing_MutexSafetyAndRar5Rejection() = runBlocking {
        val rar4File = File(tempDir, "concurrent_rar4.cbr")
        TestComicGenerator.createMockCbr(rar4File, isRar5 = false)

        val rar5File = File(tempDir, "concurrent_rar5.cbr")
        TestComicGenerator.createMockCbr(rar5File, isRar5 = true)

        // Spawn 20 concurrent coroutines mixing RAR4 and RAR5 calls
        val deferreds = (1..20).map { workerId ->
            async(Dispatchers.IO) {
                if (workerId % 2 == 0) {
                    // RAR5: Must throw UnsupportedRarV5Exception
                    var exceptionThrown = false
                    try {
                        cbrParser.extractMetadata(rar5File)
                    } catch (e: UnsupportedRarV5Exception) {
                        exceptionThrown = true
                    }
                    assertThat(exceptionThrown).isTrue()
                } else {
                    // RAR4: Must process metadata without hang
                    val metadata = cbrParser.extractMetadata(rar4File)
                    assertThat(metadata.format).isEqualTo(ComicFormat.CBR)
                }
                workerId
            }
        }

        withTimeout(20_000L) {
            val results = deferreds.awaitAll()
            assertThat(results).hasSize(20)
        }
    }

    @Test
    fun testConcurrentScannerAndReconciliationStress() = runBlocking {
        val scannerDir = File(tempDir, "scanner_stress").apply { mkdirs() }

        // Generate 12 comic archives
        val generatedFiles = (1..12).map { i ->
            val ext = when (i % 3) {
                0 -> "cbz"
                1 -> "epub"
                else -> "pdf"
            }
            val file = File(scannerDir, "stress_book_$i.$ext")
            when (ext) {
                "cbz" -> TestComicGenerator.createMockCbz(file, pageCount = 3, title = "Scanner CBZ $i")
                "epub" -> TestComicGenerator.createMockEpub(file, pageCount = 3, title = "Scanner EPUB $i")
                else -> TestComicGenerator.createMockPdf(file, pageCount = 3, title = "Scanner PDF $i")
            }
            file
        }

        val scanner = ComicFileScannerImpl(context, comicDao, parserFactory)

        // Scan directory and collect progress events
        val scanEvents = scanner.scanDirectory(scannerDir).toList()
        val finishedEvent = scanEvents.filterIsInstance<ScanProgress.Finished>().firstOrNull()
        assertThat(finishedEvent).isNotNull()
        assertThat(finishedEvent!!.addedCount).isEqualTo(12)

        // Verify all 12 records exist in Room database
        val comicsInDb = comicDao.getAllComics().first()
        assertThat(comicsInDb).hasSize(12)

        // Stress: Delete 4 files externally and invoke reconciliation
        val filesToDelete = generatedFiles.take(4)
        filesToDelete.forEach { it.delete() }

        val removedCount = scanner.reconcileLibrary()
        assertThat(removedCount).isEqualTo(4)

        // Verify database reflects the 8 remaining comics
        val remainingComics = comicDao.getAllComics().first()
        assertThat(remainingComics).hasSize(8)
    }
}
