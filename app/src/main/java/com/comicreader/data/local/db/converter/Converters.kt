package com.comicreader.data.local.db.converter

import androidx.room.TypeConverter
import com.comicreader.domain.model.ComicFormat
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromComicFormat(format: ComicFormat?): String? {
        return format?.extension
    }

    @TypeConverter
    fun toComicFormat(value: String?): ComicFormat? {
        return value?.let { ComicFormat.fromExtension(it) }
    }
}

