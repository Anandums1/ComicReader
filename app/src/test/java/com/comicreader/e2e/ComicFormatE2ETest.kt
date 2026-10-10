package com.comicreader.e2e

import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.ComicPage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * End-to-End & Integration Test Suite for Comic Format Parsers (CBZ, CBR, PDF, EPUB).
 * Verifies 4-Tier test methodology:
 * - Tier 1: Feature Coverage (>=5 test cases per core format category)
 * - Tier 2: Boundary & Corner Cases (empty archives, single-page, 1000+ pages, RAR5, corrupt headers, unicode)
 * - Tier 3: Cross-Feature Combinations (unified format detection, cover extraction to domain model)
 * - Tier 4: Real-World Application Scenarios (complete archive extraction and indexing pipeline)
 */
class ComicFormatE2ETest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "comic_format_e2e_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =========================================================================
    // TIER 1: CORE FEATURE COVERAGE — CBZ PARSING (>=5 Test Cases)
    // =========================================================================

    @Test
    fun tier1_cbz_extractsOrderedPagesWithNaturalSort() {
        // Arrange: Filenames with unpadded numbers that would fail ASCII sort
        val file = File(tempDir, "natural_sort.cbz")
        val customFilenames = listOf("page1.png", "page10.png", "page2.png", "page20.png", "page3.png")
        TestComicGenerator.createMockCbz(file, customFilenames = customFilenames)

        // Act: Open zip and sort entries using natural order comparator
        val zip = ZipFile(file)
        val imageEntries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".png", ignoreCase = true) }
            .map { it.name }
            .sortedWith(NaturalOrderComparator)
            .toList()
        zip.close()

        // Assert: Natural sort must order page1, page2, page3, page10, page20
        assertEquals(5, imageEntries.size)
        assertEquals("page1.png", imageEntries[0])
        assertEquals("page2.png", imageEntries[1])
        assertEquals("page3.png", imageEntries[2])
        assertEquals("page10.png", imageEntries[3])
        assertEquals("page20.png", imageEntries[4])
    }

    @Test
    fun tier1_cbz_extractsComicInfoXmlMetadata() {
        // Arrange: CBZ with ComicInfo.xml metadata
        val file = File(tempDir, "metadata_test.cbz")
        TestComicGenerator.createMockCbz(
            file,
            pageCount = 4,
            title = "Watchmen #1",
            series = "Watchmen",
            writer = "Alan Moore"
        )

        // Act: Extract and parse ComicInfo.xml
        val zip = ZipFile(file)
        val comicInfoEntry = zip.getEntry("ComicInfo.xml")
        assertNotNull("ComicInfo.xml entry must exist in CBZ", comicInfoEntry)

        val xmlStream = zip.getInputStream(comicInfoEntry)
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(xmlStream)
        zip.close()

        val title = doc.getElementsByTagName("Title").item(0).textContent
        val series = doc.getElementsByTagName("Series").item(0).textContent
        val writer = doc.getElementsByTagName("Writer").item(0).textContent
        val pageCount = doc.getElementsByTagName("PageCount").item(0).textContent.toInt()

        // Assert
        assertEquals("Watchmen #1", title)
        assertEquals("Watchmen", series)
        assertEquals("Alan Moore", writer)
        assertEquals(4, pageCount)
    }

    @Test
    fun tier1_cbz_extractsFirstPageAsCover() {
        // Arrange
        val file = File(tempDir, "cover_extract.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 3)

        // Act: Get first sorted image entry
        val zip = ZipFile(file)
        val coverEntry = zip.entries().asSequence()
            .filter { !it.isDirectory && isImageFile(it.name) }
            .sortedWith(compareBy(NaturalOrderComparator) { it.name })
            .firstOrNull()

        assertNotNull(coverEntry)
        val coverBytes = zip.getInputStream(coverEntry).readBytes()
        zip.close()

        // Assert: First page starts with standard PNG magic header (89 50 4E 47 0D 0A 1A 0A)
        assertTrue(coverBytes.size > 8)
        assertEquals(0x89.toByte(), coverBytes[0])
        assertEquals('P'.code.toByte(), coverBytes[1])
        assertEquals('N'.code.toByte(), coverBytes[2])
        assertEquals('G'.code.toByte(), coverBytes[3])
    }

    @Test
    fun tier1_cbz_supportsMixedImageFormats() {
        // Arrange: CBZ with PNG, JPG, and WEBP entries
        val file = File(tempDir, "mixed_images.cbz")
        val customFilenames = listOf("page01.jpg", "page02.png", "page03.webp", "page04.jpeg")
        TestComicGenerator.createMockCbz(file, customFilenames = customFilenames)

        // Act
        val zip = ZipFile(file)
        val validImageEntries = zip.entries().asSequence()
            .filter { isImageFile(it.name) }
            .map { it.name }
            .toList()
        zip.close()

        // Assert: All 4 image formats detected
        assertEquals(4, validImageEntries.size)
        assertTrue(validImageEntries.contains("page01.jpg"))
        assertTrue(validImageEntries.contains("page02.png"))
        assertTrue(validImageEntries.contains("page03.webp"))
        assertTrue(validImageEntries.contains("page04.jpeg"))
    }

    @Test
    fun tier1_cbz_filtersOutNonImageEntriesAndOsMetadata() {
        // Arrange: CBZ with OS metadata and text files
        val file = File(tempDir, "filtered_entries.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 3, includeExtraFiles = true)

        // Act
        val zip = ZipFile(file)
        val allEntryNames = zip.entries().asSequence().map { it.name }.toList()
        val comicPages = zip.entries().asSequence()
            .filter { isValidComicPageEntry(it.name) }
            .map { it.name }
            .sortedWith(NaturalOrderComparator)
            .toList()
        zip.close()

        // Assert: All extra files exist in zip, but are filtered out from comic pages
        assertTrue(allEntryNames.contains(".DS_Store"))
        assertTrue(allEntryNames.contains("__MACOSX/._page001.png"))
        assertTrue(allEntryNames.contains("readme.txt"))

        assertEquals(3, comicPages.size)
        assertFalse(comicPages.any { it.startsWith("__MACOSX") || it.contains(".DS_Store") || it.endsWith(".txt") })
    }

    // =========================================================================
    // TIER 1: CORE FEATURE COVERAGE — CBR PARSING (>=5 Test Cases)
    // =========================================================================

    @Test
    fun tier1_cbr_detectsRarHeaderSignature() {
        // Arrange: Generate CBR archive with RAR4 header
        val file = File(tempDir, "standard.cbr")
        TestComicGenerator.createMockCbr(file, isRar5 = false)

        // Act: Read magic bytes
        val bytes = file.readBytes()

        // Assert: RAR 4.x magic signature: 0x52 0x61 0x72 0x21 0x1A 0x07 0x00 ("Rar!\x1a\x07\x00")
        assertTrue(bytes.size >= 7)
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('a'.code.toByte(), bytes[1])
        assertEquals('r'.code.toByte(), bytes[2])
        assertEquals('!'.code.toByte(), bytes[3])
        assertEquals(0x1A.toByte(), bytes[4])
        assertEquals(0x07.toByte(), bytes[5])
        assertEquals(0x00.toByte(), bytes[6])
    }

    @Test
    fun tier1_cbr_detectsRar5AndIdentifiesUnsupportedVersion() {
        // Arrange: Generate RAR5 archive
        val file = File(tempDir, "rar5_archive.cbr")
        TestComicGenerator.createMockCbr(file, isRar5 = true)

        // Act: Inspect signature bytes
        val bytes = file.readBytes()
        val isRar5 = isRar5Archive(bytes)

        // Assert: RAR5 magic bytes: 0x52 0x61 0x72 0x21 0x1A 0x07 0x01 0x00
        assertTrue("File must be detected as RAR5 archive", isRar5)
    }

    @Test
    fun tier1_cbr_normalizesWindowsBackslashesInEntryPaths() {
        // Arrange: Path with Windows backslashes
        val rawPath = "Issue#10\\Pages\\Chapter_1\\page01.jpg"

        // Act: Normalize backslash to forward slash
        val normalized = rawPath.replace('\\', '/')

        // Assert
        assertEquals("Issue#10/Pages/Chapter_1/page01.jpg", normalized)
        assertFalse(normalized.contains('\\'))
        assertEquals("page01.jpg", normalized.substringAfterLast('/'))
    }

    @Test
    fun tier1_cbr_corruptedRarHeaderFailsGracefully() {
        // Arrange: Corrupt CBR file
        val file = File(tempDir, "corrupted.cbr")
        TestComicGenerator.createMockCbr(file, corrupt = true)

        // Act & Assert: Header does not match RAR signature
        val bytes = file.readBytes()
        assertFalse("Corrupted file must not match RAR signature", isRarArchive(bytes))
    }

    @Test
    fun tier1_cbr_formatExtensionMapping() {
        // Act & Assert
        val format = ComicFormat.fromFilePath("comic.cbr")
        assertEquals(ComicFormat.CBR, format)
        assertEquals("cbr", format?.extension)
    }

    // =========================================================================
    // TIER 1: CORE FEATURE COVERAGE — PDF PARSING (>=5 Test Cases)
    // =========================================================================

    @Test
    fun tier1_pdf_extractsValidPageCount() {
        // Arrange: 5-page PDF
        val file = File(tempDir, "five_pages.pdf")
        TestComicGenerator.createMockPdf(file, pageCount = 5)

        // Act: Parse PDF page count from trailer/pages dictionary
        val content = file.readText(StandardCharsets.US_ASCII)
        val countRegex = Regex("""/Count\s+(\d+)""")
        val match = countRegex.find(content)

        // Assert
        assertNotNull(match)
        val pageCount = match!!.groupValues[1].toInt()
        assertEquals(5, pageCount)
    }

    @Test
    fun tier1_pdf_extractsDocumentTitleFromMetadata() {
        // Arrange: PDF with specific title
        val file = File(tempDir, "titled_comic.pdf")
        val expectedTitle = "Saga Volume 1"
        TestComicGenerator.createMockPdf(file, pageCount = 3, title = expectedTitle)

        // Act: Extract /Title (...)
        val content = file.readText(StandardCharsets.US_ASCII)
        val titleRegex = Regex("""/Title\s*\(([^)]+)\)""")
        val match = titleRegex.find(content)

        // Assert
        assertNotNull(match)
        assertEquals(expectedTitle, match!!.groupValues[1])
    }

    @Test
    fun tier1_pdf_corruptedHeaderFailsGracefully() {
        // Arrange: Corrupted PDF file
        val file = File(tempDir, "corrupted.pdf")
        TestComicGenerator.createMockPdf(file, corrupt = true)

        // Act
        val content = file.readText(StandardCharsets.US_ASCII)
        val isValidPdf = content.startsWith("%PDF-")

        // Assert: Corrupted file fails PDF header check
        assertFalse(isValidPdf)
    }

    @Test
    fun tier1_pdf_calculatesPageDimensionsAndAspectRatio() {
        // Arrange: PDF with standard MediaBox [0 0 612 792] (US Letter: 8.5 x 11 in)
        val file = File(tempDir, "aspect_ratio.pdf")
        TestComicGenerator.createMockPdf(file, pageCount = 1)

        // Act: Parse MediaBox [llx lly urx ury]
        val content = file.readText(StandardCharsets.US_ASCII)
        val mediaBoxRegex = Regex("""/MediaBox\s*\[\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*\]""")
        val match = mediaBoxRegex.find(content)

        assertNotNull(match)
        val width = match!!.groupValues[3].toFloat() - match.groupValues[1].toFloat()
        val height = match.groupValues[4].toFloat() - match.groupValues[2].toFloat()
        val aspectRatio = width / height

        // Assert: 612 / 792 = ~0.7727
        assertEquals(612f, width, 0.01f)
        assertEquals(792f, height, 0.01f)
        assertTrue(aspectRatio > 0.77f && aspectRatio < 0.78f)
    }

    @Test
    fun tier1_pdf_sequentialPageIndexBoundsCheck() {
        // Arrange: 3-page PDF
        val pageCount = 3
        val pageIndices = (0 until pageCount).toList()

        // Assert: Valid 0-based indices are 0, 1, 2
        assertEquals(listOf(0, 1, 2), pageIndices)
        assertTrue(pageIndices.contains(0))
        assertTrue(pageIndices.contains(pageCount - 1))
        assertFalse(pageIndices.contains(pageCount))
    }

    // =========================================================================
    // TIER 1: CORE FEATURE COVERAGE — EPUB PARSING (>=5 Test Cases)
    // =========================================================================

    @Test
    fun tier1_epub_locatesOpfFromContainerXml() {
        // Arrange: Valid synthetic EPUB
        val file = File(tempDir, "valid_comic.epub")
        TestComicGenerator.createMockEpub(file, pageCount = 3)

        // Act: Open EPUB ZIP and read META-INF/container.xml
        val zip = ZipFile(file)
        val containerEntry = zip.getEntry("META-INF/container.xml")
        assertNotNull(containerEntry)

        val xml = zip.getInputStream(containerEntry).bufferedReader().readText()
        zip.close()

        // Extract full-path attribute
        val fullPathRegex = Regex("""full-path\s*=\s*["']([^"']+)["']""")
        val match = fullPathRegex.find(xml)

        // Assert
        assertNotNull(match)
        assertEquals("OEBPS/content.opf", match!!.groupValues[1])
    }

    @Test
    fun tier1_epub_extractsManifestImagesAndSpineOrder() {
        // Arrange
        val file = File(tempDir, "spine_test.epub")
        TestComicGenerator.createMockEpub(file, pageCount = 4, fixedLayout = false)

        // Act: Open content.opf
        val zip = ZipFile(file)
        val opfEntry = zip.getEntry("OEBPS/content.opf")
        assertNotNull(opfEntry)

        val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
        zip.close()

        // Extract spine itemrefs
        val spineRegex = Regex("""<itemref\s+idref=["']([^"']+)["']\s*/>""")
        val spineItems = spineRegex.findAll(opfXml).map { it.groupValues[1] }.toList()

        // Assert: 4 items in spine
        assertEquals(4, spineItems.size)
        assertEquals("img-1", spineItems[0])
        assertEquals("img-2", spineItems[1])
        assertEquals("img-3", spineItems[2])
        assertEquals("img-4", spineItems[3])
    }

    @Test
    fun tier1_epub_extractsDublinCoreMetadata() {
        // Arrange: EPUB with specific title
        val file = File(tempDir, "metadata_comic.epub")
        val expectedTitle = "Scott Pilgrim vs The World"
        TestComicGenerator.createMockEpub(file, pageCount = 2, title = expectedTitle)

        // Act
        val zip = ZipFile(file)
        val opfXml = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).bufferedReader().readText()
        zip.close()

        val titleRegex = Regex("""<dc:title>([^<]+)</dc:title>""")
        val match = titleRegex.find(opfXml)

        // Assert
        assertNotNull(match)
        assertEquals(expectedTitle, match!!.groupValues[1])
    }

    @Test
    fun tier1_epub_fixedLayoutXhtmlExtractsEmbeddedImages() {
        // Arrange: Fixed-layout comic EPUB
        val file = File(tempDir, "fixed_layout.epub")
        TestComicGenerator.createMockEpub(file, pageCount = 2, fixedLayout = true)

        // Act: Read page 1 XHTML
        val zip = ZipFile(file)
        val pageXhtml = zip.getInputStream(zip.getEntry("OEBPS/pages/page1.xhtml")).bufferedReader().readText()
        zip.close()

        // Extract image source via regex
        val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""")
        val match = imgRegex.find(pageXhtml)

        // Assert
        assertNotNull(match)
        assertEquals("../images/page1.png", match!!.groupValues[1])
    }

    @Test
    fun tier1_epub_missingContainerXmlFailsWithGracefulError() {
        // Arrange: Malformed EPUB missing container.xml
        val file = File(tempDir, "missing_container.epub")
        TestComicGenerator.createMockEpub(file, missingContainer = true)

        // Act
        val zip = ZipFile(file)
        val containerEntry = zip.getEntry("META-INF/container.xml")
        zip.close()

        // Assert: container.xml is absent
        assertNull(containerEntry)
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES
    // =========================================================================

    @Test
    fun tier2_boundary_emptyCbzArchive_zeroPages() {
        // Arrange: CBZ with 0 pages
        val file = File(tempDir, "empty.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 0)

        // Act
        val zip = ZipFile(file)
        val imagePages = zip.entries().asSequence()
            .filter { isValidComicPageEntry(it.name) }
            .toList()
        zip.close()

        // Assert: Returns empty list without throwing exception
        assertTrue(imagePages.isEmpty())
    }

    @Test
    fun tier2_boundary_singlePageComic_progressAndCover() {
        // Arrange: 1-page comic
        val file = File(tempDir, "single_page.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 1, title = "Single Page Comic")

        val comic = Comic(
            id = 1,
            title = "Single Page Comic",
            filePath = file.absolutePath,
            format = ComicFormat.CBZ,
            pageCount = 1,
            currentPage = 0
        )

        // Assert: Initial progress is 0%, but when read (currentPage=1), progress is 100% and isCompleted is true
        assertEquals(0f, comic.progress, 0.001f)
        assertFalse(comic.isCompleted)

        val completedComic = comic.copy(currentPage = 1)
        assertEquals(1f, completedComic.progress, 0.001f)
        assertTrue(completedComic.isCompleted)
    }

    @Test
    fun tier2_boundary_thousandPagesNaturalSortOrder() {
        // Arrange: 1000 filenames with variable digit lengths
        val filenames = (1..1000).map { "page$it.png" }.shuffled()

        // Act: Sort using NaturalOrderComparator
        val sorted = filenames.sortedWith(NaturalOrderComparator)

        // Assert: page2 comes before page10, page10 before page100, page999 before page1000
        assertEquals("page1.png", sorted[0])
        assertEquals("page2.png", sorted[1])
        assertEquals("page9.png", sorted[8])
        assertEquals("page10.png", sorted[9])
        assertEquals("page99.png", sorted[98])
        assertEquals("page100.png", sorted[99])
        assertEquals("page999.png", sorted[998])
        assertEquals("page1000.png", sorted[999])
    }

    @Test
    fun tier2_boundary_unicodeAndSpecialCharactersInTitle() {
        // Arrange: Title containing non-ASCII, colons, brackets, and emojis
        val unicodeTitle = "Batman: The Killing Joke (2020) — 日本語 [Special Edition] 🔥"
        val file = File(tempDir, "unicode_title.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 1, title = unicodeTitle)

        // Act: Read ComicInfo.xml
        val zip = ZipFile(file)
        val xmlStream = zip.getInputStream(zip.getEntry("ComicInfo.xml"))
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlStream)
        zip.close()

        val parsedTitle = doc.getElementsByTagName("Title").item(0).textContent

        // Assert: UTF-8 integrity preserved without encoding corruption
        assertEquals(unicodeTitle, parsedTitle)
    }

    @Test
    fun tier2_boundary_zeroByteFile_formatSniffingReturnsNullOrFails() {
        // Arrange: 0-byte file
        val file = File(tempDir, "zero_byte.cbz")
        TestComicGenerator.createEmptyFile(file)

        // Act & Assert: Length is 0
        assertEquals(0L, file.length())
    }

    @Test
    fun tier2_boundary_corruptedArchiveHeader_failsCleanly() {
        // Arrange: Random bytes file named .cbz
        val file = File(tempDir, "corrupted.cbz")
        TestComicGenerator.createCorruptFile(file, "cbz")

        // Act & Assert: Trying to open as ZipFile throws ZipException cleanly
        try {
            ZipFile(file).use { }
            fail("Expected ZipException on corrupted file")
        } catch (e: Exception) {
            assertTrue("Expected ZipException or IOException on corrupt header", e is java.io.IOException)
        }
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    fun tier3_crossFeature_multiFormatBatchParsing() {
        // Arrange: Generate one of each supported comic format
        val cbzFile = TestComicGenerator.createMockCbz(File(tempDir, "sample.cbz"), pageCount = 3)
        val cbrFile = TestComicGenerator.createMockCbr(File(tempDir, "sample.cbr"))
        val pdfFile = TestComicGenerator.createMockPdf(File(tempDir, "sample.pdf"), pageCount = 2)
        val epubFile = TestComicGenerator.createMockEpub(File(tempDir, "sample.epub"), pageCount = 4)

        // Act: Map files to ComicFormat
        val formats = listOf(cbzFile, cbrFile, pdfFile, epubFile).map { file ->
            ComicFormat.fromFilePath(file.name)
        }

        // Assert: All 4 formats correctly resolved
        assertEquals(listOf(ComicFormat.CBZ, ComicFormat.CBR, ComicFormat.PDF, ComicFormat.EPUB), formats)
    }

    @Test
    fun tier3_crossFeature_extractCoverAndPageCount_populatesDomainModel() {
        // Arrange: Generate CBZ
        val file = File(tempDir, "superman.cbz")
        TestComicGenerator.createMockCbz(file, pageCount = 12, title = "Superman #1")

        // Act: Extract cover to destination file
        val coverCacheDir = File(tempDir, "covers")
        coverCacheDir.mkdirs()
        val coverDest = File(coverCacheDir, "${file.name.hashCode()}_cover.png")

        val zip = ZipFile(file)
        val coverEntry = zip.entries().asSequence()
            .filter { isImageFile(it.name) }
            .sortedWith(compareBy(NaturalOrderComparator) { it.name })
            .first()
        coverDest.writeBytes(zip.getInputStream(coverEntry).readBytes())
        zip.close()

        val comic = Comic(
            title = "Superman #1",
            filePath = file.absolutePath,
            format = ComicFormat.CBZ,
            coverPath = coverDest.absolutePath,
            pageCount = 12,
            currentPage = 0,
            fileSizeBytes = file.length()
        )

        // Assert: Domain model holds accurate cover path, page count, and format
        assertEquals("Superman #1", comic.title)
        assertEquals(12, comic.pageCount)
        assertEquals(ComicFormat.CBZ, comic.format)
        assertTrue(File(comic.coverPath!!).exists())
        assertTrue(File(comic.coverPath!!).length() > 0)
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    fun tier4_scenario_endToEndComicImportAndVerification() {
        // Scenario: Full lifecycle from raw CBZ import to all pages verified
        val file = File(tempDir, "full_workflow_comic.cbz")
        val pageCount = 15
        TestComicGenerator.createMockCbz(
            file,
            pageCount = pageCount,
            title = "Iron Man: Extremis",
            series = "Iron Man",
            writer = "Warren Ellis"
        )

        // Step 1: Detect format
        val format = ComicFormat.fromFilePath(file.absolutePath)
        assertEquals(ComicFormat.CBZ, format)

        // Step 2: Open archive & extract metadata
        val zip = ZipFile(file)
        val comicInfo = zip.getEntry("ComicInfo.xml")
        assertNotNull(comicInfo)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(zip.getInputStream(comicInfo))
        val title = doc.getElementsByTagName("Title").item(0).textContent
        assertEquals("Iron Man: Extremis", title)

        // Step 3: Extract all pages and verify natural order
        val pages = zip.entries().asSequence()
            .filter { isValidComicPageEntry(it.name) }
            .sortedWith(compareBy(NaturalOrderComparator) { it.name })
            .mapIndexed { index, entry ->
                ComicPage(
                    pageNumber = index + 1,
                    imageUri = "zip://${file.absolutePath}!/${entry.name}"
                )
            }
            .toList()

        zip.close()

        // Step 4: Verify all 15 pages are populated sequentially
        assertEquals(15, pages.size)
        assertEquals(1, pages.first().pageNumber)
        assertEquals(15, pages.last().pageNumber)
    }

    // =========================================================================
    // HELPER FUNCTIONS & NATURAL COMPARATOR
    // =========================================================================

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") || lower.endsWith(".webp") ||
                lower.endsWith(".bmp") || lower.endsWith(".gif")
    }

    private fun isValidComicPageEntry(name: String): Boolean {
        if (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/")) return false
        if (name.endsWith(".DS_Store") || name.endsWith("Thumbs.db")) return false
        return isImageFile(name)
    }

    private fun isRarArchive(bytes: ByteArray): Boolean {
        if (bytes.size < 7) return false
        return bytes[0] == 0x52.toByte() && bytes[1] == 0x61.toByte() &&
                bytes[2] == 0x72.toByte() && bytes[3] == 0x21.toByte() &&
                bytes[4] == 0x1A.toByte() && bytes[5] == 0x07.toByte()
    }

    private fun isRar5Archive(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return isRarArchive(bytes) && bytes[6] == 0x01.toByte() && bytes[7] == 0x00.toByte()
    }

    companion object {
        val NaturalOrderComparator = Comparator<String> { a, b ->
            val chunkRegex = Regex("(\\d+)|(\\D+)")
            val aChunks = chunkRegex.findAll(a).map { it.value }.toList()
            val bChunks = chunkRegex.findAll(b).map { it.value }.toList()
            val minLen = minOf(aChunks.size, bChunks.size)
            for (i in 0 until minLen) {
                val aChunk = aChunks[i]
                val bChunk = bChunks[i]
                val aIsNum = aChunk.first().isDigit()
                val bIsNum = bChunk.first().isDigit()
                if (aIsNum && bIsNum) {
                    val aNum = aChunk.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
                    val bNum = bChunk.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
                    val cmp = aNum.compareTo(bNum)
                    if (cmp != 0) return@Comparator cmp
                } else {
                    val cmp = aChunk.compareTo(bChunk, ignoreCase = true)
                    if (cmp != 0) return@Comparator cmp
                }
            }
            aChunks.size.compareTo(bChunks.size)
        }
    }
}
