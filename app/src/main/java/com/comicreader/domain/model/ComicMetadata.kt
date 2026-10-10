package com.comicreader.domain.model

data class ComicMetadata(
    val title: String,
    val series: String? = null,
    val issueNumber: String? = null,
    val volume: String? = null,
    val summary: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val genre: String? = null,
    val pageCount: Int = 0,
    val format: ComicFormat = ComicFormat.CBZ,
    val fileSize: Long = 0L,
    val publishedYear: Int? = null,
    val publishedMonth: Int? = null
)
