package com.comicreader.data.scanner

import android.os.Build
import android.os.FileObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

data class FileSystemEvent(
    val eventMask: Int,
    val file: File
)

class RecursiveFileObserver(
    private val rootDir: File,
    private val scope: CoroutineScope,
    private val mask: Int = DEFAULT_MASK
) {
    companion object {
        const val DEFAULT_MASK = FileObserver.CREATE or
                FileObserver.CLOSE_WRITE or
                FileObserver.DELETE or
                FileObserver.MOVED_TO or
                FileObserver.MOVED_FROM
    }

    private val observers = mutableMapOf<String, FileObserver>()
    private val _events = MutableSharedFlow<FileSystemEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<FileSystemEvent> = _events.asSharedFlow()

    init {
        registerRecursive(rootDir)
    }

    @Synchronized
    private fun registerRecursive(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        val path = dir.absolutePath
        if (observers.containsKey(path)) return

        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, pathName: String?) {
                    handleEvent(event, dir, pathName)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(path, mask) {
                override fun onEvent(event: Int, pathName: String?) {
                    handleEvent(event, dir, pathName)
                }
            }
        }

        observers[path] = observer
        try {
            observer.startWatching()
        } catch (_: Exception) {
            // Ignore observer start failures (e.g. permission or platform test limits)
        }

        dir.listFiles { file -> file.isDirectory }?.forEach { subDir ->
            registerRecursive(subDir)
        }
    }

    private fun handleEvent(event: Int, parentDir: File, childPath: String?) {
        val targetFile = if (childPath != null) File(parentDir, childPath) else parentDir

        // If a new directory was created or moved in, watch it recursively
        if ((event and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0 && targetFile.isDirectory) {
            registerRecursive(targetFile)
        }

        scope.launch {
            _events.emit(FileSystemEvent(event, targetFile))
        }
    }

    @Synchronized
    fun stopWatching() {
        observers.values.forEach { observer ->
            try {
                observer.stopWatching()
            } catch (_: Exception) {
            }
        }
        observers.clear()
    }
}
