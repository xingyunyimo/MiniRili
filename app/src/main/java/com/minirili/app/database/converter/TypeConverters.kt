package com.minirili.app.database.converter

import androidx.room.TypeConverter

class TypeConverters {

    @TypeConverter
    fun fromStringToList(value: String?): List<String> {
        return value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    @TypeConverter
    fun fromListToString(list: List<String>): String = list.joinToString(",")
}