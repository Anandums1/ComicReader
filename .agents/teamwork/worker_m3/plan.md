# Implementation Plan — Milestone 3: Core Comic Reader Experience

## Overview
Implement the complete Core Comic Reader Experience per the specifications in ORIGINAL_REQUEST.md §R4, comic_reader_plan.md, and format_ui_specs.md.

## 1. ReaderUiState.kt
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

## 2. ReaderGestures.kt
- Pure, testable helper functions:
  - `enum class TapZone { LEFT, CENTER, RIGHT }`
  - `enum class TapAction { PREVIOUS_PAGE, NEXT_PAGE, TOGGLE_OVERLAY, NO_OP }`
  - `resolveTapZone(tapFractionX: Float): TapZone` (0..0.30f = LEFT, 0.30f..0.70f = CENTER, 0.70f..1.0f = RIGHT)
  - `resolveTapAction(tapFractionX: Float, direction: ReadingDirection, isZoomed: Boolean): TapAction` (If isZoomed [scale > 1.05f] -> returns TOGGLE_OVERLAY or NO_OP, NEVER page turns! In LTR: LEFT -> PREVIOUS, RIGHT -> NEXT. In RTL: LEFT -> NEXT, RIGHT -> PREVIOUS. CENTER -> TOGGLE_OVERLAY).
  - Overloads for tapX/screenWidth.
  - `isZoomLockedOut(scale: Float): Boolean = scale > 1.05f`
  - `clampZoom(scale: Float): Float = scale.coerceIn(1.0f, 5.0f)`
  - `toggleDoubleTapZoom(currentScale: Float): Float = if (currentScale > 1.5f) 1.0f else 2.5f`
  - `resolveTapPageChange(tapFraction: Float, current: Int, max: Int, direction: ReadingDirection): Int`
  - `handleVolumeKey(keyCode: Int, current: Int, max: Int): Int`

## 3. ReaderViewModel.kt
- `@HiltViewModel` injecting:
  - `ComicRepository`
  - `BookmarkRepository`
  - `StatsRepository`
  - `UserPreferencesDataStore`
  - `ComicParserFactory`
  - `SavedStateHandle`
- Features:
  - Automatic comic & pages loading via `ComicParserFactory.getParser(...)?.getPages(...)`
  - Preferences observation (`readingMode`, `pageAnimation`, `readingDirection`, `keepScreenAwake`, `brightness`, `volumeKeyNav`)
  - Bookmarks observation and toggle
  - `onPageChanged(pageIndex: Int)` updating progress in Room via `ComicRepository.updateReadingProgress`
  - Volume key handling (`onVolumeKey(keyCode: Int): Boolean`) for KEYCODE_VOLUME_DOWN / KEYCODE_VOLUME_UP
  - `onSessionEnd()` recording reading session duration and pages read to `StatsRepository.recordReadingSession`

## 4. Presentation Components
- `ZoomableBox.kt`: Pinch-to-zoom (1.0x to 5.0x), bounded pan translation offsets, double-tap zoom toggle (1.0x <-> 2.5x).
- `PageScrubber.kt`: Smooth slider with immediate visual preview, page numbers, and release commit.
- `BookmarkPanel.kt`: ModalBottomSheet showing bookmark list with page numbers, notes, jump, and delete actions.
- `ReaderOverlay.kt`: Animated top app bar (back, title, bookmark, bookmarks list, mode toggle) and bottom bar (scrubber).

## 5. Readers
- `HorizontalReader.kt`: `HorizontalPager` with `PagerState`, LTR/RTL support via `LocalLayoutDirection`, FADE / SLIDE transitions, `ZoomableBox` on each page, Coil/bitmap renderer, two-way sync of `currentPage`.
- `VerticalReader.kt`: `LazyColumn` continuous scroll (0-spacing), `ContentScale.FillWidth`, `firstVisibleItemIndex` page tracking, `ZoomableBox`, two-way sync of `currentPage`.

## 6. ReaderScreen.kt
- Composable binding `ReaderViewModel`, gestures, reader components, and overlay.
- `BackHandler` with session recording.
- Screen awake flag management (`FLAG_KEEP_SCREEN_AWAKE`).
- Window brightness adjustment.
- Tap zones, double-tap zoom, and long-press bookmarking.

## 7. Unit Tests
- `ReaderViewModelTest.kt`: Tests loading, page changes, progress saving, bookmarks, reading mode, reading direction, volume keys, and session recording.
- `ReaderGesturesTest.kt`: Tests tap zones, LTR/RTL resolution, zoom lockout, zoom clamping, double-tap toggle.

## 8. Verification
- `.\gradlew.bat testDebugUnitTest` (all tests pass)
- `.\gradlew.bat assembleDebug` (build successful)

