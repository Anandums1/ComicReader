package com.comicreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicreader.data.local.db.entity.ComicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY title ASC")
    fun getAllComics(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics ORDER BY addedTimestamp DESC")
    fun getComicsRecentlyAdded(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC LIMIT :limit")
    fun getRecentlyRead(limit: Int = 50): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE id = :id")
    fun getComicById(id: Long): Flow<ComicEntity?>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getComicByIdOnce(id: Long): ComicEntity?

    @Query("SELECT * FROM comics WHERE filePath = :filePath LIMIT 1")
    suspend fun getComicByPath(filePath: String): ComicEntity?

    @Query("""
        SELECT * FROM comics 
        WHERE (:query IS NULL OR title LIKE '%' || :query || '%')
          AND (:format IS NULL OR format = :format)
          AND (:onlyFavorites = 0 OR isFavorite = 1)
        ORDER BY 
          CASE WHEN :sortOrder = 'TITLE' THEN title END ASC,
          CASE WHEN :sortOrder = 'RECENTLY_ADDED' THEN addedTimestamp END DESC,
          CASE WHEN :sortOrder = 'LAST_READ' THEN lastReadTimestamp END DESC
    """)
    fun filterComics(
        query: String?,
        format: String?,
        onlyFavorites: Boolean,
        sortOrder: String
    ): Flow<List<ComicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComic(comic: ComicEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertComics(comics: List<ComicEntity>): List<Long>

    @Update
    suspend fun updateComic(comic: ComicEntity)

    @Query("UPDATE comics SET currentPage = :page, lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, page: Int, timestamp: Long)

    @Query("UPDATE comics SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteComic(comic: ComicEntity)

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun deleteComicById(id: Long)

    @Query("SELECT COUNT(*) FROM comics")
    fun getTotalComicsCount(): Flow<Int>
}

