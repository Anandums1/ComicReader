# 📖 Technical Specification Report: Comic Formats, Library Scanner, Reader UI & Themes

**Target System**: Android 14+ (Min SDK 34, Target SDK 35)  
**Framework**: Kotlin 2.0+ / Jetpack Compose (Material 3)  
**Document Author**: Spec Miner (Survey Agent 2)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Specification Scope

This document provides exhaustive interface contracts, data models, processing algorithms, edge case handling, and UI/UX design specifications for the core format parsing and user interaction layers of the Android Comic Book Reader application.

The four domains specified herein are:
1. **Comic Format Parsers**: `ComicParser` interface, `CbzParser`, `CbrParser`, `PdfParser`, and `EpubParser`.
2. **Library Management & File Scanner**: `ComicFileScanner`, `FolderWatcherService`, and `RecursiveFileObserver`.
3. **Core Comic Reader Experience**: `HorizontalReader`, `VerticalReader`, `ReaderOverlay`, gesture handling, and hardware key bindings.
4. **Dashboard & UI Themes**: 4 switchable visual themes (Dark AMOLED, Light, Material You Adaptive, Comic Halftone Style) and Canvas-rendered reading statistics charts.

---

## 2. Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | Parser Interface | `ComicParser` | Unified abstraction for reading pages, extracting metadata, and generating covers across all comic formats | `File`, `ComicId` | `Flow<List<ComicPage>>`, `ComicMetadata`, `File?` (cover) | `UnsupportedFormatException`, `CorruptArchiveException` | `ORIGINAL_REQUEST.md` R2, Plan § Phase 2 |
| 2 | CBZ Parser | `CbzParser` | Random-access ZIP extraction using Apache Commons Compress with natural alphanumeric page sorting and XML metadata parsing | `.cbz` / `.zip` `File` | Ordered `List<ComicPage>`, `ComicInfo.xml` metadata | `ZipException`, skips non-image files | Plan § Supported Formats |
| 3 | CBR Parser | `CbrParser` | Unrar extraction via Junrar 7.5.5, header traversal, Windows/Unix path normalization, and temp cache extraction | `.cbr` / `.rar` `File` | Ordered `List<ComicPage>`, cover image | `RarException`, `UnsupportedRarV5Exception` | Plan § Supported Formats |
| 4 | PDF Parser | `PdfParser` | Hardware-accelerated native PDF page rendering via Android `PdfRenderer` (API 34/35) with display-density scaling | `.pdf` `File`, target DPI/size | Rendered `Bitmap` per page, page count | `SecurityException` (password), `IOException` | Plan § Supported Formats |
| 5 | EPUB Parser | `EpubParser` | OPF package inspection, spine traversal, fixed-layout SVG/XHTML image resolution, and metadata extraction | `.epub` `File` | Ordered `List<ComicPage>`, Dublin Core metadata | `InvalidEpubException` if `container.xml` missing | Plan § Supported Formats |
| 6 | File Scanner | `ComicFileScanner` | Batch and incremental scanner detecting supported MIME types/extensions, hashing files, extracting covers, and populating Room DB | Root directory `File` / Uri | Stream of `ComicEntity`, extracted thumbnail files | Graceful skip of unreadable files with error logging | `ORIGINAL_REQUEST.md` R3, Plan § Phase 3 |
| 7 | Folder Watcher | `FolderWatcherService` | Dynamic background monitoring of `/Downloads/Comics` with Coroutine debouncing to prevent partial reads during file copy | Filesystem events | Triggers incremental scan upon `CLOSE_WRITE` | Auto-restarts on service crash; handles dead symlinks | Plan § Watched Folder Strategy |
| 8 | Recursive Watcher | `RecursiveFileObserver` | Subdirectory-aware tree observer dynamically attaching observers to nested folders created under the root | Root path `String`, event mask | Aggregate events emitted to Kotlin `SharedFlow` | Ignores inaccessible system directories | Android API 29+ `FileObserver` limitation |
| 9 | Horizontal Reader | `HorizontalReader` | Page-by-page reading using Jetpack Compose `HorizontalPager` supporting LTR (Western) and RTL (Manga) modes | `PagerState`, `ReadingDirection` | Compose viewport with smooth snapping | Clamps index between `0` and `pageCount - 1` | `ORIGINAL_REQUEST.md` R4, Plan § Phase 4 |
| 10 | Vertical Reader | `VerticalReader` | Seamless continuous scroll using `LazyColumn` for Webtoon/Manhwa comics with zero inter-page gap | `LazyListState`, `List<ComicPage>` | Continuous vertical scroll viewport | Graceful aspect-ratio placeholder while loading | `ORIGINAL_REQUEST.md` R4, Plan § Phase 4 |
| 11 | Reader Overlay | `ReaderOverlay` | Center-tap toggled HUD containing Top App Bar, Scrubber Slider, Bookmarks panel, and reading mode toggle | User tap on center 40% zone | Animated alpha HUD, toggles system UI visibility | Does not intercept swipes when hidden | Plan § Reader Screen |
| 12 | Gesture Navigation | Gesture Zones & Zoom | Left/Right 30% tap zones for page turns, pinch-to-zoom (1.0x-5.0x), pan lock, and long-press bookmarking | Pointer input events | Page transitions, matrix transformation, haptic pulse | Ignores tap turns when zoom scale > 1.05x | Plan § Reader Screen |
| 13 | Hardware Key Binding | Volume Key Paging | Hardware `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` intercepted for one-handed page flipping | Android `KeyEvent` | Emits page increment/decrement event | Consumes event only when reader active and enabled | Plan § Reader Screen |
| 14 | Reading Progress | Auto-save Position | Continuous scroll/page state tracker saving current index and timestamp to Room DB with 500ms debounce | Current page index, timestamp | Updated `ComicEntity` in SQLite | Handles database busy states via Coroutine mutex | Plan § Reader Screen |
| 15 | AMOLED Dark Theme | `Theme.Dark` | True-black `#000000` AMOLED theme with neon violet accents (`#7C4DFF`) for zero battery consumption | `ThemeConfig.DARK` | Compose `ColorScheme` and `MaterialTheme` | High contrast accessibility compliant | Plan § UI Themes |
| 16 | Light Theme | `Theme.Light` | Clean paper-white `#FAFAFA` theme with deep cobalt blue accents (`#1976D2`) and subtle card elevation | `ThemeConfig.LIGHT` | Compose `ColorScheme` and `MaterialTheme` | Contrast ratio > 7:1 for body text | Plan § UI Themes |
| 17 | Material You Theme | `Theme.Adaptive` | Dynamic wallpaper color extraction using Android 12+ `dynamicDarkColorScheme` / `dynamicLightColorScheme` | `ThemeConfig.ADAPTIVE`, system palette | Dynamic Material 3 `ColorScheme` | Falls back to Dark/Light if SDK < 31 or unavailable | Plan § UI Themes |
| 18 | Comic Style Theme | `Theme.Comic` | Retro pop-art theme featuring vintage cream background (`#FFFDF0`), primary CMYK colors, and bold black borders | `ThemeConfig.COMIC` | Custom comic styling modifiers and colors | Retains legible contrast for body reading | Plan § UI Themes |
| 19 | Halftone Shader | `HalftoneDotModifier` | Custom Compose Canvas shader drawing Ben-Day halftone dot pattern across card headers and surfaces | Grid step, dot radius, dot color | Decorative pop-art canvas layer | Hardware-accelerated, skips offscreen items | Plan § UI Themes |
| 20 | Stats Dashboard | `StatsScreen` | Comprehensive reading analytics showing total pages, hours read, completion ratio, and genre distribution | `ReadingStats` Room query | Interactive dashboard composable | Renders empty state placeholders for new users | Plan § Reading Stats Dashboard |
| 21 | Weekly Bar Chart | `WeeklyReadingChart` | Custom Canvas-rendered bar chart displaying pages read for Monday through Sunday with dynamic height scaling | Array of 7 day counts | Canvas bars with top value labels and day tags | Gracefully handles all-zero days without divide-by-zero | Plan § Reading Stats Dashboard |

