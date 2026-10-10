# Implementation Plan — Milestone 3 Remediation (Retry 1)

## 1. Context & Objective
Remediate test execution and synchronization in `ReaderViewModelTest` and `ReaderViewModel`, ensure `ReaderGesturesTest` passes, verify that all unit tests (`testDebugUnitTest`, including 205 baseline tests + new reader tests) succeed with 100% pass rate, and verify `assembleDebug` succeeds with 0 errors.

## 2. Root Cause Analysis
In Robolectric JVM unit tests:
- `viewModelScope` launches tasks on `Dispatchers.Main.immediate`, which is backed by Android's `Looper.getMainLooper()`.
- When flows from `DataStore` (or `Room`) are observed inside `viewModelScope`, or when `advanceUntilIdle()` / `runTest` executes, background dispatcher tasks or queued Looper messages can cause race conditions or indefinite hangs if the Main Looper is not idle or if dispatchers aren't properly configured.
- Passing a `CoroutineDispatcher = Dispatchers.Main` to `ReaderViewModel` (or utilizing `@MainDispatcher` or `UnconfinedTestDispatcher` with `ShadowLooper.idleMainLooper()`) ensures full deterministic control in test environments without affecting production Hilt injection.

## 3. Planned Changes
1. **`ReaderViewModel.kt`**:
   - Add optional `@MainDispatcher mainDispatcher: CoroutineDispatcher = Dispatchers.Main` constructor parameter, or ensure all `viewModelScope.launch` calls execute cleanly on `mainDispatcher`.
   - Ensure Hilt DI continues to compile with `@Inject constructor` and default dispatcher or `@MainDispatcher`.
2. **`ReaderViewModelTest.kt`**:
   - Use `StandardTestDispatcher` / `UnconfinedTestDispatcher` appropriately.
   - Flush `ShadowLooper.idleMainLooper()` / `shadowOf(Looper.getMainLooper()).idle()` after coroutine actions to pump Android Looper tasks.
   - Verify all 10 tests in `ReaderViewModelTest` pass.
3. **Verify `ReaderGesturesTest.kt`**:
   - Verify all tests pass cleanly.
4. **Project-wide Verification**:
   - Run `.\gradlew.bat testDebugUnitTest` and ensure 0 failures across all tests.
   - Run `.\gradlew.bat assembleDebug` and ensure successful build.
5. **Documentation & Handoff**:
   - Write `changes.md` and `handoff.md`.
   - Send completion message to parent orchestrator.

