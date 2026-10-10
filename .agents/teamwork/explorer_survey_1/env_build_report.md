# Environment & Build Assessment Report

**Project**: Comic Book Reader (Android APK)  
**Date**: 2026-10-09  
**Agent**: Environment & Build Explorer (`explorer_survey_1`)  
**Scope**: Host environment inspection, build toolchain verification, dependency coordinates, and build feasibility analysis.

---

## 1. Host System Analysis

### 1.1 Operating System & Architecture
- **OS**: Windows 11 Pro / Enterprise (Version 10.0, amd64)
- **Shell**: PowerShell (Windows native)

### 1.2 Java Development Kit (JDK)
- **Detected Runtime**: OpenJDK 21.0.12.1+1 LTS (Eclipse Adoptium Temurin-21)
- **Binary Path**: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe`
- **Architecture**: 64-Bit Server VM (mixed mode, sharing)
- **`JAVA_HOME` Environment Variable**: Currently **not set** in system environment variables.
- **Action Required**: Set `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"` in terminal sessions or configure Gradle JVM toolchain.

### 1.3 Android SDK & Command-Line Tools
- **SDK Path**: `C:\Users\anand\AppData\Local\Android\Sdk`
- **`ANDROID_HOME` / `ANDROID_SDK_ROOT`**: Currently **not set** in system environment variables.
- **Android CLI Tool**: Installed and active (`1.0.16261425`).
- **Platform-Tools**: `37.0.1` installed (`adb.exe`, etc.).
- **Installed Build-Tools**:
  - `build-tools/34.0.0`
  - `build-tools/35.0.0`
  - `build-tools/36.0.0`
  - `build-tools/36.1.0`
  - `build-tools/37.0.0`
- **Installed Platforms**:
  - `platforms/android-34` (API 34)
  - `platforms/android-36` (API 36)
  - `platforms/android-36.1`
  - `platforms/android-37.0`
  - *Note on API 35*: Platform 35 (`platforms/android-35 2.0.0`) is available in Google SDK repository and installable on demand via `android sdk install platforms/android-35`. Target SDK 35 with compileSdk 34 or compileSdk 35 works cleanly once installed.

### 1.4 Connected Devices & Emulators
- **Emulator Available**: `Medium_Phone` (Android Emulator version `37.2.12`).
- **Connected Physical Devices**: None attached (`adb devices` lists 0 active devices). Daemon starts and communicates via `tcp:5037`.

---

## 2. Gradle & Gradle Wrapper Architecture

### 2.1 Gradle Availability
- System PATH does not have a global `gradle` command.
- **Solution**: Android Gradle wrapper (`gradlew` / `gradlew.bat` with `gradle/wrapper/gradle-wrapper.jar` and `gradle-wrapper.properties`) is the standard, reliable mechanism for standalone builds.
- Initializing the wrapper can be done using the `android create` command template or placing standard wrapper files.
- The wrapper was verified to download and run cleanly on the host system using JDK 21 on Windows.

### 2.2 Recommended Gradle Version
- **Gradle 8.10.2** (or **8.11.1**): Excellent compatibility with AGP 8.7.3, Kotlin 2.0.21, and KSP 2.0.21.
- Alternatively, **Gradle 9.1.0** works with AGP 9.0+, but Gradle 8.10.2 + AGP 8.7.3 offers the most battle-tested stability for Hilt 2.52 and Room 2.6.1 annotation processors.

---

## 3. Toolchain & Plugin Compatibility (AGP, Kotlin, Compose, Java)

### 3.1 Matrix Overview

| Component | Target Version | Compatibility Notes |
|---|---|---|
| **Android Gradle Plugin (AGP)** | `8.7.3` (or `8.8.0`) | Supports Gradle 8.9+, JDK 21, API 34/35. |
| **Kotlin** | `2.0.21` | Modern Kotlin compiler with improved performance. |
| **Compose Compiler Plugin** | `2.0.21` (`org.jetbrains.kotlin.plugin.compose`) | Kotlin 2.0+ includes Compose compiler directly; no legacy `kotlinCompilerExtensionVersion` needed. |
| **Jetpack Compose BOM** | `2024.09.01` (or `2024.09.00`) | Material 3, Foundation Pager, UI tooling. |
| **Annotation Processing** | **KSP** `2.0.21-1.0.28` | **Crucial**: Replace Kapt with KSP (`com.google.devtools.ksp`). Faster and fully compatible with Kotlin 2.0+ & JDK 21. |
| **Java Target** | Java 17 | `sourceCompatibility = JavaVersion.VERSION_17`, `targetCompatibility = JavaVersion.VERSION_17`, `jvmToolchain(17)` |

### 3.2 Accompanist Pager Deprecation Notice
- `comic_reader_plan.md` mentions `com.google.accompanist:accompanist-pager:0.36.0`.
- **Finding**: Accompanist Pager is deprecated. Jetpack Compose Foundation has had official first-party `HorizontalPager` and `VerticalPager` (`androidx.compose.foundation.pager.HorizontalPager`) since Compose 1.4+.
- **Recommendation**: Use `androidx.compose.foundation.pager.HorizontalPager` from the Compose BOM. It requires 0 extra dependencies and avoids version mismatch issues.

---

## 4. Dependency Coordinates & Repository Verification

