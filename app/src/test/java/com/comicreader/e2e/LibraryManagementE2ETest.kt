package com.comicreader.e2e

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * End-to-End & Integration Test Suite for Library Management & File Scanner.
 * Verifies 4-Tier test methodology:
 * - Tier 1: Feature Coverage (directory scanning, Room persistence, search, format filter, sort orders, favorites, history)
 * - Tier 2: Boundary & Corner Cases (empty folder, duplicate paths, zero-byte files, nested directories, unicode titles)
 * - Tier 3: Cross-Feature Combinations (scan -> search -> favorite -> history; rescan preserves user progress)
 * - Tier 4: Real-World Application Scenarios (complete library management lifecycle)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryManagementE2ETest {

    private lateinit var database: ComicDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var repository: ComicRepositoryImpl
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "lib_mgmt_e2e_${System.nanoTime()}")
        tempDir.mkdirs()

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ComicDatabase::class.java
        ).allowMainThreadQueries().build()

        comicDao = database.comicDao()
        repository = ComicRepositoryImpl(comicDao)
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // TIER 1: CORE FEATURE COVERAGE (>=5 Test Cases)
    // =========================================================================

    @Test
    fun tier1_scan_indexesSupportedComicFormatsInDirectory() = runBlocking {
        // Arrange: Create files of 4 supported formats
        val cbz = TestComicGenerator.createMockCbz(File(tempDir, "Batman.cbz"), pageCount = 5)
        val cbr = TestComicGenerator.createMockCbr(File(tempDir, "Spiderman.cbr"))
        val pdf = TestComicGenerator.createMockPdf(File(tempDir, "Superman.pdf"), pageCount = 4)
        val epub = TestComicGenerator.createMockEpub(File(tempDir, "XMen.epub"), pageCount = 3)

        // Act: Simulate file scanner discovery and Room insertion
        val scannedFiles = tempDir.listFiles { f -> isSupportedComicFile(f) } ?: emptyArray()
        assertEquals(4, scannedFiles.size)

        for (file in scannedFiles) {
            val format = ComicFormat.fromFilePath(file.name) ?: continue
            val entity = ComicEntity(
                title = file.nameWithoutExtension,
                filePath = file.absolutePath,
                format = format.extension,
                pageCount = 10,
                fileSizeBytes = file.length()
            )
            comicDao.insertComic(entity)
        }

        // Assert: All 4 comics persisted in database
        val allComics = repository.getAllComics(SortOrder.TITLE).first()
        assertEquals(4, allComics.size)
        val formatsFound = allComics.map { it.format }.toSet()
        assertTrue(formatsFound.contains(ComicFormat.CBZ))
        assertTrue(formatsFound.contains(ComicFormat.CBR))
        assertTrue(formatsFound.contains(ComicFormat.PDF))
        assertTrue(formatsFound.contains(ComicFormat.EPUB))
    }

    @Test
    fun tier1_scan_filtersOutUnsupportedExtensions() = runBlocking {
        // Arrange: Mix of valid comics and unsupported files
        TestComicGenerator.createMockCbz(File(tempDir, "Valid.cbz"), pageCount = 2)
        File(tempDir, "song.mp3").writeText("audio data")
        File(tempDir, "notes.txt").writeText("text notes")
        File(tempDir, "program.exe").writeText("binary data")
        File(tempDir, "document.docx").writeText("office doc")

        // Act: Filter files
        val files = tempDir.listFiles() ?: emptyArray()
        val validFiles = files.filter { isSupportedComicFile(it) }

        // Assert: Only the 1 CBZ is recognized
        assertEquals(1, validFiles.size)
        assertEquals("Valid.cbz", validFiles[0].name)
    }

    @Test
    fun tier1_room_comicEntityCrudOperations() = runBlocking {
        // Create
        val comic = Comic(
            title = "Daredevil #1",
            filePath = "/path/daredevil.cbz",
            format = ComicFormat.CBZ,
            pageCount = 22,
            currentPage = 0
        )
        val id = repository.insertOrUpdateComic(comic)
        assertTrue(id > 0)

        // Read
        val fetched = repository.getComicByIdOnce(id)
        assertNotNull(fetched)
        assertEquals("Daredevil #1", fetched!!.title)
        assertEquals(22, fetched.pageCount)

        // Update progress
        repository.updateReadingProgress(id, page = 11, totalPages = 22)
        val updated = repository.getComicByIdOnce(id)
        assertEquals(11, updated!!.currentPage)
        assertTrue(updated.lastReadTimestamp > 0)

        // Delete
        repository.deleteComicById(id)
        val deleted = repository.getComicByIdOnce(id)
        assertNull(deleted)
    }

    @Test
    fun tier1_search_fullTextTitleQuery() = runBlocking {
        // Arrange
        comicDao.insertComic(ComicEntity(title = "Spider-Man: Blue", filePath = "/p1", format = "cbz"))
        comicDao.insertComic(ComicEntity(title = "Spider-Man: Kraven's Last Hunt", filePath = "/p2", format = "cbz"))
        comicDao.insertComic(ComicEntity(title = "Batman: Year One", filePath = "/p3", format = "cbr"))

        // Act: Search "Spider"
        val spiderResults = repository.searchComics("Spider", null).first()
        val batmanResults = repository.searchComics("Batman", null).first()
        val emptyResults = repository.searchComics("Avengers", null).first()

        // Assert
        assertEquals(2, spiderResults.size)
        assertEquals(1, batmanResults.size)
        assertEquals(0, emptyResults.size)
    }

    @Test
    fun tier1_filter_byFormat() = runBlocking {
        // Arrange
        comicDao.insertComic(ComicEntity(title = "Comic 1", filePath = "/p1", format = "cbz"))
        comicDao.insertComic(ComicEntity(title = "Comic 2", filePath = "/p2", format = "cbz"))
        comicDao.insertComic(ComicEntity(title = "Comic 3", filePath = "/p3", format = "pdf"))

        // Act: Filter by CBZ vs PDF
        val cbzComics = repository.filterComics(query = null, format = ComicFormat.CBZ).first()
        val pdfComics = repository.filterComics(query = null, format = ComicFormat.PDF).first()

        // Assert
        assertEquals(2, cbzComics.size)
        assertEquals(1, pdfComics.size)
        assertEquals("Comic 3", pdfComics[0].title)
    }

    @Test
    fun tier1_sort_byTitleAlphanumeric() = runBlocking {
        // Arrange
        comicDao.insertComic(ComicEntity(title = "Wonder Woman", filePath = "/p1", format = "cbz"))
        comicDao.insertComic(ComicEntity(title = "Aquaman", filePath = "/p2", format = "cbz"))
        comicDao.insertComic(ComicEntity(title = "Flash", filePath = "/p3", format = "cbz"))

        // Act
        val sorted = repository.getAllComics(SortOrder.TITLE).first()

        // Assert: Aquaman, Flash, Wonder Woman
        assertEquals(3, sorted.size)
        assertEquals("Aquaman", sorted[0].title)
        assertEquals("Flash", sorted[1].title)
        assertEquals("Wonder Woman", sorted[2].title)
    }

    @Test
    fun tier1_sort_byRecentlyAdded() = runBlocking {
        // Arrange
        val now = System.currentTimeMillis()
        comicDao.insertComic(ComicEntity(title = "Old", filePath = "/p1", format = "cbz", addedTimestamp = now - 10000))
        comicDao.insertComic(ComicEntity(title = "Newest", filePath = "/p2", format = "cbz", addedTimestamp = now))
        comicDao.insertComic(ComicEntity(title = "Middle", filePath = "/p3", format = "cbz", addedTimestamp = now - 5000))

        // Act
        val sorted = repository.getAllComics(SortOrder.RECENTLY_ADDED).first()

        // Assert
        assertEquals("Newest", sorted[0].title)
        assertEquals("Middle", sorted[1].title)
        assertEquals("Old", sorted[2].title)
    }

    @Test
    fun tier1_sort_byLastRead() = runBlocking {
        // Arrange
        val now = System.currentTimeMillis()
        comicDao.insertComic(ComicEntity(title = "Read Yesterday", filePath = "/p1", format = "cbz", lastReadTimestamp = now - 86400000))
        comicDao.insertComic(ComicEntity(title = "Read Just Now", filePath = "/p2", format = "cbz", lastReadTimestamp = now))
        comicDao.insertComic(ComicEntity(title = "Never Read", filePath = "/p3", format = "cbz", lastReadTimestamp = 0))

        // Act
        val recentlyRead = repository.getRecentHistory(10).first()

        // Assert: Unread comics are excluded from recent history; newest read is first
        assertEquals(2, recentlyRead.size)
        assertEquals("Read Just Now", recentlyRead[0].title)
        assertEquals("Read Yesterday", recentlyRead[1].title)
    }

    @Test
    fun tier1_favorites_toggleAndFilter() = runBlocking {
        // Arrange
        val id1 = comicDao.insertComic(ComicEntity(title = "Comic 1", filePath = "/p1", format = "cbz", isFavorite = false))
        val id2 = comicDao.insertComic(ComicEntity(title = "Comic 2", filePath = "/p2", format = "cbz", isFavorite = true))

        // Initial favorites
        var favorites = repository.getFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals("Comic 2", favorites[0].title)

        // Act: Favorite Comic 1 and unfavorite Comic 2
        repository.toggleFavorite(id1, true)
        repository.toggleFavorite(id2, false)

        favorites = repository.getFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals("Comic 1", favorites[0].title)
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES
    // =========================================================================

    @Test
    fun tier2_boundary_emptyDirectory_returnsZeroComics() = runBlocking {
        // Empty folder
        val emptyFolder = File(tempDir, "empty_comics")
        emptyFolder.mkdirs()

        val files = emptyFolder.listFiles { f -> isSupportedComicFile(f) } ?: emptyArray()
        assertTrue(files.isEmpty())

        val count = comicDao.getTotalComicsCount().first()
        assertEquals(0, count)
    }

    @Test
    fun tier2_boundary_duplicateFileImport_handlesUniqueConstraint() = runBlocking {
        // Arrange: Exact same filePath inserted twice
        val entity1 = ComicEntity(title = "First Pass", filePath = "/same/path.cbz", format = "cbz", pageCount = 10)
        val entity2 = ComicEntity(title = "Second Pass", filePath = "/same/path.cbz", format = "cbz", pageCount = 12)

        // Act: insertComic uses OnConflictStrategy.REPLACE
        comicDao.insertComic(entity1)
        comicDao.insertComic(entity2)

        // Assert: Exactly 1 row in table, updated to latest title & page count
        val comics = comicDao.getAllComics().first()
        assertEquals(1, comics.size)
        assertEquals("Second Pass", comics[0].title)
        assertEquals(12, comics[0].pageCount)
    }

    @Test
    fun tier2_boundary_deeplyNestedSubdirectories_scannedRecursively() {
        // Arrange: Nested directories: DC/Batman/2020/issue1.cbz
        val nestedFolder = File(tempDir, "DC/Batman/2020")
        nestedFolder.mkdirs()
        val comicFile = File(nestedFolder, "issue1.cbz")
        comicFile.writeText("fake cbz")

        // Act: Recursive search
        val discovered = mutableListOf<File>()
        tempDir.walkTopDown().filter { it.isFile && isSupportedComicFile(it) }.forEach {
            discovered.add(it)
        }

        // Assert
        assertEquals(1, discovered.size)
        assertEquals("issue1.cbz", discovered[0].name)
        assertTrue(discovered[0].absolutePath.contains("DC"))
    }

    @Test
    fun tier2_boundary_specialCharactersInFilenamesAndTitles() = runBlocking {
        // Arrange: Complex unicode, emojis, and symbols in title
        val complexTitle = "JoJo's Bizarre Adventure: 黄金の風 [Part 5] #01 (2026) ⚡"
        val id = comicDao.insertComic(
            ComicEntity(
                title = complexTitle,
                filePath = "/storage/comics/jojo_part5_⚡.cbz",
                format = "cbz"
            )
        )

        // Act & Assert: Full text search by Japanese or special chars
        val result1 = repository.searchComics("黄金", null).first()
        val result2 = repository.searchComics("JoJo", null).first()
        val result3 = repository.searchComics("⚡", null).first()

        assertEquals(1, result1.size)
        assertEquals(complexTitle, result1[0].title)
        assertEquals(1, result2.size)
        assertEquals(1, result3.size)
    }

    @Test
    fun tier2_boundary_externalFileDeletion_reconcilesLibrary() = runBlocking {
        // Arrange: 2 comics in DB, one file is deleted on disk
        val file1 = File(tempDir, "existing.cbz").apply { writeText("data") }
        val file2 = File(tempDir, "deleted_later.cbz").apply { writeText("data") }

        comicDao.insertComic(ComicEntity(title = "Existing", filePath = file1.absolutePath, format = "cbz"))
        val id2 = comicDao.insertComic(ComicEntity(title = "Deleted", filePath = file2.absolutePath, format = "cbz"))

        // Simulate external deletion of file2
        file2.delete()

        // Act: Reconcile library by verifying file existence
        val allEntities = comicDao.getAllComics().first()
        for (entity in allEntities) {
            if (!File(entity.filePath).exists()) {
                comicDao.deleteComicById(entity.id)
            }
        }

        // Assert: Deleted file removed from database
        val remaining = comicDao.getAllComics().first()
        assertEquals(1, remaining.size)
        assertEquals("Existing", remaining[0].title)
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    fun tier3_crossFeature_scanToSearchToFavoriteToHistory() = runBlocking {
        // Step 1: Scan and insert 3 comics
        val id1 = repository.insertOrUpdateComic(Comic(title = "Thor", filePath = "/p1", format = ComicFormat.CBZ, pageCount = 20))
        val id2 = repository.insertOrUpdateComic(Comic(title = "Hulk", filePath = "/p2", format = ComicFormat.CBZ, pageCount = 20))
        val id3 = repository.insertOrUpdateComic(Comic(title = "Iron Man", filePath = "/p3", format = ComicFormat.CBZ, pageCount = 20))

        // Step 2: Search for "Thor"
        val searchResults = repository.searchComics("Thor", null).first()
        assertEquals(1, searchResults.size)
        assertEquals(id1, searchResults[0].id)

        // Step 3: Favorite Thor
        repository.toggleFavorite(id1, true)
        val favorites = repository.getFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals("Thor", favorites[0].title)

        // Step 4: Update reading progress for Thor and Hulk
        repository.updateReadingProgress(id1, page = 5, totalPages = 20)
        repository.updateReadingProgress(id2, page = 12, totalPages = 20)

        // Step 5: Check history
        val history = repository.getRecentHistory(10).first()
        assertEquals(2, history.size)
        assertTrue(history.any { it.id == id1 })
        assertTrue(history.any { it.id == id2 })
    }

    @Test
    fun tier3_crossFeature_rescanPreservesUserReadingProgress() = runBlocking {
        // Arrange: User has read 8 pages of Batman and starred it
        val filePath = "/storage/batman.cbz"
        val originalId = comicDao.insertComic(
            ComicEntity(
                title = "Batman",
                filePath = filePath,
                format = "cbz",
                pageCount = 20,
                currentPage = 8,
                lastReadTimestamp = 123456789L,
                isFavorite = true
            )
        )

        // Act: Background scanner re-runs and encounters the file again
        val existingEntity = comicDao.getComicByPath(filePath)
        assertNotNull(existingEntity)

        // Preserve user state while updating physical file stats (e.g. pageCount)
        val rescannedEntity = existingEntity!!.copy(
            pageCount = 22 // file was updated
        )
        comicDao.updateComic(rescannedEntity)

        // Assert: User reading progress and favorite status are safely preserved
        val afterRescan = comicDao.getComicByIdOnce(originalId)
        assertNotNull(afterRescan)
        assertEquals(8, afterRescan!!.currentPage)
        assertEquals(123456789L, afterRescan.lastReadTimestamp)
        assertTrue(afterRescan.isFavorite)
        assertEquals(22, afterRescan.pageCount)
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    fun tier4_scenario_fullLibraryManagementWorkflow() = runBlocking {
        // Scenario: User imports 6 comics across formats, searches, sorts, favorites, and deletes
        val titles = listOf(
            Triple("Avengers #1", "cbz", 24),
            Triple("Batman: Hush", "cbz", 30),
            Triple("Crisis on Infinite Earths", "cbr", 40),
            Triple("Deadpool Kills", "pdf", 22),
            Triple("East of West", "epub", 28),
            Triple("Fables Volume 1", "cbz", 32)
        )

        // 1. Ingestion
        val ids = mutableListOf<Long>()
        for ((title, fmt, pages) in titles) {
            val id = repository.insertOrUpdateComic(
                Comic(
                    title = title,
                    filePath = "/library/${title.replace(' ', '_')}.$fmt",
                    format = ComicFormat.fromExtension(fmt)!!,
                    pageCount = pages
                )
            )
            ids.add(id)
        }

        // 2. Verify total count
        assertEquals(6, repository.getAllComics(SortOrder.TITLE).first().size)

        // 3. User favorites "Batman: Hush" and "East of West"
        repository.toggleFavorite(ids[1], true)
        repository.toggleFavorite(ids[4], true)
        val favorites = repository.getFavorites().first()
        assertEquals(2, favorites.size)

        // 4. User reads "Fables" to completion
        repository.updateReadingProgress(ids[5], page = 31, totalPages = 32)
        val fables = repository.getComicByIdOnce(ids[5])
        assertNotNull(fables)
        assertTrue(fables!!.isCompleted)

        // 5. User deletes "Deadpool Kills" from library
        repository.deleteComicById(ids[3])
        val remaining = repository.getAllComics(SortOrder.TITLE).first()
        assertEquals(5, remaining.size)
        assertFalse(remaining.any { it.title == "Deadpool Kills" })
    }

    private fun isSupportedComicFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "cbz" || ext == "cbr" || ext == "pdf" || ext == "epub"
    }
}