---

## 3. Edge Cases & Boundary Behaviors

| # | Feature | Input / Condition | Observed / Required Behavior |
|---|---------|-------------------|-----------------------------|
| 1 | `CbzParser` | ZIP entry filenames with mixed numeric padding: `page1.jpg`, `page2.jpg`, `page10.jpg` | Natural alphanumeric sort must place `page2.jpg` before `page10.jpg`. Standard ASCII sort (`page1`, `page10`, `page2`) is strictly prohibited. |
| 2 | `CbzParser` | Archive containing macOS metadata files (`__MACOSX/._page01.jpg`, `.DS_Store`, `Thumbs.db`) | Filtering must discard any path starting with `__MACOSX/` or matching OS hidden filenames before sorting pages. |
| 3 | `CbzParser` | Comic archive contains `ComicInfo.xml` with non-ASCII UTF-8 characters in `<Title>` or `<Writer>` | `XmlPullParser` must parse stream with explicit `UTF_8` charset decoder without crashing on XML entities or empty elements. |
| 4 | `CbrParser` | Archive encoded with RAR5 algorithm (`RarException: bad rar archive / unsupported method`) | Junrar 7.5.5 fails on RAR5. Parser must catch `RarException`, inspect signature bytes (`Rar!\x1a\x07\x01\x00`), and throw informative `UnsupportedRarV5Exception`. |
| 5 | `CbrParser` | File headers contain Windows backslash separators (`Issue#1\Pages\01.jpg`) | Path normalizer must convert all `\` to `/` before performing directory checks and sorting. |
| 6 | `PdfParser` | Concurrent render requests for Page 1 and Page 2 across background threads | Android `PdfRenderer` throws `IllegalStateException` if a page is opened while another is still open. All calls to `renderer.openPage()` must be serialized via a single-thread Coroutine dispatcher or Mutex. |
| 7 | `PdfParser` | High-resolution rendering on modern displays (e.g. 1440x3200 QHD+) | Native 72 DPI PDF coordinates must be scaled to screen density (`densityDpi / 72f`) capped at max texture size (4096px) to prevent blurry text while avoiding OOM. |
| 8 | `PdfParser` | Password-protected or encrypted PDF document | Catch `SecurityException` upon opening `PdfRenderer`; emit `ParsingResult.PasswordProtected` to prompt user for password. |
| 9 | `EpubParser` | Comic EPUB embeds pages inside fixed-layout SVG tags: `<svg><image xlink:href="img01.jpg"/></svg>` | Spine parser must extract the image reference from both standard `<img src="...">` and `<image xlink:href="...">` or `<image href="...">`. |
| 10 | `EpubParser` | Text-based novel EPUB with only 1-2 small chapter icons | Parser checks total image count and aspect ratio; if no comic-style full-page images exist, fallback cleanly or report low image ratio. |
| 11 | `ComicFileScanner`| User copies a 600MB `.cbr` file into `/Downloads/Comics` via USB / browser download | Scanner ignores initial `CREATE` and `MODIFY` events; waits for `CLOSE_WRITE` event plus a 1500ms debounce period before opening archive. |
| 12 | `ComicFileScanner`| Comic file deleted from filesystem outside the application | Scanner reconciliation detects missing file, removes entry from `comics` table or marks `isMissing = true`, and purges cached thumbnail. |
| 13 | `VerticalReader` | Ultra-tall Webtoon vertical slice exceeding OpenGL texture size limit (e.g. height > 16,000px) | Android GPU fails on textures larger than `GL_MAX_TEXTURE_SIZE` (typically 4096px or 8192px). Decoder must slice bitmap vertically or downsample to stay within limits. |
| 14 | `HorizontalReader`| Fast page flipping (swiping 20 pages in 3 seconds) on low-RAM device (4GB RAM) | Memory cache must use `LruCache` constrained to 32MB (or max 4-6 cached Bitmaps). Inactive pages must be recycled promptly to prevent OOM. |
| 15 | `GestureNavigation`| User pinches to zoom 3.0x into a comic panel and swipes sideways to pan across the panel | Reader gesture controller must detect `scale > 1.05f` and delegate drag delta to pan offset; it must NOT trigger page turning until zoomed back to 1.0x. |
| 16 | `Theme.Comic` | Drawing halftone Ben-Day dot pattern across a scrolling 100-item comic library list | Repeating `drawCircle` loops per item causes dropped frames. Must use precomputed `ShaderBrush` with `BitmapShader` (TileMode.REPEAT) for 60/120 FPS performance. |
| 17 | `WeeklyReadingChart`| User has just installed the app; reading stats for all 7 days of the current week are 0 | Maximum value calculation must default `maxPages = max(maxPages, 10)` to prevent division by zero; render empty guide lines with "No reading activity yet" state. |

---

## 4. Architectural Contracts & Interfaces

### 4.1 Comic Format Parsers

#### 4.1.1 `ComicParser` Interface
The root abstraction implemented by all format handlers (`CbzParser`, `CbrParser`, `PdfParser`, `EpubParser`).

```kotlin
package com.comicreader.format

