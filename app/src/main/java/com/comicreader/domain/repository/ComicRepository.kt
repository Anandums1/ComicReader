package com.comicreader.domain.repository

import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

interface ComicRepository {
    fun getAllComics(sortOrder: SortOrder = SortOrder.TITLE): Flow<List<Comic>>
    fun getComicById(id: Long): Flow<Comic?>
    suspend fun getComicByIdOnce(id: Long): Comic?
    fun searchComics(query: String, format: String? = null): Flow<List<Comic>>
    fun getRecentHistory(limit: Int = 50): Flow<List<Comic>>
    fun getRecentlyRead(limit: Int = 50): Flow<List<Comic>>
    fun getFavoriteComics(): Flow<List<Comic>>
    fun getFavorites(): Flow<List<Comic>>
    fun filterComics(
        query: String? = null,
        format: ComicFormat? = null,
        onlyFavorites: Boolean = false,
        sortOrder: SortOrder = SortOrder.TITLE
    ): Flow<List<Comic>>
    suspend fun insertOrUpdateComic(comic: Comic): Long
    suspend fun updateReadingProgress(comicId: Long, page: Int, totalPages: Int = 0)
    suspend fun toggleFavorite(comicId: Long, isFavorite: Boolean)
    suspend fun deleteComic(comic: Comic)
    suspend fun deleteComicById(id: Long)
}

