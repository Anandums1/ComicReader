package com.comicreader.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.dao.BookmarkDao
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.entity.BookmarkEntity
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
class BookmarkDaoTest {

    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var bookmarkDao: BookmarkDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = database.comicDao()
        bookmarkDao = database.bookmarkDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetBookmarksForComic() = runBlocking {
        val comic = ComicEntity(
            id = 1L,
            title = "X-Men",
            filePath = "/comics/xmen.cbz",
            format = "cbz"
        )
        comicDao.insertComic(comic)

        val bookmark1 = BookmarkEntity(
            id = 101L,
            comicId = 1L,
            pageNumber = 4,
            note = "Great splash page"
        )
        val bookmark2 = BookmarkEntity(
            id = 102L,
            comicId = 1L,
            pageNumber = 12,
            note = "Plot twist"
        )
        bookmarkDao.insertBookmark(bookmark1)
        bookmarkDao.insertBookmark(bookmark2)

        val bookmarks = bookmarkDao.getBookmarksForComic(1L).first()
        assertThat(bookmarks).hasSize(2)
        assertThat(bookmarks[0].pageNumber).isEqualTo(4)
        assertThat(bookmarks[1].pageNumber).isEqualTo(12)

        val isBookmarked4 = bookmarkDao.isPageBookmarked(1L, 4).first()
        assertThat(isBookmarked4).isTrue()

        val isBookmarked5 = bookmarkDao.isPageBookmarked(1L, 5).first()
        assertThat(isBookmarked5).isFalse()
    }

    @Test
    fun deleteBookmarkByPage() = runBlocking {
        val comic = ComicEntity(
            id = 2L,
            title = "Avengers",
            filePath = "/comics/avengers.cbz",
            format = "cbz"
        )
        comicDao.insertComic(comic)

        val bookmark = BookmarkEntity(
            id = 201L,
            comicId = 2L,
            pageNumber = 8
        )
        bookmarkDao.insertBookmark(bookmark)
        assertThat(bookmarkDao.isPageBookmarked(2L, 8).first()).isTrue()

        bookmarkDao.deleteBookmarkByPage(2L, 8)
        assertThat(bookmarkDao.isPageBookmarked(2L, 8).first()).isFalse()
    }

    @Test
    fun cascadeDeleteWhenComicDeleted() = runBlocking {
        val comic = ComicEntity(
            id = 3L,
            title = "Saga",
            filePath = "/comics/saga.cbr",
            format = "cbr"
        )
        comicDao.insertComic(comic)

        val bookmark = BookmarkEntity(
            id = 301L,
            comicId = 3L,
            pageNumber = 1
        )
        bookmarkDao.insertBookmark(bookmark)
        assertThat(bookmarkDao.getBookmarksForComic(3L).first()).hasSize(1)

        // Delete parent comic
        comicDao.deleteComicById(3L)

        // Child bookmark should be cascaded
        val remaining = bookmarkDao.getBookmarksForComic(3L).first()
        assertThat(remaining).isEmpty()
    }
}

