package net.dacworld.android.holyplacesofthelord.model

import androidx.room.TypeConverter
import java.util.Date
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Converters { // Changed to object
    // Formatter for LocalDate
    private val localDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    @TypeConverter
    fun fromStringToLocalDate(value: String?): LocalDate? {
        return value?.let {
            LocalDate.parse(it, localDateFormatter)
        }
    }

    @TypeConverter
    fun fromLocalDateToString(date: LocalDate?): String? {
        return date?.format(localDateFormatter)
    }

    // Converters for java.util.Date to Long (Timestamp)
    @TypeConverter
    fun fromLongToDate(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun fromDateToLong(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}