package com.comicreader.domain.model

data class Comic(
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: ComicFormat,
    val coverPath: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val lastReadTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (pageCount > 1) {
            currentPage.toFloat() / (pageCount - 1).coerceAtLeast(1)
        } else if (pageCount == 1 && currentPage >= 1) {
            1f
        } else {
            0f
        }

    val isCompleted: Boolean
        get() = if (pageCount == 1) {
            currentPage >= 1
        } else {
            pageCount > 0 && currentPage >= (pageCount - 1)
        }
}

