package com.comicreader.domain.model

data class ComicPage(
    val pageNumber: Int,
    val imageUri: String? = null,
    val filePath: String? = null,
    val width: Int = 0,
    val height: Int = 0
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}

