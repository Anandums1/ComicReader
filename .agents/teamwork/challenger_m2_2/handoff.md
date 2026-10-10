# Handoff Report — Challenger 2 (Milestone 2)

## 1. Observation

### 1.1 NaturalOrderComparator Stress Testing
We executed empirical tests in `app/src/test/java/com/comicreader/stress/NaturalOrderAndConcurrencyStressTest.kt` targeting natural alphanumeric ordering:
1. **Required Extreme Alphanumeric Patterns**:
   - Input: `listOf("p001.jpg", "p10.jpg", "p2.jpg", "p02a.jpg")`
   - Sorted Output: `["p001.jpg", "p2.jpg", "p02a.jpg", "p10.jpg"]` [PASS]
   - Numeric tie-breaking with zero-padding: `["p2.jpg", "p02.jpg", "p002.jpg", "p0002.jpg"]` [PASS]
   - Numeric ties broken by alphabetical suffix: `["p02.jpg", "p02a.jpg", "p02b.jpg", "p02c.jpg"]` [PASS]
2. **Multi-Number Filenames**:
   - `["Chapter 1 - Page 1.jpg", "Chapter 1 - Page 2.jpg", "Chapter 1 - Page 10.jpg", "Chapter 2 - Page 1.jpg", "Chapter 10 - Page 1.jpg", "Chapter 10 - Page 2.jpg"]` [PASS]
   - Semantic dotted version numbers: `["v1.0.1.cbz", "v1.0.2.cbz", "v1.0.10.cbz", "v1.2.0.cbz", "v1.10.0.cbz", "v2.0.0.cbz"]` [PASS]
3. **Unicode and International Filenames**:
   - Japanese / Kanji: `["第1話.jpg", "第2話.jpg", "第10話.jpg", "第20話.jpg", "第100話.jpg"]` [PASS]
   - French accents: `["épisode 1.cbz", "épisode 2.cbz", "épisode 10.cbz"]` [PASS]
   - Emojis: `["🦸1.png", "🦸2.png", "🦸10.png", "🦸100.png"]` [PASS]
   - Cyrillic: `["Том 1 Глава 1.cbz", "Том 1 Глава 2.cbz", "Том 2 Глава 1.cbz", "Том 10 Глава 1.cbz"]` [PASS]
4. **Arbitrary Precision (BigInteger)**:
   - 120-digit numbers: `"page" + "9".repeat(120) + ".jpg"` vs `"page1" + "0".repeat(120) + ".jpg"` [PASS]
5. **Mathematical Contract Fuzzing**:
   - Reflexivity (`compare(x, x) == 0`), Anti-symmetry (`sgn(cmp(x,y)) == -sgn(cmp(y,x))`), and Transitivity (`cmp(x,y) <= 0 && cmp(y,z) <= 0 => cmp(x,z) <= 0`) verified over 34 diverse string categories.
   - TimSort on 500 randomized perturbed strings completed without throwing `IllegalArgumentException`. [PASS]

### 1.2 APK Packaging & Signing
1. Executed `.\gradlew.bat assembleDebug`:
   ```
   BUILD SUCCESSFUL in 15s
   41 actionable tasks: 41 up-to-date
   ```
2. Output APK inspected: `app/build/outputs/apk/debug/app-debug.apk`
3. APK Signature verification via `apksigner verify -v "app/build/outputs/apk/debug/app-debug.apk"`:
   ```
   Verifies
   Verified using v1 scheme (JAR signing): false
   Verified using v2 scheme (APK Signature Scheme v2): true
   Number of signers: 1
   ```
4. Manifest & Badging verified via `aapt2 dump badging`:
   - Package: `name='com.comicreader' versionCode='1' versionName='1.0.0'`
   - SDK: `minSdkVersion:'34'`, `targetSdkVersion:'35'`, `compileSdkVersion:'34'`
   - Launchable Activity: `com.comicreader.MainActivity`

### 1.3 Concurrency Stress Testing & Empirical Failure
Command executed:
```powershell
.\gradlew.bat testDebugUnitTest
```
Verbatim test output summary:
```
NaturalOrderAndConcurrencyStressTest > testConcurrentCbzParsing_WithoutDeadlockOrFdLeaks FAILED
    java.lang.IllegalStateException at SafeCollector.kt:166

NaturalOrderAndConcurrencyStressTest > testConcurrentEpubParsing_WithoutDeadlockOrFdLeaks FAILED
    java.lang.IllegalStateException at SafeCollector.kt:166

205 tests completed, 2 failed
BUILD FAILED in 1m 18s
```

