## 2026-10-09T16:59:45Z

You are Worker 3 for Milestone 3: Core Comic Reader Experience of the Android Comic Book Reader project.
Your Working Directory: c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\worker_m3
Your Parent: Orchestrator 1 (conversation ID: 314a4493-b27a-4d47-8543-10d596768eff)

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

INPUT SPECIFICATIONS (READ CAREFULLY):
- c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\ORIGINAL_REQUEST.md
- c:\Users\anand\Desktop\Comic book reader\comic_reader_plan.md
- c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\orchestrator_1\PROJECT.md
- c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\spec_miner_survey_2\format_ui_specs.md
- c:\Users\anand\Desktop\Comic book reader\app\src\test\java\com\comicreader\e2e\ReaderWorkflowE2ETest.kt

WRITE OWNERSHIP:
You exclusively own:
- `app/src/main/java/com/comicreader/presentation/reader/` (ReaderUiState.kt, ReaderViewModel.kt, ReaderScreen.kt, HorizontalReader.kt, VerticalReader.kt, ReaderGestures.kt)
- `app/src/main/java/com/comicreader/presentation/components/` (ZoomableBox.kt, ReaderOverlay.kt, PageScrubber.kt, BookmarkPanel.kt)
- `app/src/test/java/com/comicreader/presentation/reader/` (Unit tests for ReaderViewModel, gestures, zoom)

OBJECTIVE & DELIVERABLES:
Implement the complete Core Comic Reader Experience per the specifications in ORIGINAL_REQUEST.md §R4, comic_reader_plan.md, and format_ui_specs.md:

1. `ReaderUiState.kt`:
   - State model containing:
     - `comic: Comic?`
     - `pages: List<ComicPage>`
     - `currentPage: Int`
     - `totalPages: Int`
     - `readingMode: ReadingMode` (HORIZONTAL, VERTICAL)
     - `readingDirection: ReadingDirection` (LTR, RTL)
     - `pageAnimation: PageAnimation` (SLIDE, FADE, NONE)
     - `isOverlayVisible: Boolean`
     - `bookmarks: List<Bookmark>`
     - `isCurrentPageBookmarked: Boolean`
     - `zoomScale: Float`
     - `isLoading: Boolean`
     - `errorMessage: String?`
     - `keepScreenAwake: Boolean`
     - `brightness: Float`

2. `ReaderViewModel.kt`:
   - `@HiltViewModel` injecting:
     - `ComicRepository`
     - `BookmarkRepository`
     - `StatsRepository`
     - `UserPreferencesDataStore`
     - `ComicParserFactory`
     - `SavedStateHandle` (with "comicId" argument)
   - Functionality:
     - On init: reads comicId, loads comic details and pages via `ComicRepository` and `ComicParserFactory.getParser(...)?.getPages(...)`.
     - Observes `UserPreferencesDataStore.preferencesFlow` to set default readingMode, pageAnimation, readingDirection, keepScreenAwake, brightness, and volumeKeyNav.
     - Observes `BookmarkRepository.getBookmarksForComic(comicId)`.
     - `onPageChanged(pageIndex: Int)`: Updates currentPage, updates reading progress via `ComicRepository.updateReadingProgress(comicId, pageIndex, totalPages)`, tracks pages read in session.
     - `toggleOverlay()` / `setOverlayVisible(visible: Boolean)`.
     - `setReadingMode(mode: ReadingMode)`: Updates state and persists to `UserPreferencesDataStore.setReadingMode(mode)`.
     - `setReadingDirection(direction: ReadingDirection)`: Updates state and persists to `UserPreferencesDataStore.setReadingDirection(direction)`.
     - `toggleBookmark()`: Adds or removes bookmark for current page using `BookmarkRepository`.
     - `deleteBookmark(bookmarkId: Long)`.
     - `jumpToPage(pageIndex: Int)`.
     - `onVolumeKey(keyCode: Int): Boolean`: Intercepts `KeyEvent.KEYCODE_VOLUME_UP` and `KeyEvent.KEYCODE_VOLUME_DOWN` to flip pages respecting LTR/RTL when volume navigation is enabled. Returns true if handled.
     - `onSessionEnd()`: Records session duration and pages read to `StatsRepository.recordReadingSession(comicId, pagesTurned, durationSeconds)`.

