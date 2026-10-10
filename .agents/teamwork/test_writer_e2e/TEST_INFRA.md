# 🧪 E2E Test Suite Infrastructure & Architecture Specification

**Project**: Android Comic Book Reader (`com.comicreader`)  
**Track**: E2E Integration & End-to-End Testing  
**Author**: E2E Test Writer (`test_writer_e2e`)  
**Target Runner**: JVM Unit Test (`./gradlew testDebugUnitTest`)  

---

## 1. Test Architecture & 4-Tier Methodology

The E2E test suite adopts an opaque-box, contract-driven test design based strictly on specifications in `ORIGINAL_REQUEST.md`, `comic_reader_plan.md`, `PROJECT.md`, `architecture_specs.md`, and `format_ui_specs.md`.

```
                  ┌────────────────────────────────────────┐
                  │ Tier 4: Real-World Application Journeys│
                  │   (End-to-end reading, scan & stats)   │
                  ├────────────────────────────────────────┤
                  │ Tier 3: Cross-Feature Combinations     │
                  │ (Parse -> Scan -> Read -> Save -> Theme│
                  ├────────────────────────────────────────┤
                  │ Tier 2: Boundary & Corner Cases        │
                  │ (0-byte, 1000+ pgs, RAR5, corrupt, NaN)│
                  ├────────────────────────────────────────┤
                  │ Tier 1: Core Feature Coverage          │
                  │(>=5 tests per core feature category)   │
                  └────────────────────────────────────────┘
```

### 1.1 Tier Definitions

1. **Tier 1 — Core Feature Coverage**:
   - Minimum 5 comprehensive test cases per core feature category:
     - CBZ Parsing (ZIP structure, image stream extraction, natural order sorting, ComicInfo.xml metadata)
     - CBR Parsing (RAR header reading, image extraction, backslash path normalization)
     - PDF Rendering (Page count, page rasterization, display density scaling, page dimension tracking)
     - EPUB Spine Parsing (Container discovery, OPF manifest/spine resolution, XHTML & SVG image extraction)
     - Library Management & File Scanning (Directory walk, format filtering, Room DB sync, cover extraction)
     - Room Persistence (ComicDao, BookmarkDao, ReadingStatsDao with Flow observers and cascade deletes)
     - Reader Modes & Gestures (Horizontal pager LTR/RTL, Vertical continuous scroll, tap zones, volume keys)
     - Reading Stats Analytics (Lifetime pages read, session duration, weekly Canvas chart scaling)
     - Theme Switching (Dark AMOLED, Light, Material You Adaptive, Comic Halftone Canvas style)

2. **Tier 2 — Boundary & Corner Cases**:
   - Zero-length / empty archives (0 pages)
   - Single-page comics (progress = 100%, slider edge cases)
   - Large comics (1000+ pages: alphanumeric sorting verification `page1.png` vs `page10.png` vs `page1000.png`)
   - Non-image artifacts inside archives (`__MACOSX/._page.jpg`, `.DS_Store`, `Thumbs.db`, `readme.txt`)
   - Corrupted file headers (invalid magic bytes)
   - RAR5 archives triggering `UnsupportedRarV5Exception`
   - Special characters & UTF-8 in title, author, and filename (e.g. `Batman: The Killing Joke (2020) — 日本語 [Special].cbz`)
   - Reading progress boundaries (0% initial start, 100% completion)
   - Pinch-to-zoom boundaries (1.0x minimum, 5.0x maximum, lockout threshold > 1.05x disabling tap turns)
   - Zero statistics (fresh app installation without past reading sessions)

3. **Tier 3 — Cross-Feature Combinations**:
   - Parse CBZ -> Library Scanner indexes comic -> Bookmark page 5 -> Update reading stats -> Switch theme to Comic Style -> Re-scan directory without duplicates.
   - Switch reading mode from Horizontal to Vertical mid-reading -> preserve current page index -> record reading session.
   - Delete comic from library -> foreign key cascade deletes bookmarks -> foreign key SET NULL preserves aggregate stats history.

4. **Tier 4 — Real-World Application Workflows**:
   - Complete comic reader journey: user imports library of mixed formats (CBZ, CBR, PDF, EPUB), browses library grid, opens comic, reads across 20 pages using tap zones and volume keys, zooms into action panel, adds bookmark, exits reader, verifies dashboard stats and weekly chart updated.

---

## 2. Test Data Generation Architecture (`TestComicGenerator.kt`)

Tests are **100% self-contained and isolated**. No external physical fixtures or network calls are required. `TestComicGenerator` programmatically synthesizes valid archive bytes, file headers, and metadata in temporary directories.

### 2.1 Synthetic Format Generators

| Format | Generator Method | Output Structure |
|---|---|---|
| **CBZ** | `createMockCbz(...)` | Valid ZIP archive with configurable PNG/JPEG pages, natural alphanumeric filenames, and optional `ComicInfo.xml`. |
| **CBR** | `createMockCbr(...)` | Valid RAR archive or simulated RAR5 signature bytes (`52 61 72 21 1A 07 01 00`) for exception testing. |
| **PDF** | `createMockPdf(...)` | Valid standard PDF 1.4 document with Catalog, Pages tree, Page objects, Content streams, xref table, and trailer. |
| **EPUB** | `createMockEpub(...)` | Valid EPUB archive with `mimetype`, `META-INF/container.xml`, `OEBPS/content.opf`, manifest, spine, and XHTML/image items. |
| **Corrupt** | `createCorruptFile(...)` | Arbitrary garbage bytes or truncated headers for testing error recovery and exception handling. |

