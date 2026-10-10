package com.comicreader.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.dao.BookmarkDao
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.dao.ReadingStatsDao
import com.comicreader.data.local.db.entity.BookmarkEntity
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.data.local.db.entity.ReadingStatsEntity
import com.comicreader.data.repository.StatsRepositoryImpl
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomStressTest {

    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var statsDao: ReadingStatsDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = database.comicDao()
        bookmarkDao = database.bookmarkDao()
        statsDao = database.readingStatsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testConcurrentInsertsAndReadsUnderLoad() = runBlocking(Dispatchers.IO) {
        val totalWriters = 20
        val comicsPerWriter = 25
        val expectedTotalComics = totalWriters * comicsPerWriter // 500 comics

        // Launch concurrent writer coroutines
        val writerJobs = (0 until totalWriters).map { writerIndex ->
            async {
                for (i in 0 until comicsPerWriter) {
                    val comicId = (writerIndex * comicsPerWriter + i + 1).toLong()
                    val comic = ComicEntity(
                        id = comicId,
                        title = "Concurrent Comic $comicId",
                        filePath = "/storage/comics/writer_${writerIndex}_issue_$i.cbz",
                        format = "cbz",
                        pageCount = 32,
                        currentPage = i % 32
                    )
                    comicDao.insertComic(comic)
                }
            }
        }

        // Launch concurrent reader coroutines simultaneously
        val readCounter = AtomicInteger(0)
        val readerJobs = (0 until 20).map {
            async {
                for (j in 0 until 10) {
                    val count = comicDao.getTotalComicsCount().first()
                    if (count > 0) {
                        readCounter.incrementAndGet()
                    }
                }
            }
        }

        // Await all writer and reader tasks
        writerJobs.awaitAll()
        readerJobs.awaitAll()

        // Verify total row count
        val finalCount = comicDao.getTotalComicsCount().first()
        assertThat(finalCount).isEqualTo(expectedTotalComics)

        // Verify individual entity retrieval
        val sampleComic = comicDao.getComicByIdOnce(1L)
        assertThat(sampleComic).isNotNull()
        assertThat(sampleComic?.title).isEqualTo("Concurrent Comic 1")
        assertThat(sampleComic?.pageCount).isEqualTo(32)

        val lastComic = comicDao.getComicByIdOnce(expectedTotalComics.toLong())
        assertThat(lastComic).isNotNull()
        assertThat(lastComic?.id).isEqualTo(expectedTotalComics.toLong())
    }

    @Test
    fun testBatchInsertDeduplicationAndPerformance() = runBlocking {
        // Generate batch of 1,000 comics
        val initialBatch = (1..1000).map { i ->
            ComicEntity(
                id = i.toLong(),
                title = "Batch Comic $i",
                filePath = "/comics/batch_$i.cbz",
                format = if (i % 2 == 0) "cbz" else "cbr",
                pageCount = 50,
                currentPage = 0,
                isFavorite = (i % 5 == 0)
            )
        }

        // Insert first 1,000
        val insertedIds = comicDao.insertComics(initialBatch)
        assertThat(insertedIds.size).isEqualTo(1000)

        val countAfterFirstBatch = comicDao.getTotalComicsCount().first()
        assertThat(countAfterFirstBatch).isEqualTo(1000)

        // Now attempt to insert a mixed batch of 1,000:
        // 500 existing (id = 501..1000) and 500 brand new (id = 1001..1500)
        val mixedBatch = (501..1500).map { i ->
            ComicEntity(
                id = i.toLong(),
                title = "Batch Comic $i Updated",
                filePath = "/comics/batch_$i.cbz",
                format = "pdf",
                pageCount = 100,
                currentPage = 0
            )
        }

        // With OnConflictStrategy.IGNORE, existing 500 are ignored, new 500 are inserted
        comicDao.insertComics(mixedBatch)

        val totalCount = comicDao.getTotalComicsCount().first()
        assertThat(totalCount).isEqualTo(1500)

        // Verify ignored existing items kept their original values
        val existingItem = comicDao.getComicByIdOnce(600L)
        assertThat(existingItem).isNotNull()
        assertThat(existingItem?.title).isEqualTo("Batch Comic 600")
        assertThat(existingItem?.format).isEqualTo("cbz") // Original format preserved

        // Verify newly added item was inserted
        val newItem = comicDao.getComicByIdOnce(1200L)
        assertThat(newItem).isNotNull()
        assertThat(newItem?.title).isEqualTo("Batch Comic 1200 Updated")
        assertThat(newItem?.format).isEqualTo("pdf")
    }

    @Test
    fun testTransactionRollbackAtomicity() = runBlocking {
        // Baseline count
        val initialCount = comicDao.getTotalComicsCount().first()

        // 1. Test Room withTransaction rollback
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                database.withTransaction {
                    val comic = ComicEntity(
                        id = 777L,
                        title = "Atomic Rollback Comic",
                        filePath = "/comics/rollback_comic.cbz",
                        format = "cbz"
                    )
                    comicDao.insertComic(comic)

                    val bookmark = BookmarkEntity(
                        id = 7001L,
                        comicId = 777L,
                        pageNumber = 10,
                        note = "Bookmark inside doomed transaction"
                    )
                    bookmarkDao.insertBookmark(bookmark)

                    // Intentionally throw exception to abort transaction
                    throw IllegalStateException("Intentional transaction abort")
                }
            }
        }

        // Verify neither comic nor bookmark was saved
        assertThat(comicDao.getComicByIdOnce(777L)).isNull()
        assertThat(bookmarkDao.getBookmarksForComic(777L).first()).isEmpty()
        assertThat(comicDao.getTotalComicsCount().first()).isEqualTo(initialCount)

        // 2. Test runInTransaction rollback
        assertThrows(RuntimeException::class.java) {
            database.runInTransaction {
                runBlocking {
                    comicDao.insertComic(
                        ComicEntity(
                            id = 888L,
                            title = "RunInTransaction Comic",
                            filePath = "/comics/run_in_tx.cbz",
                            format = "cbz"
                        )
                    )
                }
                throw RuntimeException("Aborting runInTransaction")
            }
        }

        assertThat(comicDao.getComicByIdOnce(888L)).isNull()
    }

    @Test
    fun testMassiveCascadeDeletionUnderLoad() = runBlocking {
        // Insert parent comic
        val parentComic = ComicEntity(
            id = 500L,
            title = "Cascade Parent Comic",
            filePath = "/comics/cascade_parent.cbz",
            format = "cbz"
        )
        comicDao.insertComic(parentComic)

        // Insert 300 child bookmarks
        for (i in 1..300) {
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    id = 10000L + i,
                    comicId = 500L,
                    pageNumber = i,
                    note = "Page $i note"
                )
            )
        }

        // Insert 50 reading stats sessions referencing parentComic
        for (j in 1..50) {
            statsDao.insertSession(
                ReadingStatsEntity(
                    id = 20000L + j,
                    comicId = 500L,
                    sessionStartTimestamp = 1000L * j,
                    sessionEndTimestamp = 1000L * j + 60L,
                    durationSeconds = 60L,
                    pagesRead = 5
                )
            )
        }

        // Verify initial state
        val bookmarksBefore = bookmarkDao.getBookmarksForComic(500L).first()
        assertThat(bookmarksBefore).hasSize(300)

        val sessionsBefore = statsDao.getAllSessions().first().filter { it.comicId == 500L }
        assertThat(sessionsBefore).hasSize(50)

        // Execute parent deletion
        comicDao.deleteComicById(500L)

        // Verify parent deleted
        assertThat(comicDao.getComicByIdOnce(500L)).isNull()

        // Verify CASCADE: all 300 bookmarks must be automatically deleted
        val bookmarksAfter = bookmarkDao.getBookmarksForComic(500L).first()
        assertThat(bookmarksAfter).isEmpty()

        // Verify SET_NULL: all 50 reading sessions must still exist, but with comicId set to null
        val allSessionsAfter = statsDao.getAllSessions().first()
        assertThat(allSessionsAfter).hasSize(50)
        assertThat(allSessionsAfter.all { it.comicId == null }).isTrue()
        assertThat(statsDao.getTotalPagesRead().first()).isEqualTo(250) // 50 * 5
    }

    @Test
    fun testForeignKeyConstraintEnforcement() = runBlocking {
        // Attempting to insert a bookmark pointing to non-existent comic ID 999999L
        // should violate foreign key constraint
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                bookmarkDao.insertBookmark(
                    BookmarkEntity(
                        id = 999L,
                        comicId = 999999L, // Does not exist
                        pageNumber = 1
                    )
                )
            }
        }

        // Direct insert of reading stats pointing to non-existent comic ID should also fail FK constraint
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                statsDao.insertSession(
                    ReadingStatsEntity(
                        id = 998L,
                        comicId = 999999L, // Does not exist
                        sessionStartTimestamp = 1000L,
                        sessionEndTimestamp = 2000L,
                        durationSeconds = 1000L,
                        pagesRead = 10
                    )
                )
            }
        }

        // But StatsRepositoryImpl safely sanitizes non-existent comicId to null to prevent crash
        val statsRepository = StatsRepositoryImpl(statsDao, comicDao)
        val sessionId = statsRepository.recordReadingSession(
            comicId = 999999L, // Non-existent
            pagesRead = 12,
            durationSeconds = 120L
        )
        assertThat(sessionId).isGreaterThan(0L)

        val recordedSession = statsDao.getAllSessions().first().first { it.id == sessionId }
        assertThat(recordedSession.comicId).isNull()
        assertThat(recordedSession.pagesRead).isEqualTo(12)
    }

    @Test
    fun testUniqueConstraintsAndConflictResolution() = runBlocking {
        // 1. ComicEntity unique filePath constraint with REPLACE strategy
        val comic1 = ComicEntity(
            id = 1L,
            title = "First Version",
            filePath = "/comics/unique_path.cbz",
            format = "cbz"
        )
        comicDao.insertComic(comic1)

        val comic2 = ComicEntity(
            id = 2L,
            title = "Second Version Replaced",
            filePath = "/comics/unique_path.cbz", // Same unique filePath
            format = "cbz"
        )
        comicDao.insertComic(comic2)

        val totalComics = comicDao.getTotalComicsCount().first()
        assertThat(totalComics).isEqualTo(1)

        val retrieved = comicDao.getComicByPath("/comics/unique_path.cbz")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.title).isEqualTo("Second Version Replaced")

        // 2. BookmarkEntity composite unique (comicId, pageNumber) with REPLACE strategy
        val parentId = retrieved!!.id
        val bm1 = BookmarkEntity(
            id = 10L,
            comicId = parentId,
            pageNumber = 15,
            note = "Old Note"
        )
        bookmarkDao.insertBookmark(bm1)

        val bm2 = BookmarkEntity(
            id = 11L,
            comicId = parentId,
            pageNumber = 15, // Same page for same comic
            note = "New Updated Note"
        )
        bookmarkDao.insertBookmark(bm2)

        val bookmarks = bookmarkDao.getBookmarksForComic(parentId).first()
        assertThat(bookmarks).hasSize(1)
        assertThat(bookmarks.first().note).isEqualTo("New Updated Note")
    }

    @Test
    fun testComplexFilteringAndSortingUnderLoad() = runBlocking {
        // Insert 200 diverse comics
        val comics = (1..200).map { i ->
            val format = when (i % 4) {
                0 -> "cbz"
                1 -> "cbr"
                2 -> "pdf"
                else -> "epub"
            }
            ComicEntity(
                id = i.toLong(),
                title = "Hero Saga Volume ${String.format("%03d", i)}",
                filePath = "/comics/saga_$i.$format",
                format = format,
                isFavorite = (i % 3 == 0),
                addedTimestamp = 1000L + i,
                lastReadTimestamp = if (i % 2 == 0) 5000L + i else 0L
            )
        }
        comicDao.insertComics(comics)

        // Test filtering by format + favorites only
        val cbzFavorites = comicDao.filterComics(
            query = null,
            format = "cbz",
            onlyFavorites = true,
            sortOrder = "TITLE"
        ).first()

        assertThat(cbzFavorites).isNotEmpty()
        assertThat(cbzFavorites.all { it.format == "cbz" && it.isFavorite }).isTrue()

        // Test filtering by query string
        val queryResult = comicDao.filterComics(
            query = "Volume 05",
            format = null,
            onlyFavorites = false,
            sortOrder = "TITLE"
        ).first()

        // Should match Volume 050 to 059
        assertThat(queryResult).hasSize(10)
        assertThat(queryResult.all { it.title.contains("Volume 05") }).isTrue()

        // Test sort by LAST_READ DESC
        val lastReadSorted = comicDao.filterComics(
            query = null,
            format = null,
            onlyFavorites = false,
            sortOrder = "LAST_READ"
        ).first()

        // Ensure first non-zero item has highest timestamp
        val nonZeroRead = lastReadSorted.filter { it.lastReadTimestamp > 0 }
        for (k in 0 until nonZeroRead.size - 1) {
            assertThat(nonZeroRead[k].lastReadTimestamp).isAtLeast(nonZeroRead[k + 1].lastReadTimestamp)
        }
    }
}
