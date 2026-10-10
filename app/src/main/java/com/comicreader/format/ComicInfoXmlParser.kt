package com.comicreader.format

import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.ComicMetadata
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.nio.charset.StandardCharsets

object ComicInfoXmlParser {

    fun parse(
        inputStream: InputStream,
        fallbackTitle: String,
        format: ComicFormat,
        fileSize: Long,
        fallbackPageCount: Int = 0
    ): ComicMetadata {
        var title: String? = null
        var series: String? = null
        var issueNumber: String? = null
        var volume: String? = null
        var summary: String? = null
        var writer: String? = null
        var penciller: String? = null
        var genre: String? = null
        var pageCount: Int? = null
        var year: Int? = null
        var month: Int? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, StandardCharsets.UTF_8.name())

            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag.lowercase()) {
                                "title" -> if (title == null) title = text
                                "series" -> if (series == null) series = text
                                "number" -> if (issueNumber == null) issueNumber = text
                                "volume" -> if (volume == null) volume = text
                                "summary" -> if (summary == null) summary = text
                                "writer" -> if (writer == null) writer = text
                                "penciller" -> if (penciller == null) penciller = text
                                "genre" -> if (genre == null) genre = text
                                "pagecount" -> if (pageCount == null) pageCount = text.toIntOrNull()
                                "year" -> if (year == null) year = text.toIntOrNull()
                                "month" -> if (month == null) month = text.toIntOrNull()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Gracefully ignore parsing errors and return whatever was extracted
        }

        return ComicMetadata(
            title = title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            series = series?.takeIf { it.isNotBlank() },
            issueNumber = issueNumber?.takeIf { it.isNotBlank() },
            volume = volume?.takeIf { it.isNotBlank() },
            summary = summary?.takeIf { it.isNotBlank() },
            writer = writer?.takeIf { it.isNotBlank() },
            penciller = penciller?.takeIf { it.isNotBlank() },
            genre = genre?.takeIf { it.isNotBlank() },
            pageCount = pageCount ?: fallbackPageCount,
            format = format,
            fileSize = fileSize,
            publishedYear = year,
            publishedMonth = month
        )
    }
}
