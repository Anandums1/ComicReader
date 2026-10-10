package com.comicreader.format

import android.graphics.Bitmap
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.ComicMetadata
import com.comicreader.domain.model.ComicPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CbzParser @Inject constructor() : ComicParser {

    override fun canParse(filePath: String): Boolean {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return ext == "cbz" || ext == "zip"
    }

    override fun extractMetadata(file: File): ComicMetadata {
        if (!file.exists() || file.length() == 0L) {
            return ComicMetadata(
                title = file.nameWithoutExtension,
                pageCount = 0,
                format = ComicFormat.CBZ,
                fileSize = 0L
            )
        }

        return try {
            openZip(file).use { zip ->
                val imageEntries = getImageEntries(zip)
                val comicInfoEntry = findComicInfoEntry(zip)

                if (comicInfoEntry != null) {
                    zip.getInputStream(comicInfoEntry).use { input ->
                        ComicInfoXmlParser.parse(
                            inputStream = input,
                            fallbackTitle = file.nameWithoutExtension,
                            format = ComicFormat.CBZ,
                            fileSize = file.length(),
                            fallbackPageCount = imageEntries.size
                        )
                    }
                } else {
                    ComicMetadata(
                        title = file.nameWithoutExtension,
                        pageCount = imageEntries.size,
                        format = ComicFormat.CBZ,
                        fileSize = file.length()
                    )
                }
            }
        } catch (_: Exception) {
            ComicMetadata(
                title = file.nameWithoutExtension,
                pageCount = 0,
                format = ComicFormat.CBZ,
                fileSize = file.length()
            )
        }
    }

    override fun extractCover(file: File, cacheDir: File): File? {
        if (!file.exists() || file.length() == 0L) return null

        return try {
            cacheDir.mkdirs()
            openZip(file).use { zip ->
                val imageEntries = getImageEntries(zip)
                val firstEntry = imageEntries.firstOrNull() ?: return null

                val ext = firstEntry.name.substringAfterLast('.', "jpg").lowercase()
                val coverFileName = "${file.nameWithoutExtension}_cover.$ext"
                val coverFile = File(cacheDir, coverFileName)

                zip.getInputStream(firstEntry).use { input ->
                    FileOutputStream(coverFile).use { output ->
                        input.copyTo(output)
                    }
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

        val pages = try {
            openZip(file).use { zip ->
                val imageEntries = getImageEntries(zip)
                imageEntries.mapIndexed { index, entry ->
                    ComicPage(
                        pageNumber = index,
                        imageUri = entry.name,
                        filePath = file.absolutePath
                    )
                }
            }
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

        // If page.filePath points directly to an extracted image file
        if (FormatUtils.isImageFile(path) && sourceFile.exists()) {
            return@withContext FormatUtils.decodeSampledBitmap(sourceFile, targetWidth, targetHeight)
        }

        if (!sourceFile.exists()) return@withContext null

        try {
            openZip(sourceFile).use { zip ->
                val entry = if (page.imageUri != null) {
                    zip.getEntry(page.imageUri) ?: getImageEntries(zip).getOrNull(page.pageNumber)
                } else {
                    getImageEntries(zip).getOrNull(page.pageNumber)
                } ?: return@use null

                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                FormatUtils.decodeSampledBitmap(bytes, targetWidth, targetHeight)
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun loadPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            openZip(file).use { zip ->
                val entry = getImageEntries(zip).getOrNull(pageIndex) ?: return@use null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                FormatUtils.decodeSampledBitmap(bytes, targetWidth, targetHeight)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openZip(file: File): ZipFile {
        return ZipFile.builder().setFile(file).get()
    }

    private fun getImageEntries(zip: ZipFile): List<ZipArchiveEntry> {
        val entries = mutableListOf<ZipArchiveEntry>()
        val en = zip.entries
        while (en.hasMoreElements()) {
            val entry = en.nextElement()
            if (!entry.isDirectory &&
                !FormatUtils.isIgnoredArtifact(entry.name) &&
                FormatUtils.isImageFile(entry.name)
            ) {
                entries.add(entry)
            }
        }
        return entries.sortedWith { e1, e2 ->
            NaturalOrderComparator.compare(e1.name, e2.name)
        }
    }

    private fun findComicInfoEntry(zip: ZipFile): ZipArchiveEntry? {
        val en = zip.entries
        while (en.hasMoreElements()) {
            val entry = en.nextElement()
            if (!entry.isDirectory) {
                val name = entry.name.substringAfterLast('/')
                if (name.equals("ComicInfo.xml", ignoreCase = true)) {
                    return entry
                }
            }
        }
        return null
    }
}
