package com.comicreader.data.repository

import com.comicreader.data.local.db.dao.BookmarkDao
import com.comicreader.data.local.db.entity.BookmarkEntity
import com.comicreader.domain.model.Bookmark
import com.comicreader.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun getBookmarksForComic(comicId: Long): Flow<List<Bookmark>> {
        return bookmarkDao.getBookmarksForComic(comicId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun isPageBookmarked(comicId: Long, pageNumber: Int): Flow<Boolean> {
        return bookmarkDao.isPageBookmarked(comicId, pageNumber)
    }

    override suspend fun addBookmark(bookmark: Bookmark): Long {
        return bookmarkDao.insertBookmark(BookmarkEntity.fromDomain(bookmark))
    }

    override suspend fun addBookmark(
        comicId: Long,
        pageNumber: Int,
        thumbnailPath: String?,
        note: String?
    ): Long {
        val entity = BookmarkEntity(
            comicId = comicId,
            pageNumber = pageNumber,
            thumbnailPath = thumbnailPath,
            note = note,
            createdAt = System.currentTimeMillis()
        )
        return bookmarkDao.insertBookmark(entity)
    }

    override suspend fun deleteBookmark(bookmarkId: Long) {
        bookmarkDao.deleteBookmarkById(bookmarkId)
    }

    override suspend fun deleteBookmark(comicId: Long, pageNumber: Int) {
        bookmarkDao.deleteBookmarkByPage(comicId, pageNumber)
    }
}

