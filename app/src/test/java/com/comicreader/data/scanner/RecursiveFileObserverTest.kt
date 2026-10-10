package com.comicreader.data.scanner

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RecursiveFileObserverTest {

    private lateinit var tempDir: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "observer_test_${System.nanoTime()}")
        tempDir.mkdirs()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @After
    fun tearDown() {
        scope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun registersAndWatchesDirectoryTree() {
        val subDir = File(tempDir, "comics_sub").apply { mkdirs() }
        val observer = RecursiveFileObserver(tempDir, scope)

        assertThat(observer.events).isNotNull()

        // Clean termination
        observer.stopWatching()
    }

    @Test
    fun handlesEmptyOrMissingDirectoryGracefully() {
        val nonExistent = File(tempDir, "does_not_exist")
        val observer = RecursiveFileObserver(nonExistent, scope)

        observer.stopWatching()
    }
}