### 4.1 Required Repositories
In `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

### 4.2 Dependency Audit Table

| Artifact | Specified in Plan | Maven Status | Recommended Resolution / Action |
|---|---|---|---|
| **Junrar** | `com.github.junrar:junrar:7.5.5` | ✅ Available in Maven Central | Fully resolvable from `mavenCentral()`. |
| **Commons Compress** | `org.apache.commons:commons-compress:1.26.2` | ✅ Available in Maven Central | Fully resolvable from `mavenCentral()`. |
| **Room Runtime & KTX** | `androidx.room:room-runtime:2.6.1`, `room-ktx:2.6.1` | ✅ Available in Google Maven | Fully resolvable from `google()`. |
| **Room Compiler** | `kapt("androidx.room:room-compiler:2.6.1")` | ✅ Available in Google Maven | Use `ksp("androidx.room:room-compiler:2.6.1")`. |
| **Hilt Android** | `com.google.dagger:hilt-android:2.52` | ✅ Available in Maven Central | Fully resolvable from `mavenCentral()`. |
| **Hilt Compiler** | `kapt("com.google.dagger:hilt-android-compiler:2.52")` | ✅ Available in Maven Central | Use `ksp("com.google.dagger:hilt-compiler:2.52")`. |
| **Hilt Navigation Compose** | `androidx.hilt:hilt-navigation-compose:1.2.0` | ✅ Available in Google Maven | Fully resolvable from `google()`. |
| **Coil Compose** | `io.coil-kt:coil-compose:2.7.0` | ✅ Available in Maven Central | Fully resolvable from `mavenCentral()`. |
| **DataStore Preferences** | `androidx.datastore:datastore-preferences:1.1.1` | ✅ Available in Google Maven | Fully resolvable from `google()`. |
| **Navigation Compose** | `androidx.navigation:navigation-compose:2.8.1` | ✅ Available in Google Maven | Fully resolvable from `google()`. |
| **EPUB Parsing Library** | `io.github.siegeindahouse:epublib-core:4.0` | ❌ **NOT FOUND** in Maven Central | **See resolution below.** |

### 4.3 EPUB Dependency Resolution & Strategy
1. **Coordinate Issue**: `io.github.siegeindahouse:epublib-core:4.0` specified in the implementation plan does **not exist** on Maven Central.
2. **Legacy `epublib` Challenges**:
   - `nl.siegmann.epublib:epublib-core:3.1` is hosted on a custom GitHub Maven repository (`https://github.com/psiegman/mvn-repo/raw/master/releases`).
   - It transitively brings `xmlpull:xmlpull` and `org.slf4j:slf4j-api`, causing duplicate class build errors on Android unless explicitly excluded.
3. **Recommended Implementation Strategy**:
   - Comic EPUBs are standard ZIP archives containing image assets (e.g. `/OEBPS/images/...` or referenced in XHTML pages).
   - Use `java.util.zip.ZipFile` or `org.apache.commons:commons-compress` combined with `org.jsoup:jsoup:1.18.1` (or Android's built-in `XmlPullParser`) to parse the OPF spine and stream comic page images directly.
   - **Benefits**:
     - No external custom repositories or JitPack required.
     - Zero duplicate class collisions.
     - Faster extraction, lower memory footprint, and full compatibility with Compose image streaming.

---

## 5. Windows-Specific Build & Test Considerations

### 5.1 `local.properties` Requirement
Because `ANDROID_HOME` is not globally defined in Windows environment variables, the Android Gradle build will fail unless `local.properties` is created at the project root:
```properties
sdk.dir=C:\\Users\\anand\\AppData\\Local\\Android\\Sdk
```

### 5.2 Line Endings (CRLF vs LF)
- Gradle wrapper scripts: `gradlew.bat` uses CRLF, while `gradlew` (Unix shell) uses LF. Ensure `gradlew` retains LF line endings so that cross-environment scripts or CI do not fail with `\r` carriage return syntax errors.

### 5.3 Windows Path Length Limitations
- Deep package directories combined with Gradle transform caches can approach Windows 260-character `MAX_PATH`.
- Keep the package structure clean (`com.comicreader`) and avoid excessively deep nested directories.

### 5.4 JVM & Memory Tuning (`gradle.properties`)
Recommended settings to prevent OOM errors or file locking on Windows:
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.daemon.jvmargs=-Xmx1024m
```

### 5.5 File Locks & Antivirus
- On Windows, file indexers or real-time scanning can lock `.lock` or `.jar` files in `build/` or `.gradle/`.
- If a locked file error occurs during clean/rebuild, stopping the Gradle daemon (`gradlew --stop`) cleanly frees file handles.

---

## 6. Actionable Recommendations for Implementation Plan

1. **Gradle Setup**:
   - Generate or supply Gradle 8.10.2 wrapper (`gradlew.bat`, `gradlew`, `gradle/wrapper/`).
   - Create `local.properties` with `sdk.dir=C:\\Users\\anand\\AppData\\Local\\Android\\Sdk`.
2. **Plugin Declarations**:
   - AGP: `8.7.3`
   - Kotlin: `2.0.21`
   - KSP: `2.0.21-1.0.28`
   - Compose Compiler: `org.jetbrains.kotlin.plugin.compose` version `2.0.21`
   - Hilt: `com.google.dagger.hilt.android` version `2.52`
3. **Switch from Kapt to KSP**:
   - Use `ksp("androidx.room:room-compiler:2.6.1")` and `ksp("com.google.dagger:hilt-compiler:2.52")`.
4. **Use Compose Foundation Pager**:
   - Replace `accompanist-pager` with built-in `androidx.compose.foundation.pager.HorizontalPager`.
5. **EPUB Parser Implementation**:
   - Parse EPUB container/OPF using `org.jsoup:jsoup:1.18.1` / built-in XML parser + ZIP image streaming instead of relying on broken `io.github.siegeindahouse:epublib-core` artifact coordinates.
