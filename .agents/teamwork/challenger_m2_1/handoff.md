# Handoff Report — Milestone 2: Comic Format Parsers & Library File Scanner (Challenger 1)

## 1. Observation

### 1.1 Empirical Verification Test Suites Implemented
To stress-test Milestone 2 parsers and scanner against edge cases, the following test suites were added:
- `app/src/test/java/com/comicreader/format/ParserStressTest.kt` (22 tests)
  - `cbz_zeroByteArchive_handlesGracefully`
  - `cbz_emptyZipArchiveWithoutEntries_handlesGracefully`
  - `cbz_corruptZip_truncatedOrGarbage_handlesGracefully`
  - `cbz_zeroByteImages_handledSafelyWithoutDecoderCrash`
  - `cbz_macOsAndHiddenArtifacts_strictlyFiltered`
  - `cbz_malformedComicInfoXml_fallsBackGracefully`
  - `cbz_largeArchive_500PagesNaturalSort`
  - `cbz_concurrentBitmapLoads_threadSafe`
  - `cbr_zeroByteArchive_handlesGracefully`
  - `cbr_corruptHeader_garbageBytes_handlesGracefully`
  - `cbr_rar5MagicHeader_strictlyRejectedWithUnsupportedRarV5Exception`
  - `pdf_zeroByteFile_handlesGracefully`
  - `pdf_corruptFile_noPdfHeader_handlesGracefully`
  - `pdf_truncatedHeader_handlesGracefully`
  - `pdf_extremeTargetDimensions_clampedToMaxTextureSize`
  - `epub_zeroByteArchive_handlesGracefully`
  - `epub_missingContainerXml_throwsInvalidEpubException`
  - `epub_corruptedContainerXml_throwsOrHandlesGracefully`
  - `epub_zeroByteImages_handledSafely`
  - `epub_complexRelativePathResolution`
  - `factory_unsupportedExtensions_returnsNull`
  - `factory_caseInsensitiveSupport`
- `app/src/test/java/com/comicreader/data/scanner/ScannerStressTest.kt` (2 tests)
  - `scanner_deeplyNestedDirectoryWith500PlusFiles_performanceAndCorrectness`: 12-level deeply nested directory containing 520 files (420 comics across CBZ, PDF, EPUB, CBR, plus 100 unsupported files). Tested full discovery, Room insertion, skip latency on re-scan, and reconciliation after mass deletion of 100 comics.
  - `scanner_corruptAndZeroByteFiles_doesNotCrashScan`: Mixed corrupt, 0-byte, and valid files in scan tree.

### 1.2 Verbatim Test Execution Output
Execution command:
```powershell
.\gradlew.bat testDebugUnitTest
```

Output:
```
> Task :app:testDebugUnitTest

NaturalOrderAndConcurrencyStressTest > testConcurrentCbzParsing_WithoutDeadlockOrFdLeaks FAILED
    java.lang.IllegalStateException at SafeCollector.kt:166

NaturalOrderAndConcurrencyStressTest > testConcurrentEpubParsing_WithoutDeadlockOrFdLeaks FAILED
    java.lang.IllegalStateException at SafeCollector.kt:166

205 tests completed, 2 failed

> Task :app:testDebugUnitTest FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:testDebugUnitTest'.
> There were failing tests. See the report at: file:///C:/Users/anand/Desktop/Comic%20book%20reader/app/build/reports/tests/testDebugUnitTest/index.html
```

### 1.3 Verbatim Stack Trace from HTML Report
From `app/build/reports/tests/testDebugUnitTest/classes/com.comicreader.stress.NaturalOrderAndConcurrencyStressTest.html`:
```
java.lang.IllegalStateException: Flow exception transparency is violated:
    Previous 'emit' call has thrown exception kotlinx.coroutines.flow.internal.AbortFlowException: Flow was aborted, no more elements needed, but then emission attempt of value '[]' has been detected.
    Emissions from 'catch' blocks are prohibited in order to avoid unspecified behaviour, 'Flow.catch' operator can be used instead.
    For a more detailed explanation, please refer to Flow documentation.
	at kotlinx.coroutines.flow.internal.SafeCollector.exceptionTransparencyViolated(SafeCollector.kt:166)
	at kotlinx.coroutines.flow.internal.SafeCollector.checkContext(SafeCollector.kt:130)
	at kotlinx.coroutines.flow.internal.SafeCollector.emit(SafeCollector.kt:109)
	at kotlinx.coroutines.flow.internal.SafeCollector.emit(SafeCollector.kt:82)
	at com.comicreader.format.CbzParser$getPages$1.invokeSuspend(CbzParser.kt:116)
...
	at com.comicreader.stress.NaturalOrderAndConcurrencyStressTest$testConcurrentCbzParsing_WithoutDeadlockOrFdLeaks$1$deferreds$1$1.invokeSuspend(NaturalOrderAndConcurrencyStressTest.kt:308)
```
And for `EpubParser`:
```
java.lang.IllegalStateException: Flow exception transparency is violated:
    Previous 'emit' call has thrown exception kotlinx.coroutines.flow.internal.AbortFlowException: Flow was aborted, no more elements needed, but then emission attempt of value '[]' has been detected.
    Emissions from 'catch' blocks are prohibited in order to avoid unspecified behaviour, 'Flow.catch' operator can be used instead.
    For a more detailed explanation, please refer to Flow documentation.
	at kotlinx.coroutines.flow.internal.SafeCollector.exceptionTransparencyViolated(SafeCollector.kt:166)
	at kotlinx.coroutines.flow.internal.SafeCollector.checkContext(SafeCollector.kt:130)
	at kotlinx.coroutines.flow.internal.SafeCollector.emit(SafeCollector.kt:109)
	at kotlinx.coroutines.flow.internal.SafeCollector.emit(SafeCollector.kt:82)
	at com.comicreader.format.EpubParser$getPages$1.invokeSuspend(EpubParser.kt:171)
...
	at com.comicreader.stress.NaturalOrderAndConcurrencyStressTest$testConcurrentEpubParsing_WithoutDeadlockOrFdLeaks$1$deferreds$1$1.invokeSuspend(NaturalOrderAndConcurrencyStressTest.kt:353)
```

