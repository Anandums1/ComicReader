package com.comicreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.SortOrder
import com.comicreader.domain.repository.ComicRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComicRepositoryTest {

    private lateinit var database: ComicDatabase
    private lateinit var repository: ComicRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ComicRepositoryImpl(database.comicDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetComicDomainMapping() = runBlocking {
        val comic = Comic(
            id = 1L,
            title = "Superman Annual",
            filePath = "/comics/superman.cbz",
            format = ComicFormat.CBZ,
            pageCount = 48,
            currentPage = 24
        )

        val id = repository.insertOrUpdateComic(comic)
        assertThat(id).isEqualTo(1L)

        val retrieved = repository.getComicByIdOnce(1L)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.title).isEqualTo("Superman Annual")
        assertThat(retrieved?.format).isEqualTo(ComicFormat.CBZ)
        assertThat(retrieved?.progress).isWithin(0.01f).of(24f / 47f)
        assertThat(retrieved?.isCompleted).isFalse()
    }

    @Test
    fun updateReadingProgressAndCompletion() = runBlocking {
        val comic = Comic(
            id = 2L,
            title = "Watchmen",
            filePath = "/comics/watchmen.pdf",
            format = ComicFormat.PDF,
            pageCount = 30,
            currentPage = 0
        )
        repository.insertOrUpdateComic(comic)

        // Update to last page
        repository.updateReadingProgress(2L, 29, 30)

        val updated = repository.getComicByIdOnce(2L)
        assertThat(updated?.currentPage).isEqualTo(29)
        assertThat(updated?.isCompleted).isTrue()
        assertThat(updated?.progress).isEqualTo(1.0f)
    }

    @Test
    fun toggleFavoriteAndFilterFavorites() = runBlocking {
        val comic = Comic(
            id = 5L,
            title = "Sandman",
            filePath = "/comics/sandman.cbz",
            format = ComicFormat.CBZ,
            isFavorite = false
        )
        repository.insertOrUpdateComic(comic)

        repository.toggleFavorite(5L, true)

        val favorites = repository.getFavorites().first()
        assertThat(favorites).hasSize(1)
        assertThat(favorites.first().title).isEqualTo("Sandman")
        assertThat(favorites.first().isFavorite).isTrue()
    }
}

