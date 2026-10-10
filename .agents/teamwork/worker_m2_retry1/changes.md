# Changes Log — Milestone 2 Remediation (Retry 1)

## 1. Summary of Changes
Remediated Kotlin Coroutines Flow Exception Transparency violations causing 2 stress test failures, fixed potential `OutOfMemoryError` risk when parsing large PDFs, and declared `FolderWatcherService` in `AndroidManifest.xml`.

---

## 2. File-by-File Modifications

### 2.1 `app/src/main/java/com/comicreader/format/CbzParser.kt`
- **Issue**: `emit(pages)` was called inside `openZip(file).use { ... }` enclosed within `try { ... } catch (_: Exception) { emit(emptyList()) }`. When downstream collectors (`.first()`, `take()`, or cancellation) aborted the flow, `SafeCollector` threw `AbortFlowException`, which was caught and triggered a forbidden secondary emission `emit(emptyList())`, violating Flow exception transparency.
- **Change**:
  - Imported `kotlinx.coroutines.CancellationException`.
  - Extracted pages list inside `try / catch` into local variable `val pages`.
  - Explicitly rethrew `CancellationException`.
  - Moved `emit(pages)` strictly outside the `try / catch` block.

### 2.2 `app/src/main/java/com/comicreader/format/CbrParser.kt`
- **Issue**: Latent Flow exception transparency violation identical to `CbzParser.kt`: `emit(pages)` inside `try / catch { emit(emptyList()) }`.
- **Change**:
  - Imported `kotlinx.coroutines.CancellationException`.
  - Extracted pages inside `try / catch` into local variable `val pages`.
  - Rethrew `UnsupportedRarV5Exception` and `CancellationException`.
  - Moved `emit(pages)` strictly outside the `try / catch` block.

### 2.3 `app/src/main/java/com/comicreader/format/EpubParser.kt`
- **Issue**: `emit(pages)` was called inside `ZipFile(file).use { ... }` enclosed in `try { ... } catch (_: Exception) { emit(emptyList()) }`. Secondary emission attempt upon flow abortion caused `SafeCollector` exception transparency violation.
- **Change**:
  - Imported `kotlinx.coroutines.CancellationException`.
  - Extracted pages inside `try / catch` into local variable `val pages`.
  - Rethrew `InvalidEpubException` and `CancellationException`.
  - Moved `emit(pages)` strictly outside the `try / catch` block.

### 2.4 `app/src/main/java/com/comicreader/data/scanner/ComicFileScanner.kt`
- **Issue**: `scanAll` caught `Throwable` and emitted `ScanProgress.Failed(e)` without rethrowing `CancellationException`, which would violate flow cancellation transparency if downstream aborted early.
- **Change**:
  - Imported `kotlinx.coroutines.CancellationException`.
  - Added explicit `catch (e: CancellationException) { throw e }` prior to `catch (e: Throwable)`.

### 2.5 `app/src/main/java/com/comicreader/format/PdfParser.kt`
- **Issue**: Reviewer 1 Finding 3.2 — `file.readText(StandardCharsets.ISO_8859_1)` read the entire file into heap memory, creating severe `OutOfMemoryError` risk for large 500MB+ PDF comics.
- **Change**:
  - Imported `java.io.RandomAccessFile`.
  - Limited stream fallback text reading:
    - Files <= 256KB: read entire file into text.
    - Files > 256KB: read first 128KB header and last 128KB trailer/xref table into a bounded `StringBuilder` via stream and `RandomAccessFile`.
  - Wrapped in `catch (_: Throwable)` to safely catch `OutOfMemoryError` and unexpected runtime errors.

### 2.6 `app/src/main/AndroidManifest.xml`
- **Issue**: Reviewer 1 Finding 3.1 — `FolderWatcherService` was not declared inside `<application>`, which would fail at runtime and violate Android 14+ background execution rules.
- **Change**:
  - Declared `FolderWatcherService` with `android:exported="false"` and `android:foregroundServiceType="dataSync"`.

---

## 3. Verification Commands & Results
- **Unit & Stress Tests**:
  - Command: `.\gradlew.bat testDebugUnitTest`
  - Result: `BUILD SUCCESSFUL in 1m 15s`
  - Total tests: 205 (100% success rate, 0 failures, 0 ignored)
  - `NaturalOrderAndConcurrencyStressTest`: 10/10 passed (including `testConcurrentCbzParsing_WithoutDeadlockOrFdLeaks` and `testConcurrentEpubParsing_WithoutDeadlockOrFdLeaks`).
- **Assemble Debug APK**:
  - Command: `.\gradlew.bat assembleDebug`
  - Result: `BUILD SUCCESSFUL in 21s`
