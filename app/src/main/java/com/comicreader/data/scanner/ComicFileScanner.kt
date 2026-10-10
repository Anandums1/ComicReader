package com.comicreader.data.scanner

import android.content.Context
import com.comicreader.data.local.db.dao.ComicDao
import com.comicreader.data.local.db.entity.ComicEntity
import com.comicreader.domain.model.ComicFormat
import com.comicreader.format.ComicParserFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ComicFileScanner {
    /**
     * Recursively scans directories for supported comics (.cbz, .cbr, .pdf, .epub).
     * Emits progress events and syncs with the database.
     */
    suspend fun scanAll(directories: List<File>): Flow<ScanProgress>

    /**
     * Convenience method to scan a single directory.
     */
    suspend fun scanDirectory(directory: File): Flow<ScanProgress>

    /**
     * Scans a single comic file, extracts cover/metadata, and persists to Room.
     * Skips unmodified files.
     */
    suspend fun scanSingleFile(file: File): ComicEntity?

    /**
     * Reconciles library by removing database entries whose files no longer exist on disk.
     * Returns the count of removed records.
     */
    suspend fun reconcileLibrary(): Int
}

sealed class ScanProgress {
    data class Started(val totalEstimatedFiles: Int) : ScanProgress()
    data class Processing(val currentFile: String, val processedCount: Int, val total: Int) : ScanProgress()
    data class FileIndexed(val comic: ComicEntity) : ScanProgress()
    data class Finished(val addedCount: Int, val updatedCount: Int, val removedCount: Int) : ScanProgress()
    data class Failed(val error: Throwable) : ScanProgress()
}

@Singleton
class ComicFileScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val comicDao: ComicDao,
    private val parserFactory: ComicParserFactory
) : ComicFileScanner {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun scanDirectory(directory: File): Flow<ScanProgress> {
        return scanAll(listOf(directory))
    }

    override suspend fun scanAll(directories: List<File>): Flow<ScanProgress> = flow {
        try {
            val supportedFiles = mutableListOf<File>()
            for (dir in directories) {
                collectSupportedFiles(dir, supportedFiles)
            }

            emit(ScanProgress.Started(supportedFiles.size))

            var addedCount = 0
            var updatedCount = 0

            for ((index, file) in supportedFiles.withIndex()) {
                emit(ScanProgress.Processing(file.name, index + 1, supportedFiles.size))

                val existing = comicDao.getComicByPath(file.absolutePath)
                val isNew = (existing == null)

                val entity = scanSingleFile(file)
                if (entity != null) {
                    if (isNew) {
                        addedCount++
                    } else if (existing.fileSizeBytes != file.length()) {
                        updatedCount++
                    }
                    emit(ScanProgress.FileIndexed(entity))
                }
            }

            val removedCount = reconcileLibrary()
            emit(ScanProgress.Finished(addedCount, updatedCount, removedCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(ScanProgress.Failed(e))
        }
    }.flowOn(ioDispatcher)

    override suspend fun scanSingleFile(file: File): ComicEntity? = withContext(ioDispatcher) {
        if (!file.exists() || !file.isFile || !parserFactory.isSupported(file)) {
            return@withContext null
        }

        val existing = comicDao.getComicByPath(file.absolutePath)

        // Skip unmodified file if size hasn't changed and cover exists
        if (existing != null &&
            existing.fileSizeBytes == file.length() &&
            existing.pageCount > 0 &&
            existing.coverPath != null &&
            File(existing.coverPath).exists()
        ) {
            return@withContext existing
        }

        val parser = parserFactory.getParser(file) ?: return@withContext null
        val metadata = try {
            parser.extractMetadata(file)
        } catch (_: Exception) {
            null
        }

        val coversDir = File(context.cacheDir, "covers").apply { mkdirs() }
        val coverFile = try {
            parser.extractCover(file, coversDir)
        } catch (_: Exception) {
            null
        }

        val format = ComicFormat.fromFilePath(file.absolutePath)?.extension
            ?: file.extension.lowercase()

        val entity = ComicEntity(
            id = existing?.id ?: 0L,
            title = metadata?.title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            filePath = file.absolutePath,
            format = format,
            coverPath = coverFile?.absolutePath ?: existing?.coverPath,
            pageCount = metadata?.pageCount ?: existing?.pageCount ?: 0,
            currentPage = existing?.currentPage ?: 0,
            lastReadTimestamp = existing?.lastReadTimestamp ?: 0L,
            isFavorite = existing?.isFavorite ?: false,
            fileSizeBytes = file.length(),
            addedTimestamp = existing?.addedTimestamp ?: System.currentTimeMillis()
        )

        val id = comicDao.insertComic(entity)
        val savedEntity = if (entity.id == 0L) entity.copy(id = id) else entity
        savedEntity
    }

    override suspend fun reconcileLibrary(): Int = withContext(ioDispatcher) {
        var removedCount = 0
        try {
            val allComics = comicDao.getAllComics().first()
            for (comic in allComics) {
                val file = File(comic.filePath)
                if (!file.exists()) {
                    comicDao.deleteComicById(comic.id)
                    comic.coverPath?.let { coverPath ->
                        val coverFile = File(coverPath)
                        if (coverFile.exists()) {
                            coverFile.delete()
                        }
                    }
                    removedCount++
                }
            }
        } catch (_: Exception) {
            // Reconcile safe failure
        }
        removedCount
    }

    private fun collectSupportedFiles(dir: File, result: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectSupportedFiles(child, result)
            } else if (child.isFile && parserFactory.isSupported(child)) {
                result.add(child)
            }
        }
    }
}
