package com.comicreader.format

import android.graphics.Bitmap
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.ComicMetadata
import com.comicreader.domain.model.ComicPage
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CbrParser @Inject constructor() : ComicParser {

    private val extractionMutex = Mutex()

    override fun canParse(filePath: String): Boolean {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return ext == "cbr" || ext == "rar"
    }

    /**
     * Checks if the file contains the RAR5 magic signature (0x52 0x61 0x72 0x21 0x1A 0x07 0x01 0x00).
     */
    fun isRar5(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                read == 8 &&
                        header[0] == 0x52.toByte() &&
                        header[1] == 0x61.toByte() &&
                        header[2] == 0x72.toByte() &&
                        header[3] == 0x21.toByte() &&
                        header[4] == 0x1A.toByte() &&
                        header[5] == 0x07.toByte() &&
                        header[6] == 0x01.toByte() &&
                        header[7] == 0x00.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun extractMetadata(file: File): ComicMetadata {
        if (!file.exists() || file.length() == 0L) {
            return ComicMetadata(
                title = file.nameWithoutExtension,
                pageCount = 0,
                format = ComicFormat.CBR,
                fileSize = 0L
            )
        }

        if (isRar5(file)) {
            throw UnsupportedRarV5Exception("RAR5 archives are not supported by Junrar. Please re-compress as CBZ or RAR4.")
        }

        return try {
            Archive(file).use { archive ->
                val imageHeaders = getImageHeaders(archive)
                val comicInfo = findComicInfo(archive)

                if (comicInfo != null) {
                    ComicInfoXmlParser.parse(
                        inputStream = comicInfo.inputStream(),
                        fallbackTitle = file.nameWithoutExtension,
                        format = ComicFormat.CBR,
                        fileSize = file.length(),
                        fallbackPageCount = imageHeaders.size
                    )
                } else {
                    ComicMetadata(
                        title = file.nameWithoutExtension,
                        pageCount = imageHeaders.size,
                        format = ComicFormat.CBR,
                        fileSize = file.length()
                    )
                }
            }
        } catch (e: UnsupportedRarV5Exception) {
            throw e
        } catch (_: Exception) {
            ComicMetadata(
                title = file.nameWithoutExtension,
                pageCount = 0,
                format = ComicFormat.CBR,
                fileSize = file.length()
            )
        }
    }

    override fun extractCover(file: File, cacheDir: File): File? {
        if (!file.exists() || file.length() == 0L || isRar5(file)) return null

        return try {
            cacheDir.mkdirs()
            Archive(file).use { archive ->
                val imageHeaders = getImageHeaders(archive)
                val firstHeader = imageHeaders.firstOrNull() ?: return null

                val normalizedName = normalizeHeaderName(firstHeader)
                val ext = normalizedName.substringAfterLast('.', "jpg").lowercase()
                val coverFile = File(cacheDir, "${file.nameWithoutExtension}_cover.$ext")

                FileOutputStream(coverFile).use { output ->
                    archive.extractFile(firstHeader, output)
                }

                if (coverFile.exists() && coverFile.length() > 0L) coverFile else null
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

        if (isRar5(file)) {
            throw UnsupportedRarV5Exception("RAR5 archives are not supported by Junrar. Please re-compress as CBZ or RAR4.")
        }

        val pages = try {
            Archive(file).use { archive ->
                val imageHeaders = getImageHeaders(archive)
                imageHeaders.mapIndexed { index, header ->
                    ComicPage(
                        pageNumber = index,
                        imageUri = normalizeHeaderName(header),
                        filePath = file.absolutePath
                    )
                }
            }
        } catch (e: UnsupportedRarV5Exception) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
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

        if (FormatUtils.isImageFile(path) && sourceFile.exists()) {
            return@withContext FormatUtils.decodeSampledBitmap(sourceFile, targetWidth, targetHeight)
        }

        if (!sourceFile.exists() || isRar5(sourceFile)) return@withContext null

        extractionMutex.withLock {
            try {
                Archive(sourceFile).use { archive ->
                    val imageHeaders = getImageHeaders(archive)
                    val header = if (page.imageUri != null) {
                        imageHeaders.find { normalizeHeaderName(it) == page.imageUri }
                            ?: imageHeaders.getOrNull(page.pageNumber)
                    } else {
                        imageHeaders.getOrNull(page.pageNumber)
                    } ?: return@use null

                    val baos = ByteArrayOutputStream()
                    archive.extractFile(header, baos)
                    val bytes = baos.toByteArray()
                    FormatUtils.decodeSampledBitmap(bytes, targetWidth, targetHeight)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun loadPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists() || isRar5(file)) return@withContext null
        extractionMutex.withLock {
            try {
                Archive(file).use { archive ->
                    val header = getImageHeaders(archive).getOrNull(pageIndex) ?: return@use null
                    val baos = ByteArrayOutputStream()
                    archive.extractFile(header, baos)
                    val bytes = baos.toByteArray()
                    FormatUtils.decodeSampledBitmap(bytes, targetWidth, targetHeight)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun normalizeHeaderName(header: FileHeader): String {
        return header.fileName.replace('\\', '/')
    }

    private fun getImageHeaders(archive: Archive): List<FileHeader> {
        val headers = mutableListOf<FileHeader>()
        for (header in archive.fileHeaders) {
            val name = normalizeHeaderName(header)
            if (!header.isDirectory &&
                !FormatUtils.isIgnoredArtifact(name) &&
                FormatUtils.isImageFile(name)
            ) {
                headers.add(header)
            }
        }
        return headers.sortedWith { h1, h2 ->
            NaturalOrderComparator.compare(normalizeHeaderName(h1), normalizeHeaderName(h2))
        }
    }

    private fun findComicInfo(archive: Archive): ByteArray? {
        for (header in archive.fileHeaders) {
            if (!header.isDirectory) {
                val name = normalizeHeaderName(header).substringAfterLast('/')
                if (name.equals("ComicInfo.xml", ignoreCase = true)) {
                    val baos = ByteArrayOutputStream()
                    archive.extractFile(header, baos)
                    return baos.toByteArray()
                }
            }
        }
        return null
    }
}
