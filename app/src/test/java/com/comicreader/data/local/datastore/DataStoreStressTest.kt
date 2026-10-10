package com.comicreader.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreStressTest {

    private lateinit var context: Context
    private lateinit var dataStore: UserPreferencesDataStore

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext<Context>()
            context.userDataStore.edit { it.clear() }
            dataStore = UserPreferencesDataStore(context)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            context.userDataStore.edit { it.clear() }
        }
    }

    @Test
    fun testRapidConcurrentMutationsAcrossDifferentKeys() = runBlocking(Dispatchers.IO) {
        val themes = AppTheme.values()
        val modes = ReadingMode.values()
        val anims = PageAnimation.values()
        val directions = ReadingDirection.values()

        // Launch 60 concurrent coroutines performing updates
        val jobs = (0 until 60).map { index ->
            async {
                when (index % 6) {
                    0 -> dataStore.setTheme(themes[index % themes.size])
                    1 -> dataStore.setReadingMode(modes[index % modes.size])
                    2 -> dataStore.setPageAnimation(anims[index % anims.size])
                    3 -> dataStore.setKeepScreenAwake(index % 2 == 0)
                    4 -> dataStore.setBrightnessLevel((index % 10) / 10f)
                    5 -> dataStore.setReadingDirection(directions[index % directions.size])
                }
            }
        }

        jobs.awaitAll()

        // DataStore should remain fully consistent and readable without corruption
        val finalPrefs = dataStore.preferencesFlow.first()
        assertThat(finalPrefs).isNotNull()
        assertThat(finalPrefs.theme).isAnyOf(AppTheme.DARK, AppTheme.LIGHT, AppTheme.ADAPTIVE, AppTheme.COMIC)
        assertThat(finalPrefs.readingMode).isAnyOf(ReadingMode.HORIZONTAL, ReadingMode.VERTICAL)
        assertThat(finalPrefs.pageTurnAnimation).isAnyOf(PageAnimation.NONE, PageAnimation.SLIDE, PageAnimation.CURL, PageAnimation.FADE)
        assertThat(finalPrefs.readingDirection).isAnyOf(ReadingDirection.LTR, ReadingDirection.RTL)
    }

    @Test
    fun testRapidConcurrentMutationsOnSameKey() = runBlocking(Dispatchers.IO) {
        val themes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.ADAPTIVE, AppTheme.COMIC)

        // 40 concurrent workers hammering setTheme
        val jobs = (0 until 40).map { index ->
            async {
                dataStore.setTheme(themes[index % themes.size])
            }
        }
        jobs.awaitAll()

        // Final targeted write should cleanly take precedence
        dataStore.setTheme(AppTheme.COMIC)
        val finalTheme = dataStore.themeFlow.first()
        assertThat(finalTheme).isEqualTo(AppTheme.COMIC)
    }

    @Test
    fun testHighFrequencyFlowUpdates() = runBlocking {
        for (i in 0 until 25) {
            val mode = if (i % 2 == 0) ReadingMode.HORIZONTAL else ReadingMode.VERTICAL
            dataStore.setReadingMode(mode)
        }

        dataStore.setReadingMode(ReadingMode.VERTICAL)
        val finalMode = dataStore.readingModeFlow.first()
        assertThat(finalMode).isEqualTo(ReadingMode.VERTICAL)
    }

    @Test
    fun testGracefulFallbackOnInvalidOrCorruptData() = runBlocking {
        // Direct write of malformed enum string values to preferences
        context.userDataStore.edit { prefs ->
            prefs[UserPreferencesDataStore.KEY_THEME] = "NON_EXISTENT_THEME_XYZ"
            prefs[UserPreferencesDataStore.KEY_READING_MODE] = "INVALID_READING_MODE"
            prefs[UserPreferencesDataStore.KEY_PAGE_TURN_ANIM] = "UNKNOWN_ANIMATION_MODE"
            prefs[UserPreferencesDataStore.KEY_READING_DIRECTION] = "UPSIDE_DOWN_DIRECTION"
        }

        // Verify preferencesFlow handles unknown enum names with safe defaults and does not crash
        val prefs = dataStore.preferencesFlow.first()
        assertThat(prefs.theme).isEqualTo(AppTheme.DARK)
        assertThat(prefs.readingMode).isEqualTo(ReadingMode.HORIZONTAL)
        assertThat(prefs.pageTurnAnimation).isEqualTo(PageAnimation.SLIDE)
        assertThat(prefs.readingDirection).isEqualTo(ReadingDirection.LTR)
    }

    @Test
    fun testBoundaryAndExtremeValues() = runBlocking {
        // Boundary float brightness
        dataStore.setBrightnessLevel(-1.0f)
        assertThat(dataStore.brightnessLevelFlow.first()).isEqualTo(-1.0f)

        dataStore.setBrightnessLevel(0.0f)
        assertThat(dataStore.brightnessLevelFlow.first()).isEqualTo(0.0f)

        dataStore.setBrightnessLevel(1.0f)
        assertThat(dataStore.brightnessLevelFlow.first()).isEqualTo(1.0f)

        // Empty watched directory
        dataStore.setWatchedDirectory("")
        assertThat(dataStore.watchedDirectoryFlow.first()).isEqualTo("")

        // Extremely long path string
        val longPath = "/storage/emulated/0/" + "nested/folder/path/".repeat(50) + "comics"
        dataStore.setWatchedDirectory(longPath)
        assertThat(dataStore.watchedDirectoryFlow.first()).isEqualTo(longPath)
    }
}
