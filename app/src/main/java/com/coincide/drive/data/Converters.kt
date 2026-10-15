package com.coincide.drive.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room Type Converters for handling common type conversions.
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
