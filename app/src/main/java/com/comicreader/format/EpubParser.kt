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
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubParser @Inject constructor() : ComicParser {

    data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String
    )

    override fun canParse(filePath: String): Boolean {
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return ext == "epub"
    }

    override fun extractMetadata(file: File): ComicMetadata {
        if (!file.exists() || file.length() == 0L) {
            return ComicMetadata(
                title = file.nameWithoutExtension,
                pageCount = 0,
                format = ComicFormat.EPUB,
                fileSize = 0L
            )
        }

        return try {
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip)
                    ?: throw InvalidEpubException("Missing META-INF/container.xml or rootfile in EPUB")

                val opfEntry = zip.getEntry(opfPath)
                    ?: throw InvalidEpubException("OPF file not found at $opfPath")

                val opfContent = zip.getInputStream(opfEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val opfDir = opfPath.substringBeforeLast('/', "")

                val doc = Jsoup.parse(opfContent, "", Parser.xmlParser())

                val title = doc.select("metadata > dc|title, metadata > title").firstOrNull()?.text()
                    ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                val creator = doc.select("metadata > dc|creator, metadata > creator").firstOrNull()?.text()
                    ?.takeIf { it.isNotBlank() }

                val pages = resolveImagePages(zip, doc, opfDir)

                ComicMetadata(
                    title = title,
                    writer = creator,
                    pageCount = pages.size,
                    format = ComicFormat.EPUB,
                    fileSize = file.length()
                )
            }
        } catch (e: InvalidEpubException) {
            throw e
        } catch (_: Exception) {
            ComicMetadata(
                title = file.nameWithoutExtension,
                pageCount = 0,
                format = ComicFormat.EPUB,
                fileSize = file.length()
            )
        }
    }

    override fun extractCover(file: File, cacheDir: File): File? {
        if (!file.exists() || file.length() == 0L) return null

        return try {
            cacheDir.mkdirs()
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfContent = zip.getInputStream(opfEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val opfDir = opfPath.substringBeforeLast('/', "")
                val doc = Jsoup.parse(opfContent, "", Parser.xmlParser())

                // Check meta cover tag: <meta name="cover" content="cover-image-id"/>
                val coverId = doc.select("metadata > meta[name=cover]").attr("content")
                var coverZipPath: String? = null

                if (coverId.isNotBlank()) {
                    val coverItem = doc.select("manifest > item[id=$coverId]").firstOrNull()
                    if (coverItem != null) {
                        coverZipPath = resolvePath(opfDir, coverItem.attr("href"))
                    }
                }

                // If not found via meta cover, find first manifest item with properties="cover-image"
                if (coverZipPath == null) {
                    val coverItem = doc.select("manifest > item[properties*=cover-image]").firstOrNull()
                    if (coverItem != null) {
                        coverZipPath = resolvePath(opfDir, coverItem.attr("href"))
                    }
                }

                // Fallback to first page image
                if (coverZipPath == null) {
                    val pages = resolveImagePages(zip, doc, opfDir)
                    coverZipPath = pages.firstOrNull()
                }

                if (coverZipPath == null) return null

                val entry = zip.getEntry(coverZipPath) ?: return null
                val ext = entry.name.substringAfterLast('.', "jpg").lowercase()
                val coverFile = File(cacheDir, "${file.nameWithoutExtension}_cover.$ext")

                zip.getInputStream(entry).use { input ->
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
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip)
                    ?: throw InvalidEpubException("Missing META-INF/container.xml in EPUB")
                val opfEntry = zip.getEntry(opfPath)
                    ?: throw InvalidEpubException("OPF file not found at $opfPath")
                val opfContent = zip.getInputStream(opfEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val opfDir = opfPath.substringBeforeLast('/', "")
                val doc = Jsoup.parse(opfContent, "", Parser.xmlParser())

                val imagePaths = resolveImagePages(zip, doc, opfDir)
                imagePaths.mapIndexed { index, path ->
                    ComicPage(
                        pageNumber = index,
                        imageUri = path,
                        filePath = file.absolutePath
                    )
                }
            }
        } catch (e: InvalidEpubException) {
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

        if (!sourceFile.exists()) return@withContext null

        try {
            ZipFile(sourceFile).use { zip ->
                val entryPath = page.imageUri ?: return@use null
                val entry = zip.getEntry(entryPath) ?: return@use null
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
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return@use null
                val opfEntry = zip.getEntry(opfPath) ?: return@use null
                val opfContent = zip.getInputStream(opfEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val opfDir = opfPath.substringBeforeLast('/', "")
                val doc = Jsoup.parse(opfContent, "", Parser.xmlParser())

                val pages = resolveImagePages(zip, doc, opfDir)
                val entryPath = pages.getOrNull(pageIndex) ?: return@use null
                val entry = zip.getEntry(entryPath) ?: return@use null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                FormatUtils.decodeSampledBitmap(bytes, targetWidth, targetHeight)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        return try {
            val content = zip.getInputStream(containerEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val doc = Jsoup.parse(content, "", Parser.xmlParser())
            val rootfile = doc.select("rootfiles > rootfile").firstOrNull()
            rootfile?.attr("full-path")
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveImagePages(zip: ZipFile, opfDoc: org.jsoup.nodes.Document, opfDir: String): List<String> {
        val manifestMap = mutableMapOf<String, ManifestItem>()
        opfDoc.select("manifest > item").forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            val mediaType = item.attr("media-type")
            manifestMap[id] = ManifestItem(id, href, mediaType)
        }

        val spineItemrefs = opfDoc.select("spine > itemref").map { it.attr("idref") }
        val resolvedPages = mutableListOf<String>()

        for (idref in spineItemrefs) {
            val item = manifestMap[idref] ?: continue

            if (item.mediaType.startsWith("image/")) {
                // Direct image in spine
                val fullPath = resolvePath(opfDir, item.href)
                if (zip.getEntry(fullPath) != null) {
                    resolvedPages.add(fullPath)
                }
            } else if (item.mediaType == "application/xhtml+xml" || item.mediaType == "text/html") {
                // Fixed-layout XHTML page
                val xhtmlZipPath = resolvePath(opfDir, item.href)
                val xhtmlEntry = zip.getEntry(xhtmlZipPath) ?: continue
                val xhtmlDir = xhtmlZipPath.substringBeforeLast('/', "")

                try {
                    val xhtmlContent = zip.getInputStream(xhtmlEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    val xhtmlDoc = Jsoup.parse(xhtmlContent, "", Parser.xmlParser())

                    // Find <img> tags
                    val imgTags = xhtmlDoc.select("img[src]")
                    for (img in imgTags) {
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            val resolvedImgPath = resolvePath(xhtmlDir, src)
                            if (zip.getEntry(resolvedImgPath) != null) {
                                resolvedPages.add(resolvedImgPath)
                            }
                        }
                    }

                    // Find SVG <image> tags: <image xlink:href="..."> or <image href="...">
                    val svgImages = xhtmlDoc.select("image")
                    for (svgImg in svgImages) {
                        val href = when {
                            svgImg.hasAttr("xlink:href") -> svgImg.attr("xlink:href")
                            svgImg.hasAttr("href") -> svgImg.attr("href")
                            else -> ""
                        }
                        if (href.isNotBlank()) {
                            val resolvedImgPath = resolvePath(xhtmlDir, href)
                            if (zip.getEntry(resolvedImgPath) != null && !resolvedPages.contains(resolvedImgPath)) {
                                resolvedPages.add(resolvedImgPath)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Skip corrupt xhtml
                }
            }
        }

        // Fallback: If spine yielded no images, inspect manifest directly for image items
        if (resolvedPages.isEmpty()) {
            val manifestImages = manifestMap.values
                .filter { it.mediaType.startsWith("image/") }
                .map { resolvePath(opfDir, it.href) }
                .filter { zip.getEntry(it) != null }
                .sortedWith { a, b -> NaturalOrderComparator.compare(a, b) }
            resolvedPages.addAll(manifestImages)
        }

        return resolvedPages
    }

    fun resolvePath(baseDir: String, relativePath: String): String {
        val cleanRel = relativePath.replace('\\', '/')
        if (cleanRel.startsWith("/")) {
            return cleanRel.removePrefix("/")
        }
        val combined = if (baseDir.isBlank() || baseDir == ".") cleanRel else "$baseDir/$cleanRel"
        val parts = combined.split('/')
        val resolved = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(part)
            }
        }
        return resolved.joinToString("/")
    }
}
