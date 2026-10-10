package com.comicreader.di

import android.content.Context
import androidx.room.Room
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.local.db.dao.BookmarkDao
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.dao.ReadingStatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideComicDatabase(
        @ApplicationContext context: Context
    ): ComicDatabase {
        return Room.databaseBuilder(
            context,
            ComicDatabase::class.java,
            "comic_reader.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideComicDao(database: ComicDatabase): ComicDao {
        return database.comicDao()
    }

    @Provides
    fun provideBookmarkDao(database: ComicDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    fun provideReadingStatsDao(database: ComicDatabase): ReadingStatsDao {
        return database.readingStatsDao()
    }
}

