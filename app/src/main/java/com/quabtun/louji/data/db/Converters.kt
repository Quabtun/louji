package com.quabtun.louji.data.db

import androidx.room.TypeConverter
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
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
    fun elevatorTypeToString(value: ElevatorType): String = value.name

    @TypeConverter
    fun stringToElevatorType(value: String): ElevatorType =
        runCatching { ElevatorType.valueOf(value) }.getOrDefault(ElevatorType.UNKNOWN)

    @TypeConverter
    fun deliveryMethodToString(value: DeliveryMethod): String = value.name

    @TypeConverter
    fun stringToDeliveryMethod(value: String): DeliveryMethod =
        runCatching { DeliveryMethod.valueOf(value) }.getOrDefault(DeliveryMethod.UNKNOWN)

    @TypeConverter
    fun attachmentCategoryToString(value: AttachmentCategory): String = value.name

    @TypeConverter
    fun stringToAttachmentCategory(value: String): AttachmentCategory =
        runCatching { AttachmentCategory.valueOf(value) }.getOrDefault(AttachmentCategory.OTHER)

    @TypeConverter
    fun tagsToString(value: List<String>): String =
        runCatching { Json.encodeToString(value) }.getOrDefault("[]")

    @TypeConverter
    fun stringToTags(value: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}
