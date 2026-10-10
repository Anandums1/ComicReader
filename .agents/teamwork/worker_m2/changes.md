# Changes Log — Milestone 2: Comic Format Parsers & Library File Scanner

## 1. Domain Models & Format Core
- `app/src/main/java/com/comicreader/domain/model/ComicMetadata.kt`:
  - Created domain metadata model containing `title`, `series`, `issueNumber`, `volume`, `summary`, `writer`, `penciller`, `genre`, `pageCount`, `format`, `fileSize`, `publishedYear`, `publishedMonth`.
- `app/src/main/java/com/comicreader/format/ComicMetadata.kt`:
  - Exported `typealias ComicMetadata = com.comicreader.domain.model.ComicMetadata` for format package compatibility.
- `app/src/main/java/com/comicreader/format/Exceptions.kt`:
  - Added `UnsupportedRarV5Exception`, `CorruptArchiveException`, `UnsupportedFormatException`, `InvalidEpubException`.
- `app/src/main/java/com/comicreader/format/NaturalOrderComparator.kt`:
  - Implemented alphanumeric natural sorting splitting string chunks into digits and non-digits with `BigInteger` numeric comparison and case-insensitive string fallback.
- `app/src/main/java/com/comicreader/format/FormatUtils.kt`:
  - Helper functions for supported image extensions (`jpg`, `jpeg`, `png`, `webp`, `bmp`, `gif`, `avif`), macOS artifact filtering (`__MACOSX`, `.DS_Store`, `Thumbs.db`, `._`), `calculateInSampleSize`, and safe `decodeSampledBitmap`.
- `app/src/main/java/com/comicreader/format/ComicInfoXmlParser.kt`:
  - Robust XML pull parser extracting ComicInfo.xml metadata tags with fallback handling.

## 2. Comic Format Parsers
- `app/src/main/java/com/comicreader/format/ComicParser.kt`:
  - Unified interface declaring `canParse`, `extractMetadata`, `extractCover`, `getPages`, and `loadPageBitmap`.
- `app/src/main/java/com/comicreader/format/CbzParser.kt`:
  - ZIP archive parsing via Apache Commons Compress `ZipFile`.
  - Natural sorting order for page entries via `NaturalOrderComparator`.
  - Filters out macOS resource forks (`__MACOSX`) and non-image files.
  - ComicInfo.xml metadata extraction with fallback to filename.
  - Safe cover extraction to cache directory and reactive `getPages` Flow.
- `app/src/main/java/com/comicreader/format/CbrParser.kt`:
  - RAR archive parsing via Junrar `com.github.junrar.Archive`.
  - Signature detection for RAR5 archives (`0x52 0x61 0x72 0x21 0x1A 0x07 0x01 0x00`) throwing `UnsupportedRarV5Exception`.
  - Normalizes Windows backslashes (`\`) to forward slashes (`/`).
  - Thread-safe page extraction serialized via Coroutines `Mutex`.
  - Graceful handling of corrupt archives.
- `app/src/main/java/com/comicreader/format/PdfParser.kt`:
  - Android native `PdfRenderer` integration with `ParcelFileDescriptor`.
  - Direct PDF stream metadata fallback for extracting page tree counts and `/Title`.
  - Thread-safe page rendering with synchronized Coroutine access.
  - Clamps dimensions to max texture size (4096px) with white background fill.
- `app/src/main/java/com/comicreader/format/EpubParser.kt`:
  - Locates `META-INF/container.xml` and discovers OPF rootfile path.
  - Traverses manifest items and spine itemrefs.
  - Resolves fixed-layout XHTML image references (`<img>` and SVG `<image>`) and direct image spines with recursive relative path resolution.
  - Fallback manifest image extraction.
  - Cover extraction via `<meta name="cover">` or first page.
- `app/src/main/java/com/comicreader/format/ComicParserFactory.kt`:
  - Central factory dispatching files and `ComicFormat` enums to matching parsers.
  - Injected into Hilt `@SingletonComponent`.

## 3. Library Scanner & Folder Watcher
- `app/src/main/java/com/comicreader/data/scanner/ComicFileScanner.kt`:
  - `ComicFileScanner` interface and `ComicFileScannerImpl`.
  - Recursive directory scanner discovering supported comics (`.cbz`, `.cbr`, `.pdf`, `.epub`).
  - Extracts cover art to app cache directory (`context.cacheDir/covers`).
  - Incremental scanning: skips unmodified files matching `fileSizeBytes` and existing cover file.
  - Reconciles deleted files from Room database and purges orphaned cached covers.
  - Reactive `Flow<ScanProgress>` emitting `Started`, `Processing`, `FileIndexed`, `Finished`, `Failed`.
- `app/src/main/java/com/comicreader/data/scanner/RecursiveFileObserver.kt`:
  - Multi-level directory observer for Android API 34+.
  - Dynamically attaches observers to newly created subdirectories on `CREATE`/`MOVED_TO`.
  - Emits `FileSystemEvent` to `SharedFlow`.
- `app/src/main/java/com/comicreader/data/scanner/FolderWatcherService.kt`:
  - Background Service observing watched directory from `UserPreferencesDataStore`.
  - 1500ms Coroutines debounce to avoid reading partially written files.
  - Automatic reconciliation on file deletion.

## 4. Dependency Injection
- `app/src/main/java/com/comicreader/di/ParserModule.kt`:
  - Hilt `@SingletonComponent` bindings for `ComicFileScannerImpl` to `ComicFileScanner`.
  - Provides `@Singleton` instances of `CbzParser`, `CbrParser`, `PdfParser`, `EpubParser`, and `ComicParserFactory`.

## 5. Comprehensive Unit Test Suites
- `app/src/test/java/com/comicreader/format/NaturalOrderComparatorTest.kt`: 6 test cases verifying natural ordering, zero-padding, multi-segment strings, case insensitivity, and large numbers.
- `app/src/test/java/com/comicreader/format/CbzParserTest.kt`: 9 test cases covering natural sort, macOS artifact filtering, ComicInfo.xml metadata, fallback metadata, cover extraction, corrupt archive recovery, non-existent file handling, and page bitmap decoding.
- `app/src/test/java/com/comicreader/format/CbrParserTest.kt`: 7 test cases covering format checks, RAR5 detection, RAR5 exception throwing, corrupt archive recovery, cover extraction null return, and empty file handling.
- `app/src/test/java/com/comicreader/format/PdfParserTest.kt`: 6 test cases covering format check, page count & title extraction, `getPages` flow, corrupt PDF handling, missing file handling, and texture size limit.
- `app/src/test/java/com/comicreader/format/EpubParserTest.kt`: 8 test cases covering fixed-layout XHTML spine extraction, direct image spine, OPF metadata, cover extraction, missing container exception, corrupt archive recovery, and relative path resolution.
- `app/src/test/java/com/comicreader/format/ComicParserFactoryTest.kt`: 5 test cases testing format resolution, unsupported extensions, `isSupported`, `getParserForFormat`, and parser enumeration.
- `app/src/test/java/com/comicreader/data/scanner/ComicFileScannerTest.kt`: 4 test cases testing single file scan, recursive multi-format directory scan, unmodified file skipping, and deleted file reconciliation with Room database.
- `app/src/test/java/com/comicreader/data/scanner/RecursiveFileObserverTest.kt`: 2 test cases verifying directory tree registration and missing folder resilience.
- `app/src/test/java/com/comicreader/data/scanner/FolderWatcherServiceTest.kt`: 2 test cases verifying directory watch start/stop lifecycle and invalid path handling.
