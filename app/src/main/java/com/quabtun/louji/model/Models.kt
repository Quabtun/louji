package com.quabtun.louji.model

enum class TriState {
    YES,
    NO,
    UNKNOWN,
}

enum class Gender {
    MALE,
    FEMALE,
    UNKNOWN,
}

enum class MediaType {
    IMAGE,
    VIDEO,
}

enum class RecordFilter {
    ALL,
    STAIRS,
    NO_STAIRS,
    WILL_COME_DOWN,
    WITH_MEDIA,
}

object PersonalityTags {
    val presets = listOf(
        "好沟通",
        "爽快",
        "不耐烦",
        "要求多",
        "电话不接",
        "必须送上门",
    )
}

data class RecordStats(
    val total: Int = 0,
    val noStairs: Int = 0,
    val willComeDown: Int = 0,
)

data class MediaDraft(
    val type: MediaType,
    val filePath: String,
    val thumbPath: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
)
