package com.quabtun.louji.data.db

import androidx.room.TypeConverter
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.TriState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun triStateToString(value: TriState): String = value.name

    @TypeConverter
    fun stringToTriState(value: String): TriState =
        runCatching { TriState.valueOf(value) }.getOrDefault(TriState.UNKNOWN)

    @TypeConverter
    fun genderToString(value: Gender): String = value.name

    @TypeConverter
    fun stringToGender(value: String): Gender =
        runCatching { Gender.valueOf(value) }.getOrDefault(Gender.UNKNOWN)

    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType =
        runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.IMAGE)

    @TypeConverter
    fun tagsToString(value: List<String>): String =
        runCatching { Json.encodeToString(value) }.getOrDefault("[]")

    @TypeConverter
    fun stringToTags(value: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}
