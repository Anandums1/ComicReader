# Comic Reader 📖🦸‍♂️

A modern, fast, and highly customizable local comic book reader for Android, built entirely with Kotlin and Jetpack Compose. 

Read your favorite `.cbz`, `.cbr`, `.epub`, and `.pdf` files offline with a seamless, fluid reading experience.

---

## ✨ Features

### 📚 Robust Library Management
* **Wide Format Support:** Natively parses and reads **CBZ, CBR, PDF, and EPUB** comic archives.
* **Smart Scanning:** Automatically scans your designated comic folder in the background. Adding a comic to your device makes it instantly appear in your library.
* **Storage Access Framework (SAF):** Pick your comic directory using Android's native folder picker, seamlessly supporting modern Android 11+ Scoped Storage.
* **Favorites & History:** Keep track of the comics you love and pick up right where you left off.
* **Search & Filter:** Quickly find your comics by title or format.

### 👓 Immersive Reading Experience
* **Multiple Reading Modes:** 
  * Horizontal Pager (Left-to-Right) - Western comics.
  * Horizontal Pager (Right-to-Left) - Manga.
  * Vertical Continuous Scroll - Webtoons and Manhwa.
* **Hardware Controls:** Turn pages using your device's physical volume keys.
* **Smart Zoom:** Pinch-to-zoom and double-tap zoom for reading fine print.
* **Bookmarks:** Save specific pages within a comic to refer back to later.
* **Keep Screen Awake:** Prevents your screen from timing out while reading.

### 🎨 Customization & Themes
* **Dark AMOLED:** Pure black background with vibrant accents to save battery on OLED screens.
* **Light Theme:** Clean, paper-white aesthetic.
* **Material You Adaptive:** Extracts dynamic colors directly from your system wallpaper.
* **Comic Halftone:** A vintage pop-art newsprint aesthetic with Ben-Day dot styling.

### 📊 Reading Statistics
* Tracks your reading habits, including pages turned and time spent reading, displayed in a beautiful weekly chart.

---

## 🛠️ Tech Stack & Architecture

Comic Reader is built using modern Android development practices and architecture:

* **100% Kotlin:** Fully written in Kotlin using Coroutines and StateFlow for asynchronous, reactive programming.
* **Jetpack Compose:** The entire UI is built declaratively using Material 3 components.
* **Clean Architecture:** Strict separation of concerns (Presentation, Domain, and Data layers).
* **Dagger Hilt:** Dependency injection.
* **Room Database:** Local persistence for library caching, bookmarks, and reading stats.
* **Jetpack DataStore:** Type-safe local storage for user preferences.
* **Format Parsers:** Uses `Apache Commons Compress`, `Junrar`, Android `PdfRenderer`, and `Jsoup` for rapid archive extraction and metadata parsing.

---

## 🚀 Installation

You can download the latest optimized release APK directly from the [Releases Tab](https://github.com/Anandums1/ComicReader/releases).

1. Download `ComicReader-Optimized-Release.apk`.
2. Open the APK on your Android device to install it.
3. Upon first launch, grant the "All Files Access" permission so the app can locate and index your local `.cbz` and `.cbr` files.
4. Go to **Settings -> Library Storage** and pick the folder where you store your comics!

---

## 💻 Building Locally

To build this project locally on your machine:

1. Clone the repository:
   ```bash
   git clone https://github.com/Anandums1/ComicReader.git
   ```
2. Open the project in **Android Studio**.
3. Let Gradle sync the dependencies.
4. Hit **Run** (`Shift + F10`) to build and deploy to your connected device or emulator.

To generate a highly optimized production build:
```bash
./gradlew :app:assembleRelease
```
*Note: The release build is configured with R8 tree shaking, resource shrinking, and ABI filtering (`arm64-v8a` only) to drastically reduce the binary size.*

---

## 🤝 Contributing
Pull requests are welcome! If you find any bugs or have feature requests, please open an issue first to discuss what you would like to change.

## 📄 License
This project is open-source and available under the standard MIT License.