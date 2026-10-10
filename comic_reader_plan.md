# 📚 Comic Book Reader — Android APK Implementation Plan

## Overview
A feature-rich, beautifully designed comic book reader for Android, built in **Kotlin** with **Jetpack Compose**, following **MVVM + StateFlow** architecture.

---

## 🗂️ Project Specifications

| Property | Value |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| Architecture | MVVM + StateFlow |
| Min SDK | Android 14 (API 34) |
| Target SDK | Android 15 (API 35) |
| Build Tool | Gradle (Kotlin DSL) |

---

## 📦 Supported Formats

| Format | Library |
|---|---|
| CBZ (ZIP-based) | Apache Commons Compress |
| CBR (RAR-based) | Apache Commons Compress (junrar) |
| PDF | Android PdfRenderer (built-in) |
| EPUB | Epublib / Jsoup parsing |

---

## 🏗️ Project Structure

```
com.comicreader/
├── data/
│   ├── local/
│   │   ├── db/                    # Room Database
│   │   │   ├── ComicDatabase.kt
│   │   │   ├── dao/
│   │   │   │   ├── ComicDao.kt
│   │   │   │   ├── BookmarkDao.kt
│   │   │   │   └── ReadingStatsDao.kt
│   │   │   └── entity/
│   │   │       ├── ComicEntity.kt
│   │   │       ├── BookmarkEntity.kt
│   │   │       └── ReadingStatsEntity.kt
│   │   └── datastore/
│   │       └── UserPreferencesDataStore.kt
│   ├── repository/
│   │   ├── ComicRepository.kt
│   │   ├── BookmarkRepository.kt
│   │   └── StatsRepository.kt
│   └── scanner/
│       ├── FolderWatcherService.kt   # Auto-scans /Downloads/Comics
│       └── ComicFileScanner.kt
├── domain/
│   ├── model/
│   │   ├── Comic.kt
│   │   ├── ComicPage.kt
│   │   ├── Bookmark.kt
│   │   └── ReadingStats.kt
│   └── usecase/
│       ├── GetLibraryUseCase.kt
│       ├── GetComicPagesUseCase.kt
│       ├── SaveReadingProgressUseCase.kt
│       └── GetReadingStatsUseCase.kt
├── format/
│   ├── ComicParser.kt              # Interface
│   ├── CbzParser.kt
│   ├── CbrParser.kt
│   ├── PdfParser.kt
│   └── EpubParser.kt
├── ui/
│   ├── theme/
│   │   ├── Theme.kt               # 4 themes: Dark, Light, Adaptive, Comic
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   ├── screens/
│   │   ├── library/
│   │   │   ├── LibraryScreen.kt   # Grid with cover tiles
│   │   │   └── LibraryViewModel.kt
│   │   ├── reader/
│   │   │   ├── ReaderScreen.kt    # Core reading experience
│   │   │   ├── ReaderViewModel.kt
│   │   │   ├── HorizontalReader.kt
│   │   │   └── VerticalReader.kt
│   │   ├── details/
│   │   │   ├── ComicDetailScreen.kt
│   │   │   └── ComicDetailViewModel.kt
│   │   ├── search/
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   ├── history/
│   │   │   └── HistoryScreen.kt
│   │   ├── favorites/
│   │   │   └── FavoritesScreen.kt
│   │   ├── stats/
│   │   │   └── StatsScreen.kt     # Reading stats dashboard
│   │   └── settings/
│   │       ├── SettingsScreen.kt  # Theme + reading mode toggles
│   │       └── SettingsViewModel.kt
│   └── components/
│       ├── ComicCoverCard.kt
│       ├── PageNavigationBar.kt
│       ├── BookmarkPanel.kt
│       └── ReaderOverlay.kt
└── di/
    ├── AppModule.kt               # Hilt modules
    ├── DatabaseModule.kt
    └── RepositoryModule.kt
```

---

## 🎨 UI Themes (All Switchable in Settings)

| Theme | Description |
|---|---|
| **Dark** | AMOLED-friendly deep blacks, purple/indigo accents |
| **Light** | Clean white, soft shadows, blue accents |
| **Adaptive** | Material You — pulls colors from wallpaper |
| **Comic Style** | Halftone patterns, bold black outlines, primary colors (red/yellow/blue), retro feel |

---

## ✨ Features Breakdown

### 📖 Library Screen
- Grid layout with large cover art tiles (Coil image loading)
- Auto-scans `/Downloads/Comics` folder on launch & via `FileObserver`
- Swipe-to-delete comic from library
- Sort by: Title, Recently Added, Last Read, Favorites
- Visual "Continue Reading" progress indicator on each cover

### 📚 Reader Screen
- **Reading Modes:**
  - Horizontal swipe (page-by-page, LTR/RTL)
  - Vertical scroll (webtoon/manga style)
  - Toggle button in top bar or via settings
- **Gestures:**
  - Swipe left/right or up/down to turn pages
  - Tap left/right zones (30% of screen) for page navigation
  - Volume Up/Down keys navigate pages
  - Long-press → bookmark current page
  - Pinch-to-zoom on any page
