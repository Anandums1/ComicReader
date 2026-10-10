package com.comicreader.format

import android.graphics.Bitmap
import com.comicreader.domain.model.ComicMetadata
import com.comicreader.domain.model.ComicPage
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ComicParser {
    /**
     * Returns true if this parser can handle the file at the given path.
     */
    fun canParse(filePath: String): Boolean

    /**
     * Convenience method to check support for a [File].
     */
    fun canParse(file: File): Boolean = canParse(file.absolutePath)

    /**
     * Extracts metadata (title, series, page count, format, etc.) from the file.
     * Parses ComicInfo.xml if present in CBZ/CBR/EPUB, or derives metadata from the file.
     */
    fun extractMetadata(file: File): ComicMetadata

    /**
     * Extracts the front cover image to [cacheDir].
     * Returns the cached cover file or null if extraction failed.
     */
    fun extractCover(file: File, cacheDir: File): File?

    /**
     * Returns a reactive Flow emitting the sorted list of pages for the comic.
     */
    fun getPages(file: File): Flow<List<ComicPage>>

    /**
     * Safely renders or decodes the page into a Bitmap.
     * Optional targetWidth and targetHeight can be used for subsampling.
     */
    suspend fun loadPageBitmap(
        page: ComicPage,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap?

    /**
     * Overload to load a page bitmap by file and 0-based page index.
     */
    suspend fun loadPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? = null
}
