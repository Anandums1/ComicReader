package com.comicreader.presentation.screens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comicreader.data.local.db.ComicDatabase
import com.comicreader.data.repository.ComicRepositoryImpl
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.presentation.screens.favorites.FavoritesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val directExecutor = Executor { it.run() }

    private lateinit var database: ComicDatabase
    private lateinit var comicRepository: ComicRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ComicDatabase::class.java
        )
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        comicRepository = ComicRepositoryImpl(database.comicDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun testFavoritesList_onlyShowsFavoritedComics() = runTest {
        val id1 = comicRepository.insertOrUpdateComic(Comic(title = "Fav 1", filePath = "/f1.cbz", format = ComicFormat.CBZ, isFavorite = true))
        comicRepository.insertOrUpdateComic(Comic(title = "Non Fav", filePath = "/nf.cbz", format = ComicFormat.CBZ, isFavorite = false))
        val id2 = comicRepository.insertOrUpdateComic(Comic(title = "Fav 2", filePath = "/f2.cbz", format = ComicFormat.CBZ, isFavorite = true))

        val viewModel = FavoritesViewModel(comicRepository)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.comics.size)

        // Toggle favorite off for one
        viewModel.toggleFavorite(id1, false)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.comics.size)
        assertEquals("Fav 2", viewModel.uiState.value.comics[0].title)
    }
}
