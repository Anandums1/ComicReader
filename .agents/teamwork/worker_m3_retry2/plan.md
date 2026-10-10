# Implementation Plan — Milestone 3 Remediation (Retry 2)

## 1. Context & Objective
Remediate Gate 1 defects identified by Reviewer and Challengers in `ReaderViewModel.kt`, `ReaderLifecycleStressTest.kt`, and `ReaderViewModelStressTest.kt`:
1. Fix `ClassCastException` on `SavedStateHandle` navigation parameters (`ReaderViewModel.kt:50-59`).
2. Prevent `SQLiteConstraintException: FOREIGN KEY constraint failed` when handling non-existent or invalid comic IDs (`ReaderViewModel.kt:78-96, 171-180, 227-247, 295-309`).
3. Ensure asynchronous DataStore preferences and Room bookmark observation across activity recreation are properly synchronized in `ReaderLifecycleStressTest.kt`.
4. Update/add tests in `ReaderLifecycleStressTest.kt` to verify that String and Long IDs in `SavedStateHandle` are both fully supported without throwing `ClassCastException`.
5. Verify 100% test pass rate across the entire test suite and verify `assembleDebug` succeeds with 0 errors.

---

## 2. Root Cause & Concrete Fixes

### 2.1 SavedStateHandle ClassCastException
- **Location**: `ReaderViewModel.kt:50-59`
- **Cause**: `savedStateHandle.get<Long>("comicId")` performs an unchecked generic cast `(T) map.get(key)`. If a `String` is passed (e.g. from Jetpack Compose Navigation route `reader/{comicId}`), this throws `ClassCastException` at runtime before the Elvis operator fallback is reached.
- **Fix**:
  ```kotlin
  val rawId = savedStateHandle.get<Any>("comicId")
  val initialId = when (rawId) {
      is Long -> rawId
      is Number -> rawId.toLong()
      is String -> rawId.toLongOrNull()
      else -> null
  }
  ```

### 2.2 SQLiteConstraintException Foreign Key Failure on Missing Comics
- **Location**: `ReaderViewModel.kt:78-96, 171-180, 227-247, 295-309`
- **Cause**:
  1. `loadComic(comicId)` sets `currentComicId = comicId`. If `comicRepository.getComicByIdOnce(comicId)` returns `null`, `currentComicId` was not reset to `0L`.
  2. In `toggleBookmark()`: Checked only `currentComicId <= 0L`, but not whether the comic actually exists (`_uiState.value.comic == null`).
  3. In `onSessionEnd()`: Checked only `currentComicId <= 0L`, but not whether `_uiState.value.comic == null`.
  4. In `onPageChanged()`: Checked only `currentComicId > 0L`, but not whether `_uiState.value.comic == null`.
- **Fix**:
  - In `loadComic(comicId)`:
    If `comic == null`, set `currentComicId = 0L` and update `_uiState` with `comic = null, isLoading = false, errorMessage = "Comic not found (id: $comicId)"`.
    In `catch (e: Exception)`: set `currentComicId = 0L`.
  - In `toggleBookmark()`: Guard with `if (currentComicId <= 0L || _uiState.value.comic == null) return`.
  - In `onSessionEnd()`: Guard with `if (currentComicId <= 0L || _uiState.value.comic == null) return`.
  - In `onPageChanged()`: Guard with `if (comicId <= 0L || _uiState.value.comic == null) return` before calling `comicRepository.updateReadingProgress`.

### 2.3 DataStore & Lifecycle Recreation Synchronization
- **Location**: `ReaderLifecycleStressTest.kt`
- **Cause**: DataStore reads on background IO dispatchers asynchronously. Upon activity recreation / new ViewModel creation, `ReaderUiState` defaults to `ReadingMode.HORIZONTAL` until the background IO collection updates `_uiState`.
- **Fix**:
  - In `testActivityRecreation_preservesBookmarksAndPreferencesContinuity`:
    Await `recreatedVm.uiState.first { !it.isLoading && it.readingMode == ReadingMode.VERTICAL && it.readingDirection == ReadingDirection.RTL }` and pump `ShadowLooper.idleMainLooper()`.
  - In `testSavedStateHandle_stringAndLongIdVariantsSupportedAcrossRecreation`:
    Verify that `SavedStateHandle` with `String` ID loads the comic cleanly without `ClassCastException`, and that `Long` ID also loads properly.

---

## 3. Execution Steps
1. Edit `ReaderViewModel.kt` with the safe `SavedStateHandle` parsing and foreign key guards.
2. Edit `ReaderLifecycleStressTest.kt` to synchronize preference emissions and test String + Long navigation arguments.
3. Verify `ReaderViewModelStressTest.kt` passes `testNonExistentComic_gracefulErrorHandling` and all other stress cases.
4. Run `./gradlew.bat testDebugUnitTest` across the entire project (all baseline and milestone tests).
5. Run `./gradlew.bat assembleDebug` to confirm build integrity.
6. Write `changes.md` and `handoff.md`.
7. Message parent agent with summary of findings and verification results.
