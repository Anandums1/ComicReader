package com.comicreader.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.comicreader.domain.model.ReadingSession

@Entity(
    tableName = "reading_stats",
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comicId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["comicId"]),
        Index(value = ["sessionStartTimestamp"])
    ]
)
data class ReadingStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val comicId: Long? = null,
    val sessionStartTimestamp: Long,
    val sessionEndTimestamp: Long,
    val durationSeconds: Long = 0L,
    val pagesRead: Int = 0
) {
    fun toDomain(comicTitle: String? = null): ReadingSession {
        return ReadingSession(
            id = id,
            comicId = comicId,
            comicTitle = comicTitle,
            startTime = sessionStartTimestamp,
            endTime = sessionEndTimestamp,
            durationSeconds = durationSeconds,
            pagesRead = pagesRead
        )
    }

    companion object {
        fun fromDomain(session: ReadingSession): ReadingStatsEntity {
            return ReadingStatsEntity(
                id = session.id,
                comicId = session.comicId,
                sessionStartTimestamp = session.startTime,
                sessionEndTimestamp = session.endTime,
                durationSeconds = session.durationSeconds,
                pagesRead = session.pagesRead
            )
        }
    }
}

