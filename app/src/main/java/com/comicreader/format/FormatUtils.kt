package com.comicreader.format

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream

object FormatUtils {

    val SUPPORTED_IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "avif"
    )

    fun isImageFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_IMAGE_EXTENSIONS
    }

    fun isIgnoredArtifact(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        return normalized.startsWith("__MACOSX/") ||
                normalized.contains("/__MACOSX/") ||
                name.startsWith("._") ||
                name.equals(".DS_Store", ignoreCase = true) ||
                name.equals("Thumbs.db", ignoreCase = true) ||
                name.startsWith(".")
    }

    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (reqWidth > 0 && reqHeight > 0) {
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun decodeSampledBitmap(
        bytes: ByteArray,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            if (targetWidth > 0 && targetHeight > 0) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeSampledBitmap(
        file: File,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            if (targetWidth > 0 && targetHeight > 0) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
