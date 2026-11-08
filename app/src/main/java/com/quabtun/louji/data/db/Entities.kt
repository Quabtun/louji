package com.quabtun.louji.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.TriState

@Entity(
    tableName = "records",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["location"]),
        Index(value = ["orderNo"]),
        Index(value = ["phoneTail"]),
    ],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val location: String,
    val orderNo: String,
    val gender: Gender,
    val phoneTail: String,
    val floor: String,
    val roomNo: String,
    val doorIntact: TriState,
    val canOpenFromAbove: TriState,
    val noStairs: TriState,
    val willComeDown: TriState,
    val personalityTags: List<String>,
    val personalityNote: String,
    val createdAt: Long,
    val updatedAt: Long,
    val remark: String = "",
    val climbNote: String = "",
    val archived: Boolean = false,
    val deletedAt: Long = 0,
    val phoneExt: String = "",
    val accessCode: String = "",
    val elevatorType: ElevatorType = ElevatorType.UNKNOWN,
    val deliveryMethod: DeliveryMethod = DeliveryMethod.UNKNOWN,
    val isWarning: Boolean = false,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = RecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recordId"])],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val type: MediaType,
    val filePath: String,
    val thumbPath: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val createdAt: Long,
    val category: AttachmentCategory = AttachmentCategory.OTHER,
)

@Entity(
    tableName = "building_profiles",
    indices = [Index(value = ["location"], unique = true)],
)
data class BuildingProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val location: String,
    val elevatorType: ElevatorType = ElevatorType.UNKNOWN,
    val accessCode: String = "",
    val doorIntact: TriState = TriState.UNKNOWN,
    val canOpenFromAbove: TriState = TriState.UNKNOWN,
    val lockerInfo: String = "",
    val accessTags: List<String> = emptyList(),
    val notes: String = "",
    val coverPhotoPath: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
