package com.comicreader.data.local.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesDataStoreTest {

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
    fun defaultPreferencesValues() = runBlocking {
        val prefs = dataStore.preferencesFlow.first()
        assertThat(prefs.theme).isEqualTo(AppTheme.DARK)
        assertThat(prefs.readingMode).isEqualTo(ReadingMode.HORIZONTAL)
        assertThat(prefs.pageTurnAnimation).isEqualTo(PageAnimation.SLIDE)
        assertThat(prefs.keepScreenAwake).isTrue()
        assertThat(prefs.brightness).isEqualTo(-1.0f)
        assertThat(prefs.readingDirection).isEqualTo(ReadingDirection.LTR)
        assertThat(prefs.volumeKeyNav).isTrue()
    }

    @Test
    fun updateThemeAndReadingMode() = runBlocking {
        dataStore.setTheme(AppTheme.COMIC)
        val theme = dataStore.themeFlow.first()
        assertThat(theme).isEqualTo(AppTheme.COMIC)

        dataStore.setReadingMode(ReadingMode.VERTICAL)
        val mode = dataStore.readingModeFlow.first()
        assertThat(mode).isEqualTo(ReadingMode.VERTICAL)

        dataStore.setPageAnimation(PageAnimation.CURL)
        val anim = dataStore.pageAnimationFlow.first()
        assertThat(anim).isEqualTo(PageAnimation.CURL)

        dataStore.setBrightness(0.75f)
        val brightness = dataStore.brightnessLevelFlow.first()
        assertThat(brightness).isEqualTo(0.75f)

        dataStore.setKeepScreenAwake(false)
        val awake = dataStore.keepScreenAwakeFlow.first()
        assertThat(awake).isFalse()

        dataStore.setReadingDirection(ReadingDirection.RTL)
        val dir = dataStore.readingDirectionFlow.first()
        assertThat(dir).isEqualTo(ReadingDirection.RTL)

        dataStore.setVolumeKeyNav(false)
        val vol = dataStore.volumeKeyNavFlow.first()
        assertThat(vol).isFalse()
    }
}

