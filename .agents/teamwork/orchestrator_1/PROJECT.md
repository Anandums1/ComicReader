# Project: Android Comic Book Reader

## Architecture
The application is a standalone Android comic reader implemented in Kotlin and Jetpack Compose following MVVM + StateFlow clean architecture, Hilt dependency injection, Room persistence, and DataStore preferences.

```
com.comicreader/
├── ComicReaderApplication.kt
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── ComicDatabase.kt
│   │   │   ├── dao/ {ComicDao, BookmarkDao, ReadingStatsDao}.kt
│   │   │   └── entity/ {ComicEntity, BookmarkEntity, ReadingStatsEntity}.kt
│   │   └── datastore/ UserPreferencesDataStore.kt
│   ├── repository/ {ComicRepositoryImpl, BookmarkRepositoryImpl, StatsRepositoryImpl}.kt
│   └── scanner/ {ComicFileScanner, FolderWatcherService, RecursiveFileObserver}.kt
├── domain/
│   ├── model/ {Comic, ComicPage, Bookmark, ReadingStats, ReadingMode, AppTheme, PageAnimation}.kt
│   ├── repository/ {ComicRepository, BookmarkRepository, StatsRepository}.kt
│   └── usecase/ {GetLibraryUseCase, GetComicPagesUseCase, SaveReadingProgressUseCase, GetReadingStatsUseCase, ...}.kt
├── format/
│   ├── ComicParser.kt
│   ├── CbzParser.kt
│   ├── CbrParser.kt
│   ├── PdfParser.kt
│   └── EpubParser.kt
├── ui/
│   ├── theme/ {Theme, Color, Type}.kt
│   ├── navigation/ {NavGraph, Screen}.kt
│   ├── screens/
│   │   ├── library/ {LibraryScreen, LibraryViewModel}.kt
│   │   ├── reader/ {ReaderScreen, ReaderViewModel, HorizontalReader, VerticalReader}.kt
│   │   ├── details/ {ComicDetailScreen, ComicDetailViewModel}.kt
│   │   ├── search/ {SearchScreen, SearchViewModel}.kt
│   │   ├── history/ {HistoryScreen, HistoryViewModel}.kt
│   │   ├── favorites/ {FavoritesScreen, FavoritesViewModel}.kt
│   │   ├── stats/ {StatsScreen, StatsViewModel}.kt
│   │   └── settings/ {SettingsScreen, SettingsViewModel}.kt
│   └── components/ {ComicCoverCard, PageNavigationBar, BookmarkPanel, ReaderOverlay, HalftonePattern}.kt
└── di/ {AppModule, DatabaseModule, RepositoryModule, ParserModule}.kt
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Gradle & SDK Setup | Kotlin DSL, Min SDK 34, Target SDK 35, Java 17/21, AGP 8.7.3, KSP | M1 | ORIGINAL_REQUEST §R1 |
| 2 | Room Database | Entities (Comic, Bookmark, ReadingStats), DAOs with Flow, RoomDatabase | M1 | ORIGINAL_REQUEST §R1 |
| 3 | DataStore Preferences | UserPreferencesDataStore with Flow for themes, reading mode, animations, folder | M1 | ORIGINAL_REQUEST §R1 |
| 4 | Domain Models & Repositories | Core domain models, interfaces, repository implementations, use cases | M1 | ORIGINAL_REQUEST §R1 |
| 5 | Hilt Dependency Injection | Application class, AppModule, DatabaseModule, RepositoryModule, ParserModule | M1 | ORIGINAL_REQUEST §R1 |
| 6 | CBZ Parser | ZIP extraction via Commons Compress, natural alphanumeric sorting, cover extract | M2 | ORIGINAL_REQUEST §R2 |
| 7 | CBR Parser | RAR extraction via Junrar 7.5.5, natural sorting, error handling, cover extract | M2 | ORIGINAL_REQUEST §R2 |
| 8 | PDF Parser | Android PdfRenderer integration, Bitmap rasterization, page scaling, texture clamp | M2 | ORIGINAL_REQUEST §R2 |
| 9 | EPUB Parser | ZIP container extraction, OPF/Jsoup spine parsing, sequential comic page extraction | M2 | ORIGINAL_REQUEST §R2 |
| 10 | Comic File Scanner | Directory crawler, format sniffing, thumbnail caching, database sync | M2 | ORIGINAL_REQUEST §R3 |
| 11 | Folder Watcher Service | RecursiveFileObserver on /Downloads/Comics with 1500ms debounce | M2 | ORIGINAL_REQUEST §R3 |
| 12 | Horizontal Reader | Foundation HorizontalPager, LTR/RTL support, page transitions | M3 | ORIGINAL_REQUEST §R4 |
| 13 | Vertical Reader | LazyColumn continuous scroll, reading position preservation | M3 | ORIGINAL_REQUEST §R4 |
| 14 | Gesture Navigation | 30% tap zones, swipe gestures, volume key interception, long-press bookmark | M3 | ORIGINAL_REQUEST §R4 |
| 15 | Pinch-to-Zoom | Zoomable transformable modifier with tap zone conflict lockout (>1.05x) | M3 | ORIGINAL_REQUEST §R4 |
| 16 | Reader Overlay Controls | Center 40% tap toggle, page slider, scrubber, bookmark drawer, mode toggle | M3 | ORIGINAL_REQUEST §R4 |
| 17 | Reading Progress Auto-save | Automatic state persistence to Room on page change / lifecycle pause | M3 | ORIGINAL_REQUEST §R4 |
| 18 | 4 Switchable Themes | Dark AMOLED, Light, Material You Adaptive, Comic Halftone Canvas style | M4 | ORIGINAL_REQUEST §R5 |
| 19 | Library Screen | Grid of cover cards (Coil), swipe-to-delete, sort orders, progress indicators | M4 | comic_reader_plan §Phase 3 |
| 20 | Detail & Bookmarks Screen | Metadata overview, bookmark list with jump-to-page, start/continue reading | M4 | comic_reader_plan §Phase 5 |
| 21 | Search Screen | Full-text title search, format filters (CBZ, CBR, PDF, EPUB), favorites/unread | M4 | comic_reader_plan §Phase 5 |
| 22 | Favorites & History | Favorites toggle, History list of last 50 read comics | M4 | comic_reader_plan §Phase 5 |
| 23 | Reading Stats Dashboard | Pages read, reading session tracker, Canvas weekly reading bar charts | M4 | ORIGINAL_REQUEST §R5 |
| 24 | Settings Screen | Theme selector, default reading mode, animation style, folder picker, brightness | M4 | comic_reader_plan §Phase 6 |
| 25 | Compose NavGraph | Typed navigation graph linking all screens and passing comicId / page params | M4 | comic_reader_plan §Phase 1 |
| 26 | E2E Integration & Verification | 100% pass of E2E test suite (Tiers 1-4) & adversarial test hardening (Tier 5) | M5 | ORIGINAL_REQUEST §Acceptance |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Project Setup & Architecture Foundation | Gradle project, Kotlin DSL, KSP, Room DB, DataStore, Domain Models, Repositories, Hilt DI | None | DONE |
| M2 | Comic Parsers & File Scanner | ComicParser, CbzParser, CbrParser, PdfParser, EpubParser, ComicFileScanner, RecursiveFileObserver | M1 | DONE |
| M3 | Core Comic Reader Experience | ReaderViewModel, HorizontalReader, VerticalReader, ReaderOverlay, Gestures, Zoom, Progress Auto-save | M1, M2 | DONE |
| M4 | Library, Stats Dashboard, Settings & UI Themes | LibraryScreen, DetailScreen, SearchScreen, Favorites/History, StatsScreen (Canvas chart), Settings, 4 Themes, NavGraph | M1, M2, M3 | DONE |
| M5 | E2E Integration & Final Verification | Full E2E Test Suite pass (Tiers 1-4), Adversarial hardening (Tier 5), Clean Forensic Audit | M1, M2, M3, M4, TEST_READY.md | DONE |

## Interface Contracts

### ComicParser Contract
```kotlin
interface ComicParser {
    fun canParse(filePath: String): Boolean
    fun extractMetadata(file: File): ComicMetadata
    fun extractCover(file: File, cacheDir: File): File?
    fun getPages(file: File): Flow<List<ComicPage>>
    suspend fun loadPageBitmap(page: ComicPage, targetWidth: Int, targetHeight: Int): Bitmap?
}
```

### Data Layer Contracts
```kotlin
interface ComicRepository {
    fun getAllComics(): Flow<List<Comic>>
    fun getComicById(id: Long): Flow<Comic?>
    suspend fun insertOrUpdateComic(comic: Comic): Long
    suspend fun deleteComic(comic: Comic)
    suspend fun updateReadingProgress(comicId: Long, page: Int, totalPages: Int)
    suspend fun toggleFavorite(comicId: Long, isFavorite: Boolean)
    fun searchComics(query: String, format: String?): Flow<List<Comic>>
    fun getRecentHistory(limit: Int): Flow<List<Comic>>
    fun getFavoriteComics(): Flow<List<Comic>>
}

