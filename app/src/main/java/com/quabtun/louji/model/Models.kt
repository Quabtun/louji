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

data class DecisionStats(
    val total: Int = 0,
    val noStairs: Int = 0,
    val stairs: Int = 0,
    val unknown: Int = 0,
    val willComeDown: Int = 0,
    val wontComeDown: Int = 0,
)

data class LocationStat(
    val location: String = "",
    val total: Int = 0,
    val noStairs: Int = 0,
    val willComeDown: Int = 0,
)

data class RecordInsights(
    val decision: DecisionStats = DecisionStats(),
    val locations: List<LocationStat> = emptyList(),
)

data class MediaDraft(
    val type: MediaType,
    val filePath: String,
    val thumbPath: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
)
