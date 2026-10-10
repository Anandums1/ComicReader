# 📐 Architectural & Technical Specification Report

**Project**: Android Comic Book Reader  
**Package Name**: `com.comicreader`  
**Specification Miner**: Architecture Spec Miner (`spec_miner_survey_1`)  
**Target Platform**: Android (Min SDK 34 / Android 14, Target SDK 35 / Android 15)  
**Authoritative Sources**: `comic_reader_plan.md`, `ORIGINAL_REQUEST.md`, Android Official Developer Documentation (Android 14/15, Compose BOM 2024.09.00, Hilt 2.52, Room 2.6.1, DataStore 1.1.1).

---

## 1. Executive Summary & Architectural Overview

The application is structured following modern **Clean Architecture** with **MVVM** (Model-View-ViewModel) and **Unidirectional Data Flow (UDF)** powered by Kotlin Coroutines and `StateFlow` / `SharedFlow`.

### Layer Separation
1. **Presentation Layer (`com.comicreader.ui`)**:
   - Built entirely in **Jetpack Compose** (Material 3).
   - ViewModels expose immutable `StateFlow<UiState>` to composables.
   - User interactions trigger intent events (`onEvent(UiEvent)`).
   - Single Activity architecture (`MainActivity`) hosting `NavGraph`.
2. **Domain Layer (`com.comicreader.domain`)**:
   - Pure Kotlin business logic. Zero Android framework dependencies where possible.
   - Core Domain Models: `Comic`, `ComicPage`, `Bookmark`, `ReadingStats`.
   - Business Use Cases: `GetLibraryUseCase`, `GetComicPagesUseCase`, `SaveReadingProgressUseCase`, `GetReadingStatsUseCase`, `ToggleFavoriteUseCase`, `SearchComicsUseCase`.
3. **Data Layer (`com.comicreader.data`)**:
   - Local persistence via **Room Database** (`ComicDatabase`) with reactive `Flow` query streams.
   - Key-value user configuration via **DataStore Preferences** (`UserPreferencesDataStore`).
   - Repository implementations (`ComicRepositoryImpl`, `BookmarkRepositoryImpl`, `StatsRepositoryImpl`) orchestrating local DB, file storage, and format parsers.
   - File observation and directory scanning via `FolderWatcherService` and `ComicFileScanner`.
4. **Format Engine Layer (`com.comicreader.format`)**:
   - Unified interface `ComicParser` providing uniform reactive extraction of comic pages (`Flow<List<ComicPage>>`).
   - Dedicated parsers: `CbzParser` (ZIP), `CbrParser` (RAR), `PdfParser` (PdfRenderer), `EpubParser` (EPUB/XHTML).
5. **Dependency Injection Layer (`com.comicreader.di`)**:
   - **Hilt** (Dagger) providing compile-time dependency injection across application lifecycle.

---

## 2. Android Gradle Project Configuration (Kotlin DSL)

### 2.1 Build System & Toolchain Requirements
- **Gradle Version**: 8.7 or 8.9
- **Android Gradle Plugin (AGP)**: 8.5.2 / 8.6.0
- **Kotlin Version**: 2.0.20 (or 1.9.24)
- **Compose Compiler**: Kotlin Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) for Kotlin 2.0+, or Compose Compiler 1.5.14 for Kotlin 1.9.24.
- **Annotation Processing**: KSP (`com.google.devtools.ksp:2.0.20-1.0.25` or compatible) preferred over Kapt for faster Room and Hilt generation.
- **Java Toolchain**: Java 21 (Temurin-21 installed in runtime environment).

### 2.2 Root `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") } // For junrar & epublib
    }
}

rootProject.name = "ComicReader"
include(":app")
```

### 2.3 Root `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

### 2.4 App Module `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.comicreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.comicreader"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    // Jetpack Compose BOM & Core UI
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation & Lifecycle
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Image Loading (Coil Compose)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Comic Format Parsers
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("io.github.siegeindahouse:epublib-core:4.0")
    implementation("org.jsoup:jsoup:1.18.1")

    // Coroutines & Concurrency
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Foundation Pager (Compose Native Pager replaces Accompanist)
    implementation("androidx.compose.foundation:foundation")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

