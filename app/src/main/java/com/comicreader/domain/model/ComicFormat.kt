package com.comicreader.domain.model

enum class ComicFormat(val extension: String) {
    CBZ("cbz"),
    CBR("cbr"),
    PDF("pdf"),
    EPUB("epub");

    companion object {
        fun fromExtension(ext: String): ComicFormat? =
            entries.find { it.extension.equals(ext.trimStart('.'), ignoreCase = true) }

        fun fromFilePath(path: String): ComicFormat? {
            val extension = path.substringAfterLast('.', "")
            return fromExtension(extension)
        }
    }
}

