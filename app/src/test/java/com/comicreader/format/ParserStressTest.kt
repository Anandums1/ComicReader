package com.comicreader.format

import android.graphics.Bitmap
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.ComicPage
import com.comicreader.e2e.TestComicGenerator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ParserStressTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File

    private lateinit var cbzParser: CbzParser
    private lateinit var cbrParser: CbrParser
    private lateinit var pdfParser: PdfParser
    private lateinit var epubParser: EpubParser
    private lateinit var parserFactory: ComicParserFactory

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "parser_stress_test_${System.nanoTime()}").apply { mkdirs() }
        cacheDir = File(tempDir, "cache").apply { mkdirs() }

        cbzParser = CbzParser()
        cbrParser = CbrParser()
        pdfParser = PdfParser()
        epubParser = EpubParser()
        parserFactory = ComicParserFactory(cbzParser, cbrParser, pdfParser, epubParser)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // CBZ Stress Tests
    // =========================================================================

    @Test
    fun cbz_zeroByteArchive_handlesGracefully() = runBlocking {
        val file = File(tempDir, "empty.cbz").apply { writeBytes(ByteArray(0)) }

        val metadata = cbzParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)
        assertThat(metadata.format).isEqualTo(ComicFormat.CBZ)
        assertThat(metadata.fileSize).isEqualTo(0L)

        val cover = cbzParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = cbzParser.getPages(file).first()
        assertThat(pages).isEmpty()

        val dummyPage = ComicPage(0, "p.png", file.absolutePath)
        val bitmap = cbzParser.loadPageBitmap(dummyPage, 100, 100)
        assertThat(bitmap).isNull()
    }

    @Test
    fun cbz_emptyZipArchiveWithoutEntries_handlesGracefully() = runBlocking {
        val file = File(tempDir, "empty_zip.cbz")
        ZipOutputStream(FileOutputStream(file)).use { /* no entries */ }

        val metadata = cbzParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = cbzParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = cbzParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun cbz_corruptZip_truncatedOrGarbage_handlesGracefully() = runBlocking {
        val file = File(tempDir, "corrupted.cbz")
        file.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x12, 0x34, 0x56)) // Truncated ZIP header

        val metadata = cbzParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)
        assertThat(metadata.title).isEqualTo("corrupted")

        val cover = cbzParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = cbzParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun cbz_zeroByteImages_handledSafelyWithoutDecoderCrash() = runBlocking {
        val file = File(tempDir, "zero_byte_images.cbz")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for (i in 1..3) {
                val entry = ZipEntry("page$i.png")
                zos.putNextEntry(entry)
                // 0 bytes written
                zos.closeEntry()
            }
        }

        val metadata = cbzParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(3)

        val pages = cbzParser.getPages(file).first()
        assertThat(pages).hasSize(3)

        // Loading 0-byte image must return null without crashing
        val bitmap = cbzParser.loadPageBitmap(pages.first(), 100, 100)
        assertThat(bitmap).isNull()

        val cover = cbzParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()
    }

    @Test
    fun cbz_macOsAndHiddenArtifacts_strictlyFiltered() = runBlocking {
        val file = File(tempDir, "artifacts.cbz")
        val validImg = TestComicGenerator.generateValidImageBytes(width = 50, height = 50)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            // Artifacts
            val artifacts = listOf(
                "__MACOSX/._page1.png",
                "subfolder/__MACOSX/._page2.png",
                ".DS_Store",
                "Thumbs.db",
                "._hidden.png",
                ".hidden_file.jpg",
                "readme.txt",
                "metadata.nfo"
            )
            for (art in artifacts) {
                zos.putNextEntry(ZipEntry(art))
                zos.write("artifact data".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
            // Valid pages
            for (i in 1..2) {
                zos.putNextEntry(ZipEntry("page0$i.png"))
                zos.write(validImg)
                zos.closeEntry()
            }
        }

        val pages = cbzParser.getPages(file).first()
        assertThat(pages).hasSize(2)
        assertThat(pages[0].imageUri).isEqualTo("page01.png")
        assertThat(pages[1].imageUri).isEqualTo("page02.png")
    }

    @Test
    fun cbz_malformedComicInfoXml_fallsBackGracefully() {
        val file = File(tempDir, "bad_xml.cbz")
        val validImg = TestComicGenerator.generateValidImageBytes(width = 50, height = 50)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("page1.png"))
            zos.write(validImg)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("ComicInfo.xml"))
            zos.write("<<<BROKEN XML NOT CLOSED".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        val metadata = cbzParser.extractMetadata(file)
        // Should not throw, should fall back to filename and image count
        assertThat(metadata.title).isEqualTo("bad_xml")
        assertThat(metadata.pageCount).isEqualTo(1)
        assertThat(metadata.format).isEqualTo(ComicFormat.CBZ)
    }

    @Test
    fun cbz_largeArchive_500PagesNaturalSort() = runBlocking {
        val file = File(tempDir, "large_500.cbz")
        val smallPng = TestComicGenerator.generateValidImageBytes(width = 10, height = 10)
        val shuffledIndices = (1..500).toList().shuffled()

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for (i in shuffledIndices) {
                val entry = ZipEntry("img_$i.png")
                zos.putNextEntry(entry)
                zos.write(smallPng)
                zos.closeEntry()
            }
        }

        val metadata = cbzParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(500)

        val pages = cbzParser.getPages(file).first()
        assertThat(pages).hasSize(500)
        // Check natural ordering: img_1, img_2, ..., img_9, img_10, ..., img_500
        assertThat(pages[0].imageUri).isEqualTo("img_1.png")
        assertThat(pages[1].imageUri).isEqualTo("img_2.png")
        assertThat(pages[8].imageUri).isEqualTo("img_9.png")
        assertThat(pages[9].imageUri).isEqualTo("img_10.png")
        assertThat(pages[499].imageUri).isEqualTo("img_500.png")
    }

    @Test
    fun cbz_concurrentBitmapLoads_threadSafe() = runBlocking {
        val file = File(tempDir, "concurrent.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 5)
        val pages = cbzParser.getPages(file).first()

        val jobs = (1..20).map { i ->
            async(Dispatchers.IO) {
                val page = pages[i % pages.size]
                cbzParser.loadPageBitmap(page, 100, 100)
            }
        }
        val results = jobs.awaitAll()
        assertThat(results).hasSize(20)
        results.forEach { bitmap ->
            assertThat(bitmap).isNotNull()
        }
    }

    // =========================================================================
    // CBR Stress Tests
    // =========================================================================

    @Test
    fun cbr_zeroByteArchive_handlesGracefully() = runBlocking {
        val file = File(tempDir, "empty.cbr").apply { writeBytes(ByteArray(0)) }

        val metadata = cbrParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)
        assertThat(metadata.format).isEqualTo(ComicFormat.CBR)

        val cover = cbrParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = cbrParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun cbr_corruptHeader_garbageBytes_handlesGracefully() = runBlocking {
        val file = File(tempDir, "corrupt.cbr").apply {
            writeBytes("COMPLETELY_RANDOM_GARBAGE_BYTES_THAT_ARE_NOT_RAR".toByteArray(StandardCharsets.UTF_8))
        }

        val metadata = cbrParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = cbrParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = cbrParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun cbr_rar5MagicHeader_strictlyRejectedWithUnsupportedRarV5Exception() {
        val file = File(tempDir, "archive_rar5.cbr")
        TestComicGenerator.createMockCbr(file, isRar5 = true)

        assertThat(cbrParser.isRar5(file)).isTrue()

        assertThrows(UnsupportedRarV5Exception::class.java) {
            cbrParser.extractMetadata(file)
        }

        assertThrows(UnsupportedRarV5Exception::class.java) {
            runBlocking {
                cbrParser.getPages(file).first()
            }
        }

        val cover = cbrParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()
    }

    // =========================================================================
    // PDF Stress Tests
    // =========================================================================

    @Test
    fun pdf_zeroByteFile_handlesGracefully() = runBlocking {
        val file = File(tempDir, "empty.pdf").apply { writeBytes(ByteArray(0)) }

        val metadata = pdfParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)
        assertThat(metadata.format).isEqualTo(ComicFormat.PDF)

        val cover = pdfParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = pdfParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun pdf_corruptFile_noPdfHeader_handlesGracefully() = runBlocking {
        val file = File(tempDir, "corrupt.pdf").apply {
            writeText("Random plain text not a pdf")
        }

        val metadata = pdfParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)
        assertThat(metadata.title).isEqualTo("corrupt")

        val cover = pdfParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = pdfParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun pdf_truncatedHeader_handlesGracefully() = runBlocking {
        val file = File(tempDir, "truncated.pdf").apply {
            writeText("%PDF-1.4\n%end\n")
        }

        val metadata = pdfParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)

        val pages = pdfParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun pdf_extremeTargetDimensions_clampedToMaxTextureSize() {
        assertThat(PdfParser.MAX_TEXTURE_SIZE).isEqualTo(4096)
    }

    // =========================================================================
    // EPUB Stress Tests
    // =========================================================================

    @Test
    fun epub_zeroByteArchive_handlesGracefully() = runBlocking {
        val file = File(tempDir, "empty.epub").apply { writeBytes(ByteArray(0)) }

        val metadata = epubParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(0)
        assertThat(metadata.format).isEqualTo(ComicFormat.EPUB)

        val cover = epubParser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = epubParser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun epub_missingContainerXml_throwsInvalidEpubException() {
        val file = File(tempDir, "no_container.epub")
        TestComicGenerator.createMockEpub(file, pageCount = 2, missingContainer = true)

        assertThrows(InvalidEpubException::class.java) {
            epubParser.extractMetadata(file)
        }

        assertThrows(InvalidEpubException::class.java) {
            runBlocking {
                epubParser.getPages(file).first()
            }
        }
    }

    @Test
    fun epub_corruptedContainerXml_throwsOrHandlesGracefully() {
        val file = File(tempDir, "bad_container.epub")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(StandardCharsets.US_ASCII))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write("GARBAGE_CONTAINER_XML".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        assertThrows(InvalidEpubException::class.java) {
            epubParser.extractMetadata(file)
        }
    }

    @Test
    fun epub_zeroByteImages_handledSafely() = runBlocking {
        val file = File(tempDir, "zero_img.epub")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(StandardCharsets.US_ASCII))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
            zos.write(containerXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 0-byte image
            zos.putNextEntry(ZipEntry("OEBPS/images/p1.png"))
            zos.closeEntry()

            val opfXml = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata><title>Zero Img Comic</title></metadata>
  <manifest>
    <item id="i1" href="images/p1.png" media-type="image/png"/>
  </manifest>
  <spine>
    <itemref idref="i1"/>
  </spine>
</package>"""
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(opfXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }

        val metadata = epubParser.extractMetadata(file)
        assertThat(metadata.pageCount).isEqualTo(1)

        val pages = epubParser.getPages(file).first()
        assertThat(pages).hasSize(1)

        // Loading 0-byte image returns null safely
        val bitmap = epubParser.loadPageBitmap(pages.first(), 100, 100)
        assertThat(bitmap).isNull()
    }

    @Test
    fun epub_complexRelativePathResolution() {
        assertThat(epubParser.resolvePath("OEBPS/content/pages", "../../../images/cover.png"))
            .isEqualTo("images/cover.png")
        assertThat(epubParser.resolvePath("OEBPS", "/root/image.jpg"))
            .isEqualTo("root/image.jpg")
        assertThat(epubParser.resolvePath("", "./page.png"))
            .isEqualTo("page.png")
        assertThat(epubParser.resolvePath("OPS/chap1", "sub/../img.png"))
            .isEqualTo("OPS/chap1/img.png")
    }

    // =========================================================================
    // Factory Dispatcher Stress Tests
    // =========================================================================

    @Test
    fun factory_unsupportedExtensions_returnsNull() {
        val unsupported = listOf(
            "file.txt", "video.mp4", "music.mp3", "doc.docx", "data.json", "setup.exe", "image.raw"
        )
        for (name in unsupported) {
            assertThat(parserFactory.getParser(name)).isNull()
            assertThat(parserFactory.isSupported(name)).isFalse()
        }
    }

    @Test
    fun factory_caseInsensitiveSupport() {
        assertThat(parserFactory.isSupported("COMIC.CBZ")).isTrue()
        assertThat(parserFactory.isSupported("MANGA.CBR")).isTrue()
        assertThat(parserFactory.isSupported("BOOK.PDF")).isTrue()
        assertThat(parserFactory.isSupported("GRAPHIC.EPUB")).isTrue()
    }
}
