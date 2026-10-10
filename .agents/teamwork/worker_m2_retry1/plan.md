# Implementation Plan — Milestone 2 Remediation (Retry 1)

## Objective
Remediate Kotlin Coroutines Flow Exception Transparency violations in `CbzParser`, `CbrParser`, and `EpubParser`, fix the PDF memory/OOM risk in `PdfParser.extractMetadata`, add `FolderWatcherService` to `AndroidManifest.xml`, and ensure all 205+ unit and stress tests pass with 100% success rate.

---

## 1. Flow Exception Transparency Remediation
### Files:
- `app/src/main/java/com/comicreader/format/CbzParser.kt`
- `app/src/main/java/com/comicreader/format/CbrParser.kt`
- `app/src/main/java/com/comicreader/format/EpubParser.kt`
- `app/src/main/java/com/comicreader/data/scanner/ComicFileScanner.kt`

### Mechanics:
1. In `CbzParser.getPages`:
   - Compute `pages: List<ComicPage>` inside a `try { ... } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }` block.
   - Call `emit(pages)` strictly outside the `try/catch` block.
   - When downstream calls `.first()`, `take()`, or cancels, the resulting `AbortFlowException` / `CancellationException` will propagate normally without being caught or re-emitting `[]`.
2. In `CbrParser.getPages`:
   - Keep `UnsupportedRarV5Exception` check upfront.
   - Compute `pages: List<ComicPage>` inside `try { ... } catch (e: UnsupportedRarV5Exception) { throw e } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }`.
   - Call `emit(pages)` outside `try/catch`.
3. In `EpubParser.getPages`:
   - Compute `pages: List<ComicPage>` inside `try { ... } catch (e: InvalidEpubException) { throw e } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }`.
   - Call `emit(pages)` outside `try/catch`.
4. In `ComicFileScanner.scanAll`:
   - Catch and rethrow `CancellationException` specifically before `catch (e: Throwable)` to ensure flow cancellation transparency.

---

## 2. PDF Metadata Stream Fallback Bounded Read
### File:
- `app/src/main/java/com/comicreader/format/PdfParser.kt`

### Mechanics:
1. When `PdfRenderer` fails or does not yield title/pages, fallback to stream reading.
2. If `file.length() <= 256 * 1024L` (256 KB), read file text directly.
3. If `file.length() > 256 * 1024L`, read the first 128KB and last 128KB (where PDF catalog, info dict, and xref/trailer reside) into a bounded `StringBuilder` rather than loading a 500MB+ file entirely into memory.
4. Catch `Throwable` (including `OutOfMemoryError`) to guarantee graceful degradation.

---

## 3. AndroidManifest Service Declaration
### File:
- `app/src/main/AndroidManifest.xml`

### Mechanics:
1. Declare `FolderWatcherService` inside `<application>`:
   ```xml
   <service
       android:name=".data.scanner.FolderWatcherService"
       android:exported="false"
       android:foregroundServiceType="dataSync" />
   ```

---

## 4. Verification Plan
1. Run `.\gradlew.bat testDebugUnitTest` and ensure:
   - `NaturalOrderAndConcurrencyStressTest` passes both CBZ and EPUB concurrency stress tests.
   - All 205+ tests pass with 0 failures and 0 ignored.
2. Run `.\gradlew.bat assembleDebug` and ensure build passes.
3. Verify changes log and generate handoff report.
