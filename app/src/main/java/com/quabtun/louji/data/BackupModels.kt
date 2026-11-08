package com.quabtun.louji.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val app: String = "louji",
    val schema: Int = 2,
    val exportedAt: Long,
    val appVersion: String,
    val recordCount: Int,
    val mediaCount: Int,
    val buildingCount: Int = 0,
)

@Serializable
data class BackupRecord(
    val location: String,
    val orderNo: String = "",
    val gender: String = "UNKNOWN",
    val phoneTail: String = "",
    val floor: String = "",
    val roomNo: String = "",
    val doorIntact: String = "UNKNOWN",
    val canOpenFromAbove: String = "UNKNOWN",
    val noStairs: String = "UNKNOWN",
    val willComeDown: String = "UNKNOWN",
    val personalityTags: List<String> = emptyList(),
    val personalityNote: String = "",
    val remark: String = "",
    val climbNote: String = "",
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<BackupAttachment> = emptyList(),
    // 2.0 fields — all defaulted so v1 backups still deserialize.
    val phoneExt: String = "",
    val accessCode: String = "",
    val elevatorType: String = "UNKNOWN",
    val deliveryMethod: String = "UNKNOWN",
    val isWarning: Boolean = false,
    val deletedAt: Long = 0,
)

@Serializable
data class BackupAttachment(
    val type: String,
    val file: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val category: String = "OTHER",
)

@Serializable
data class BackupBuilding(
    val location: String,
    val elevatorType: String = "UNKNOWN",
    val accessCode: String = "",
    val doorIntact: String = "UNKNOWN",
    val canOpenFromAbove: String = "UNKNOWN",
    val lockerInfo: String = "",
    val accessTags: List<String> = emptyList(),
    val notes: String = "",
    val coverPhotoPath: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

enum class ImportMode {
    MERGE,
    OVERWRITE,
}

data class ImportSummary(
    val imported: Int,
    val skipped: Int,
    val media: Int,
)

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
