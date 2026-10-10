package com.comicreader.format

import com.comicreader.domain.model.ComicFormat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComicParserFactory @Inject constructor(
    private val cbzParser: CbzParser,
    private val cbrParser: CbrParser,
    private val pdfParser: PdfParser,
    private val epubParser: EpubParser
) {

    private val parsers: List<ComicParser> = listOf(
        cbzParser,
        cbrParser,
        pdfParser,
        epubParser
    )

    fun getParser(filePath: String): ComicParser? {
        val format = ComicFormat.fromFilePath(filePath)
        if (format != null) {
            return getParserForFormat(format)
        }
        return parsers.firstOrNull { it.canParse(filePath) }
    }

    fun getParser(file: File): ComicParser? = getParser(file.absolutePath)

    fun getParserForFormat(format: ComicFormat): ComicParser {
        return when (format) {
            ComicFormat.CBZ -> cbzParser
            ComicFormat.CBR -> cbrParser
            ComicFormat.PDF -> pdfParser
            ComicFormat.EPUB -> epubParser
        }
    }

    fun isSupported(filePath: String): Boolean {
        return getParser(filePath) != null
    }

    fun isSupported(file: File): Boolean = isSupported(file.absolutePath)

    fun getAllParsers(): List<ComicParser> = parsers
}