import android.graphics.Bitmap
import com.comicreader.domain.model.ComicMetadata
import com.comicreader.domain.model.ComicPage
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ComicParser {
    /**
     * Determines whether this parser can handle the provided file based on
     * magic header inspection or file extension.
     */
    suspend fun canParse(file: File): Boolean

    /**
     * Extracts high-level metadata (title, page count, publication details)
     * without fully decompressing or rendering all pages.
     */
    suspend fun extractMetadata(file: File): ComicMetadata

    /**
     * Extracts and renders the front cover image to the designated destination file.
     * Returns the file reference on success, or null if no cover could be extracted.
     */
    suspend fun extractCover(file: File, destinationFile: File): File?

    /**
     * Emits a reactive stream of pages as they are indexed and prepared for rendering.
     * Pages contain URI/path references or lazy loading hooks.
     */
    fun getPages(file: File): Flow<List<ComicPage>>

    /**
     * Renders or decodes a specific page into a high-resolution Bitmap on demand.
     * @param file The source comic archive/document.
     * @param pageIndex 0-based page index.
     * @param targetWidth Max bounding width for downsampling (or 0 for native).
     * @param targetHeight Max bounding height for downsampling (or 0 for native).
     */
    suspend fun loadPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap
}
```

#### 4.1.2 Data Models for Format Domain
```kotlin
package com.comicreader.domain.model

