package com.comicreader.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.entity.ComicEntity
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
class ComicDaoTest {

    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = database.comicDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetComicById() = runBlocking {
        val comic = ComicEntity(
            id = 1L,
            title = "Spider-Man Issue 1",
            filePath = "/comics/spiderman1.cbz",
            format = "cbz",
            pageCount = 24,
            currentPage = 5
        )

        val id = comicDao.insertComic(comic)
        assertThat(id).isEqualTo(1L)

        val retrieved = comicDao.getComicById(1L).first()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.title).isEqualTo("Spider-Man Issue 1")
        assertThat(retrieved?.pageCount).isEqualTo(24)
        assertThat(retrieved?.currentPage).isEqualTo(5)
    }

    @Test
    fun updateReadingProgress() = runBlocking {
        val comic = ComicEntity(
            id = 2L,
            title = "Batman Year One",
            filePath = "/comics/batman.cbr",
            format = "cbr",
            pageCount = 30,
            currentPage = 0
        )
        comicDao.insertComic(comic)

        val timestamp = System.currentTimeMillis()
        comicDao.updateReadingProgress(id = 2L, page = 15, timestamp = timestamp)

        val updated = comicDao.getComicByIdOnce(2L)
        assertThat(updated?.currentPage).isEqualTo(15)
        assertThat(updated?.lastReadTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun filterComicsByFormatAndFavorites() = runBlocking {
        val comic1 = ComicEntity(
            id = 10L,
            title = "Alpha",
            filePath = "/comics/alpha.cbz",
            format = "cbz",
            isFavorite = true,
            addedTimestamp = 1000L
        )
        val comic2 = ComicEntity(
            id = 20L,
            title = "Beta",
            filePath = "/comics/beta.pdf",
            format = "pdf",
            isFavorite = false,
            addedTimestamp = 2000L
        )
        comicDao.insertComic(comic1)
        comicDao.insertComic(comic2)

        // Filter only favorites
        val favorites = comicDao.filterComics(
            query = null,
            format = null,
            onlyFavorites = true,
            sortOrder = "TITLE"
        ).first()
        assertThat(favorites).hasSize(1)
        assertThat(favorites.first().title).isEqualTo("Alpha")

        // Filter by format PDF
        val pdfs = comicDao.filterComics(
            query = null,
            format = "pdf",
            onlyFavorites = false,
            sortOrder = "TITLE"
        ).first()
        assertThat(pdfs).hasSize(1)
        assertThat(pdfs.first().title).isEqualTo("Beta")
    }

    @Test
    fun deleteComicRemovesRow() = runBlocking {
        val comic = ComicEntity(
            id = 99L,
            title = "To Delete",
            filePath = "/comics/delete.epub",
            format = "epub"
        )
        comicDao.insertComic(comic)
        assertThat(comicDao.getComicByIdOnce(99L)).isNotNull()

        comicDao.deleteComicById(99L)
        assertThat(comicDao.getComicByIdOnce(99L)).isNull()
    }
}

