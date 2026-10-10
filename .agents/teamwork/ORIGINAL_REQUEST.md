# Original User Request

## 2026-10-09T11:56:07Z

Build a feature-rich, standalone Android Comic Book Reader app in Kotlin and Jetpack Compose based on the implementation plan in `comic_reader_plan.md`, supporting CBZ, CBR, PDF, and EPUB formats with library management, dual-mode reading (horizontal page-by-page and vertical scroll), reading statistics, and four switchable UI themes.

Working directory: c:\Users\anand\Desktop\Comic book reader
Integrity mode: demo

Reference material: comic_reader_plan.md

## Requirements

### R1. Android Project Setup & Architecture
Set up a clean Android Gradle project (Kotlin DSL, Min SDK 34, Target SDK 35) using Jetpack Compose (Material 3), MVVM with StateFlow, Room Database for local persistence, DataStore Preferences for app settings, and Hilt for dependency injection.

### R2. Comic Format Parsers
Implement robust parsing for CBZ (ZIP extraction), CBR (RAR extraction via Junrar), PDF (Android PdfRenderer), and EPUB formats, exposing comic pages uniformly as reactive streams (`Flow<List<ComicPage>>`).

### R3. Library Management & File Scanner
Implement local file scanning for `/Downloads/Comics` and custom directories with `FileObserver` / background scanner, cover art thumbnail extraction, search by title and format, and favorites & history tracking.

### R4. Core Comic Reader Experience
Provide dual reading modes (horizontal page-by-page and vertical continuous scroll), gesture navigation (swipe, tap zones, volume keys, pinch-to-zoom), bookmarking with thumbnails, center-tap overlay controls, and reading progress auto-saving.

### R5. Dashboard & UI Themes
Implement a reading stats dashboard (pages read, session duration, progress charts via Canvas) and four switchable themes (Dark AMOLED, Light, Material You Adaptive, and Comic Halftone style) with settings configuration.

## Acceptance Criteria

### Build & Verification
- [ ] `./gradlew assembleDebug` builds an installable Android APK with 0 errors.
- [ ] Unit tests for parsers (CBZ, CBR, PDF, EPUB) and Room DAOs pass cleanly via `./gradlew testDebugUnitTest`.
- [ ] Dual reading modes (horizontal pager and vertical scroll) and overlay controls are fully wired to state.
- [ ] Four switchable themes (Dark, Light, Adaptive, Comic) are implemented and selectable from Settings.
- [ ] Architecture strictly adheres to the clean MVVM + StateFlow structure specified in `comic_reader_plan.md`.

