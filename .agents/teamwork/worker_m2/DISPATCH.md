## 2026-10-09T13:12:34Z

Sender: 314a4493-b27a-4d47-8543-10d596768eff
Priority: MESSAGE_PRIORITY_HIGH

You are Worker 2 for Milestone 2: Comic Format Parsers & Library File Scanner of the Android Comic Book Reader project.
Your Working Directory: c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\worker_m2
Your Parent: Orchestrator 1 (conversation ID: 314a4493-b27a-4d47-8543-10d596768eff)

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

INPUT SPECIFICATIONS (READ CAREFULLY):
- c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\ORIGINAL_REQUEST.md
- c:\Users\anand\Desktop\Comic book reader\comic_reader_plan.md
- c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\orchestrator_1\PROJECT.md
- c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\spec_miner_survey_2\format_ui_specs.md
- c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\worker_m1\handoff.md
- c:\Users\anand\Desktop\Comic book reader\app\src\test\java\com\comicreader\e2e\TestComicGenerator.kt

WRITE OWNERSHIP:
You exclusively own:
- `app/src/main/java/com/comicreader/format/` (ComicParser interface, CbzParser, CbrParser, PdfParser, EpubParser, ComicParserFactory, NaturalOrderComparator)
- `app/src/main/java/com/comicreader/data/scanner/` (ComicFileScanner, FolderWatcherService, RecursiveFileObserver)
- `app/src/di/ParserModule.kt` -> `app/src/main/java/com/comicreader/di/ParserModule.kt`
- `app/src/test/java/com/comicreader/format/` (Parser unit tests)
- `app/src/test/java/com/comicreader/data/scanner/` (Scanner unit tests)

OBJECTIVE & DELIVERABLES:
1. Implement Comic Format Parsers in `com.comicreader.format`:
   - `ComicParser.kt` interface:
     - `canParse(filePath: String): Boolean`
     - `extractMetadata(file: File): ComicMetadata` (parses ComicInfo.xml if present in CBZ/CBR/EPUB, or derives from file)
     - `extractCover(file: File, cacheDir: File): File?` (extracts first page to cacheDir as JPEG/PNG file)
     - `getPages(file: File): Flow<List<ComicPage>>` (reactive Flow emitting sorted list of pages)
     - `loadPageBitmap(page: ComicPage, targetWidth: Int, targetHeight: Int): Bitmap?` (renders/decodes page bitmap safely)
   - `CbzParser.kt`:
     - Uses Apache Commons Compress / `java.util.zip.ZipFile`.
     - Natural alphanumeric filename sort (e.g. `page1.jpg`, `page2.jpg`, `page10.jpg`).
     - Ignores non-image files and macOS `__MACOSX` artifacts.
   - `CbrParser.kt`:
     - Uses Junrar (`com.github.junrar.Archive`).
     - Natural alphanumeric sort order.
     - Gracefully handles RAR5 or corrupt files by returning empty/error rather than crashing.
   - `PdfParser.kt`:
     - Uses Android `PdfRenderer` (built-in) via `ParcelFileDescriptor`.
     - Thread-safe page rendering with synchronized access.
     - Clamps rendered bitmap dimensions to prevent exceeding texture size limits.
   - `EpubParser.kt`:
     - Pure Kotlin / Java zip extraction + Jsoup (`org.jsoup.Jsoup`).
     - Locates `META-INF/container.xml`, finds OPF file, reads manifest & spine itemrefs, and traverses fixed-layout comic XHTML image tags (`<img>` or `<image>`).
   - `ComicParserFactory.kt`:
     - Dispatches file to matching parser based on extension / MIME.
2. Implement Library Scanner & Folder Watcher in `com.comicreader.data.scanner`:
   - `ComicFileScanner.kt`:
     - Scans directory recursively for supported extensions (`.cbz`, `.cbr`, `.pdf`, `.epub`).
     - Extracts cover art via matching parser and writes to app cache directory.
     - Inserts or updates records into `ComicDao` / `ComicRepository`.
     - Skips unmodified files by checking `lastModified` and `fileSize`.
     - Detects deleted files and removes from database.
   - `RecursiveFileObserver.kt`:
     - Recursive `FileObserver` for API 34+ watching directory tree with 1500ms Coroutine debounce to avoid reading partially written files.
   - `FolderWatcherService.kt`:
     - Service / worker observing watched directory from `UserPreferencesDataStore`.
3. Provide Parsers in Hilt `ParserModule.kt`:
   - Bind `ComicParserFactory`, individual parsers, and `ComicFileScanner` into Hilt `@SingletonComponent`.
4. Comprehensive Unit Tests:
   - Authored in `app/src/test/java/com/comicreader/format/` and `app/src/test/java/com/comicreader/data/scanner/`.
   - Test CBZ (natural sort, cover extraction, corrupted zip).
   - Test CBR (junrar extraction, RAR5 detection).
   - Test PDF (page count, metadata).
   - Test EPUB (container, spine, image extraction).
   - Test ComicFileScanner (directory scanning, cover caching, Room insertion).
5. Verification:
   - Run `.\gradlew.bat assembleDebug` and confirm 0 errors.
   - Run `.\gradlew.bat testDebugUnitTest` and confirm 100% tests pass cleanly.

REPORTING:
Write changes log to `c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\worker_m2\changes.md`.
Write handoff report to `c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\worker_m2\handoff.md`.
Send a completion message back to Parent (314a4493-b27a-4d47-8543-10d596768eff).
