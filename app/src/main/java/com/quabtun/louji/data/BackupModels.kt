package com.quabtun.louji.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val app: String = "louji",
    val schema: Int = 1,
    val exportedAt: Long,
    val appVersion: String,
    val recordCount: Int,
    val mediaCount: Int,
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
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<BackupAttachment> = emptyList(),
)

@Serializable
data class BackupAttachment(
    val type: String,
    val file: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
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
