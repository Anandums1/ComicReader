package com.comicreader.di

import com.comicreader.data.repository.BookmarkRepositoryImpl
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.data.repository.StatsRepositoryImpl
import com.comicreader.domain.repository.BookmarkRepository
import com.comicreader.domain.repository.ComicRepository
import com.comicreader.domain.repository.StatsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindComicRepository(
        comicRepositoryImpl: ComicRepositoryImpl
    ): ComicRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        bookmarkRepositoryImpl: BookmarkRepositoryImpl
    ): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(
        statsRepositoryImpl: StatsRepositoryImpl
    ): StatsRepository
}

