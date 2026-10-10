package com.comicreader.format

import com.comicreader.domain.model.ComicFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComicParserFactoryTest {

    private lateinit var cbzParser: CbzParser
    private lateinit var cbrParser: CbrParser
    private lateinit var pdfParser: PdfParser
    private lateinit var epubParser: EpubParser
    private lateinit var factory: ComicParserFactory

    @Before
    fun setUp() {
        cbzParser = CbzParser()
        cbrParser = CbrParser()
        pdfParser = PdfParser()
        epubParser = EpubParser()
        factory = ComicParserFactory(cbzParser, cbrParser, pdfParser, epubParser)
    }

    @Test
    fun returnsCorrectParserByExtension() {
        assertThat(factory.getParser("test.cbz")).isSameInstanceAs(cbzParser)
        assertThat(factory.getParser("test.zip")).isSameInstanceAs(cbzParser)
        assertThat(factory.getParser("test.cbr")).isSameInstanceAs(cbrParser)
        assertThat(factory.getParser("test.rar")).isSameInstanceAs(cbrParser)
        assertThat(factory.getParser("test.pdf")).isSameInstanceAs(pdfParser)
        assertThat(factory.getParser("test.epub")).isSameInstanceAs(epubParser)
    }

    @Test
    fun returnsNullForUnsupportedExtensions() {
        assertThat(factory.getParser("test.txt")).isNull()
        assertThat(factory.getParser("test.mp4")).isNull()
        assertThat(factory.getParser("test.docx")).isNull()
    }

    @Test
    fun isSupportedIdentifiesValidFormats() {
        assertThat(factory.isSupported("comic.cbz")).isTrue()
        assertThat(factory.isSupported("comic.cbr")).isTrue()
        assertThat(factory.isSupported("comic.pdf")).isTrue()
        assertThat(factory.isSupported("comic.epub")).isTrue()
        assertThat(factory.isSupported("notes.txt")).isFalse()
    }

    @Test
    fun getParserForFormatReturnsMatchingParser() {
        assertThat(factory.getParserForFormat(ComicFormat.CBZ)).isSameInstanceAs(cbzParser)
        assertThat(factory.getParserForFormat(ComicFormat.CBR)).isSameInstanceAs(cbrParser)
        assertThat(factory.getParserForFormat(ComicFormat.PDF)).isSameInstanceAs(pdfParser)
        assertThat(factory.getParserForFormat(ComicFormat.EPUB)).isSameInstanceAs(epubParser)
    }

    @Test
    fun getAllParsersContainsAllFourParsers() {
        val parsers = factory.getAllParsers()
        assertThat(parsers).hasSize(4)
        assertThat(parsers).containsExactly(cbzParser, cbrParser, pdfParser, epubParser)
    }
}
