# Forensic Audit Report — Milestone 2: Comic Format Parsers & Library File Scanner

**Work Product**: Milestone 2 Comic Format Parsers (`com.comicreader.format.*`) and Library File Scanner (`com.comicreader.data.scanner.*`)  
**Profile**: General Project (Integrity Forensics)  
**Integrity Mode**: Demo (per `ORIGINAL_REQUEST.md`)  
**Verdict**: **CLEAN**

---

## 1. Executive Summary

A forensic integrity audit was conducted on Milestone 2 of the Android Comic Book Reader project. All source code, parser logic, test suites, and build artifacts were independently audited. 

The implementations of the CBZ parser, CBR parser, PDF parser, EPUB parser, and Library File Scanner were empirically verified to be genuine, functional, and devoid of facade patterns or hardcoded cheating.

No prohibited integrity patterns were detected:
- ❌ No hardcoded page lists or dummy mock returns.
- ❌ No fake facades pretending to read archives.
- ❌ No bypassed assertions or trivial self-certifying tests in the test suite.
- ❌ No pre-populated test results or fabricated execution logs.

---

## 2. Forensic Phase Results

### Phase 1: Source Code Analysis
| Check | Status | Verification Detail |
|---|:---:|---|
| **Hardcoded Output Detection** | **PASS** | Grepped for fixed page counts, hardcoded page lists, and static metadata constants. All parsers dynamically derive page lists and metadata from archive streams (`ZipFile.entries`, `Archive.fileHeaders`, `PdfRenderer.pageCount`, and OPF manifest/spine parsing). |
| **Facade Detection** | **PASS** | Evaluated all 11 format classes and 3 scanner classes. Interfaces declare genuine contracts; implementations execute authentic decompression, regex normalization, XML pull parsing, Jsoup DOM traversal, downsampling, and Room transactions. |
| **Pre-populated Artifact Detection** | **PASS** | Searched workspace for pre-existing `*.log`, `*result*`, and `*output*` files predating auditor execution. None detected. |

### Phase 2: Behavioral Verification
| Check | Status | Verification Detail |
|---|:---:|---|
| **Build from Source** | **PASS** | `.\gradlew.bat assembleDebug` completed with exit code 0 in 26s (41 actionable tasks up to date), generating an installable APK at `app/build/outputs/apk/debug/app-debug.apk` (64,380,962 bytes). |
| **Baseline Test Execution** | **PASS** | All 171 baseline unit tests (M1 + M2 worker suites: `CbzParserTest`, `CbrParserTest`, `PdfParserTest`, `EpubParserTest`, `ComicParserFactoryTest`, `NaturalOrderComparatorTest`, `ComicFileScannerTest`, `RecursiveFileObserverTest`, `FolderWatcherServiceTest`, Room DAOs, DataStore) executed with a 100% pass rate (0 failures). |
| **Output Verification** | **PASS** | Verified that synthetic test archives generated with valid binary PNG structures, ISO 32000 PDF streams, and standard EPUB OCF/OPF packages are accurately parsed and decoded into `Bitmap` instances without synthetic mocks. |
| **Dependency Audit (Demo Mode)** | **PASS** | Uses Apache Commons Compress (1.26.2), Junrar (7.5.5), Android PdfRenderer (built-in), and Jsoup (1.18.1). All dependencies strictly conform to `comic_reader_plan.md` §📦 Supported Formats and §🛠️ Key Libraries. |

---

## 3. Deep-Dive Component Forensic Verification

### 3.1 CBZ Parser (`CbzParser.kt`)
- **Decompression Engine**: Apache Commons Compress `ZipFile`.
- **Sorting Integrity**: Filenames sorted via `NaturalOrderComparator`, ordering `page2` before `page10`.
- **Sanitization**: Excludes macOS resource forks (`__MACOSX/`, `._*`), `.DS_Store`, and `Thumbs.db`.
- **Metadata**: Parses `ComicInfo.xml` via streaming `XmlPullParser` extracting `Title`, `Series`, `Number`, `Volume`, `Summary`, `Writer`, `Penciller`, `Genre`, `PageCount`, `Year`, `Month`.
- **Bitmap Rendering**: Renders bitmaps via `FormatUtils.decodeSampledBitmap` with `inSampleSize` calculations.
- **Finding**: Authentic implementation.

