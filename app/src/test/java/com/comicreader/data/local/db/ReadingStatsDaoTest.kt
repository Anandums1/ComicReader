package com.comicreader.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.dao.ReadingStatsDao
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.data.local.db.entity.ReadingStatsEntity
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
class ReadingStatsDaoTest {

    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var statsDao: ReadingStatsDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = database.comicDao()
        statsDao = database.readingStatsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordSessionsAndCalculateTotals() = runBlocking {
        val session1 = ReadingStatsEntity(
            id = 1L,
            comicId = null,
            sessionStartTimestamp = 1000L,
            sessionEndTimestamp = 1300L,
            durationSeconds = 300L,
            pagesRead = 15
        )
        val session2 = ReadingStatsEntity(
            id = 2L,
            comicId = null,
            sessionStartTimestamp = 2000L,
            sessionEndTimestamp = 2500L,
            durationSeconds = 500L,
            pagesRead = 25
        )
        statsDao.insertSession(session1)
        statsDao.insertSession(session2)

        val totalPages = statsDao.getTotalPagesRead().first()
        assertThat(totalPages).isEqualTo(40)

        val totalTime = statsDao.getTotalReadingTimeSeconds().first()
        assertThat(totalTime).isEqualTo(800L)
    }

    @Test
    fun completedAndInProgressCounts() = runBlocking {
        // Comic 1: In progress (page 10 of 20)
        val comic1 = ComicEntity(
            id = 10L,
            title = "In Progress Comic",
            filePath = "/c1.cbz",
            format = "cbz",
            pageCount = 20,
            currentPage = 10
        )
        // Comic 2: Completed (page 19 of 20 -> pageCount - 1)
        val comic2 = ComicEntity(
            id = 20L,
            title = "Finished Comic",
            filePath = "/c2.cbz",
            format = "cbz",
            pageCount = 20,
            currentPage = 19
        )
        // Comic 3: Unread (page 0 of 20)
        val comic3 = ComicEntity(
            id = 30L,
            title = "Unread Comic",
            filePath = "/c3.cbz",
            format = "cbz",
            pageCount = 20,
            currentPage = 0
        )
        comicDao.insertComic(comic1)
        comicDao.insertComic(comic2)
        comicDao.insertComic(comic3)

        val inProgress = statsDao.getInProgressComicsCount().first()
        assertThat(inProgress).isEqualTo(1)

        val completed = statsDao.getCompletedComicsCount().first()
        assertThat(completed).isEqualTo(1)
    }
}