interface BookmarkRepository {
    fun getBookmarksForComic(comicId: Long): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark): Long
    suspend fun deleteBookmark(bookmarkId: Long)
}

interface StatsRepository {
    fun getReadingStats(): Flow<ReadingStats>
    suspend fun recordReadingSession(comicId: Long, pagesRead: Int, durationSeconds: Long)
    fun getWeeklyStats(): Flow<List<DailyReadingStat>>
}
```

### DataStore Preference Contract
```kotlin
interface UserPreferencesDataStore {
    val themeFlow: Flow<AppTheme>
    val readingModeFlow: Flow<ReadingMode>
    val pageAnimationFlow: Flow<PageAnimation>
    val watchedDirectoryFlow: Flow<String>
    val keepScreenAwakeFlow: Flow<Boolean>
    val brightnessLevelFlow: Flow<Float>
    suspend fun setTheme(theme: AppTheme)
    suspend fun setReadingMode(mode: ReadingMode)
    suspend fun setPageAnimation(animation: PageAnimation)
    suspend fun setWatchedDirectory(path: String)
    suspend fun setKeepScreenAwake(keepAwake: Boolean)
    suspend fun setBrightnessLevel(level: Float)
}
```

## Code Layout
```
app/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/comicreader/
│   │   │   ├── ComicReaderApplication.kt
│   │   │   ├── data/
│   │   │   ├── domain/
│   │   │   ├── format/
│   │   │   ├── ui/
│   │   │   └── di/
│   │   └── res/
│   │       ├── values/
│   │       ├── drawable/
│   │       └── mipmap/
│   └── test/
│       └── java/com/comicreader/
│           ├── data/
│           ├── format/
│           ├── domain/
│           └── ui/
build.gradle.kts
settings.gradle.kts
gradle.properties
local.properties
```
