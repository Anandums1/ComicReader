package com.comicreader.domain.repository

import com.comicreader.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getBookmarksForComic(comicId: Long): Flow<List<Bookmark>>
    fun getAllBookmarks(): Flow<List<Bookmark>>
    fun isPageBookmarked(comicId: Long, pageNumber: Int): Flow<Boolean>
    suspend fun addBookmark(bookmark: Bookmark): Long
    suspend fun addBookmark(comicId: Long, pageNumber: Int, thumbnailPath: String? = null, note: String? = null): Long
    suspend fun deleteBookmark(bookmarkId: Long)
    suspend fun deleteBookmark(comicId: Long, pageNumber: Int)
}

