package com.comicreader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.comicreader.data.local.db.converter.Converters
import com.comicreader.data.local.db.dao.BookmarkDao
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.dao.ReadingStatsDao
import com.comicreader.data.local.db.entity.BookmarkEntity
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.data.local.db.entity.ReadingStatsEntity

@Database(
    entities = [
        ComicEntity::class,
        BookmarkEntity::class,
        ReadingStatsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ComicDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingStatsDao(): ReadingStatsDao
}

