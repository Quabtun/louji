package com.quabtun.louji.model

/** Chinese labels for the 2.0 enums. Kept separate from Formatters to avoid name clashes. */
fun ElevatorType.elevatorLabel(): String = when (this) {
    ElevatorType.DIRECT -> "直达电梯"
    ElevatorType.STAIRS_ONLY -> "无电梯步梯"
    ElevatorType.HALF_FLOOR -> "半层错层梯"
    ElevatorType.ODD_EVEN -> "单双层停靠"
    ElevatorType.CARD_CONTROL -> "需刷卡梯控"
    ElevatorType.FREIGHT_SLOW -> "高峰慢货梯"
    ElevatorType.UNKNOWN -> "未知"
}

fun ElevatorType.elevatorShortLabel(): String = when (this) {
    ElevatorType.DIRECT -> "直达梯"
    ElevatorType.STAIRS_ONLY -> "步梯"
    ElevatorType.HALF_FLOOR -> "半层梯"
    ElevatorType.ODD_EVEN -> "单双层梯"
    ElevatorType.CARD_CONTROL -> "刷卡梯"
    ElevatorType.FREIGHT_SLOW -> "慢货梯"
    ElevatorType.UNKNOWN -> ""
}

fun DeliveryMethod.deliveryLabel(): String = when (this) {
    DeliveryMethod.DOORSTEP -> "放门口"
    DeliveryMethod.COME_DOWN -> "下楼拿"
    DeliveryMethod.LOCKER -> "外卖柜/驿站"
    DeliveryMethod.HAND_TO_HAND -> "当面交"
    DeliveryMethod.UNKNOWN -> "未指定"
}

fun AttachmentCategory.categoryLabel(): String = when (this) {
    AttachmentCategory.ORDER_SCREENSHOT -> "订单截图"
    AttachmentCategory.BUILDING_ENTRY -> "单元门禁"
    AttachmentCategory.DROP_OFF -> "放餐凭证"
    AttachmentCategory.ROUTE_GUIDE -> "路线指引"
    AttachmentCategory.OTHER -> "其他"
}

/** Preset access restrictions shown as chips on building profiles. */
object AccessTagPresets {
    val presets = listOf(
        "禁电动车",
        "可骑车直达",
        "需保安登记",
        "夜间楼道黑",
        "有恶犬",
        "违停贴条区",
    )
}

/** High-frequency climb reasons offered as one-tap chips in the form. */
object ClimbReasons {
    val stairs = listOf(
        "老小区无电梯",
        "电梯损坏",
        "需业主刷卡",
        "半层错层梯",
        "客户要求送上门",
    )
    val noStairs = listOf(
        "电梯直达",
        "客户下楼拿",
        "放外卖柜",
        "放保安亭",
    )
    val uncertain = listOf(
        "先去楼下看看",
        "电话确认再定",
    )
}

/** Aggregated per-building counts used by the building wiki and leaderboards. */
data class BuildingStats(
    val total: Int = 0,
    val noStairs: Int = 0,
    val stairs: Int = 0,
    val warnings: Int = 0,
    val willComeDown: Int = 0,
) {
    val noStairRate: Int get() = if (total > 0) noStairs * 100 / total else 0
    val stairsRate: Int get() = if (total > 0) stairs * 100 / total else 0
}

/** A customer (or household) remembered by phone tail or building + room. */
data class CustomerProfile(
    val total: Int = 0,
    val noStairs: Int = 0,
    val willComeDown: Int = 0,
    val warnings: Int = 0,
    val lastNoStairs: TriState = TriState.UNKNOWN,
    val lastNote: String = "",
    val lastRemark: String = "",
    val tags: List<String> = emptyList(),
    val lastAt: Long = 0L,
) {
    val hasHistory: Boolean get() = total > 0
    val hasRisk: Boolean
        get() = warnings > 0 || tags.any {
            it.contains("电话不接") || it.contains("不耐烦") || it.contains("要求多")
        }
}

/** Total floors climbed in a period, used by the milestone card. */
data class ClimbTotals(
    val recordCount: Int = 0,
    val noStairs: Int = 0,
    val willComeDown: Int = 0,
    val floorsClimbed: Int = 0,
)

/** One day of activity for the trend chart and heatmap. */
data class DayBucket(
    val dayStart: Long = 0L,
    val total: Int = 0,
    val noStairs: Int = 0,
    val stairs: Int = 0,
)

/** Leaderboard row (building ranking). */
data class BuildingRank(
    val location: String = "",
    val total: Int = 0,
    val noStairs: Int = 0,
    val stairs: Int = 0,
)

/** Pure floor arithmetic shared by the insights screen and its unit tests. */
object FloorMath {
    private val digits = Regex("""\d{1,3}""")

    /**
     * Returns the number of storeys a record represents, or null when it is a basement/unknown.
     * Handles "6", "6F", "12楼", "18层"; skips "B1", "-1", "地下1层", "1-2", "".
     */
    fun climbedFloor(floor: String): Int? {
        val text = floor.trim().uppercase()
        if (text.isEmpty()) return null
        if (text.startsWith("B") || text.startsWith("-") || text.contains("负") ||
            text.contains("地下")
        ) {
            return null
        }
        if (text.contains("层") && text.contains("地下")) return null
        val match = digits.find(text) ?: return null
        return match.value.toIntOrNull()?.takeIf { it in 1..99 }
    }

    fun totalFloors(floors: List<String>): Int = floors.sumOf { climbedFloor(it) ?: 0 }

    /** Vertical metres assuming 3.0m per storey. */
    fun metres(totalFloors: Int): Double = totalFloors * 3.0

    fun kilometres(totalFloors: Int): Double = metres(totalFloors) / 1000.0

    /** Burned kcal estimate for a 70kg rider climbing stairs. */
    fun kilocalories(totalFloors: Int): Int = (totalFloors * 0.17 * 70).toInt()

    /** A playful achievement line derived from the climbed height. */
    fun achievement(totalFloors: Int): String {
        val metres = metres(totalFloors)
        return when {
            totalFloors <= 0 -> "还没开始爬，下一单就是起点"
            metres < 100 -> "已经爬了约 ${metres.toInt()} 米，热身完成"
            metres < 300 -> "相当于爬完 ${(metres / 30).toInt()} 层普通住宅楼，腿开始有感觉"
            metres < 600 -> "高度已超越一栋 20 层写字楼"
            metres < 1000 -> "相当于攀登了 ${"%.1f".format(metres / 300)} 座埃菲尔铁塔"
            metres < 2000 -> "高度进入云层，相当于 ${"%.1f".format(metres / 884.8)} 座泰山"
            else -> "累计攀升 ${"%.1f".format(metres / 1000)} 公里，比泰山还高 ${"%.1f".format(metres / 884.8)} 倍"
        }
    }
}
