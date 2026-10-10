package com.comicreader.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val theme: AppTheme = AppTheme.DARK,
    val readingMode: ReadingMode = ReadingMode.HORIZONTAL,
    val pageTurnAnimation: PageAnimation = PageAnimation.SLIDE,
    val watchedDirectory: String = "/storage/emulated/0/Downloads/Comics",
    val keepScreenAwake: Boolean = true,
    val brightness: Float = -1.0f,
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val volumeKeyNav: Boolean = true
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_READING_MODE = stringPreferencesKey("reading_mode")
        val KEY_PAGE_TURN_ANIM = stringPreferencesKey("page_turn_anim")
        val KEY_WATCHED_DIR = stringPreferencesKey("watched_directory")
        val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val KEY_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_VOLUME_KEY_NAV = booleanPreferencesKey("volume_key_nav")

        const val DEFAULT_WATCHED_DIR = "/storage/emulated/0/Downloads/Comics"
    }

    val preferencesFlow: Flow<UserPreferences> = context.userDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val themeStr = prefs[KEY_THEME] ?: AppTheme.DARK.name
            val theme = runCatching { AppTheme.valueOf(themeStr) }.getOrDefault(AppTheme.DARK)

            val modeStr = prefs[KEY_READING_MODE] ?: ReadingMode.HORIZONTAL.name
            val readingMode = runCatching { ReadingMode.valueOf(modeStr) }.getOrDefault(ReadingMode.HORIZONTAL)

            val animStr = prefs[KEY_PAGE_TURN_ANIM] ?: PageAnimation.SLIDE.name
            val pageAnimation = runCatching { PageAnimation.valueOf(animStr) }.getOrDefault(PageAnimation.SLIDE)

            val watchedDir = prefs[KEY_WATCHED_DIR] ?: DEFAULT_WATCHED_DIR
            val keepAwake = prefs[KEY_KEEP_SCREEN_AWAKE] ?: true
            val brightness = prefs[KEY_BRIGHTNESS] ?: -1.0f

            val dirStr = prefs[KEY_READING_DIRECTION] ?: ReadingDirection.LTR.name
            val readingDirection = runCatching { ReadingDirection.valueOf(dirStr) }.getOrDefault(ReadingDirection.LTR)

            val volumeNav = prefs[KEY_VOLUME_KEY_NAV] ?: true

            UserPreferences(
                theme = theme,
                readingMode = readingMode,
                pageTurnAnimation = pageAnimation,
                watchedDirectory = watchedDir,
                keepScreenAwake = keepAwake,
                brightness = brightness,
                readingDirection = readingDirection,
                volumeKeyNav = volumeNav
            )
        }

    val themeFlow: Flow<AppTheme> = preferencesFlow.map { it.theme }.distinctUntilChanged()
    val readingModeFlow: Flow<ReadingMode> = preferencesFlow.map { it.readingMode }.distinctUntilChanged()
    val pageAnimationFlow: Flow<PageAnimation> = preferencesFlow.map { it.pageTurnAnimation }.distinctUntilChanged()
    val watchedDirectoryFlow: Flow<String> = preferencesFlow.map { it.watchedDirectory }.distinctUntilChanged()
    val keepScreenAwakeFlow: Flow<Boolean> = preferencesFlow.map { it.keepScreenAwake }.distinctUntilChanged()
    val brightnessLevelFlow: Flow<Float> = preferencesFlow.map { it.brightness }.distinctUntilChanged()
    val readingDirectionFlow: Flow<ReadingDirection> = preferencesFlow.map { it.readingDirection }.distinctUntilChanged()
    val volumeKeyNavFlow: Flow<Boolean> = preferencesFlow.map { it.volumeKeyNav }.distinctUntilChanged()

    suspend fun setTheme(theme: AppTheme) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.name
        }
    }

    suspend fun setReadingMode(mode: ReadingMode) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_READING_MODE] = mode.name
        }
    }

    suspend fun setPageAnimation(animation: PageAnimation) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_PAGE_TURN_ANIM] = animation.name
        }
    }

    suspend fun setWatchedDirectory(path: String) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_WATCHED_DIR] = path
        }
    }

    suspend fun setKeepScreenAwake(keepAwake: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_KEEP_SCREEN_AWAKE] = keepAwake
        }
    }

    suspend fun setBrightnessLevel(level: Float) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_BRIGHTNESS] = level
        }
    }

    suspend fun setBrightness(brightness: Float) = setBrightnessLevel(brightness)

    suspend fun setReadingDirection(direction: ReadingDirection) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_READING_DIRECTION] = direction.name
        }
    }

    suspend fun setVolumeKeyNav(enabled: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_VOLUME_KEY_NAV] = enabled
        }
    }
}