### 2.5 AndroidManifest.xml Specification
- **Permissions**:
  ```xml
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
  <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  <uses-permission android:name="android.permission.WAKE_LOCK" />
  ```
- **Application Node**:
  - `android:name=".ComicReaderApp"` (annotated with `@HiltAndroidApp`)
  - `android:allowBackup="true"`
  - `android:supportsRtl="true"`
  - `android:theme="@style/Theme.ComicReader"`
- **MainActivity**:
  - `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"`
  - `android:windowSoftInputMode="adjustResize"`
  - `intent-filter` for `MAIN` and `LAUNCHER`.
  - `intent-filter` for `android.intent.action.VIEW` for `.cbz`, `.cbr`, `.pdf`, `.epub` mime-types.

---

## 3. Room Database Schema Specification

### 3.1 Overview
Database Name: `comic_reader.db`  
Version: `1`  
Room Database Class: `com.comicreader.data.local.db.ComicDatabase`

### 3.2 Entities & Schemas

#### 1. `ComicEntity` (`comics` table)
Represents a parsed or discovered comic item in the local library.

| Column | Type | Nullable | Primary Key | Default | Constraints / Index | Description |
|---|---|---|---|---|---|---|
| `id` | `INTEGER` (`Long`) | No | Yes (auto-generate) | `0L` | Primary Key | Unique comic identifier |
| `title` | `TEXT` (`String`) | No | No | - | - | Extracted or filename-derived title |
| `filePath` | `TEXT` (`String`) | No | No | - | **UNIQUE INDEX** (`index_comics_filePath`) | Absolute path to archive/document |
| `format` | `TEXT` (`String`) | No | No | - | Index (`index_comics_format`) | Format string: "CBZ", "CBR", "PDF", "EPUB" |
| `coverPath` | `TEXT` (`String`) | Yes | No | `null` | - | Cached thumbnail file path |
| `pageCount` | `INTEGER` (`Int`) | No | No | `0` | - | Total number of pages |
| `currentPage` | `INTEGER` (`Int`) | No | No | `0` | - | Last read page index (0-based) |
| `lastReadTimestamp` | `INTEGER` (`Long`) | No | No | `0L` | Index (`index_comics_lastRead`) | Epoch millis of last reading session |
| `isFavorite` | `INTEGER` (`Boolean`) | No | No | `0` (`false`) | Index (`index_comics_isFavorite`) | Starred/favorite flag |
| `fileSizeBytes` | `INTEGER` (`Long`) | No | No | `0L` | - | Size in bytes for sorting/info |
| `addedTimestamp` | `INTEGER` (`Long`) | No | No | System millis | Index (`index_comics_addedTimestamp`) | Epoch millis when comic was imported |

**Indices**:
- `index_comics_filePath`: `unique = true` on `["filePath"]`
- `index_comics_format`: `["format"]`
- `index_comics_isFavorite`: `["isFavorite"]`
- `index_comics_lastReadTimestamp`: `["lastReadTimestamp"]`
- `index_comics_addedTimestamp`: `["addedTimestamp"]`

#### 2. `BookmarkEntity` (`bookmarks` table)
Represents a user-created bookmark referencing a specific page of a comic.

| Column | Type | Nullable | Primary Key | Default | Constraints / Index | Description |
|---|---|---|---|---|---|---|
| `id` | `INTEGER` (`Long`) | No | Yes (auto-generate) | `0L` | Primary Key | Unique bookmark identifier |
| `comicId` | `INTEGER` (`Long`) | No | No | - | **FOREIGN KEY**, Index (`index_bookmarks_comicId`) | References `comics(id)` ON DELETE CASCADE |
| `pageNumber` | `INTEGER` (`Int`) | No | No | - | Composite Index (`index_bookmarks_comic_page`) | 0-based page number |
| `thumbnailPath` | `TEXT` (`String`) | Yes | No | `null` | - | Cached page thumbnail image path |
| `note` | `TEXT` (`String`) | Yes | No | `null` | - | Optional user annotation/note |
| `createdAt` | `INTEGER` (`Long`) | No | No | System millis | - | Epoch millis of bookmark creation |

