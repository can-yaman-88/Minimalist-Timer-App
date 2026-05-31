package com.deepwork.focustimer.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromSessionType(value: SessionType): String = value.name

    @TypeConverter
    fun toSessionType(value: String): SessionType = SessionType.valueOf(value)

    @TypeConverter
    fun fromSessionOrigin(value: SessionOrigin): String = value.name

    @TypeConverter
    fun toSessionOrigin(value: String): SessionOrigin = SessionOrigin.valueOf(value)
}
