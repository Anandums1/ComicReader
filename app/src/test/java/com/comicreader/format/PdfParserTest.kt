package com.comicreader.format

import com.comicreader.domain.model.ComicFormat
import com.comicreader.e2e.TestComicGenerator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PdfParserTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var parser: PdfParser

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "pdf_parser_test_${System.nanoTime()}")
        tempDir.mkdirs()
        cacheDir = File(tempDir, "cache").apply { mkdirs() }
        parser = PdfParser()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun canParseReturnsTrueForPdfExtension() {
        assertThat(parser.canParse("saga.pdf")).isTrue()
        assertThat(parser.canParse("saga.PDF")).isTrue()
        assertThat(parser.canParse("saga.cbz")).isFalse()
        assertThat(parser.canParse("saga.cbr")).isFalse()
        assertThat(parser.canParse("saga.epub")).isFalse()
    }

    @Test
    fun extractsPageCountAndMetadataFromPdf() {
        val file = File(tempDir, "sample_comic.pdf")
        TestComicGenerator.createMockPdf(
            file,
            pageCount = 6,
            title = "Saga Chapter 1"
        )

        val metadata = parser.extractMetadata(file)

        assertThat(metadata.title).isEqualTo("Saga Chapter 1")
        assertThat(metadata.pageCount).isEqualTo(6)
        assertThat(metadata.format).isEqualTo(ComicFormat.PDF)
    }

    @Test
    fun getPagesEmitsCorrectNumberOfPages() = runBlocking {
        val file = File(tempDir, "pages_test.pdf")
        TestComicGenerator.createMockPdf(file, pageCount = 4)

        val pages = parser.getPages(file).first()

        assertThat(pages).hasSize(4)
        for (i in 0 until 4) {
            assertThat(pages[i].pageNumber).isEqualTo(i)
            assertThat(pages[i].imageUri).contains("pages_test.pdf#$i")
        }
    }

    @Test
    fun handlesCorruptedPdfGracefully() = runBlocking {
        val corruptFile = File(tempDir, "corrupt.pdf")
        TestComicGenerator.createMockPdf(corruptFile, corrupt = true)

        val metadata = parser.extractMetadata(corruptFile)
        assertThat(metadata.title).isEqualTo("corrupt")
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = parser.extractCover(corruptFile, cacheDir)
        assertThat(cover).isNull()

        val pages = parser.getPages(corruptFile).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun handlesNonExistentPdfGracefully() = runBlocking {
        val nonExistent = File(tempDir, "missing.pdf")

        val metadata = parser.extractMetadata(nonExistent)
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = parser.extractCover(nonExistent, cacheDir)
        assertThat(cover).isNull()

        val pages = parser.getPages(nonExistent).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun enforcesMaxTextureLimit() {
        assertThat(PdfParser.MAX_TEXTURE_SIZE).isEqualTo(4096)
    }
}