**Foreign Key**:
```kotlin
ForeignKey(
    entity = ComicEntity::class,
    parentColumns = ["id"],
    childColumns = ["comicId"],
    onDelete = ForeignKey.CASCADE
)
```
**Indices**:
- `index_bookmarks_comicId`: `["comicId"]`
- `index_bookmarks_comic_page`: `unique = true` on `["comicId", "pageNumber"]`

#### 3. `ReadingStatsEntity` (`reading_stats` table)
Represents a recorded reading session for analytics and charts.

| Column | Type | Nullable | Primary Key | Default | Constraints / Index | Description |
|---|---|---|---|---|---|---|
| `id` | `INTEGER` (`Long`) | No | Yes (auto-generate) | `0L` | Primary Key | Unique session log entry ID |
| `comicId` | `INTEGER` (`Long`) | Yes | No | `null` | **FOREIGN KEY**, Index (`index_stats_comicId`) | References `comics(id)` ON DELETE SET NULL |
| `sessionStartTimestamp` | `INTEGER` (`Long`) | No | No | - | Index (`index_stats_start`) | Epoch millis when session started |
| `sessionEndTimestamp` | `INTEGER` (`Long`) | No | No | - | - | Epoch millis when session ended |
| `durationSeconds` | `INTEGER` (`Long`) | No | No | `0L` | - | Active reading time in seconds |
| `pagesRead` | `INTEGER` (`Int`) | No | No | `0` | - | Total pages turned during session |

**Foreign Key**:
```kotlin
ForeignKey(
    entity = ComicEntity::class,
    parentColumns = ["id"],
    childColumns = ["comicId"],
    onDelete = ForeignKey.SET_NULL
)
```
**Indices**:
- `index_stats_comicId`: `["comicId"]`
- `index_stats_sessionStart`: `["sessionStartTimestamp"]`

---

### 3.3 Data Access Objects (DAOs)

#### `ComicDao`
```kotlin
@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY title ASC")
    fun getAllComics(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics ORDER BY addedTimestamp DESC")
    fun getComicsRecentlyAdded(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE lastReadTimestamp > 0 ORDER BY lastReadTimestamp DESC LIMIT :limit")
    fun getRecentlyRead(limit: Int = 50): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics WHERE id = :id")
    fun getComicById(id: Long): Flow<ComicEntity?>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getComicByIdOnce(id: Long): ComicEntity?

    @Query("SELECT * FROM comics WHERE filePath = :filePath LIMIT 1")
    suspend fun getComicByPath(filePath: String): ComicEntity?

    @Query("""
        SELECT * FROM comics 
        WHERE (:query IS NULL OR title LIKE '%' || :query || '%')
          AND (:format IS NULL OR format = :format)
          AND (:onlyFavorites = 0 OR isFavorite = 1)
        ORDER BY 
          CASE WHEN :sortOrder = 'TITLE' THEN title END ASC,
          CASE WHEN :sortOrder = 'RECENTLY_ADDED' THEN addedTimestamp END DESC,
          CASE WHEN :sortOrder = 'LAST_READ' THEN lastReadTimestamp END DESC
    """)
    fun filterComics(
        query: String?,
        format: String?,
        onlyFavorites: Boolean,
        sortOrder: String
    ): Flow<List<ComicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComic(comic: ComicEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertComics(comics: List<ComicEntity>): List<Long>

    @Update
    suspend fun updateComic(comic: ComicEntity)

    @Query("UPDATE comics SET currentPage = :page, lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, page: Int, timestamp: Long)

    @Query("UPDATE comics SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteComic(comic: ComicEntity)

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun deleteComicById(id: Long)

    @Query("SELECT COUNT(*) FROM comics")
    fun getTotalComicsCount(): Flow<Int>
}
```

#### `BookmarkDao`
```kotlin
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE comicId = :comicId ORDER BY pageNumber ASC")
    fun getBookmarksForComic(comicId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE comicId = :comicId AND pageNumber = :pageNumber)")
    fun isPageBookmarked(comicId: Long, pageNumber: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE comicId = :comicId AND pageNumber = :pageNumber")
    suspend fun deleteBookmarkByPage(comicId: Long, pageNumber: Int)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)
}
```