3. `ReaderGestures.kt`:
   - Pure, testable helper functions:
     - `enum class TapZone { LEFT, CENTER, RIGHT }`
     - `enum class TapAction { PREVIOUS_PAGE, NEXT_PAGE, TOGGLE_OVERLAY, NO_OP }`
     - `resolveTapZone(tapFractionX: Float): TapZone` (0..0.30f = LEFT, 0.30f..0.70f = CENTER, 0.70f..1.0f = RIGHT)
     - `resolveTapAction(tapFractionX: Float, direction: ReadingDirection, isZoomed: Boolean): TapAction` (If isZoomed [scale > 1.05f] -> returns TOGGLE_OVERLAY or NO_OP, NEVER page turns! In LTR: LEFT -> PREVIOUS, RIGHT -> NEXT. In RTL: LEFT -> NEXT, RIGHT -> PREVIOUS. CENTER -> TOGGLE_OVERLAY).
     - `isZoomLockedOut(scale: Float): Boolean = scale > 1.05f`
     - `clampZoom(scale: Float): Float = scale.coerceIn(1.0f, 5.0f)`

4. Components:
   - `ZoomableBox.kt`:
     - Composable wrapping page content. Handles `detectTransformGestures` for pinch-to-zoom (1.0f to 5.0f) and panning with bounded translation offsets. Handles double-tap to toggle between 1.0f and 2.5f zoom.
   - `ReaderOverlay.kt`:
     - Top bar (`AnimatedVisibility`): Back button (`onBackClick`), comic title, bookmark toggle button, reading mode switch button/menu.
     - Bottom bar (`AnimatedVisibility`): Page scrubber slider (`Slider`), page count indicator ("Page ${currentPage + 1} of $totalPages"), jump-to-page button.
   - `PageScrubber.kt`:
     - Slider with immediate visual preview and debounced/commit page change on release.
   - `BookmarkPanel.kt`:
     - ModalBottomSheet or dialog showing bookmarks list for current comic with page index, note, and delete/jump actions.

5. Readers:
   - `HorizontalReader.kt`:
     - `HorizontalPager` with `rememberPagerState`.
     - Respects `ReadingDirection` (LTR / RTL via `CompositionLocalProvider(LocalLayoutDirection provides ...)`).
     - Page transition animations (SLIDE, FADE via graphicsLayer alpha).
     - Each page wrapped in `ZoomableBox` and rendered via Coil `AsyncImage` or bitmap loader.
     - Emits `onPageChanged(pagerState.currentPage)` via `LaunchedEffect`.
   - `VerticalReader.kt`:
     - `LazyColumn` with `rememberLazyListState` (0 item spacing for webtoon continuous strip).
     - Pages displayed with `ContentScale.FillWidth`.
     - `snapshotFlow { listState.firstVisibleItemIndex }` emitting `onPageChanged`.

6. `ReaderScreen.kt`:
   - Top-level composable binding `ReaderViewModel`, gestures, reader components, and overlay.
   - Manages `BackHandler` (invokes `onSessionEnd()` and navigates back).
   - Manages screen awake flag (`FLAG_KEEP_SCREEN_AWAKE`) when `keepScreenAwake` is true.
   - Handles tap zones, double-tap zoom, and long-press bookmarking.

7. Unit Tests:
   - In `app/src/test/java/com/comicreader/presentation/reader/`:
     - `ReaderViewModelTest.kt`: Test page loading, page turn auto-saving to ComicRepository, bookmark toggle, reading mode changes, volume key navigation.
     - `ReaderGesturesTest.kt`: Test 30%/40%/30% tap zones, RTL direction inversion, zoom lockout when scale > 1.05x, zoom clamping.

8. Verification:
   - Run `.\gradlew.bat testDebugUnitTest` and ensure ALL tests (including all 205 baseline tests + new reader tests) pass with 100% success rate.
   - Run `.\gradlew.bat assembleDebug` and ensure BUILD SUCCESSFUL with 0 errors.

REPORTING:
Write changes log to `c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\worker_m3\changes.md`.
Write handoff report to `c:\Users\anand\Desktop\Comic book reader\.agents\teamwork\worker_m3\handoff.md` with:
1. Observation (files created, build output)
2. Logic Chain (architecture decisions, gesture conflict resolution)
3. Caveats
4. Conclusion
5. Verification Method (exact test execution output)

Send a completion message back to Parent (314a4493-b27a-4d47-8543-10d596768eff).

