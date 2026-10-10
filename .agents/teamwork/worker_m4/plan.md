# Implementation Plan — Milestone 4: Library, Stats Dashboard, Settings & UI Themes

## 1. Overview & Objective
Implement Milestone 4 of the Android Comic Book Reader project:
- Four Switchable UI Themes: Dark AMOLED, Light, Material You Adaptive, and Comic Halftone style with Ben-Day dot Canvas pattern modifier.
- Presentation Components: `ComicCoverCard` (Coil image loading, format badge, progress bar, favorite toggle, delete action) and `WeeklyReadingChart` (Canvas-rendered 7-day bar chart with divide-by-zero protection).
- Screens & ViewModels (`@HiltViewModel`):
  1. `LibraryScreen`, `LibraryViewModel`, `LibraryUiState`
  2. `ComicDetailScreen`, `ComicDetailViewModel`, `ComicDetailUiState`
  3. `SearchScreen`, `SearchViewModel`, `SearchUiState`
  4. `FavoritesScreen`, `FavoritesViewModel`, `FavoritesUiState`
  5. `HistoryScreen`, `HistoryViewModel`, `HistoryUiState`
  6. `StatsScreen`, `StatsViewModel`, `StatsUiState`
  7. `SettingsScreen`, `SettingsViewModel`, `SettingsUiState`
- Navigation: `Screen.kt` type-safe routes and `NavGraph.kt` with NavHost wiring all screens and argument passing.
- Activity Integration: Update `MainActivity.kt` to load `ComicReaderTheme` from `UserPreferencesDataStore` and host `NavGraph`.
- Unit Tests: Comprehensive test suite for all ViewModels, Themes, and Components in `app/src/test/java/com/comicreader/presentation/screens/`.
- Verification: 100% test pass rate across baseline (254 tests) and new M4 tests (`.\gradlew.bat testDebugUnitTest`), plus clean debug APK packaging (`.\gradlew.bat assembleDebug`).

---

## 2. Architectural Design & Deliverables

### 2.1 UI Themes (`app/src/main/java/com/comicreader/ui/theme/`)
1. **`Color.kt`**:
   - Dark AMOLED palette: Background `#000000`, Primary `#7C4DFF` (Deep Violet), Secondary `#00E5FF` (Cyber Cyan), Surface `#121212`, SurfaceVariant `#1E1E1E`, Outline `#2C2C2C`.
   - Light palette: Background `#FAFAFA`, Primary `#1976D2` (Cobalt Blue), Secondary `#FF4081` (Vivid Pink), Surface `#FFFFFF`, SurfaceVariant `#F0F0F0`, Outline `#E0E0E0`.
   - Comic Halftone palette: Background `#FFFDF0` (Vintage Cream), Primary `#D32F2F` (Action Red), Secondary `#FFD600` (Comic Yellow), Tertiary `#1976D2` (Hero Blue), Surface `#FFF8E7`, SurfaceVariant `#FFE0B2`, OnBackground/Outline `#000000`.
2. **`ComicHalftonePattern.kt`**:
   - `Modifier.comicHalftonePattern(dotColor, dotRadius, spacing)` using `drawBehind` to render repeating Ben-Day dots with high performance.
3. **`Theme.kt`**:
   - `ComicReaderTheme` composable supporting `AppTheme.DARK`, `AppTheme.LIGHT`, `AppTheme.ADAPTIVE` (dynamic colors on API 31+ via `dynamicDarkColorScheme` / `dynamicLightColorScheme`), and `AppTheme.COMIC`.

### 2.2 Presentation Components (`app/src/main/java/com/comicreader/presentation/components/`)
1. **`ComicCoverCard.kt`**:
   - Card containing comic cover loaded via Coil `AsyncImage`.
   - Title text with ellipsis, format badge chip (`CBZ`, `CBR`, `PDF`, `EPUB`).
   - Reading progress bar (`LinearProgressIndicator`).
   - Favorite toggle button with animated / filled heart icon.
   - Click to open details / reader.
   - Delete callback or confirmation dialog.
2. **`WeeklyReadingChart.kt`**:
   - Canvas-rendered bar chart for 7 days (Monday..Sunday).
   - Rounded bar tops (`drawRoundRect`), height scaled by max count with `coerceAtLeast(10)` to prevent division by zero.
   - Day labels ("M", "T", "W", "T", "F", "S", "S").

### 2.3 Screens & ViewModels
1. **`Library`**:
   - `LibraryViewModel`: Injects `ComicRepository`, `UserPreferencesDataStore`, `ComicFileScanner`. Manages comic list, sort order (`SortOrder`), deletion, favorites, and folder scan trigger.
   - `LibraryScreen`: Adaptive grid of `ComicCoverCard`s, TopAppBar with action icons (Search, Favorites, History, Stats, Settings, Scan), sort order selector, empty state.
