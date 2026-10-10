package com.comicreader.format

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
class CbrParserTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var parser: CbrParser

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "cbr_parser_test_${System.nanoTime()}")
        tempDir.mkdirs()
        cacheDir = File(tempDir, "cache").apply { mkdirs() }
        parser = CbrParser()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun canParseReturnsTrueForCbrAndRarExtensions() {
        assertThat(parser.canParse("batman.cbr")).isTrue()
        assertThat(parser.canParse("batman.rar")).isTrue()
        assertThat(parser.canParse("batman.CBR")).isTrue()
        assertThat(parser.canParse("batman.cbz")).isFalse()
        assertThat(parser.canParse("batman.pdf")).isFalse()
        assertThat(parser.canParse("batman.epub")).isFalse()
    }

    @Test
    fun detectsRar5ArchivesViaHeaderInspection() {
        val rar5File = File(tempDir, "test_rar5.cbr")
        TestComicGenerator.createMockCbr(rar5File, isRar5 = true)

        assertThat(parser.isRar5(rar5File)).isTrue()

        val nonRar5File = File(tempDir, "test_rar4.cbr")
        TestComicGenerator.createMockCbr(nonRar5File, isRar5 = false)

        assertThat(parser.isRar5(nonRar5File)).isFalse()
    }

    @Test
    fun throwsUnsupportedRarV5ExceptionWhenExtractingRar5Metadata() {
        val rar5File = File(tempDir, "test_rar5.cbr")
        TestComicGenerator.createMockCbr(rar5File, isRar5 = true)

        assertThrows(UnsupportedRarV5Exception::class.java) {
            parser.extractMetadata(rar5File)
        }
    }

    @Test
    fun throwsUnsupportedRarV5ExceptionWhenGettingRar5Pages() {
        val rar5File = File(tempDir, "test_rar5.cbr")
        TestComicGenerator.createMockCbr(rar5File, isRar5 = true)

        assertThrows(UnsupportedRarV5Exception::class.java) {
            runBlocking {
                parser.getPages(rar5File).first()
            }
        }
    }

    @Test
    fun extractCoverReturnsNullForRar5() {
        val rar5File = File(tempDir, "test_rar5.cbr")
        TestComicGenerator.createMockCbr(rar5File, isRar5 = true)

        val cover = parser.extractCover(rar5File, cacheDir)
        assertThat(cover).isNull()
    }

    @Test
    fun handlesCorruptCbrGracefullyWithoutCrashing() = runBlocking {
        val corruptFile = File(tempDir, "corrupt.cbr")
        TestComicGenerator.createMockCbr(corruptFile, corrupt = true)

        val metadata = parser.extractMetadata(corruptFile)
        assertThat(metadata.title).isEqualTo("corrupt")
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = parser.extractCover(corruptFile, cacheDir)
        assertThat(cover).isNull()

        val pages = parser.getPages(corruptFile).first()
        assertThat(pages).isEmpty()
    }

    @Test
    fun handlesEmptyOrNonExistentFileGracefully() = runBlocking {
        val nonExistent = File(tempDir, "does_not_exist.cbr")

        val metadata = parser.extractMetadata(nonExistent)
        assertThat(metadata.pageCount).isEqualTo(0)

        val cover = parser.extractCover(nonExistent, cacheDir)
        assertThat(cover).isNull()

        val pages = parser.getPages(nonExistent).first()
        assertThat(pages).isEmpty()
    }
}
