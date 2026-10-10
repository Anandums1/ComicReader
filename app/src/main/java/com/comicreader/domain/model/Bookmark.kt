package com.comicreader.domain.model

data class Bookmark(
    val id: Long = 0,
    val comicId: Long,
    val pageNumber: Int,
    val thumbnailPath: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

