# Changes Log — Milestone 3 Remediation (Retry 1)

## Overview
Worker 3 Retry 1 resolved coroutine test synchronization in `ReaderViewModel.kt` and `ReaderViewModelTest.kt`, ensuring deterministic execution in Robolectric JVM tests, passing all 10 `ReaderViewModelTest` tests, all 13 `ReaderGesturesTest` tests, 100% pass across all 228 project unit tests, and 0 errors on `assembleDebug`.

---

## Files Modified

### 1. `app/src/main/java/com/comicreader/presentation/reader/ReaderViewModel.kt`
- **Injected `@MainDispatcher mainDispatcher: CoroutineDispatcher = Dispatchers.Main`**:
  - Added `@MainDispatcher private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main` constructor parameter with default value to maintain full backwards-compatibility and standard Hilt dependency injection.
  - Updated all `viewModelScope.launch` calls to execute on `mainDispatcher`:
    - `observePreferences()`
    - `loadComic()` and inner bookmark observer
    - `onPageChanged()`
    - `setReadingMode()`
    - `setReadingDirection()`
    - `setPageAnimation()`
    - `toggleBookmark()`
    - `deleteBookmark()`
    - `setBrightness()`
    - `setKeepScreenAwake()`
    - `onSessionEnd()`
  - Ensures coroutine tasks in tests run on the designated test dispatcher instead of defaulting to un-pumped background or platform looper threads.

### 2. `app/src/test/java/com/comicreader/presentation/reader/ReaderViewModelTest.kt`
- **Deterministic Test Synchronization**:
  - In `setUp()`: Initialized `dataStore` with known baseline state (`setReadingMode(ReadingMode.HORIZONTAL)` and `setReadingDirection(ReadingDirection.LTR)`) using `runBlocking` to prevent cross-test preference pollution.
  - In `testReadingModeChange_persistsToPreferences` and `testReadingDirectionChange_persistsToPreferences`:
    - Passed `mainDispatcher = testDispatcher` to `ReaderViewModel`.
    - Synchronized with `dataStore.readingModeFlow.first { it == ReadingMode.VERTICAL }` and `dataStore.readingDirectionFlow.first { it == ReadingDirection.RTL }` to allow asynchronous DataStore disk I/O to commit before asserting `viewModel.uiState`.
    - Pushed looper work using `ShadowLooper.idleMainLooper()` and `advanceUntilIdle()`.

---

## Verification Results
1. **Reader Unit Tests**:
   - `ReaderViewModelTest`: 10/10 passed (0 failures, 0 skipped).
   - `ReaderGesturesTest`: 13/13 passed (0 failures, 0 skipped).
2. **Whole Project Unit Tests**:
   - `.\gradlew.bat testDebugUnitTest`: 228 tests completed, 0 failures, 0 ignored, 100% success rate.
3. **Build Target**:
   - `.\gradlew.bat assembleDebug`: BUILD SUCCESSFUL (0 errors).

