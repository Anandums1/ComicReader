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
class CbzParserTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var parser: CbzParser

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "cbz_parser_test_${System.nanoTime()}")
        tempDir.mkdirs()
        cacheDir = File(tempDir, "cache").apply { mkdirs() }
        parser = CbzParser()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun canParseReturnsTrueForCbzAndZipExtensions() {
        assertThat(parser.canParse("comic.cbz")).isTrue()
        assertThat(parser.canParse("comic.zip")).isTrue()
        assertThat(parser.canParse("comic.CBZ")).isTrue()
        assertThat(parser.canParse("comic.cbr")).isFalse()
        assertThat(parser.canParse("comic.pdf")).isFalse()
        assertThat(parser.canParse("comic.epub")).isFalse()
    }

    @Test
    fun extractsOrderedPagesWithNaturalSort() = runBlocking {
        val file = File(tempDir, "natural_cbz.cbz")
        val filenames = listOf("page10.png", "page1.png", "page2.png", "page20.png")
        TestComicGenerator.createMockCbz(file, customFilenames = filenames)

        val pages = parser.getPages(file).first()

        assertThat(pages).hasSize(4)
        assertThat(pages[0].imageUri).isEqualTo("page1.png")
        assertThat(pages[1].imageUri).isEqualTo("page2.png")
        assertThat(pages[2].imageUri).isEqualTo("page10.png")
        assertThat(pages[3].imageUri).isEqualTo("page20.png")
    }

    @Test
    fun filtersOutMacOsArtifactsAndNonImageFiles() = runBlocking {
        val file = File(tempDir, "artifacts_cbz.cbz")
        TestComicGenerator.createMockCbz(
            file,
            pageCount = 3,
            includeExtraFiles = true
        )

        val pages = parser.getPages(file).first()

        // Mock generator includes 3 pages plus .DS_Store, __MACOSX/._page001.png, readme.txt
        assertThat(pages).hasSize(3)
        pages.forEach { page ->
            assertThat(page.imageUri).doesNotContain("__MACOSX")
            assertThat(page.imageUri).doesNotContain(".DS_Store")
            assertThat(page.imageUri).doesNotContain("readme.txt")
        }
    }

    @Test
    fun extractsComicInfoXmlMetadata() {
        val file = File(tempDir, "metadata_cbz.cbz")
        TestComicGenerator.createMockCbz(
            file,
            pageCount = 5,
            title = "Spider-Man #1",
            series = "The Amazing Spider-Man",
            writer = "Stan Lee"
        )

        val metadata = parser.extractMetadata(file)

        assertThat(metadata.title).isEqualTo("Spider-Man #1")
        assertThat(metadata.series).isEqualTo("The Amazing Spider-Man")
        assertThat(metadata.writer).isEqualTo("Stan Lee")
        assertThat(metadata.pageCount).isEqualTo(5)
        assertThat(metadata.format).isEqualTo(ComicFormat.CBZ)
    }

    @Test
    fun extractsFallbackMetadataWhenComicInfoXmlIsMissing() {
        val file = File(tempDir, "NoMetadataComic.cbz")
        TestComicGenerator.createMockCbz(
            file,
            pageCount = 3,
            title = null // Omits ComicInfo.xml
        )

        val metadata = parser.extractMetadata(file)

        assertThat(metadata.title).isEqualTo("NoMetadataComic")
        assertThat(metadata.pageCount).isEqualTo(3)
        assertThat(metadata.format).isEqualTo(ComicFormat.CBZ)
    }

    @Test
    fun extractsCoverToCacheDir() {
        val file = File(tempDir, "cover_test.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 3)

        val cover = parser.extractCover(file, cacheDir)

        assertThat(cover).isNotNull()
        assertThat(cover!!.exists()).isTrue()
        assertThat(cover.length()).isGreaterThan(0L)
        assertThat(cover.name).startsWith("cover_test_cover")
    }

    @Test
    fun handlesCorruptedZipGracefullyWithoutCrashing() = runBlocking {
        val file = File(tempDir, "corrupted.cbz")
        TestComicGenerator.createCorruptFile(file, "cbz")

        val metadata = parser.extractMetadata(file)
        assertThat(metadata.title).isEqualTo("corrupted")
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = parser.extractCover(file, cacheDir)
        assertThat(cover).isNull()

        val pages = parser.getPages(file).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun handlesEmptyOrNonExistentFileGracefully() = runBlocking {
        val nonExistent = File(tempDir, "does_not_exist.cbz")

        val metadata = parser.extractMetadata(nonExistent)
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = parser.extractCover(nonExistent, cacheDir)
        assertThat(cover).isNull()

        val pages = parser.getPages(nonExistent).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun loadsPageBitmapSuccessfully() = runBlocking {
        val file = File(tempDir, "bitmap_test.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 2)

        val pages = parser.getPages(file).first()
        assertThat(pages).isNotEmpty()

        val bitmap = parser.loadPageBitmap(pages.first(), targetWidth = 100, targetHeight = 150)
        assertThat(bitmap).isNotNull()
    }
}