#### `ReadingStatsDao`
```kotlin
@Dao
interface ReadingStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingStatsEntity): Long

    @Query("SELECT * FROM reading_stats ORDER BY sessionStartTimestamp DESC")
    fun getAllSessions(): Flow<List<ReadingStatsEntity>>

    @Query("SELECT COALESCE(SUM(pagesRead), 0) FROM reading_stats")
    fun getTotalPagesRead(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM reading_stats")
    fun getTotalReadingTimeSeconds(): Flow<Long>

    @Query("SELECT COUNT(*) FROM comics WHERE currentPage >= (pageCount - 1) AND pageCount > 0")
    fun getCompletedComicsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM comics WHERE currentPage > 0 AND currentPage < (pageCount - 1)")
    fun getInProgressComicsCount(): Flow<Int>

    @Query("""
        SELECT * FROM reading_stats 
        WHERE sessionStartTimestamp >= :sinceTimestamp 
        ORDER BY sessionStartTimestamp ASC
    """)
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<ReadingStatsEntity>>
}
```

---

## 4. DataStore Preferences Specification

### 4.1 Class: `UserPreferencesDataStore`
Path: `com.comicreader.data.local.datastore.UserPreferencesDataStore`  
Storage: AndroidX DataStore (`datastore-preferences:1.1.1`)  
File name: `user_preferences.preferences_pb`

### 4.2 Preference Keys & Definitions

| Preference Key | Type | Default Value | Enum / Allowed Values | Description |
|---|---|---|---|---|
| `KEY_THEME` | `stringPreferencesKey("app_theme")` | `"DARK"` | `"DARK"`, `"LIGHT"`, `"ADAPTIVE"`, `"COMIC"` | Active UI theme |
| `KEY_READING_MODE` | `stringPreferencesKey("reading_mode")` | `"HORIZONTAL"` | `"HORIZONTAL"`, `"VERTICAL"` | Pager mode (page-by-page vs continuous webtoon) |
| `KEY_PAGE_TURN_ANIM` | `stringPreferencesKey("page_turn_anim")` | `"SLIDE"` | `"SLIDE"`, `"CURL"`, `"FADE"`, `"NONE"` | Transition animation style |
| `KEY_WATCHED_DIR` | `stringPreferencesKey("watched_directory")` | `"/storage/emulated/0/Downloads/Comics"` | Absolute folder path | Auto-scan directory |
| `KEY_KEEP_SCREEN_AWAKE`| `booleanPreferencesKey("keep_screen_awake")` | `true` | `true`, `false` | Prevent screen timeout while reading |
| `KEY_BRIGHTNESS` | `floatPreferencesKey("reader_brightness")` | `-1.0f` | `-1.0f` (system) or `0.0f` to `1.0f` | In-app reader brightness override |
| `KEY_READING_DIRECTION`| `stringPreferencesKey("reading_direction")` | `"LTR"` | `"LTR"`, `"RTL"` | Reading direction (Western vs Manga) |
| `KEY_VOLUME_KEY_NAV` | `booleanPreferencesKey("volume_key_nav")` | `true` | `true`, `false` | Use volume buttons to turn pages |

### 4.3 Data Model & Flow API
```kotlin
data class UserPreferences(
    val theme: ThemeMode = ThemeMode.DARK,
    val readingMode: ReadingMode = ReadingMode.HORIZONTAL,
    val pageTurnAnimation: PageTurnAnimation = PageTurnAnimation.SLIDE,
    val watchedDirectory: String = "/storage/emulated/0/Downloads/Comics",
    val keepScreenAwake: Boolean = true,
    val brightness: Float = -1.0f,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val volumeKeyNav: Boolean = true
)

interface UserPreferencesDataSource {
    val preferencesFlow: Flow<UserPreferences>
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setReadingMode(mode: ReadingMode)
    suspend fun setPageTurnAnimation(animation: PageTurnAnimation)
    suspend fun setWatchedDirectory(path: String)
    suspend fun setKeepScreenAwake(keepAwake: Boolean)
    suspend fun setBrightness(brightness: Float)
    suspend fun setReadingDirection(direction: ReadingDirection)
    suspend fun setVolumeKeyNav(enabled: Boolean)
}
```

