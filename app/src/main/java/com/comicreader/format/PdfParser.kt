package com.comicreader.format

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.ComicMetadata
import com.comicreader.domain.model.ComicPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfParser @Inject constructor() : ComicParser {

    private val pdfMutex = Mutex()

    companion object {
        const val MAX_TEXTURE_SIZE = 4096
    }

    override fun canParse(filePath: String): Boolean {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return ext == "pdf"
    }

    override fun extractMetadata(file: File): ComicMetadata {
        if (!file.exists() || file.length() == 0L) {
            return ComicMetadata(
                title = file.nameWithoutExtension,
                pageCount = 0,
                format = ComicFormat.PDF,
                fileSize = 0L
            )
        }

        var pageCount = 0
        var title: String? = null

        // Attempt extraction via PdfRenderer
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                }
            }
        } catch (_: Throwable) {
            // Fallback to direct PDF stream parsing below
        }

        // Direct stream parsing fallback for metadata & page count
        try {
            val maxReadBytes = 256 * 1024L
            val content = if (file.length() <= maxReadBytes) {
                file.readText(StandardCharsets.ISO_8859_1)
            } else {
                val buffer = java.lang.StringBuilder(256 * 1024)
                file.inputStream().use { input ->
                    val headBytes = ByteArray(128 * 1024)
                    val readHead = input.read(headBytes)
                    if (readHead > 0) {
                        buffer.append(String(headBytes, 0, readHead, StandardCharsets.ISO_8859_1))
                    }
                }
                RandomAccessFile(file, "r").use { raf ->
                    val tailSize = (128 * 1024).toLong().coerceAtMost(file.length())
                    raf.seek(file.length() - tailSize)
                    val tailBytes = ByteArray(tailSize.toInt())
                    val readTail = raf.read(tailBytes)
                    if (readTail > 0) {
                        buffer.append("\n")
                        buffer.append(String(tailBytes, 0, readTail, StandardCharsets.ISO_8859_1))
                    }
                }
                buffer.toString()
            }

            // Extract Title from Info dictionary: /Title (My Comic)
            if (title == null) {
                val titleRegex = Regex("""/Title\s*\(([^)]+)\)""")
                val match = titleRegex.find(content)
                if (match != null) {
                    title = match.groupValues[1]
                }
            }

            // Extract page count if PdfRenderer was not available
            if (pageCount == 0) {
                val countRegex = Regex("""/Type\s*/Pages\b[^>]*?/Count\s+(\d+)""")
                val countMatch = countRegex.find(content)
                if (countMatch != null) {
                    pageCount = countMatch.groupValues[1].toIntOrNull() ?: 0
                }
                if (pageCount == 0) {
                    // Count occurrences of /Type /Page
                    val pageRegex = Regex("""/Type\s*/Page\b""")
                    pageCount = pageRegex.findAll(content).count()
                }
            }
        } catch (_: Throwable) {
            // Ignore stream read errors and protect against OutOfMemoryError
        }

        return ComicMetadata(
            title = title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            pageCount = pageCount,
            format = ComicFormat.PDF,
            fileSize = file.length()
        )
    }

    override fun extractCover(file: File, cacheDir: File): File? {
        if (!file.exists() || file.length() == 0L) return null

        return try {
            cacheDir.mkdirs()
            val coverFile = File(cacheDir, "${file.nameWithoutExtension}_cover.png")

            var rendered = false
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount > 0) {
                            val page = renderer.openPage(0)
                            try {
                                val w = page.width.coerceIn(1, 1080)
                                val h = page.height.coerceIn(1, 1920)
                                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                FileOutputStream(coverFile).use { fos ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                                }
                                rendered = true
                            } finally {
                                page.close()
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                // Ignore native renderer errors
            }

            if (rendered && coverFile.exists() && coverFile.length() > 0L) {
                coverFile
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun getPages(file: File): Flow<List<ComicPage>> = flow {
        if (!file.exists() || file.length() == 0L) {
            emit(emptyList())
            return@flow
        }

        val metadata = extractMetadata(file)
        val pages = (0 until metadata.pageCount).map { index ->
            ComicPage(
                pageNumber = index,
                imageUri = "pdf://${file.absolutePath}#$index",
                filePath = file.absolutePath
            )
        }
        emit(pages)
    }.flowOn(Dispatchers.IO)

    override suspend fun loadPageBitmap(
        page: ComicPage,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val path = page.filePath ?: return@withContext null
        val sourceFile = File(path)
        if (!sourceFile.exists()) return@withContext null

        loadPageBitmap(sourceFile, page.pageNumber, targetWidth, targetHeight)
    }

    override suspend fun loadPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        pdfMutex.withLock {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@use null

                        val page = renderer.openPage(pageIndex)
                        try {
                            var width = page.width
                            var height = page.height

                            // Scale up for high density if target specified, but clamp to MAX_TEXTURE_SIZE
                            if (targetWidth > 0 && targetHeight > 0) {
                                val scale = minOf(
                                    targetWidth.toFloat() / width.toFloat(),
                                    targetHeight.toFloat() / height.toFloat()
                                ).coerceAtLeast(1.0f)
                                width = (width * scale).toInt()
                                height = (height * scale).toInt()
                            }

                            // Clamp dimensions to max texture size to prevent GL crashes and OOM
                            width = width.coerceIn(1, MAX_TEXTURE_SIZE)
                            height = height.coerceIn(1, MAX_TEXTURE_SIZE)

                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        } finally {
                            page.close()
                        }
                    }
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
