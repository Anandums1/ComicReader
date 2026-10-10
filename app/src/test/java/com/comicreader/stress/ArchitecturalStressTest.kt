package com.comicreader.stress

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.data.local.datastore.userDataStore
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.data.repository.BookmarkRepositoryImpl
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.data.repository.StatsRepositoryImpl
import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.Bookmark
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import com.comicreader.domain.model.SortOrder
import com.comicreader.domain.usecase.GetComicPagesUseCase
import com.comicreader.domain.usecase.GetLibraryUseCase
import com.comicreader.domain.usecase.GetReadingStatsUseCase
import com.comicreader.domain.usecase.ManageBookmarksUseCase
import com.comicreader.domain.usecase.SaveReadingProgressUseCase
import com.comicreader.domain.usecase.SearchComicsUseCase
import com.comicreader.domain.usecase.ToggleFavoriteUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Architectural Boundary & Extreme Input Stress Harness
 * Empirically challenges data persistence, mapping, domain use cases, and DataStore
 * under edge cases, corrupt inputs, boundary values, and constraint collisions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArchitecturalStressTest {

    private lateinit var context: Context
    private lateinit var database: ComicDatabase
    private lateinit var comicRepo: ComicRepositoryImpl
    private lateinit var bookmarkRepo: BookmarkRepositoryImpl
    private lateinit var statsRepo: StatsRepositoryImpl
    private lateinit var dataStore: UserPreferencesDataStore

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext<Context>()
            database = Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            comicRepo = ComicRepositoryImpl(database.comicDao())
            bookmarkRepo = BookmarkRepositoryImpl(database.bookmarkDao())
            statsRepo = StatsRepositoryImpl(database.readingStatsDao(), database.comicDao())
            context.userDataStore.edit { it.clear() }
            dataStore = UserPreferencesDataStore(context)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            context.userDataStore.edit { it.clear() }
            database.close()
        }
    }

    // =========================================================================
    // 1. EMPTY STRINGS & SPECIAL CHARACTERS STRESS TESTS
    // =========================================================================

    @Test
    fun testEmptyStringsAndSpecialCharactersInComics() = runBlocking {
        val longString = "A".repeat(10000)
        val sqlInjection = "'; DROP TABLE comics; SELECT * FROM bookmarks WHERE '1'='1"
        val unicodeTitle = "コミック 漫画 🦸‍♂️ 🚀 <xml>&\"'</xml>"
        val strangePath = "C:\\Comics\\Special [2024] / #1 & (Vol. 2) %3F.cbz"

        val extremeComic1 = Comic(
            id = 1L,
            title = "",
            filePath = "",
            format = ComicFormat.CBZ,
            pageCount = 0
        )
        val extremeComic2 = Comic(
            id = 2L,
            title = sqlInjection,
            filePath = strangePath,
            format = ComicFormat.CBR,
            pageCount = 50
        )
        val extremeComic3 = Comic(
            id = 3L,
            title = unicodeTitle,
            filePath = "/comics/unicode.pdf",
            format = ComicFormat.PDF,
            pageCount = 100
        )
        val extremeComic4 = Comic(
            id = 4L,
            title = longString,
            filePath = "/comics/long_string.epub",
            format = ComicFormat.EPUB,
            pageCount = 200
        )

        comicRepo.insertOrUpdateComic(extremeComic1)
        comicRepo.insertOrUpdateComic(extremeComic2)
        comicRepo.insertOrUpdateComic(extremeComic3)
        comicRepo.insertOrUpdateComic(extremeComic4)

        // Verify retrieval of empty string comic
        val retrieved1 = comicRepo.getComicByIdOnce(1L)
        assertThat(retrieved1).isNotNull()
        assertThat(retrieved1?.title).isEqualTo("")
        assertThat(retrieved1?.filePath).isEqualTo("")

        // Verify SQL injection didn't disrupt database
        val retrieved2 = comicRepo.getComicByIdOnce(2L)
        assertThat(retrieved2).isNotNull()
        assertThat(retrieved2?.title).isEqualTo(sqlInjection)
        assertThat(retrieved2?.filePath).isEqualTo(strangePath)

        // Verify Unicode and emoji support
        val retrieved3 = comicRepo.getComicByIdOnce(3L)
        assertThat(retrieved3?.title).isEqualTo(unicodeTitle)

        // Verify 10,000 char string support
        val retrieved4 = comicRepo.getComicByIdOnce(4L)
        assertThat(retrieved4?.title).isEqualTo(longString)

        // Verify table integrity: total comics should be exactly 4
        val all = comicRepo.getAllComics(SortOrder.TITLE).first()
        assertThat(all).hasSize(4)
    }

    @Test
    fun testSearchComicsUnderAdversarialQueries() = runBlocking {
        val comic = Comic(
            id = 10L,
            title = "100% Cotton & Wool _ Special",
            filePath = "/comics/cotton.cbz",
            format = ComicFormat.CBZ
        )
        comicRepo.insertOrUpdateComic(comic)

        val searchUseCase = SearchComicsUseCase(comicRepo)

        // Empty query -> returns all matching format
        val rEmpty = searchUseCase("", ComicFormat.CBZ).first()
        assertThat(rEmpty).hasSize(1)

        // Blank query (whitespace) -> returns all matching format
        val rBlank = searchUseCase("     ", ComicFormat.CBZ).first()
        assertThat(rBlank).hasSize(1)

        // SQL wildcard % query -> matches title containing %
        val rWildcardPercent = searchUseCase("%", null).first()
        assertThat(rWildcardPercent).hasSize(1)

        // SQL wildcard _ query -> matches title containing _
        val rWildcardUnderscore = searchUseCase("_", null).first()
        assertThat(rWildcardUnderscore).hasSize(1)

        // SQL Injection search query -> safely handled, returns empty list without syntax error
        val rSqlInj = searchUseCase("' OR 1=1 --", null).first()
        assertThat(rSqlInj).isEmpty()

        // Non-existent search query
        val rNotFound = searchUseCase("NonExistentXYZ123", null).first()
        assertThat(rNotFound).isEmpty()
    }

    // =========================================================================
    // 2. NEGATIVE AND BOUNDARY IDS STRESS TESTS
    // =========================================================================

    @Test
    fun testNegativeAndBoundaryIdsInRepositories() = runBlocking {
        // Query negative ID
        val comicNeg = comicRepo.getComicById(-1L).first()
        assertThat(comicNeg).isNull()

        val comicNegOnce = comicRepo.getComicByIdOnce(-999L)
        assertThat(comicNegOnce).isNull()

        val comicMinOnce = comicRepo.getComicByIdOnce(Long.MIN_VALUE)
        assertThat(comicMinOnce).isNull()

        // Update progress on non-existent negative ID -> should not crash
        comicRepo.updateReadingProgress(-1L, 10, 20)

        // Toggle favorite on non-existent negative ID -> should not crash
        comicRepo.toggleFavorite(-1L, true)

        // Delete non-existent ID -> should not crash
        comicRepo.deleteComicById(-1L)
        comicRepo.deleteComicById(Long.MAX_VALUE)

        // Bookmarks on negative ID
        val bookmarksNeg = bookmarkRepo.getBookmarksForComic(-1L).first()
        assertThat(bookmarksNeg).isEmpty()

        val isBookmarkedNeg = bookmarkRepo.isPageBookmarked(-1L, 5).first()
        assertThat(isBookmarkedNeg).isFalse()

        bookmarkRepo.deleteBookmark(-1L)
        bookmarkRepo.deleteBookmark(-1L, 5)
    }

    @Test
    fun testForeignConstraintSafetyOnNegativeAndNullComicIdsInStats() = runBlocking {
        // Record session with negative comic ID:
        // StatsRepositoryImpl should safely detect that comic doesn't exist and set comicId = null
        val sessionId1 = statsRepo.recordReadingSession(
            comicId = -42L,
            pagesRead = 15,
            durationSeconds = 300L
        )
        assertThat(sessionId1).isGreaterThan(0L)

        // Record session with null comic ID
        val sessionId2 = statsRepo.recordReadingSession(
            comicId = null,
            pagesRead = 20,
            durationSeconds = 400L
        )
        assertThat(sessionId2).isGreaterThan(0L)

        val sessions = statsRepo.getAllSessions().first()
        assertThat(sessions).hasSize(2)
        assertThat(sessions[0].comicId).isNull()
        assertThat(sessions[1].comicId).isNull()

        val stats = statsRepo.getReadingStats().first()
        assertThat(stats.totalPagesRead).isEqualTo(35)
        assertThat(stats.totalReadingTimeSeconds).isEqualTo(700L)
    }

    // =========================================================================
    // 3. LARGE PAGE COUNTS & NEGATIVE PAGES STRESS TESTS
    // =========================================================================

    @Test
    fun testExtremePageCountsAndProgressCalculation() = runBlocking {
        // Case: pageCount = 0
        val comicZero = Comic(id = 1L, title = "Zero", filePath = "/zero.cbz", format = ComicFormat.CBZ, pageCount = 0, currentPage = 0)
        assertThat(comicZero.progress).isEqualTo(0f)
        assertThat(comicZero.isCompleted).isFalse()

        // Case: negative pageCount & currentPage
        val comicNeg = Comic(id = 2L, title = "Neg", filePath = "/neg.cbz", format = ComicFormat.CBZ, pageCount = -10, currentPage = -5)
        assertThat(comicNeg.progress).isEqualTo(0f)
        assertThat(comicNeg.isCompleted).isFalse()

        // Case: pageCount = 1, currentPage = 0
        val comicOneP0 = Comic(id = 3L, title = "One0", filePath = "/one0.cbz", format = ComicFormat.CBZ, pageCount = 1, currentPage = 0)
        assertThat(comicOneP0.progress).isEqualTo(0f)
        assertThat(comicOneP0.isCompleted).isFalse()

        // Case: pageCount = 1, currentPage = 1
        val comicOneP1 = Comic(id = 4L, title = "One1", filePath = "/one1.cbz", format = ComicFormat.CBZ, pageCount = 1, currentPage = 1)
        assertThat(comicOneP1.progress).isEqualTo(1f)
        assertThat(comicOneP1.isCompleted).isTrue()

        // Case: currentPage exceeds pageCount
        val comicOverflow = Comic(id = 5L, title = "Over", filePath = "/over.cbz", format = ComicFormat.CBZ, pageCount = 10, currentPage = 15)
        assertThat(comicOverflow.isCompleted).isTrue()
        assertThat(comicOverflow.progress).isGreaterThan(1f)

        // Case: very large page count (Int.MAX_VALUE / 2)
        val hugePages = 1_000_000
        val comicHuge = Comic(id = 6L, title = "Huge", filePath = "/huge.cbz", format = ComicFormat.CBZ, pageCount = hugePages, currentPage = hugePages - 1)
        assertThat(comicHuge.progress).isWithin(0.0001f).of(1.0f)
        assertThat(comicHuge.isCompleted).isTrue()
    }

    @Test
    fun testGetComicPagesUseCaseStress() = runBlocking {
        val useCase = GetComicPagesUseCase(comicRepo)

        // Non-existent comic -> returns empty list
        val pagesNotFound = useCase(9999L).first()
        assertThat(pagesNotFound).isEmpty()

        // Comic with 0 pages -> returns empty list
        val zeroComic = Comic(id = 20L, title = "Zero Pages", filePath = "/z.cbz", format = ComicFormat.CBZ, pageCount = 0)
        comicRepo.insertOrUpdateComic(zeroComic)
        val pagesZero = useCase(20L).first()
        assertThat(pagesZero).isEmpty()

        // Comic with negative page count -> returns empty list
        val negComic = Comic(id = 21L, title = "Neg Pages", filePath = "/n.cbz", format = ComicFormat.CBZ, pageCount = -50)
        comicRepo.insertOrUpdateComic(negComic)
        val pagesNeg = useCase(21L).first()
        assertThat(pagesNeg).isEmpty()

        // Comic with 2500 pages -> generates 2500 sequential pages without OOM or off-by-one errors
        val bigComic = Comic(id = 22L, title = "Big Comic", filePath = "/big.cbz", format = ComicFormat.CBZ, pageCount = 2500)
        comicRepo.insertOrUpdateComic(bigComic)
        val pagesBig = useCase(22L).first()
        assertThat(pagesBig).hasSize(2500)
        assertThat(pagesBig.first().pageNumber).isEqualTo(0)
        assertThat(pagesBig.last().pageNumber).isEqualTo(2499)
    }

    @Test
    fun testSaveReadingProgressUseCaseStress() = runBlocking {
        val comic = Comic(id = 30L, title = "Progress Comic", filePath = "/prog.cbz", format = ComicFormat.CBZ, pageCount = 50)
        comicRepo.insertOrUpdateComic(comic)

        val useCase = SaveReadingProgressUseCase(comicRepo, statsRepo)

        // Normal progress save
        useCase(comicId = 30L, pageNumber = 25, totalPages = 50, sessionDurationSeconds = 120L, pagesTurned = 5)
        val updated = comicRepo.getComicByIdOnce(30L)
        assertThat(updated?.currentPage).isEqualTo(25)

        val stats = statsRepo.getReadingStats().first()
        assertThat(stats.totalPagesRead).isEqualTo(5)
        assertThat(stats.totalReadingTimeSeconds).isEqualTo(120L)

        // Progress save with zero duration & zero pages turned -> progress updated, no new session logged
        useCase(comicId = 30L, pageNumber = 35, totalPages = 50, sessionDurationSeconds = 0L, pagesTurned = 0)
        val updated2 = comicRepo.getComicByIdOnce(30L)
        assertThat(updated2?.currentPage).isEqualTo(35)
        val stats2 = statsRepo.getReadingStats().first()
        assertThat(stats2.totalPagesRead).isEqualTo(5) // Unchanged

        // Progress save with negative ID -> does not throw
        useCase(comicId = -1L, pageNumber = 10, totalPages = 20, sessionDurationSeconds = 60L, pagesTurned = 2)
    }

    // =========================================================================
    // 4. CORRUPT & EXTREME TIMESTAMPS STRESS TESTS
    // =========================================================================

    @Test
    fun testCorruptAndBoundaryTimestampsInReadingStats() = runBlocking {
        // Record session with extreme long duration (check overflow handling)
        val sId1 = statsRepo.recordReadingSession(
            comicId = null,
            pagesRead = 10,
            durationSeconds = Long.MAX_VALUE / 2000L // large without immediate overflow
        )
        assertThat(sId1).isGreaterThan(0L)

        // Record session with negative duration
        val sId2 = statsRepo.recordReadingSession(
            comicId = null,
            pagesRead = 5,
            durationSeconds = -300L
        )
        assertThat(sId2).isGreaterThan(0L)

        // Verify weekly stats survives sessions and always returns 7 days of week
        val weekly = statsRepo.getWeeklyStats().first()
        assertThat(weekly).hasSize(7)
        val dayNames = weekly.map { it.dayOfWeek }
        assertThat(dayNames).containsExactly("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").inOrder()
    }

    // =========================================================================
    // 5. CONFLICT RESOLUTION & CONSTRAINT COLLISIONS
    // =========================================================================

    @Test
    fun testUniqueFilePathConflictResolutionInComicDao() = runBlocking {
        val path = "/storage/shared/duplicate.cbz"

        val comic1 = Comic(
            id = 101L,
            title = "First Version",
            filePath = path,
            format = ComicFormat.CBZ,
            pageCount = 20
        )
        val insertedId1 = comicRepo.insertOrUpdateComic(comic1)

        // Second comic with DIFFERENT id but SAME filePath
        val comic2 = Comic(
            id = 102L,
            title = "Updated Version",
            filePath = path,
            format = ComicFormat.CBZ,
            pageCount = 25
        )
        val insertedId2 = comicRepo.insertOrUpdateComic(comic2)

        // OnConflictStrategy.REPLACE should update/replace without SQLiteConstraintException
        val allComics = comicRepo.getAllComics(SortOrder.TITLE).first()
        assertThat(allComics).hasSize(1)
        assertThat(allComics.first().title).isEqualTo("Updated Version")
        assertThat(allComics.first().pageCount).isEqualTo(25)
    }

    @Test
    fun testUniqueBookmarkCompositeIndexConflictResolution() = runBlocking {
        val comic = Comic(id = 200L, title = "Bookmark Test", filePath = "/b.cbz", format = ComicFormat.CBZ, pageCount = 50)
        comicRepo.insertOrUpdateComic(comic)

        // Insert first bookmark on page 10
        val b1 = Bookmark(id = 1L, comicId = 200L, pageNumber = 10, note = "First note")
        bookmarkRepo.addBookmark(b1)

        // Insert second bookmark on SAME comicId and SAME pageNumber with updated note
        val b2 = Bookmark(id = 2L, comicId = 200L, pageNumber = 10, note = "Updated note")
        bookmarkRepo.addBookmark(b2)

        val bookmarks = bookmarkRepo.getBookmarksForComic(200L).first()
        assertThat(bookmarks).hasSize(1)
        assertThat(bookmarks.first().note).isEqualTo("Updated note")
    }

    // =========================================================================
    // 6. DOMAIN MAPPING ROBUSTNESS UNDER CORRUPT ENUMS & ENTITY VALUES
    // =========================================================================

    @Test
    fun testEntityToDomainFallbackOnUnknownFormatExtension() {
        val entity = ComicEntity(
            id = 301L,
            title = "Corrupt Format",
            filePath = "/comics/test.unknown_format",
            format = "corrupt_xyz", // Unknown format string in DB
            pageCount = 10
        )

        // toDomain should fallback gracefully to ComicFormat.CBZ rather than crashing
        val domain = entity.toDomain()
        assertThat(domain.format).isEqualTo(ComicFormat.CBZ)

        // ComicFormat.fromExtension edge cases
        assertThat(ComicFormat.fromExtension("")).isNull()
        assertThat(ComicFormat.fromExtension(".cbz")).isEqualTo(ComicFormat.CBZ)
        assertThat(ComicFormat.fromExtension(".CBR")).isEqualTo(ComicFormat.CBR)
        assertThat(ComicFormat.fromExtension("PDF")).isEqualTo(ComicFormat.PDF)
        assertThat(ComicFormat.fromExtension("ePub")).isEqualTo(ComicFormat.EPUB)
        assertThat(ComicFormat.fromExtension("invalid")).isNull()

        // ComicFormat.fromFilePath edge cases
        assertThat(ComicFormat.fromFilePath("no_extension")).isNull()
        assertThat(ComicFormat.fromFilePath("comic.final.v2.cbz")).isEqualTo(ComicFormat.CBZ)
        assertThat(ComicFormat.fromFilePath(".hidden_cbz")).isNull()
    }

    // =========================================================================
    // 7. DATASTORE PREFERENCES RESILIENCE UNDER CORRUPTED STORED VALUES
    // =========================================================================

    @Test
    fun testDataStoreRecoversFromCorruptedEnumStrings() = runBlocking {
        // Deliberately write invalid enum strings into DataStore
        context.userDataStore.edit { prefs ->
            prefs[UserPreferencesDataStore.KEY_THEME] = "NON_EXISTENT_THEME_VALUE"
            prefs[UserPreferencesDataStore.KEY_READING_MODE] = "INVALID_READING_MODE"
            prefs[UserPreferencesDataStore.KEY_PAGE_TURN_ANIM] = "UNKNOWN_ANIMATION"
            prefs[UserPreferencesDataStore.KEY_READING_DIRECTION] = "UPSIDE_DOWN"
        }

        // Verify DataStore falls back to valid default values without throwing IllegalArgumentException
        val prefs = dataStore.preferencesFlow.first()
        assertThat(prefs.theme).isEqualTo(AppTheme.DARK)
        assertThat(prefs.readingMode).isEqualTo(ReadingMode.HORIZONTAL)
        assertThat(prefs.pageTurnAnimation).isEqualTo(PageAnimation.SLIDE)
        assertThat(prefs.readingDirection).isEqualTo(ReadingDirection.LTR)
    }

    // =========================================================================
    // 8. USE CASE PIPELINE INTEGRATION UNDER EXTREME FILTER CRITERIA
    // =========================================================================

    @Test
    fun testGetLibraryUseCaseUnderExtremeFilters() = runBlocking {
        val c1 = Comic(id = 1L, title = "Batman", filePath = "/batman.cbz", format = ComicFormat.CBZ, isFavorite = true, addedTimestamp = 1000L)
        val c2 = Comic(id = 2L, title = "Avengers", filePath = "/avengers.pdf", format = ComicFormat.PDF, isFavorite = false, addedTimestamp = 2000L)
        val c3 = Comic(id = 3L, title = "Spider-Man", filePath = "/spiderman.cbr", format = ComicFormat.CBR, isFavorite = true, addedTimestamp = 3000L)
        comicRepo.insertOrUpdateComic(c1)
        comicRepo.insertOrUpdateComic(c2)
        comicRepo.insertOrUpdateComic(c3)

        val getLibraryUseCase = GetLibraryUseCase(comicRepo)

        // Filter: onlyFavorites = true
        val favs = getLibraryUseCase(onlyFavorites = true).first()
        assertThat(favs).hasSize(2)
        assertThat(favs.map { it.title }).containsExactly("Batman", "Spider-Man").inOrder()

        // Filter: format = PDF
        val pdfs = getLibraryUseCase(formatFilter = ComicFormat.PDF).first()
        assertThat(pdfs).hasSize(1)
        assertThat(pdfs.first().title).isEqualTo("Avengers")

        // Filter: query = "man", format = CBZ, onlyFavorites = true
        val specific = getLibraryUseCase(
            query = "man",
            formatFilter = ComicFormat.CBZ,
            onlyFavorites = true
        ).first()
        assertThat(specific).hasSize(1)
        assertThat(specific.first().title).isEqualTo("Batman")

        // SortOrder.RECENTLY_ADDED
        val recent = getLibraryUseCase(sortOrder = SortOrder.RECENTLY_ADDED).first()
        assertThat(recent.first().title).isEqualTo("Spider-Man")
        assertThat(recent.last().title).isEqualTo("Batman")
    }
}
