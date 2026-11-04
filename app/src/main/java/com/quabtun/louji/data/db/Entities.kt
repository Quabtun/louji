package com.quabtun.louji.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
)