- **Overlay Controls (tap center to show/hide):**
  - Page counter (current/total)
  - Chapter/Page navigation slider
  - Bookmark panel
  - Reading mode switcher

### 🔖 Bookmarks
- Long-press to bookmark any page
- Bookmarks stored in Room DB with page number + thumbnail
- Visible in comic detail screen & reader panel

### 🔍 Search
- Full-text search by title
- Filter by format (CBZ, CBR, PDF, EPUB)
- Filter by favorites / unread

### ❤️ Favorites & History
- Tap heart icon to favorite a comic
- History shows last 50 comics read, sorted by date

### 📊 Reading Stats Dashboard
- Total pages read (all-time)
- Time spent reading (tracked per session)
- Comics finished vs. in-progress
- Most read genres / series
- Weekly/monthly reading chart (bar chart via Canvas)

### ⚙️ Settings Screen
- Theme selector (Dark / Light / Adaptive / Comic)
- Default reading mode (Horizontal / Vertical)
- Page turn animation (Slide / Curl / Fade / None)
- Watched folder path configuration
- Keep screen awake toggle
- Brightness slider (reader-specific)

---

## 🛠️ Key Libraries & Dependencies

```kotlin
// Jetpack Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.09.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.9.2")

// Navigation
implementation("androidx.navigation:navigation-compose:2.8.1")

// ViewModel + Lifecycle
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

// Hilt
implementation("com.google.dagger:hilt-android:2.52")
kapt("com.google.dagger:hilt-android-compiler:2.52")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.1.1")

// Coil (image loading)
implementation("io.coil-kt:coil-compose:2.7.0")

// Apache Commons Compress (CBZ/CBR)
implementation("org.apache.commons:commons-compress:1.26.2")
implementation("com.github.junrar:junrar:7.5.5") // RAR support

// PDF (built-in Android PdfRenderer, no extra dep needed)

// EPUB
implementation("io.github.siegeindahouse:epublib-core:4.0") // or similar

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// Accompanist (pager for horizontal reader)
implementation("com.google.accompanist:accompanist-pager:0.36.0")
```

---

## 🚀 Implementation Phases

### Phase 1 — Project Setup & Foundation
- [ ] Create new Android project (Kotlin + Compose + API 34)
- [ ] Configure `build.gradle.kts` with all dependencies
- [ ] Set up Hilt DI
- [ ] Create Room DB schema (Comic, Bookmark, ReadingStats entities + DAOs)
- [ ] Set up DataStore for preferences
- [ ] Implement 4 app themes in `Theme.kt`
- [ ] Set up `NavGraph.kt` with all routes

### Phase 2 — Comic Format Parsers
- [ ] `ComicParser` interface with `getPages(): Flow<List<ComicPage>>`
- [ ] `CbzParser` (unzip + sort image files)
- [ ] `CbrParser` (unrar + sort image files)
- [ ] `PdfParser` (Android `PdfRenderer` page-by-page)
- [ ] `EpubParser` (extract images from EPUB spine)

### Phase 3 — Library & File Scanner
- [ ] `FolderWatcherService` using `FileObserver` on `/Downloads/Comics`
- [ ] `ComicFileScanner` — detect format, extract cover art, save to DB
- [ ] `LibraryScreen` — grid of `ComicCoverCard` composables
- [ ] Cover art extraction (first page of comic as thumbnail)

### Phase 4 — Reader Screen
- [ ] `HorizontalReader` using `HorizontalPager`
- [ ] `VerticalReader` using `LazyColumn`
- [ ] `ReaderOverlay` (controls that show/hide on center tap)
- [ ] Gesture handling: swipe, tap zones, volume keys, long-press bookmark
- [ ] Pinch-to-zoom implementation
- [ ] Auto-save reading progress to Room DB

### Phase 5 — Supporting Screens
- [ ] Comic Detail Screen (metadata, bookmarks, start/continue reading)
- [ ] Search Screen with filters
- [ ] Favorites & History screens
- [ ] Stats Dashboard with custom Canvas charts

### Phase 6 — Settings & Polish
- [ ] Settings Screen (theme, reading mode, animations, folder path)
- [ ] Page turn animations (slide, curl, fade)
- [ ] Brightness control slider (overlay on reader)
- [ ] Immersive fullscreen mode (hide system bars)
- [ ] App icon and splash screen

---

## 📁 Watched Folder Strategy

- Default watched folder: `/storage/emulated/0/Downloads/Comics/`
- App uses `FileObserver` (API 29+ version) to detect new files added
- On first launch, scans entire folder and populates library
- Uses `WorkManager` for periodic background re-scan (every 6 hours)
- Requires `READ_MEDIA_IMAGES` + `READ_EXTERNAL_STORAGE` permissions (handled with permission flow on first launch)

---

## 🔐 Required Permissions

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

---

> [!IMPORTANT]
> On Android 14+, scoped media permissions are enforced. The app will use the `Photo Picker` API or `READ_MEDIA_IMAGES` combined with `MediaStore` queries to safely access comic files.

> [!NOTE]
> The project will be initialized in `C:\Users\anand\Desktop\Comic book reader\` as a standard Android Gradle project.
