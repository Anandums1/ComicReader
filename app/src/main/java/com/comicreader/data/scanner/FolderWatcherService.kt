package com.comicreader.data.scanner

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.FileObserver
import android.os.IBinder
import com.comicreader.data.local.datastore.UserPreferencesDataStore
import com.comicreader.format.ComicParserFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class)
@AndroidEntryPoint
class FolderWatcherService : Service() {

    @Inject
    lateinit var scanner: ComicFileScanner

    @Inject
    lateinit var parserFactory: ComicParserFactory

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var currentObserver: RecursiveFileObserver? = null
    private var preferenceJob: Job? = null
    private var eventJob: Job? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): FolderWatcherService = this@FolderWatcherService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startPreferenceObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startPreferenceObserver() {
        preferenceJob?.cancel()
        preferenceJob = userPreferencesDataStore.watchedDirectoryFlow
            .onEach { dirPath ->
                watchDirectory(File(dirPath))
            }
            .launchIn(serviceScope)
    }

    @Synchronized
    fun watchDirectory(directory: File) {
        currentObserver?.stopWatching()
        eventJob?.cancel()

        if (!directory.exists() || !directory.isDirectory) return

        val observer = RecursiveFileObserver(directory, serviceScope)
        currentObserver = observer

        // Observe write completion and file moves with 1500ms debounce
        eventJob = observer.events
            .filter { event ->
                val mask = event.eventMask
                val isWriteOrMove = (mask and (FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO)) != 0
                val isDelete = (mask and (FileObserver.DELETE or FileObserver.MOVED_FROM)) != 0
                isWriteOrMove || isDelete
            }
            .debounce(1500L)
            .onEach { event ->
                val mask = event.eventMask
                if ((mask and (FileObserver.DELETE or FileObserver.MOVED_FROM)) != 0) {
                    scanner.reconcileLibrary()
                } else if (parserFactory.isSupported(event.file)) {
                    scanner.scanSingleFile(event.file)
                }
            }
            .launchIn(serviceScope)

        // Perform initial directory scan
        serviceScope.launch {
            scanner.scanDirectory(directory).collect { }
        }
    }

    @Synchronized
    fun stopWatching() {
        currentObserver?.stopWatching()
        currentObserver = null
        eventJob?.cancel()
        eventJob = null
    }

    fun isWatching(): Boolean = currentObserver != null

    override fun onDestroy() {
        stopWatching()
        preferenceJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
