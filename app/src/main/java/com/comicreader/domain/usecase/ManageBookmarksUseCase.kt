package com.comicreader.domain.usecase

import com.comicreader.domain.model.Bookmark
import com.comicreader.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    fun getBookmarks(comicId: Long): Flow<List<Bookmark>> =
        bookmarkRepository.getBookmarksForComic(comicId)

    fun getAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkRepository.getAllBookmarks()

    fun isBookmarked(comicId: Long, pageNumber: Int): Flow<Boolean> =
        bookmarkRepository.isPageBookmarked(comicId, pageNumber)

    suspend fun addBookmark(
        comicId: Long,
        pageNumber: Int,
        thumbnailPath: String? = null,
        note: String? = null
    ): Long = bookmarkRepository.addBookmark(comicId, pageNumber, thumbnailPath, note)

    suspend fun removeBookmark(comicId: Long, pageNumber: Int) =
        bookmarkRepository.deleteBookmark(comicId, pageNumber)

    suspend fun removeBookmarkById(bookmarkId: Long) =
        bookmarkRepository.deleteBookmark(bookmarkId)
}