---

## 5. Domain Models and Use Cases Specification

### 5.1 Domain Models (`com.comicreader.domain.model`)

#### `Comic`
```kotlin
data class Comic(
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: ComicFormat,
    val coverPath: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val lastReadTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (pageCount > 0) currentPage.toFloat() / (pageCount - 1).coerceAtLeast(1) else 0f
    val isCompleted: Boolean
        get() = pageCount > 0 && currentPage >= (pageCount - 1)
}
```

#### `ComicPage`
```kotlin
data class ComicPage(
    val pageNumber: Int,
    val imageUri: String? = null,
    val bitmap: Bitmap? = null,
    val width: Int = 0,
    val height: Int = 0
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 1f
}
```

#### `Bookmark`
```kotlin
data class Bookmark(
    val id: Long = 0,
    val comicId: Long,
    val pageNumber: Int,
    val thumbnailPath: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

#### `ReadingStats`
```kotlin
data class ReadingStats(
    val totalPagesRead: Int,
    val totalReadingTimeSeconds: Long,
    val completedComics: Int,
    val inProgressComics: Int,
    val recentSessions: List<ReadingSession> = emptyList(),
    val weeklyPageCounts: Map<String, Int> = emptyMap() // "Mon" -> 45, etc.
)

data class ReadingSession(
    val id: Long = 0,
    val comicId: Long?,
    val comicTitle: String? = null,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val pagesRead: Int
)
```

#### Domain Enums
```kotlin
enum class ComicFormat(val extension: String) {
    CBZ("cbz"),
    CBR("cbr"),
    PDF("pdf"),
    EPUB("epub");

    companion object {
        fun fromExtension(ext: String): ComicFormat? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }
    }
}

