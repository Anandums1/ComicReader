package com.comicreader.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.comicreader.data.local.db.entity.ReadingStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingStatsEntity): Long

    @Query("SELECT * FROM reading_stats ORDER BY sessionStartTimestamp DESC")
    fun getAllSessions(): Flow<List<ReadingStatsEntity>>

    @Query("SELECT COALESCE(SUM(pagesRead), 0) FROM reading_stats")
    fun getTotalPagesRead(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_stats")
    fun getTotalReadingTimeSeconds(): Flow<Long>

    @Query("SELECT COUNT(*) FROM comics WHERE currentPage >= (pageCount - 1) AND pageCount > 0")
    fun getCompletedComicsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM comics WHERE currentPage > 0 AND currentPage < (pageCount - 1)")
    fun getInProgressComicsCount(): Flow<Int>

    @Query("""
        SELECT * FROM reading_stats 
        WHERE sessionStartTimestamp >= :sinceTimestamp 
        ORDER BY sessionStartTimestamp ASC
    """)
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<ReadingStatsEntity>>
}