### 1.4 Code Inspection Observations
In `app/src/main/java/com/comicreader/format/CbzParser.kt`:
```kotlin
97:     override fun getPages(file: File): Flow<List<ComicPage>> = flow {
98:         if (!file.exists() || file.length() == 0L) {
99:             emit(emptyList())
100:             return@flow
101:         }
102: 
103:         try {
104:             openZip(file).use { zip ->
...
113:                 emit(pages)
114:             }
115:         } catch (_: Exception) {
116:             emit(emptyList())
117:         }
118:     }.flowOn(Dispatchers.IO)
```
In `app/src/main/java/com/comicreader/format/EpubParser.kt`:
```kotlin
142:     override fun getPages(file: File): Flow<List<ComicPage>> = flow {
...
148:         try {
149:             ZipFile(file).use { zip ->
...
166:                 emit(pages)
167:             }
168:         } catch (e: InvalidEpubException) {
169:             throw e
170:         } catch (_: Exception) {
171:             emit(emptyList())
172:         }
173:     }.flowOn(Dispatchers.IO)
```
In `app/src/main/java/com/comicreader/format/CbrParser.kt`:
```kotlin
128:     override fun getPages(file: File): Flow<List<ComicPage>> = flow {
...
138:         try {
139:             Archive(file).use { archive ->
...
148:                 emit(pages)
149:             }
150:         } catch (e: UnsupportedRarV5Exception) {
151:             throw e
152:         } catch (_: Exception) {
153:             emit(emptyList())
154:         }
155:     }.flowOn(Dispatchers.IO)
```

---

## 2. Logic Chain

1. **Downstream Flow Cancellation**: In Kotlin Coroutines, when a terminal operator such as `.first()`, `.take(1)`, or a timeout/cancellation is invoked by a downstream consumer, the collector signals completion by throwing `kotlinx.coroutines.flow.internal.AbortFlowException` (or `CancellationException`).
2. **Generic Exception Swallowing**: In `CbzParser.kt`, `EpubParser.kt`, and `CbrParser.kt`, `emit(pages)` is placed directly inside a `try { ... } catch (_: Exception)` block. Because `AbortFlowException` and `CancellationException` derive from `java.lang.Exception`, the catch block intercepts this cancellation.
3. **Flow Exception Transparency Violation**: Upon catching the exception, lines 116 (`CbzParser`), 171 (`EpubParser`), and 153 (`CbrParser`) execute `emit(emptyList())`. Kotlin's `SafeCollector` strictly enforces that no emission may occur from a `catch` block after a previous emission has aborted or cancelled. `SafeCollector` throws `java.lang.IllegalStateException: Flow exception transparency is violated`.
4. **Empirical Failure**: As confirmed in Section 1.2, executing `.\gradlew.bat testDebugUnitTest` results in 2 test failures in `NaturalOrderAndConcurrencyStressTest` and `BUILD FAILED`.
5. **Required Remediation**: The parsing/extraction of pages must be isolated within the try-catch block, and `CancellationException` must be rethrown (or `emit(pages)` must occur outside the catch block so that cancellation propagates normally without triggering secondary `emit(emptyList())`).

---

## 3. Caveats

- All other 203 unit and stress tests (including `ParserStressTest` 22 tests and `ScannerStressTest` deeply nested 520-file directory scan) pass cleanly.
- `PdfParser` is unaffected because it does not wrap `emit(pages)` in a catch block that emits again.
- Memory handling, natural sorting, 0-byte file resilience, and database reconciliation are verified robust.
- Reviewer is constrained to review-only; implementation fix is delegated to Worker M2.

---

## 4. Conclusion

**Verdict: REJECT.**

Milestone 2 cannot be approved in its current state because `.\gradlew.bat testDebugUnitTest` fails due to Kotlin Coroutines Flow exception transparency violations in `CbzParser.kt:116` and `EpubParser.kt:171` (with identical latent bug in `CbrParser.kt:153`).

### Actionable Fix Required for Worker M2:
In `CbzParser.kt`, `EpubParser.kt`, and `CbrParser.kt`:
Extract `pages` into a local variable and rethrow `CancellationException` (or emit outside the `catch` block):
```kotlin
val pages = try {
    openZip(file).use { zip ->
        val imageEntries = getImageEntries(zip)
        imageEntries.mapIndexed { index, entry ->
            ComicPage(
                pageNumber = index,
                imageUri = entry.name,
                filePath = file.absolutePath
            )
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    emptyList()
}
emit(pages)
```

---

## 5. Verification Method

1. Run the test suite:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
2. Confirm whether all 205 tests pass with 0 failures:
   Inspect `app/build/reports/tests/testDebugUnitTest/index.html`.
3. Invalidation condition: If any test throws `java.lang.IllegalStateException: Flow exception transparency is violated`, the fix is invalid.