### 2.2 In-Memory Image Synthesis
- Generates valid 100x100 or custom dimension PNG/JPEG image bytes using Java's standard `BufferedImage` and `ImageIO` or pure PNG header bytes (`89 50 4E 47 0D 0A 1A 0A`).
- Page numbers are embedded into image metadata or generated with distinct pixel color hashes for assertion verification.

---

## 3. Test Runner & Execution Strategy

Tests run directly within the JVM test environment (`app/src/test/java/com/comicreader/e2e/`) via:
```bash
./gradlew testDebugUnitTest --tests "com.comicreader.e2e.*"
```

### 3.1 Test Isolation Principles
- Each test class initializes a clean JUnit `TemporaryFolder` rule or temporary directory.
- Database tests utilize Room in-memory database: `Room.inMemoryDatabaseBuilder(...)`.
- Flows and coroutines run with `StandardTestDispatcher` / `runTest` from `kotlinx.coroutines.test`.
- DataStore preferences use temporary preference files with unique names per test.

---

## 4. Coverage Matrix (26 Cataloged Features)

| Feature # | Feature Name | Test Suite | Tiers Covered | Target Verification |
|---|---|---|---|---|
| 1 | Gradle & SDK Setup | Build Verification | Tier 1 | JVM toolchain, dependencies loaded |
| 2 | Room Database | `LibraryManagementE2ETest`, `ReaderWorkflowE2ETest` | T1, T2, T3 | Comic, Bookmark, Stats entities & DAOs |
| 3 | DataStore Preferences | `StatsAndThemesE2ETest` | T1, T2, T3 | Preferences flow, setting mutations |
| 4 | Domain Models & Repos | All Suites | T1, T2, T3, T4 | Comic, Bookmark, ReadingStats, ComicPage |
| 5 | Hilt Dependency Injection | All Suites | T1, T4 | Factory instantiation & module contracts |
| 6 | CBZ Parser | `ComicFormatE2ETest` | T1, T2, T3, T4 | Natural sort, ComicInfo.xml, covers |
| 7 | CBR Parser | `ComicFormatE2ETest` | T1, T2, T3 | RAR headers, RAR5 exception handling |
| 8 | PDF Parser | `ComicFormatE2ETest` | T1, T2, T3 | PdfRenderer contract, page count, scaling |
| 9 | EPUB Parser | `ComicFormatE2ETest` | T1, T2, T3, T4 | OPF spine traversal, fixed-layout SVG/XHTML |
| 10 | Comic File Scanner | `LibraryManagementE2ETest` | T1, T2, T3, T4 | Batch & single scan, format sniffing |
| 11 | Folder Watcher Service | `LibraryManagementE2ETest` | T1, T2, T3 | Event debounce, CLOSE_WRITE filter |
| 12 | Horizontal Reader | `ReaderWorkflowE2ETest` | T1, T2, T3, T4 | LTR/RTL page navigation, bounds clamp |
| 13 | Vertical Reader | `ReaderWorkflowE2ETest` | T1, T2, T3 | Continuous scroll index tracking |
| 14 | Gesture Navigation | `ReaderWorkflowE2ETest` | T1, T2, T4 | 30% tap zones, volume key paging |
| 15 | Pinch-to-Zoom | `ReaderWorkflowE2ETest` | T1, T2, T4 | 1.0x-5.0x zoom, pan lock, tap lockout |
| 16 | Reader Overlay Controls | `ReaderWorkflowE2ETest` | T1, T2, T4 | Center 40% tap toggle, scrubber slider |
| 17 | Reading Progress Auto-save | `ReaderWorkflowE2ETest` | T1, T2, T3, T4 | Auto-save on page change, bounds check |
| 18 | 4 Switchable Themes | `StatsAndThemesE2ETest` | T1, T2, T3, T4 | Dark, Light, Adaptive, Comic Halftone |
| 19 | Library Screen | `LibraryManagementE2ETest` | T1, T2, T3, T4 | Grid sorting, swipe delete, progress tag |
| 20 | Detail & Bookmarks Screen | `LibraryManagementE2ETest`, `ReaderWorkflowE2ETest` | T1, T2, T3 | Metadata view, jump-to-page, cascade del |
| 21 | Search Screen | `LibraryManagementE2ETest` | T1, T2, T3 | Title query, format filter (CBZ, CBR, ...) |
| 22 | Favorites & History | `LibraryManagementE2ETest` | T1, T2, T3 | Favorite toggle, last 50 read history |
| 23 | Reading Stats Dashboard | `StatsAndThemesE2ETest` | T1, T2, T3, T4 | Lifetime totals, weekly Canvas chart |
| 24 | Settings Screen | `StatsAndThemesE2ETest` | T1, T2, T3 | Theme, reading mode, brightness override |
| 25 | Compose NavGraph | `ReaderWorkflowE2ETest`, `LibraryManagementE2ETest` | T1, T3, T4 | Route arguments (comicId, page) |
| 26 | E2E Integration & Verification | All Suites | T1, T2, T3, T4 | Full multi-tier pass, 0 facade tests |
