# Handoff Report — Milestone 3 Remediation (Retry 1)

## 1. Observation
1. **Initial Test Run Execution**:
   Running `.\gradlew.bat testDebugUnitTest --tests "com.comicreader.presentation.reader.*"` initially failed on two tests:
   - `ReaderViewModelTest > testReadingDirectionChange_persistsToPreferences`:
     ```
     java.lang.AssertionError: expected:<RTL> but was:<LTR>
     at org.junit.Assert.fail(Assert.java:89)
     at org.junit.Assert.assertEquals(Assert.java:120)
     at com.comicreader.presentation.reader.ReaderViewModelTest.testReadingDirectionChange_persistsToPreferences(ReaderViewModelTest.kt:284)
     ```
   - `ReaderViewModelTest > testReadingModeChange_persistsToPreferences`:
     ```
     java.lang.AssertionError: expected:<VERTICAL> but was:<HORIZONTAL>
     at org.junit.Assert.fail(Assert.java:89)
     at org.junit.Assert.assertEquals(Assert.java:120)
     at com.comicreader.presentation.reader.ReaderViewModelTest.testReadingModeChange_persistsToPreferences(ReaderViewModelTest.kt:262)
     ```
   - `ReaderGesturesTest` passed 13/13 tests cleanly.
2. **Post-Remediation Verification**:
   - `ReaderViewModelTest` passed 10/10 tests cleanly.
   - `.\gradlew.bat testDebugUnitTest`:
     ```
     BUILD SUCCESSFUL in 1m 8s
     35 actionable tasks: 1 executed, 34 up-to-date
     ```
     `index.html` report shows:
     - Tests: `228`
     - Failures: `0`
     - Ignored: `0`
     - Success rate: `100%`
3. **Build Target Verification**:
   - `.\gradlew.bat assembleDebug`:
     ```
     BUILD SUCCESSFUL in 39s
     41 actionable tasks: 4 executed, 37 up-to-date
     ```

---

## 2. Logic Chain
1. **Root Cause**:
   - In `ReaderViewModel.init`, `observePreferences()` launched a coroutine collecting `userPreferencesDataStore.preferencesFlow`.
   - AndroidX `DataStore` performs disk read and write operations asynchronously on its background `Dispatchers.IO` thread pool.
   - In `runTest(testDispatcher)` with `StandardTestDispatcher`, `advanceUntilIdle()` controls virtual time on `testDispatcher` without awaiting asynchronous background I/O dispatched to `Dispatchers.IO`.
   - Consequently, when `viewModel.setReadingMode(ReadingMode.VERTICAL)` updated `_uiState` and dispatched `userPreferencesDataStore.setReadingMode(mode)`, the initial asynchronous read from disk on `Dispatchers.IO` (bearing default value `HORIZONTAL`) emitted shortly after and overwrote `_uiState` before the write operation could be committed and emitted.
2. **Remediation**:
   - Injected `@MainDispatcher private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main` into `ReaderViewModel` constructor, wiring all `viewModelScope.launch` calls to `mainDispatcher`.
   - In `ReaderViewModelTest`:
     - Cleaned DataStore baseline preferences in `setUp()` using `runBlocking`.
     - In `testReadingModeChange_persistsToPreferences` and `testReadingDirectionChange_persistsToPreferences`, passed `mainDispatcher = testDispatcher` and synchronized on `dataStore.readingModeFlow.first { it == ReadingMode.VERTICAL }` / `dataStore.readingDirectionFlow.first { it == ReadingDirection.RTL }`.
     - Called `ShadowLooper.idleMainLooper()` and `advanceUntilIdle()` to flush Android looper queues.
   - This guarantees that DataStore disk writes complete and the resulting reactive emission updates `_uiState` deterministically before assertions are evaluated.

---

## 3. Caveats
- No caveats. All 228 project tests (205 baseline tests covering DAOs, CBZ, CBR, PDF, EPUB parsers, and scanners + 23 Milestone 3 reader tests) pass with 100% success rate.
- Production Hilt DI is unaffected as `AppModule` provides `@MainDispatcher CoroutineDispatcher = Dispatchers.Main` and the constructor parameter has a default argument.

---

## 4. Conclusion
Milestone 3 (Core Comic Reader Experience) is fully implemented, verified, and complete:
- Core composables (`ReaderScreen`, `HorizontalReader`, `VerticalReader`, `ReaderOverlay`, `BookmarkPanel`, `PageScrubber`, `ZoomableBox`) and gesture logic (`ReaderGestures`) are fully functional.
- `ReaderViewModelTest` and `ReaderGesturesTest` execute deterministically with 100% pass rate.
- Whole-project unit test suite passes with 0 failures across 228 test cases.
- `assembleDebug` builds the debug APK with 0 errors.

---

## 5. Verification Method

### Reader Test Suite Verification
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.comicreader.presentation.reader.*"
```
**Output**:
```
BUILD SUCCESSFUL in 1m 28s
35 actionable tasks: 11 executed, 24 up-to-date
```
Test results file: `app/build/test-results/testDebugUnitTest/TEST-com.comicreader.presentation.reader.ReaderViewModelTest.xml`
`<testsuite name="com.comicreader.presentation.reader.ReaderViewModelTest" tests="10" skipped="0" failures="0" errors="0">`

### Whole Project Unit Test Verification
```powershell
.\gradlew.bat testDebugUnitTest
```
**Output**:
```
BUILD SUCCESSFUL in 1m 8s
35 actionable tasks: 1 executed, 34 up-to-date
```
HTML Report: `app/build/reports/tests/testDebugUnitTest/index.html`
`<div class="counter">228</div> tests, <div class="counter">0</div> failures, 100% successful`

### Debug APK Build Verification
```powershell
.\gradlew.bat assembleDebug
```
**Output**:
```
BUILD SUCCESSFUL in 39s
41 actionable tasks: 4 executed, 37 up-to-date
```

