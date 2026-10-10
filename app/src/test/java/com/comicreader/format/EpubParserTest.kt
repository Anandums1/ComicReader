package com.comicreader.format

import com.comicreader.domain.model.ComicFormat
import com.comicreader.e2e.TestComicGenerator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EpubParserTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var parser: EpubParser

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "epub_parser_test_${System.nanoTime()}")
        tempDir.mkdirs()
        cacheDir = File(tempDir, "cache").apply { mkdirs() }
        parser = EpubParser()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun canParseReturnsTrueForEpubExtension() {
        assertThat(parser.canParse("comic.epub")).isTrue()
        assertThat(parser.canParse("comic.EPUB")).isTrue()
        assertThat(parser.canParse("comic.cbz")).isFalse()
        assertThat(parser.canParse("comic.cbr")).isFalse()
        assertThat(parser.canParse("comic.pdf")).isFalse()
    }

    @Test
    fun extractsFixedLayoutEpubPagesThroughSpineAndXhtml() = runBlocking {
        val file = File(tempDir, "fixed_layout.epub")
        TestComicGenerator.createMockEpub(
            file,
            pageCount = 3,
            title = "Fixed Layout Comic",
            fixedLayout = true
        )

        val pages = parser.getPages(file).first()

        assertThat(pages).hasSize(3)
        assertThat(pages[0].imageUri).isEqualTo("OEBPS/images/page1.png")
        assertThat(pages[1].imageUri).isEqualTo("OEBPS/images/page2.png")
        assertThat(pages[2].imageUri).isEqualTo("OEBPS/images/page3.png")
    }

    @Test
    fun extractsDirectImageSpineEpubPages() = runBlocking {
        val file = File(tempDir, "direct_image_spine.epub")
        TestComicGenerator.createMockEpub(
            file,
            pageCount = 4,
            title = "Direct Image Comic",
            fixedLayout = false
        )

        val pages = parser.getPages(file).first()

        assertThat(pages).hasSize(4)
        for (i in 0 until 4) {
            assertThat(pages[i].imageUri).isEqualTo("OEBPS/images/page${i + 1}.png")
        }
    }

    @Test
    fun extractsMetadataFromOpf() {
        val file = File(tempDir, "metadata_test.epub")
        TestComicGenerator.createMockEpub(
            file,
            pageCount = 3,
            title = "Invincible #1"
        )

        val metadata = parser.extractMetadata(file)

        assertThat(metadata.title).isEqualTo("Invincible #1")
        assertThat(metadata.pageCount).isEqualTo(3)
        assertThat(metadata.format).isEqualTo(ComicFormat.EPUB)
    }

    @Test
    fun extractsCoverImageToCache() {
        val file = File(tempDir, "cover_epub.epub")
        TestComicGenerator.createMockEpub(file, pageCount = 3)

        val cover = parser.extractCover(file, cacheDir)

        assertThat(cover).isNotNull()
        assertThat(cover!!.exists()).isTrue()
        assertThat(cover.length()).isGreaterThan(0L)
        assertThat(cover.name).startsWith("cover_epub_cover")
    }

    @Test
    fun throwsInvalidEpubExceptionWhenContainerXmlIsMissing() {
        val file = File(tempDir, "no_container.epub")
        TestComicGenerator.createMockEpub(file, pageCount = 2, missingContainer = true)

        assertThrows(InvalidEpubException::class.java) {
            parser.extractMetadata(file)
        }
    }

    @Test
    fun handlesCorruptedEpubGracefully() = runBlocking {
        val file = File(tempDir, "corrupt.epub")
        TestComicGenerator.createCorruptFile(file, "epub")

        val metadata = parser.extractMetadata(file)
        assertThat(metadata.title).isEqualTo("corrupt")
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = parser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = parser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun resolvePathHandlesRelativeNavigationCorrectly() {
        assertThat(parser.resolvePath("OEBPS/pages", "../images/page1.png"))
            .isEqualTo("OEBPS/images/page1.png")

        assertThat(parser.resolvePath("OEBPS", "images/page1.png"))
            .isEqualTo("OEBPS/images/page1.png")

        assertThat(parser.resolvePath("", "images/page1.png"))
            .isEqualTo("images/page1.png")
    }
}
