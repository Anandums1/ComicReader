package com.comicreader.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat

@Entity(
    tableName = "comics",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["format"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastReadTimestamp"]),
        Index(value = ["addedTimestamp"])
    ]
)
data class ComicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: String,
    val coverPath: String? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val lastReadTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): Comic {
        return Comic(
            id = id,
            title = title,
            filePath = filePath,
            format = ComicFormat.fromExtension(format) ?: ComicFormat.CBZ,
            coverPath = coverPath,
            pageCount = pageCount,
            currentPage = currentPage,
            lastReadTimestamp = lastReadTimestamp,
            isFavorite = isFavorite,
            fileSizeBytes = fileSizeBytes,
            addedTimestamp = addedTimestamp
        )
    }

    companion object {
        fun fromDomain(comic: Comic): ComicEntity {
            return ComicEntity(
                id = comic.id,
                title = comic.title,
                filePath = comic.filePath,
                format = comic.format.extension,
                coverPath = comic.coverPath,
                pageCount = comic.pageCount,
                currentPage = comic.currentPage,
                lastReadTimestamp = comic.lastReadTimestamp,
                isFavorite = comic.isFavorite,
                fileSizeBytes = comic.fileSizeBytes,
                addedTimestamp = comic.addedTimestamp
            )
        }
    }
}

