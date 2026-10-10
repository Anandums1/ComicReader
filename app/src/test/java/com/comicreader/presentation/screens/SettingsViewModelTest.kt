package com.comicreader.presentation.screens

import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.domain.model.AppTheme
import com.comicreader.domain.model.PageAnimation
import com.comicreader.domain.model.ReadingDirection
import com.comicreader.domain.model.ReadingMode
import com.comicreader.presentation.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import org.robolectric.shadows.ShadowLooper
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dataStore: UserPreferencesDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataStore = UserPreferencesDataStore(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUpdateTheme_persistsToDataStoreAndUpdatesState() = runTest {
        val viewModel = SettingsViewModel(dataStore)
        viewModel.uiState.first() // wait for initial emission
        ShadowLooper.idleMainLooper()

        viewModel.setTheme(AppTheme.COMIC)
        viewModel.uiState.first { it.theme == AppTheme.COMIC }
        ShadowLooper.idleMainLooper()

        assertEquals(AppTheme.COMIC, viewModel.uiState.value.theme)

        viewModel.setTheme(AppTheme.LIGHT)
        viewModel.uiState.first { it.theme == AppTheme.LIGHT }
        ShadowLooper.idleMainLooper()

        assertEquals(AppTheme.LIGHT, viewModel.uiState.value.theme)
    }

    @Test
    fun testUpdateReadingModeAndDirection_persistsState() = runTest {
        val viewModel = SettingsViewModel(dataStore)
        viewModel.uiState.first()
        ShadowLooper.idleMainLooper()

        viewModel.setReadingMode(ReadingMode.VERTICAL)
        viewModel.uiState.first { it.readingMode == ReadingMode.VERTICAL }
        ShadowLooper.idleMainLooper()
        assertEquals(ReadingMode.VERTICAL, viewModel.uiState.value.readingMode)

        viewModel.setReadingDirection(ReadingDirection.RTL)
        viewModel.uiState.first { it.readingDirection == ReadingDirection.RTL }
        ShadowLooper.idleMainLooper()
        assertEquals(ReadingDirection.RTL, viewModel.uiState.value.readingDirection)
    }

    @Test
    fun testUpdateVolumeKeyNavAndWatchedDir() = runTest {
        val viewModel = SettingsViewModel(dataStore)
        viewModel.uiState.first()
        ShadowLooper.idleMainLooper()

        viewModel.setVolumeKeyNav(false)
        viewModel.uiState.first { !it.volumeKeyNav }
        ShadowLooper.idleMainLooper()
        assertFalse(viewModel.uiState.value.volumeKeyNav)

        viewModel.setWatchedDirectory("/custom/comics/dir")
        viewModel.uiState.first { it.watchedDirectory == "/custom/comics/dir" }
        ShadowLooper.idleMainLooper()
        assertEquals("/custom/comics/dir", viewModel.uiState.value.watchedDirectory)
    }
}
