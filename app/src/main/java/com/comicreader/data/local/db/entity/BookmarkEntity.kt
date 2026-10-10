package com.comicreader.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.comicreader.domain.model.Bookmark

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["comicId"]),
        Index(value = ["comicId", "pageNumber"], unique = true)
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val comicId: Long,
    val pageNumber: Int,
    val thumbnailPath: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Bookmark {
        return Bookmark(
            id = id,
            comicId = comicId,
            pageNumber = pageNumber,
            thumbnailPath = thumbnailPath,
            note = note,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(bookmark: Bookmark): BookmarkEntity {
            return BookmarkEntity(
                id = bookmark.id,
                comicId = bookmark.comicId,
                pageNumber = bookmark.pageNumber,
                thumbnailPath = bookmark.thumbnailPath,
                note = bookmark.note,
                createdAt = bookmark.createdAt
            )
        }
    }
}