import java.io.File

enum class ComicFormat {
    CBZ,
    CBR,
    PDF,
    EPUB,
    UNKNOWN
}

data class ComicMetadata(
    val title: String,
    val series: String? = null,
    val issueNumber: String? = null,
    val volume: String? = null,
    val summary: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val genre: String? = null,
    val pageCount: Int,
    val format: ComicFormat,
    val fileSize: Long,
    val publishedYear: Int? = null,
    val publishedMonth: Int? = null
)

data class ComicPage(
    val pageIndex: Int,           // 0-based index
    val pageNumber: Int,          // 1-based display number
    val pageUri: String,          // Internal cache file URI or content identifier
    val width: Int = 0,           // Native width if known, else 0
    val height: Int = 0,          // Native height if known, else 0
    val isDoublePageSpread: Boolean = false
)
```

---

### 4.2 Detailed Parser Implementation Specifications

#### 4.2.1 `CbzParser` (Apache Commons Compress)
- **Library Dependency**: `org.apache.commons:commons-compress:1.26.2`
- **Class**: `ZipFile(file)` enables random access without decompressing the entire archive.
- **Natural Alphanumeric Sort Algorithm**:
  Image entries must be sorted using an alphanumeric natural comparator that extracts contiguous digit blocks and compares them numerically:
  ```kotlin
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
  ```
- **Entry Filtering Rules**:
  1. Skip directories: `entry.isDirectory == true`.
  2. Skip OS metadata: `name.startsWith("__MACOSX/")`, `name.endsWith(".DS_Store")`, `name.endsWith("Thumbs.db")`.
  3. Validate image extensions: `.jpg`, `.jpeg`, `.png`, `.webp`, `.bmp`, `.gif`, `.avif`.
- **`ComicInfo.xml` Extraction**:
  Extract and stream `ComicInfo.xml` using Android's `XmlPullParserFactory`. Parse tags: `<Title>`, `<Series>`, `<Number>`, `<Summary>`, `<Writer>`, `<Penciller>`, `<Year>`. If missing, infer title from filename without extension.

#### 4.2.2 `CbrParser` (Junrar)
- **Library Dependency**: `com.github.junrar:junrar:7.5.5`
- **Class**: `com.github.junrar.Archive(file)`
- **Header Parsing**:
  - Traversal: iterate through `archive.fileHeaders`.
  - Normalization: `val normalizedName = header.fileName.replace('\\', '/')`.
  - Filter: `!header.isDirectory && isImageFile(normalizedName)`.
  - Sort: apply `NaturalOrderComparator` to normalized names.
- **Single-Page Extraction**:
  Junrar requires sequential seeking unless entry is directly extracted via `archive.extractFile(header, outputStream)`. To ensure thread safety and avoid race conditions, wrap extraction in a Coroutine Mutex:
  ```kotlin
  private val extractionMutex = Mutex()
  ```
- **RAR5 Detection**:
  If `archive.isEncrypted` or throws `RarException`, inspect initial 7 bytes. If header equals `52 61 72 21 1A 07 01 00` (RAR5 signature), throw `UnsupportedRarV5Exception("RAR5 archives are not supported by Junrar. Please re-compress as CBZ or RAR4.")`.

#### 4.2.3 `PdfParser` (Android `PdfRenderer`)
- **Framework API**: Built-in `android.graphics.pdf.PdfRenderer` (Available API 21+, fully enhanced in API 34/35).
- **File Descriptor Handling**:
  ```kotlin
  val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  val renderer = PdfRenderer(pfd)
  ```
- **Thread Safety & Lifecycle**:
  Android `PdfRenderer` requires strict single-thread access. Opening page $N$ while page $M$ is active throws `IllegalStateException`.
  Enforce a single-threaded Coroutine dispatcher:
  ```kotlin
  private val pdfDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  ```
- **Display-Density Scaled Rendering Algorithm**:
  ```kotlin
  suspend fun renderPage(renderer: PdfRenderer, pageIndex: Int, screenDensityDpi: Int, maxWidth: Int): Bitmap = withContext(pdfDispatcher) {
      val page = renderer.openPage(pageIndex)
      try {
          // Native PDF point is 1/72 inch
          val scale = (screenDensityDpi / 160f).coerceAtLeast(1.5f)
          val targetW = (page.width * scale).toInt().coerceAtMost(maxWidth.coerceAtLeast(1080))
          val targetH = (page.height * scale).toInt()
          val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
          bitmap.eraseColor(android.graphics.Color.WHITE) // PDFs often have transparent backgrounds
          page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          bitmap
      } finally {
          page.close()
      }
  }
  ```

#### 4.2.4 `EpubParser` (Fixed-Layout & Comic EPUBs)
- **Container Discovery**:
  1. Open EPUB as `ZipFile(file)`.
  2. Parse `META-INF/container.xml` using `XmlPullParser` to locate `full-path` attribute inside `<rootfile>` (typically `OEBPS/content.opf` or `content.opf`).
- **Package Manifest & Spine Traversal**:
  1. Parse OPF file `<manifest>`: build map of `id -> (href, media-type)`.
  2. Parse `<spine>`: get ordered list of item `idref`s.
  3. Resolution Strategy:
     - Case A (Direct Image Spine): `<item media-type="image/jpeg">` directly in spine.
     - Case B (Fixed-Layout XHTML Spine): `<item media-type="application/xhtml+xml">`. Read XHTML content and extract image reference via regex:
       `val imgRegex = Regex("""<(?:img|image)[^>]+(?:src|href|xlink:href)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)`
     - Resolve relative paths against OPF directory.
- **Cover Image Resolution**:
  Check `<meta name="cover" content="item_id"/>` in OPF `<metadata>`, find corresponding item in manifest; if not present, use first spine image.

---

### 4.3 Library Management & File Scanner

#### 4.3.1 `ComicFileScanner`
- **Class Contract**:
  ```kotlin
  package com.comicreader.data.scanner

  import com.comicreader.data.local.db.entity.ComicEntity
  import kotlinx.coroutines.flow.Flow
  import java.io.File

  interface ComicFileScanner {
      /**
       * Scans all configured directories (e.g. /Downloads/Comics and user custom folders).
       * Emits progress events and updates Room database.
       */
      suspend fun scanAll(directories: List<File>): Flow<ScanProgress>

      /**
       * Scans a single newly added or modified file and updates the database.
       */
      suspend fun scanSingleFile(file: File): ComicEntity?

      /**
       * Purges database entries whose physical files no longer exist.
       */
      suspend fun reconcileLibrary(): Int
  }

  sealed class ScanProgress {
      data class Started(val totalEstimatedFiles: Int) : ScanProgress()
      data class Processing(val currentFile: String, val processedCount: Int, val total: Int) : ScanProgress()
      data class FileIndexed(val comic: ComicEntity) : ScanProgress()
      data class Finished(val addedCount: Int, val updatedCount: Int, val removedCount: Int) : ScanProgress()
      data class Failed(val error: Throwable) : ScanProgress()
  }
  ```

- **Thumbnail Caching Strategy**:
  Thumbnails are rendered to WebP (85% quality) and saved in app-private storage:
  `context.filesDir / "thumbnails" / "${file.canonicalPath.hashCode()}_cover.webp"`.
  This avoids re-extracting covers during library browsing and prevents memory bloat.

#### 4.3.2 `FolderWatcherService` & `RecursiveFileObserver`
- **Recursive Observation Architecture**:
  Android's native `FileObserver` does not recursively watch child directories. `RecursiveFileObserver` maintains an internal map of child observers:
  ```kotlin
  package com.comicreader.data.scanner

  import android.os.Build
  import android.os.FileObserver
  import kotlinx.coroutines.CoroutineScope
  import kotlinx.coroutines.flow.MutableSharedFlow
  import kotlinx.coroutines.flow.SharedFlow
  import kotlinx.coroutines.launch
  import java.io.File

  class RecursiveFileObserver(
      private val rootDir: File,
      private val scope: CoroutineScope,
      mask: Int = FileObserver.CREATE or FileObserver.CLOSE_WRITE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM
  ) {
      private val observers = mutableMapOf<String, FileObserver>()
      private val _events = MutableSharedFlow<FileSystemEvent>(extraBufferCapacity = 64)
      val events: SharedFlow<FileSystemEvent> = _events

      init {
          registerRecursive(rootDir)
      }

      private fun registerRecursive(dir: File) {
          if (!dir.exists() || !dir.isDirectory) return
          val path = dir.absolutePath
          if (observers.containsKey(path)) return

          val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              object : FileObserver(dir, FileObserver.ALL_EVENTS) {
                  override fun onEvent(event: Int, path: String?) {
                      handleEvent(event, dir, path)
                  }
              }
          } else {
              @Suppress("DEPRECATION")
              object : FileObserver(path, FileObserver.ALL_EVENTS) {
                  override fun onEvent(event: Int, path: String?) {
                      handleEvent(event, dir, path)
                  }
              }
          }
          observers[path] = observer
          observer.startWatching()

          dir.listFiles { f -> f.isDirectory }?.forEach { subDir ->
              registerRecursive(subDir)
          }
      }

      private fun handleEvent(event: Int, parentDir: File, childPath: String?) {
          val fullFile = if (childPath != null) File(parentDir, childPath) else parentDir
          if ((event and FileObserver.CREATE) != 0 && fullFile.isDirectory) {
              registerRecursive(fullFile)
          }
          scope.launch {
              _events.emit(FileSystemEvent(event, fullFile))
          }
      }

      fun stopWatching() {
          observers.values.forEach { it.stopWatching() }
          observers.clear()
      }
  }

  data class FileSystemEvent(val eventMask: Int, val file: File)
  ```

- **Debounce Strategy**:
  When files are written or downloaded, multiple events fire. The watcher pipes events into a Kotlin Coroutines flow:
  ```kotlin
  fileObserver.events
      .filter { it.eventMask and (FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO) != 0 }
      .filter { isSupportedComicFile(it.file) }
      .debounce(1500L) // Wait 1.5 seconds for complete disk write
      .onEach { event -> scanner.scanSingleFile(event.file) }
      .launchIn(serviceScope)
  ```

---

### 4.4 Core Comic Reader Experience

#### 4.4.1 `HorizontalReader` Composable
- **Foundation Pager**: Uses `androidx.compose.foundation.pager.HorizontalPager`.
- **Reading Direction**:
  - `ReadingDirection.LEFT_TO_RIGHT` (Western)
  - `ReadingDirection.RIGHT_TO_LEFT` (Manga)
- **RTL Implementation**:
  ```kotlin
  @Composable
  fun HorizontalReader(
      pages: List<ComicPage>,
      initialPage: Int,
      readingDirection: ReadingDirection,
      pageTurnAnimation: PageAnimation,
      onPageChanged: (Int) -> Unit,
      modifier: Modifier = Modifier
  ) {
      val pagerState = rememberPagerState(
          initialPage = initialPage,
          pageCount = { pages.size }
      )

      val layoutDirection = if (readingDirection == ReadingDirection.RIGHT_TO_LEFT) {
          LayoutDirection.Rtl
      } else {
          LayoutDirection.Ltr
      }

      CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
          HorizontalPager(
              state = pagerState,
              modifier = modifier.fillMaxSize(),
              beyondViewportPageCount = 1,
              key = { pages[it].pageIndex }
          ) { pageIndex ->
              ComicPageItem(
                  page = pages[pageIndex],
                  modifier = Modifier.graphicsLayer {
                      if (pageTurnAnimation == PageAnimation.FADE) {
                          val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                          alpha = 1f - kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                      }
                  }
              )
          }
      }

      LaunchedEffect(pagerState.currentPage) {
          onPageChanged(pagerState.currentPage)
      }
  }
  ```

#### 4.4.2 `VerticalReader` Composable (Continuous Scroll / Webtoon)
- **Continuous Scroll Architecture**:
  ```kotlin
  @Composable
  fun VerticalReader(
      pages: List<ComicPage>,
      initialPage: Int,
      onPageChanged: (Int) -> Unit,
      modifier: Modifier = Modifier
  ) {
      val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

      LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(0.dp), // Zero gap for continuous strip
          modifier = modifier.fillMaxSize()
      ) {
          items(
              items = pages,
              key = { it.pageIndex }
          ) { page ->
              AsyncImage(
                  model = page.pageUri,
                  contentDescription = "Page ${page.pageNumber}",
                  contentScale = ContentScale.FillWidth,
                  modifier = Modifier
                      .fillMaxWidth()
                      .wrapContentHeight()
              )
          }
      }

      // Track first visible page with 500ms debounce
      LaunchedEffect(listState) {
          snapshotFlow { listState.firstVisibleItemIndex }
              .distinctUntilChanged()
              .collect { index ->
                  onPageChanged(index)
              }
      }
  }
  ```

#### 4.4.3 Gesture Navigation & Zone Division
The screen width is segmented into 3 active interaction zones:
- **Left Zone (0% to 30%)**: Tap triggers Page Previous (or Page Next if RTL).
- **Center Zone (30% to 70%)**: Tap triggers Overlay HUD visibility toggle.
- **Right Zone (70% to 100%)**: Tap triggers Page Next (or Page Previous if RTL).

```kotlin
@Composable
fun ReaderGestureContainer(
    isOverlayVisible: Boolean,
    onToggleOverlay: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onLongPress: (Offset) -> Unit,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        if (scale > 1.05f) {
                            // Zoomed in: tapping does not turn pages, toggles overlay or resets
                            onToggleOverlay()
                            return@detectTapGestures
                        }
                        val screenWidth = size.width
                        val tapX = tapOffset.x
                        when {
                            tapX < screenWidth * 0.30f -> onPreviousPage()
                            tapX > screenWidth * 0.70f -> onNextPage()
                            else -> onToggleOverlay()
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        scale = if (scale > 1.5f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                    onLongPress = { tapOffset ->
                        onLongPress(tapOffset)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        val maxPanX = (scale - 1f) * size.width / 2f
                        val maxPanY = (scale - 1f) * size.height / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        content()
    }
}
```

#### 4.4.4 Hardware Key Interception (Volume Keys)
- In the Reader Activity / Host:
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (readerViewModel.isVolumeKeyNavigationEnabled.value) {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                readerViewModel.nextPage()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                readerViewModel.previousPage()
                return true
            }
        }
    }
    return super.onKeyDown(keyCode, event)
}
```

#### 4.4.5 `ReaderOverlay` Composable
- **Top App Bar**: Back navigation button, comic title text, chapter title, Reading Mode selector icon button (Horizontal LTR, Horizontal RTL, Vertical Scroll), and Bookmark button.
- **Bottom Scrubber**:
  - `Slider` component linked to `currentPage / totalPages` with bubble badge showing `"Page ${sliderVal.toInt() + 1} of $totalPages"`.
  - Next and Previous step buttons.
  - Quick action bar: Reading mode switcher, Bookmarks drawer sheet button, screen brightness slider.
- **System Bars Synchronization**:
  When `isOverlayVisible == false`, hide system bars using `WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }`.

---

### 4.5 Dashboard & UI Themes

#### 4.5.1 The 4 Switchable Themes
```kotlin
package com.comicreader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 1. Dark Theme (AMOLED Deep Black)
val DarkAmoledColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),          // Deep Violet Accent
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF311B92),
    secondary = Color(0xFF00E5FF),        // Cyber Cyan
    background = Color(0xFF000000),       // Pure AMOLED Black
    surface = Color(0xFF121212),          // Deep Charcoal
    surfaceVariant = Color(0xFF1E1E1E),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
    outline = Color(0xFF2C2C2C)
)

// 2. Light Theme (Crisp Clean White)
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),          // Cobalt Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFFF4081),        // Vivid Pink Accent
    background = Color(0xFFFAFAFA),       // Paper White
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F0),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    outline = Color(0xFFE0E0E0)
)

// 3. Comic Style Theme (Vintage Pop-Art / Halftone)
val ComicStyleColorScheme = lightColorScheme(
    primary = Color(0xFFD32F2F),          // Vintage Action Red
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFCDD2),
    secondary = Color(0xFFFFD600),        // Comic Book Yellow
    tertiary = Color(0xFF1976D2),         // Classic Hero Blue
    background = Color(0xFFFFFDF0),       // Vintage Comic Newsprint Cream
    surface = Color(0xFFFFF8E7),          // Aged Paper
    surfaceVariant = Color(0xFFFFE0B2),
    onBackground = Color(0xFF000000),     // Jet Black
    onSurface = Color(0xFF000000),
    outline = Color(0xFF000000)           // Bold Inked Comic Outlines
)
```

#### 4.5.2 Comic Halftone Dot Canvas Pattern
The pop-art halftone pattern is implemented via a high-performance Compose Canvas modifier using a repeating circular grid with 8% opacity:
```kotlin
fun Modifier.comicHalftonePattern(
    dotColor: Color = Color.Black.copy(alpha = 0.08f),
    dotRadius: Dp = 1.5.dp,
    spacing: Dp = 8.dp
): Modifier = this.drawBehind {
    val radiusPx = dotRadius.toPx()
    val stepPx = spacing.toPx()
    var y = stepPx / 2f
    while (y < size.height) {
        var x = stepPx / 2f
        while (x < size.width) {
            drawCircle(
                color = dotColor,
                radius = radiusPx,
                center = Offset(x, y)
            )
            x += stepPx
        }
        y += stepPx
    }
}
```

#### 4.5.3 Reading Stats Dashboard & Weekly Canvas Bar Chart
- **Dashboard Metrics**:
  - Total Pages Read (Lifetime aggregate)
  - Time Spent Reading (Hours & Minutes formatted from seconds)
  - Completed Comics vs In-Progress Comics
  - Average Daily Reading Rate
- **Weekly Bar Chart Composable**:
```kotlin
@Composable
fun WeeklyReadingChart(
    weeklyCounts: List<DayReadingStat>, // 7 items (Mon..Sun)
    barColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
) {
    if (weeklyCounts.isEmpty()) return

    val maxCount = (weeklyCounts.maxOfOrNull { it.pagesRead } ?: 0).coerceAtLeast(10)

    Canvas(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val totalBars = weeklyCounts.size
        val barSpacing = 16.dp.toPx()
        val availableWidth = size.width - (barSpacing * (totalBars + 1))
        val barWidth = (availableWidth / totalBars).coerceAtLeast(12.dp.toPx())
        val chartHeight = size.height - 30.dp.toPx() // Reserve 30dp for bottom text

        weeklyCounts.forEachIndexed { index, stat ->
            val left = barSpacing + index * (barWidth + barSpacing)
            val barHeight = (stat.pagesRead.toFloat() / maxCount) * chartHeight
            val top = chartHeight - barHeight

            // Draw rounded bar
            drawRoundRect(
                color = if (stat.pagesRead > 0) barColor else barColor.copy(alpha = 0.2f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight.coerceAtLeast(4.dp.toPx())),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
        }
    }
}

data class DayReadingStat(
    val dayLabel: String, // "M", "T", "W", "T", "F", "S", "S"
    val pagesRead: Int
)
```

---

## 5. Testability & Independent Verification Matrix

| Component | Target Verification Test | Verification Strategy |
|-----------|--------------------------|-----------------------|
| `CbzParser` | `CbzParserTest` | Create synthetic ZIP archive with out-of-order filenames (`p1.png`, `p10.png`, `p2.png`) and `ComicInfo.xml`; assert correct sorting and metadata fields. |
| `CbrParser` | `CbrParserTest` | Validate handling of RAR4 archive fixture and catch `UnsupportedRarV5Exception` on RAR5 fixture. |
| `PdfParser` | `PdfParserTest` | Create sample 3-page PDF via `android.graphics.pdf.PdfDocument`; assert `renderer.pageCount == 3` and non-null Bitmaps. |
| `EpubParser` | `EpubParserTest` | Create minimal EPUB fixture containing `container.xml`, `content.opf`, and 2 images in spine; assert 2 pages extracted. |
| `ComicFileScanner` | `ComicFileScannerTest` | Mock directory with `.cbz`, `.pdf`, `.txt`, `.cbr`; assert only supported formats are indexed into Room DAO. |
| `FolderWatcherService` | `FolderWatcherTest` | Emit file creation events; verify debounce collects events into a single scan invocation. |
| `HorizontalReader` | `HorizontalReaderTest` | Compose UI Test: assert LTR starts at page 0, swiping advances page index; RTL starts at designated page and inverts swipe direction. |
| `ReaderOverlay` | `ReaderOverlayTest` | Compose UI Test: tap center 40% area; assert overlay visibility toggles from false to true. |
| `Theme.Comic` | `ThemeTest` | Verify `ComicStyleColorScheme` provides expected RGB values and contrast compliance. |
| `WeeklyReadingChart` | `WeeklyReadingChartTest` | Test canvas rendering with all 0s, 7 active days, and 1 extreme outlier; assert no crash and valid coordinates. |

---

## 6. Implementation Readiness Conclusion

The technical specifications for Comic Format Parsers, Library Scanner, Core Reader UI, and UI Themes are fully detailed and self-contained. The component contracts strictly respect the project's target SDK 35, Jetpack Compose Material 3 standards, and MVVM + StateFlow clean architecture.
