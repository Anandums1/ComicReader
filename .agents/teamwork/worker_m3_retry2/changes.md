# Changes Log — Milestone 3 Remediation (Retry 2)

## 1. `app/src/main/java/com/comicreader/presentation/reader/ReaderViewModel.kt`
- **SavedStateHandle Safe Argument Resolution**:
  - Replaced unchecked runtime cast `savedStateHandle.get<Long>("comicId")` with type inspection on `savedStateHandle.get<Any>("comicId")`.
  - Added pattern matching (`when (rawId)`) handling `Long`, `Number` (`rawId.toLong()`), `String` (`rawId.toLongOrNull()`), and fallback `null`. This prevents runtime `ClassCastException` when route arguments are passed as Strings from Jetpack Compose Navigation (`reader/{comicId}`).
- **Foreign Key Relational Guarding for Non-Existent Comics**:
  - In `loadComic(comicId)`: If `comicRepository.getComicByIdOnce(comicId)` returns `null`, reset `currentComicId = 0L` and clear `comic = null` before returning. Also reset `currentComicId = 0L` if an exception occurs.
  - In `toggleBookmark()`: Added guard `if (currentComicId <= 0L || _uiState.value.comic == null) return` to prevent dispatching background Room insertions referencing non-existent comic IDs in `BookmarkEntity`.
  - In `onSessionEnd()`: Added guard `if (currentComicId <= 0L || _uiState.value.comic == null) return` to prevent inserting orphaned sessions into `ReadingStatsEntity`.
  - In `onPageChanged(pageIndex)`: Added `_uiState.value.comic != null` check before dispatching progress updates to `ComicRepository`.

## 2. `app/src/test/java/com/comicreader/presentation/reader/ReaderLifecycleStressTest.kt`
- **DataStore & ViewModel Lifecycle Synchronization**:
  - In `testActivityRecreation_preservesBookmarksAndPreferencesContinuity`:
    Synchronized recreated ViewModel state with `recreatedVm.uiState.first { !it.isLoading && it.readingMode == ReadingMode.VERTICAL && it.readingDirection == ReadingDirection.RTL }` followed by `ShadowLooper.idleMainLooper()`, ensuring asynchronous DataStore emissions on background IO dispatchers complete deterministically before assertions are evaluated.
- **String and Long Route Argument Variant Verification**:
  - Replaced `testSavedStateHandle_stringIdVariantThrowsClassCastExceptionDueToGenerics` with `testSavedStateHandle_stringAndLongIdVariantsSupportedAcrossRecreation`.
  - Validates that passing `"comicId"` as a String (standard Jetpack Compose Navigation route parameter format) as well as Long restores the active comic and state without `ClassCastException`.
