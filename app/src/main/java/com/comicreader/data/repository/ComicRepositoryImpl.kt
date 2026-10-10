package com.comicreader.data.repository

import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.SortOrder
import com.comicreader.domain.repository.ComicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComicRepositoryImpl @Inject constructor(
    private val comicDao: ComicDao
) : ComicRepository {

    override fun getAllComics(sortOrder: SortOrder): Flow<List<Comic>> {
        val flow = when (sortOrder) {
            SortOrder.TITLE -> comicDao.getAllComics()
            SortOrder.RECENTLY_ADDED -> comicDao.getComicsRecentlyAdded()
            SortOrder.LAST_READ -> comicDao.getRecentlyRead(Int.MAX_VALUE)
            SortOrder.FAVORITES -> comicDao.getFavorites()
        }
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    override fun getComicById(id: Long): Flow<Comic?> {
        return comicDao.getComicById(id).map { it?.toDomain() }
    }

    override suspend fun getComicByIdOnce(id: Long): Comic? {
        return comicDao.getComicByIdOnce(id)?.toDomain()
    }

    override fun searchComics(query: String, format: String?): Flow<List<Comic>> {
        val formatParam = format?.takeIf { it.isNotBlank() }
        val queryParam = query.takeIf { it.isNotBlank() }
        return comicDao.filterComics(
            query = queryParam,
            format = formatParam,
            onlyFavorites = false,
            sortOrder = "TITLE"
        ).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getRecentHistory(limit: Int): Flow<List<Comic>> {
        return comicDao.getRecentlyRead(limit).map { entities -> entities.map { it.toDomain() } }
    }

    override fun getRecentlyRead(limit: Int): Flow<List<Comic>> {
        return getRecentHistory(limit)
    }

    override fun getFavoriteComics(): Flow<List<Comic>> {
        return comicDao.getFavorites().map { entities -> entities.map { it.toDomain() } }
    }

    override fun getFavorites(): Flow<List<Comic>> {
        return getFavoriteComics()
    }

    override fun filterComics(
        query: String?,
        format: ComicFormat?,
        onlyFavorites: Boolean,
        sortOrder: SortOrder
    ): Flow<List<Comic>> {
        val sortOrderStr = when (sortOrder) {
            SortOrder.TITLE -> "TITLE"
            SortOrder.RECENTLY_ADDED -> "RECENTLY_ADDED"
            SortOrder.LAST_READ -> "LAST_READ"
            SortOrder.FAVORITES -> "TITLE"
        }
        return comicDao.filterComics(
            query = query?.takeIf { it.isNotBlank() },
            format = format?.extension,
            onlyFavorites = onlyFavorites,
            sortOrder = sortOrderStr
        ).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun insertOrUpdateComic(comic: Comic): Long {
        return comicDao.insertComic(ComicEntity.fromDomain(comic))
    }

    override suspend fun updateReadingProgress(comicId: Long, page: Int, totalPages: Int) {
        val timestamp = System.currentTimeMillis()
        comicDao.updateReadingProgress(id = comicId, page = page, timestamp = timestamp)
    }

    override suspend fun toggleFavorite(comicId: Long, isFavorite: Boolean) {
        comicDao.toggleFavorite(id = comicId, isFavorite = isFavorite)
    }

    override suspend fun deleteComic(comic: Comic) {
        comicDao.deleteComic(ComicEntity.fromDomain(comic))
    }

    override suspend fun deleteComicById(id: Long) {
        comicDao.deleteComicById(id)
    }
}

