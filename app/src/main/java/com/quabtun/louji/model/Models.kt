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

data class DateRange(
    val start: Long = 0L,
    val end: Long = 0L,
) {
    val isActive: Boolean get() = start != 0L || end != 0L
}

object DateRangePresets {
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Rolling window: from [days] days ago (inclusive) until now. */
    fun lastDays(now: Long, days: Int): DateRange = DateRange(start = now - days * DAY_MS)

    /**
     * Converts the UTC-midnight timestamp produced by Material3 DatePicker into the local
     * start-of-day for [zone], so range filtering lines up with the user's calendar day.
     */
    fun startOfDay(utcDateMillis: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long {
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcDateMillis
        }
        val local = java.util.Calendar.getInstance(zone).apply {
            set(
                utc.get(java.util.Calendar.YEAR),
                utc.get(java.util.Calendar.MONTH),
                utc.get(java.util.Calendar.DAY_OF_MONTH),
                0, 0, 0,
            )
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return local.timeInMillis
    }

    /** Exclusive end bound: local start of the day after [localDayStart]. */
    fun nextDayStart(localDayStart: Long): Long = localDayStart + DAY_MS
}

data class MediaDraft(
    val type: MediaType,
    val filePath: String,
    val thumbPath: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
)