Verbatim stack trace from `app/build/reports/tests/testDebugUnitTest/classes/com.comicreader.stress.NaturalOrderAndConcurrencyStressTest.html`:
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
```
and:
```
	at com.comicreader.format.CbzParser$getPages$1.invokeSuspend(CbzParser.kt:116)
```

---

## 2. Logic Chain

1. **Root Cause Analysis**:
   - In `CbzParser.kt` (lines 103–117):
     ```kotlin
     try {
         openZip(file).use { zip ->
             ...
             emit(pages) // Line 113
         }
     } catch (_: Exception) {
         emit(emptyList()) // Line 116
     }
     ```
   - In `EpubParser.kt` (lines 149–173):
     ```kotlin
     try {
         ZipFile(file).use { zip ->
             ...
             emit(pages) // Line 166
         }
     } catch (e: InvalidEpubException) {
         throw e
     } catch (_: Exception) {
         emit(emptyList()) // Line 171
     }
     ```
   - In `CbrParser.kt` (lines 138–155):
     ```kotlin
     try {
         Archive(file).use { archive ->
             ...
             emit(pages) // Line 148
         }
     } catch (e: UnsupportedRarV5Exception) {
         throw e
     } catch (_: Exception) {
         emit(emptyList()) // Line 153
     }
     ```

2. **Mechanism of Failure**:
   - In Kotlin Coroutines, when downstream collects a Flow using operators like `.first()`, `.take(n)`, or early cancellation, the collector terminates collection by throwing `AbortFlowException`.
   - `AbortFlowException` extends `kotlinx.coroutines.CancellationException`, which extends `java.lang.IllegalStateException`, which is a subclass of `java.lang.Exception`.
   - Because `emit(pages)` was wrapped inside `try { ... } catch (_: Exception)`, the `catch` block catches `AbortFlowException` thinking it is an I/O error, and attempts a secondary emission: `emit(emptyList())`.
   - Kotlin Coroutines `SafeCollector` detects that `emit` was called after a previous emission threw an abort/cancellation exception, and immediately throws `IllegalStateException: Flow exception transparency is violated`.
   - This causes unpredictable crashes whenever a UI ViewModel or background coroutine cancels or calls `.first()` on `getPages(...)`.

3. **Required Mitigation for Worker**:
   Extract page computation into a local variable within the `try / catch` block, and perform `emit(pages)` outside the `try / catch` block:
   ```kotlin
   val pages = try {
       openZip(file).use { zip ->
           val imageEntries = getImageEntries(zip)
           imageEntries.mapIndexed { index, entry ->
               ComicPage(pageNumber = index, imageUri = entry.name, filePath = file.absolutePath)
           }
       }
   } catch (_: Exception) {
       emptyList()
   }
   emit(pages)
   ```
   Apply identical pattern in `CbrParser.kt` and `EpubParser.kt`.

---

## 3. Caveats
- No caveats regarding reproduction: the defect is 100% reproducible via `.\gradlew.bat testDebugUnitTest`.
- `NaturalOrderComparator` and `ComicFileScanner` passed all stress tests without defects.
- APK packaging and DEX assembly passed with zero errors.

---

## 4. Conclusion
**Verdict: REJECT**

While `NaturalOrderComparator`, `PdfParser`, `ComicFileScanner`, and `assembleDebug` are fully functional and verified, Milestone 2 cannot be approved because `CbzParser`, `CbrParser`, and `EpubParser` violate Kotlin Flow exception transparency contracts. Under concurrent coroutine consumption (`.first()`, `.take()`, or coroutine cancellation), this defect causes `IllegalStateException` crashes and breaks test suite pass rates (203/205 passing, 2 failing).

---

## 5. Verification Method

1. Run the test suite:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
2. Observe test failures:
   `NaturalOrderAndConcurrencyStressTest > testConcurrentCbzParsing_WithoutDeadlockOrFdLeaks FAILED`
   `NaturalOrderAndConcurrencyStressTest > testConcurrentEpubParsing_WithoutDeadlockOrFdLeaks FAILED`
3. Inspect `app/build/reports/tests/testDebugUnitTest/index.html`.
4. Invalidation condition: Worker updates `CbzParser.kt`, `CbrParser.kt`, and `EpubParser.kt` to perform `emit(pages)` outside the archive `try / catch` block, and `.\gradlew.bat testDebugUnitTest` runs with 205/205 tests passing (100% success rate).