2. **`ComicDetail`**:
   - `ComicDetailViewModel`: Injects `SavedStateHandle`, `ComicRepository`, `BookmarkRepository`. Loads comic metadata, bookmarks, handles favorite toggle and bookmark deletion.
   - `ComicDetailScreen`: Displays cover, metadata (title, format, size, pages, progress, last read), "Read" / "Resume" button navigating to Reader, bookmark list with jump-to-page and delete actions.
3. **`Search`**:
   - `SearchViewModel`: Injects `ComicRepository`. Real-time query search, format filter chips (ALL, CBZ, CBR, PDF, EPUB), favorites-only and unread-only filters.
   - `SearchScreen`: Search text field, filter chip row, result count header, results grid.
4. **`Favorites`**:
   - `FavoritesViewModel`: Injects `ComicRepository`. Observes favorite comics.
   - `FavoritesScreen`: Grid of favorite comics, empty state.
5. **`History`**:
   - `HistoryViewModel`: Injects `ComicRepository`. Observes last 50 read comics ordered by `lastReadTimestamp DESC`.
   - `HistoryScreen`: Chronological list/grid of reading history.
6. **`Stats`**:
   - `StatsViewModel`: Injects `StatsRepository`. Formats total pages read, total reading time (hours & minutes), completed count, in-progress count, and 7-day weekly stats.
   - `StatsScreen`: Lifetime statistics cards and `WeeklyReadingChart`.
7. **`Settings`**:
   - `SettingsViewModel`: Injects `UserPreferencesDataStore`. Updates theme (`AppTheme`), default reading mode (`ReadingMode`), reading direction (`ReadingDirection`), volume key nav, watched folder path, keep screen awake.
   - `SettingsScreen`: Theme selection cards/radios, reading mode options, toggles for volume navigation and screen awake, folder path setting.

### 2.4 Navigation & Activity Integration
1. **`Screen.kt`**:
   - Routes: `Library`, `Reader(comicId: Long)`, `Detail(comicId: Long)`, `Search`, `Favorites`, `History`, `Stats`, `Settings`.
2. **`NavGraph.kt`**:
   - `ComicReaderNavGraph` hosting `NavHost` connecting all destinations and passing arguments.
3. **`MainActivity.kt`**:
   - Injects `UserPreferencesDataStore`, applies `ComicReaderTheme` based on stored theme flow, hosts `ComicReaderNavGraph`.

### 2.5 Unit Testing Suite (`app/src/test/java/com/comicreader/presentation/screens/`)
- `LibraryViewModelTest.kt`: Sort order changes, comic deletion, scanning flow, favorite toggle.
- `ComicDetailViewModelTest.kt`: Metadata loading, bookmark deletion, favorite toggle, missing comic handling.
- `SearchViewModelTest.kt`: Query filtering, format filtering, favorites/unread filtering.
- `FavoritesViewModelTest.kt`: Favorite list collection, unfavorite action.
- `HistoryViewModelTest.kt`: History list sorting and limit.
- `StatsViewModelTest.kt`: Stats calculations, hours/minutes formatting, 7-day aggregation.
- `SettingsViewModelTest.kt`: Theme updates, reading mode updates, volume key toggle, watched folder update.
- `WeeklyReadingChartTest.kt`: Divide-by-zero protection, 7-day label assertions.
- `ThemeTest.kt`: Theme color scheme values (AMOLED true black `#000000`, violet `#7C4DFF`, cyan `#00E5FF`, light `#FAFAFA`, comic `#FFFDF0`).

---

## 3. Step-by-Step Execution Plan
1. Create theme files: `Color.kt`, `ComicHalftonePattern.kt`, `Theme.kt`.
2. Create presentation components: `ComicCoverCard.kt`, `WeeklyReadingChart.kt`.
3. Create navigation routes in `Screen.kt` and `NavGraph.kt`.
4. Create Library screen: `LibraryUiState.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`.
5. Create Detail screen: `ComicDetailUiState.kt`, `ComicDetailViewModel.kt`, `ComicDetailScreen.kt`.
6. Create Search screen: `SearchUiState.kt`, `SearchViewModel.kt`, `SearchScreen.kt`.
7. Create Favorites screen: `FavoritesUiState.kt`, `FavoritesViewModel.kt`, `FavoritesScreen.kt`.
8. Create History screen: `HistoryUiState.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`.
9. Create Stats screen: `StatsUiState.kt`, `StatsViewModel.kt`, `StatsScreen.kt`.
10. Create Settings screen: `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`.
11. Update `MainActivity.kt` to bind theme and navigation graph.
12. Author comprehensive unit test suite in `app/src/test/java/com/comicreader/presentation/screens/`.
13. Execute `.\gradlew.bat testDebugUnitTest` and ensure all tests pass (254 baseline + new M4 tests).
14. Execute `.\gradlew.bat assembleDebug` and ensure APK compiles cleanly.
15. Write `changes.md` and `handoff.md`.
16. Report back to Orchestrator parent via `send_message`.
