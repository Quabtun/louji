package com.quabtun.louji.model

/**
 * Decides the initial "need to climb?" state for an auto-saved order. Kept pure so the inference
 * rules stay testable and never invent a decision when the evidence is weak.
 */
object OrderInference {
    private val noStairsHints = Regex("不用爬|免爬|电梯直达|电梯上楼|送上楼")
    private val stairsHints = Regex("步梯|无电梯|没有电梯|电梯坏|电梯停|电梯维修|走楼梯|要爬楼|爬楼梯")
    private val levelRegex = Regex("""\d{1,3}""")

    fun inferNoStairs(
        deliveryMethod: DeliveryMethod?,
        elevatorType: ElevatorType?,
        floor: String?,
        rawText: String?,
        warningAtBuilding: Boolean = false,
    ): TriState {
        val text = rawText.orEmpty()
        if (noStairsHints.containsMatchIn(text)) return TriState.YES
        if (deliveryMethod == DeliveryMethod.COME_DOWN) return TriState.YES
        if (stairsHints.containsMatchIn(text)) return TriState.NO
        if (warningAtBuilding) return TriState.UNKNOWN
        if (elevatorType == ElevatorType.DIRECT) return TriState.YES
        val level = floor?.let { levelRegex.find(it)?.value?.toIntOrNull() }
        if (level != null && level >= 7 && elevatorType == ElevatorType.STAIRS_ONLY) {
            return TriState.NO
        }
        return TriState.UNKNOWN
    }
}