### 3.2 CBR Parser (`CbrParser.kt`)
- **Decompression Engine**: Junrar `com.github.junrar.Archive`.
- **RAR5 Signature Guard**: Inspects leading 8 bytes for `0x52 0x61 0x72 0x21 0x1A 0x07 0x01 0x00`. Safely throws `UnsupportedRarV5Exception` before attempting Junrar execution, preventing JVM aborts.
- **Path Normalization**: Normalizes Windows backslashes (`\`) to standard Unix delimiters (`/`).
- **Thread Safety**: Decompression and bitmap loading wrapped in `extractionMutex.withLock` to guard against native thread contention.
- **Finding**: Authentic implementation.

### 3.3 PDF Parser (`PdfParser.kt`)
- **Rendering Engine**: Android `PdfRenderer` via `ParcelFileDescriptor`.
- **Texture Clamp**: Dimensions clamped to `MAX_TEXTURE_SIZE` (4096px) to avoid GL render crashes and OOM.
- **Stream Fallback**: Includes ISO 32000 direct stream parsing fallback for metadata (`/Title`) and `/Pages /Count` to support host JVM test execution environments where `libpdfium` is unavailable.
- **Concurrency**: Guarded with `pdfMutex.withLock`.
- **Finding**: Authentic implementation.

### 3.4 EPUB Parser (`EpubParser.kt`)
- **Container Discovery**: Reads `META-INF/container.xml` to discover the OPF rootfile path.
- **OPF Parsing**: Parses OPF package with Jsoup XML parser; extracts Dublin Core `<dc:title>` and `<dc:creator>`.
- **Spine Navigation**: Resolves both direct image spines (`image/*`) and fixed-layout XHTML spines (`application/xhtml+xml`). Parses embedded `<img>` and SVG `<image xlink:href="...">` tags.
- **Path Resolution**: Handles relative traversal (`..`, `.`, subdirectories) with recursive path normalization (`resolvePath`).
- **Finding**: Authentic implementation.

### 3.5 Comic File Scanner (`ComicFileScannerImpl.kt`)
- **Crawler**: Recursively explores directories for `.cbz`, `.cbr`, `.pdf`, `.epub`.
- **Cache**: Caches extracted covers to `context.cacheDir/covers`.
- **Incremental Efficiency**: Skips re-parsing if file length matches database record and cached cover file exists.
- **Reconciliation**: Reconciles deleted files from Room database (`comicDao.deleteComicById`) and purges orphaned covers.
- **Reactive Stream**: Emits `ScanProgress` states (`Started`, `Processing`, `FileIndexed`, `Finished`, `Failed`).
- **Finding**: Authentic implementation.

---

## 4. Test Assertion & Anti-Cheating Inspection

All test suites were inspected line-by-line for anti-cheating compliance:
1. `CbzParserTest.kt`: 9 tests asserting exact natural sort sequence (`containsExactly("page1.png", "page2.png", "page10.png", "page20.png").inOrder()`), ComicInfo.xml metadata extraction, cover file existence on disk, and corruption recovery.
2. `CbrParserTest.kt`: 7 tests asserting RAR5 byte signature detection, `assertThrows(UnsupportedRarV5Exception::class.java)` on metadata extraction and page flows, and corrupt archive handling.
3. `PdfParserTest.kt`: 6 tests asserting page count extraction, title extraction, corrupt PDF handling, and texture clamp constant.
4. `EpubParserTest.kt`: 8 tests asserting XHTML image spine resolution, direct image spine resolution, Dublin Core metadata, missing container exception, and relative path navigation.
5. `ComicFileScannerTest.kt`: 4 tests asserting Room DB insertion, recursive multi-format indexing, incremental skip behavior, and library reconciliation.

**Anti-Cheating Verification**: No assertions bypassed. No trivial `assertTrue(true)` statements.

---

## 5. Non-Integrity Empirical Finding (Defect Advisory)

During adversarial concurrency stress testing conducted across 205 test cases, 3 failures were observed in `NaturalOrderAndConcurrencyStressTest.kt`:
1. **Assertion Typo in Test File**: Line 154–160 of `NaturalOrderAndConcurrencyStressTest.kt` checked for 5 elements on a 4-element token list due to a duplicate check.
2. **Coroutines Exception Transparency in Flow**: In `CbzParser.kt`, `CbrParser.kt`, and `EpubParser.kt`, `getPages()` encloses `emit(pages)` inside `try { ... emit(pages) } catch (e: Exception) { emit(emptyList()) }`. When downstream collectors cancel early using `.first()`, `AbortFlowException` is caught by `catch (e: Exception)` which attempts a second `emit(emptyList())`, violating Kotlin Coroutines Flow exception transparency (`SafeCollector.kt:166`).
   - *Severity*: Code Defect / Resilience Improvement (NOT an integrity violation or facade).
   - *Recommendation*: Catch only specific format/IO exceptions (`ZipException`, `IOException`, `InvalidEpubException`) and rethrow `CancellationException` / `AbortFlowException`.

---

## 6. Audit Verdict

**VERDICT: CLEAN**

Milestone 2 satisfies all integrity requirements. The parsers and file scanner are genuinely implemented, correctly integrated, and fully verifiable from source.