enum class ThemeMode { DARK, LIGHT, ADAPTIVE, COMIC }
enum class ReadingMode { HORIZONTAL, VERTICAL }
enum class PageTurnAnimation { SLIDE, CURL, FADE, NONE }
enum class ReadingDirection { LTR, RTL }
enum class SortOrder { TITLE, RECENTLY_ADDED, LAST_READ, FAVORITES }
```

---

### 5.2 Use Cases (`com.comicreader.domain.usecase`)

1. **`GetLibraryUseCase`**:
   - Signature: `operator fun invoke(sortOrder: SortOrder = SortOrder.TITLE, formatFilter: ComicFormat? = null, query: String? = null): Flow<List<Comic>>`
   - Coordinates with `ComicRepository.filterComics(...)`.
2. **`GetComicPagesUseCase`**:
   - Signature: `operator fun invoke(comicId: Long): Flow<List<ComicPage>>`
   - Fetches comic metadata via `ComicRepository`, resolves format, and delegates page decoding to `ComicParserFactory`.
3. **`SaveReadingProgressUseCase`**:
   - Signature: `suspend operator fun invoke(comicId: Long, pageNumber: Int, sessionDurationSeconds: Long, pagesTurned: Int)`
   - Atomically updates `Comic.currentPage`, `lastReadTimestamp`, and logs a new `ReadingStatsEntity` session in Room DB.
4. **`GetReadingStatsUseCase`**:
   - Signature: `operator fun invoke(): Flow<ReadingStats>`
   - Combines total pages read, total reading time, completed and in-progress comic counts, and aggregates weekly breakdown for Canvas charting.
5. **`ToggleFavoriteUseCase`**:
   - Signature: `suspend operator fun invoke(comicId: Long, isFavorite: Boolean)`
6. **`ManageBookmarksUseCase`**:
   - Signatures:
     - `fun getBookmarks(comicId: Long): Flow<List<Bookmark>>`
     - `suspend fun addBookmark(comicId: Long, pageNumber: Int, thumbnailPath: String?, note: String?): Long`
     - `suspend fun removeBookmark(comicId: Long, pageNumber: Int)`
     - `fun isBookmarked(comicId: Long, pageNumber: Int): Flow<Boolean>`

---

## 6. Repository Layer Specification

### 6.1 `ComicRepository`
```kotlin
interface ComicRepository {
    fun getAllComics(sortOrder: SortOrder = SortOrder.TITLE): Flow<List<Comic>>
    fun getComicById(id: Long): Flow<Comic?>
    suspend fun getComicByIdOnce(id: Long): Comic?
    fun searchComics(query: String, filterFormat: ComicFormat? = null): Flow<List<Comic>>
    fun getFavorites(): Flow<List<Comic>>
    fun getRecentlyRead(limit: Int = 50): Flow<List<Comic>>
    suspend fun insertOrUpdateComic(comic: Comic): Long
    suspend fun updateReadingProgress(id: Long, pageNumber: Int)
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)
    suspend fun deleteComic(id: Long)
    suspend fun scanDirectory(directoryPath: String): Result<List<Comic>>
}
```

### 6.2 `BookmarkRepository`
```kotlin
interface BookmarkRepository {
    fun getBookmarksForComic(comicId: Long): Flow<List<Bookmark>>
    fun getAllBookmarks(): Flow<List<Bookmark>>
    fun isPageBookmarked(comicId: Long, pageNumber: Int): Flow<Boolean>
    suspend fun addBookmark(comicId: Long, pageNumber: Int, thumbnailPath: String? = null, note: String? = null): Long
    suspend fun deleteBookmark(comicId: Long, pageNumber: Int)
    suspend fun deleteBookmarkById(id: Long)
}
```

### 6.3 `StatsRepository`
```kotlin
interface StatsRepository {
    suspend fun recordReadingSession(comicId: Long?, durationSeconds: Long, pagesRead: Int): Long
    fun getReadingStats(): Flow<ReadingStats>
    fun getAllSessions(): Flow<List<ReadingSession>>
}
```

---

## 7. Hilt Dependency Injection Architecture

### 7.1 Application Class
```kotlin
@HiltAndroidApp
class ComicReaderApp : Application()
```

### 7.2 Modules

#### 1. `AppModule` (`com.comicreader.di.AppModule`)
- Scope: `@InstallIn(SingletonComponent::class)`
- Provides:
  - `@Provides @Singleton fun provideApplicationContext(@ApplicationContext context: Context): Context`
  - `@Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO`
  - `@Provides @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default`
  - `@Provides @MainDispatcher fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main`
  - `@Provides @Singleton fun provideUserPreferencesDataStore(@ApplicationContext context: Context): UserPreferencesDataStore`
  - `@Provides @Singleton fun provideComicFileScanner(...)`

#### 2. `DatabaseModule` (`com.comicreader.di.DatabaseModule`)
- Scope: `@InstallIn(SingletonComponent::class)`
- Provides:
  - `@Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): ComicDatabase`
    - Room builder: `Room.databaseBuilder(context, ComicDatabase::class.java, "comic_reader.db").fallbackToDestructiveMigration().build()`
  - `@Provides fun provideComicDao(db: ComicDatabase): ComicDao = db.comicDao()`
  - `@Provides fun provideBookmarkDao(db: ComicDatabase): BookmarkDao = db.bookmarkDao()`
  - `@Provides fun provideReadingStatsDao(db: ComicDatabase): ReadingStatsDao = db.readingStatsDao()`

#### 3. `RepositoryModule` (`com.comicreader.di.RepositoryModule`)
- Scope: `@InstallIn(SingletonComponent::class)`
- Binds:
  - `@Binds @Singleton abstract fun bindComicRepository(impl: ComicRepositoryImpl): ComicRepository`
  - `@Binds @Singleton abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository`
  - `@Binds @Singleton abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository`

#### 4. `ParserModule` (`com.comicreader.di.ParserModule`)
- Scope: `@InstallIn(SingletonComponent::class)`
- Provides:
  - `@Provides @Singleton fun provideComicParserFactory(@ApplicationContext context: Context): ComicParserFactory` (encapsulates `CbzParser`, `CbrParser`, `PdfParser`, `EpubParser`).

---

## 8. Navigation Architecture (Jetpack Navigation Compose)

### 8.1 Destinations & Route Definitions
```kotlin
sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Reader : Screen("reader/{comicId}?page={page}") {
        fun createRoute(comicId: Long, page: Int = 0) = "reader/$comicId?page=$page"
    }
    object ComicDetail : Screen("comic_detail/{comicId}") {
        fun createRoute(comicId: Long) = "comic_detail/$comicId"
    }
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object History : Screen("history")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
}
```

### 8.2 Route Argument Specifications

| Destination | Route | Arguments | Argument Type & Default | Behavior / Transition |
|---|---|---|---|---|
| `Library` | `"library"` | None | - | Top-level start destination. Shows bottom navigation / drawer. |
| `Reader` | `"reader/{comicId}?page={page}"` | `comicId`, `page` | `comicId`: `NavType.LongType` (required)<br>`page`: `NavType.IntType`, `defaultValue = 0` | Fullscreen immersive. System bars hidden. Overlay toggled by center tap. |
| `ComicDetail` | `"comic_detail/{comicId}"` | `comicId` | `comicId`: `NavType.LongType` (required) | Displays cover, metadata, bookmark list, "Read/Continue" CTA. |
| `Search` | `"search"` | None | - | Search bar with format filter chips. |
| `Favorites` | `"favorites"` | None | - | Grid of bookmarked/favorite comics. |
| `History` | `"history"` | None | - | Chronological list of last 50 read comics. |
| `Stats` | `"stats"` | None | - | Dashboard with Canvas bar charts and reading counters. |
| `Settings` | `"settings"` | None | - | Theme selector, reading mode, animations, folder path. |

---

## 9. Specification Miner Standard Tables

### Features Discovered
| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Gradle / Build | Kotlin DSL Configuration | Multi-component Gradle build with Min SDK 34, Target SDK 35, Java 21 | `app/build.gradle.kts`, `settings.gradle.kts` | Compilable APK build targets | Fails on missing dependencies or compiler mismatch | `comic_reader_plan.md` § Project Specifications |
| 2 | Gradle / Build | Compose BOM & Foundation | Modern Compose UI with Material 3 and Compose BOM 2024.09.00 | BOM artifact `androidx.compose:compose-bom:2024.09.00` | UI runtime components | Runtime theme/component crashes if mismatched BOM | `comic_reader_plan.md` § Key Libraries |
| 3 | Room Database | `ComicEntity` Persistence | Entity storing comic metadata, file path, current page, last read, favorite status | File paths, title, pageCount, format, timestamps | Persisted SQLite rows in `comics` table | Unique constraint violation on duplicate `filePath` | `comic_reader_plan.md` § Project Structure |
| 4 | Room Database | `BookmarkEntity` with Cascade Delete | Entity storing user bookmarks tied to comic and page with thumbnail path | `comicId`, `pageNumber`, `thumbnailPath`, `note` | Persisted rows in `bookmarks` table | Foreign key failure if `comicId` doesn't exist | `comic_reader_plan.md` § Bookmarks |
| 5 | Room Database | `ReadingStatsEntity` Analytics | Entity tracking reading sessions, duration, and pages read | `comicId`, session timestamps, `durationSeconds`, `pagesRead` | Persisted rows in `reading_stats` table | Graceful fallback (`comicId` set to NULL on comic delete) | `comic_reader_plan.md` § Reading Stats |
| 6 | Room Database | Reactive DAOs (`Flow`) | Room DAOs returning `Flow<List<T>>` for automatic reactive UI updates | SQL queries with parameters | Reactive Kotlin `Flow` | Emits empty lists on missing rows; throws SQLiteException on invalid queries | `ORIGINAL_REQUEST.md` R1 |
| 7 | DataStore | `UserPreferencesDataStore` | AndroidX DataStore for user preferences (Theme, Mode, Animations, Brightness) | Key-value settings modifications | `Flow<UserPreferences>` stream | Reverts to default values on missing or corrupt keys | `comic_reader_plan.md` § Settings Screen |
| 8 | Domain Models | Pure Kotlin Entities | Uncoupled business models (`Comic`, `ComicPage`, `Bookmark`, `ReadingStats`) | Entity or Parser inputs | Clean domain representations | Validation errors on negative pages or missing paths | `comic_reader_plan.md` § Domain |
| 9 | Use Cases | Business Logic Encapsulation | Single-responsibility use cases (`GetLibrary`, `GetComicPages`, `SaveReadingProgress`, `GetReadingStats`) | Repository abstractions | Business results / domain Flow streams | Caught repository exceptions mapped to Domain results | `comic_reader_plan.md` § Domain / Usecase |
| 10 | Repositories | Repository Pattern Abstractions | Clean interface contracts isolating Data layer from Domain / Presentation | DB queries, files, scanners | Reactive Flows and suspend functions | Propagates `Result.failure` or logs error | `comic_reader_plan.md` § Repositories |
| 11 | Hilt DI | Dependency Injection Tree | Singleton and ViewModel scoped dependency graph (`AppModule`, `DatabaseModule`, etc.) | Android Application & Context | Injected DAOs, Repositories, ViewModels | Compile-time injection failure if binding is missing | `comic_reader_plan.md` § di |
| 12 | Navigation | NavGraph Route Architecture | Type-safe route navigation supporting deep linking and argument passing | Route strings, route arguments (`comicId`, `page`) | NavHost transitions between composable screens | Navigation fails gracefully or routes to fallback if argument missing | `comic_reader_plan.md` § Navigation |

### Edge Cases
| # | Feature | Input | Observed / Expected Behavior |
|---|---------|-------|-------------------------------|
| 1 | Room `ComicEntity` | Comic file moved or deleted from disk | Entity remains in DB until library sync; when opened, repository emits file-not-found error, UI prompts user to remove or rescan. |
| 2 | Room `ComicEntity` | Duplicate import of exact same file path | `OnConflictStrategy.IGNORE` or unique index on `filePath` prevents duplicate DB entries; updates metadata if scanned again. |
| 3 | Room `BookmarkEntity` | Deleting a comic that has 10 bookmarks | `ForeignKey.CASCADE` deletes all 10 child bookmarks automatically without orphaned rows or SQLite foreign key violations. |
| 4 | Room `ReadingStatsEntity` | Deleting a comic referenced in past reading stats | `ForeignKey.SET_NULL` retains session duration and page count history while setting `comicId` to null, preserving all-time statistics. |
| 5 | DataStore Preferences | App launched for the first time without stored preferences | DataStore emits default preferences: Theme=`DARK`, ReadingMode=`HORIZONTAL`, PageTurnAnim=`SLIDE`, Directory=`/Downloads/Comics`. |
| 6 | Navigation `ReaderScreen` | Opening reader without specifying `page` query parameter | Navigation parses optional `page` with default `0`; reader loads `currentPage` from DB entity or starts at page 0. |
| 7 | Navigation `ReaderScreen` | Passing invalid or non-existent `comicId` | ViewModel receives null from `getComicById`; emits error UI state, navigating back to Library with a snackbar message. |
| 8 | Reading Progress | User closes app or kills process mid-session | Auto-save progress is triggered on `onPause` / `DisposableEffect` in Reader, ensuring last page and elapsed time are not lost. |
| 9 | Android 14+ Permissions | Scoped storage access for `/Downloads/Comics` | If `READ_MEDIA_IMAGES` is denied, app offers system document/folder picker (Storage Access Framework `OPEN_DOCUMENT_TREE`) as transparent fallback. |
| 10 | Compose Pager Migration | Accompanist Pager vs Native Foundation Pager | Accompanist Pager is deprecated in Compose 2024.09.00; specification explicitly mandates `androidx.compose.foundation.pager.HorizontalPager` for stability. |

---

## 10. Verification Method

To verify the architectural integrity and correctness of this specification:
1. **Compilation Check**:
   - Verify Gradle build configurations with `./gradlew assembleDebug --dry-run` or `./gradlew help`.
2. **Room Database Tests**:
   - Verify Room DAOs and schema migrations using Room in-memory database in `app/src/test/java/...`:
   - `Room.inMemoryDatabaseBuilder(context, ComicDatabase::class.java).build()`.
3. **Unit Tests**:
   - Run `./gradlew testDebugUnitTest` to test UseCases, Repository implementations with mocked DAOs, and DataStore serialization.
