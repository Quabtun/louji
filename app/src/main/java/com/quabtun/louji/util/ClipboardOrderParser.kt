package com.quabtun.louji.util

import com.quabtun.louji.model.Gender

data class ParsedOrder(
    val location: String? = null,
    val orderNo: String? = null,
    val phoneTail: String? = null,
    val floor: String? = null,
    val roomNo: String? = null,
    val gender: Gender? = null,
) {
    val isEmpty: Boolean
        get() = location == null && orderNo == null && phoneTail == null &&
            floor == null && roomNo == null && gender == null

    fun labels(): List<String> = buildList {
        if (location != null) add("位置")
        if (orderNo != null) add("订单号")
        if (phoneTail != null) add("尾号")
        if (floor != null) add("楼层")
        if (roomNo != null) add("房号")
        if (gender != null) add("性别")
    }

    fun summary(): String = buildList {
        location?.let { add("位置：$it") }
        orderNo?.let { add("订单号：$it") }
        phoneTail?.let { add("尾号：$it") }
        floor?.let { add("楼层：$it") }
        roomNo?.let { add("房号：$it") }
        gender?.let { add("性别：${it.label()}") }
    }.joinToString("\n")
}

/**
 * Best-effort extraction of the fields riders usually copy from a delivery platform.
 * It never invents a value: anything not confidently found is left null for manual entry.
 */
object ClipboardOrderParser {
    private const val MAX_INPUT = 12_000

    private val addressLabel = Regex(
        """(?:收货地址|送餐地址|配送地址|地址|送到)\s*[:：]\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )
    private val addressKeyword = Regex("""(小区|花园|公寓|大厦|苑|号楼|栋|单元|路|街|道|巷)""")
    private val orderLabel = Regex(
        """(?:订单号|订单编号|单号)\s*[:：#]?\s*([A-Za-z0-9][A-Za-z0-9-]{2,31})""",
        RegexOption.IGNORE_CASE,
    )
    private val genericOrder = Regex(
        """(?:^|\s)订单\s*[:：#]?\s*([A-Za-z0-9][A-Za-z0-9-]{2,31})""",
        RegexOption.IGNORE_CASE,
    )
    private val phoneTail = Regex("""尾号\s*[:：]?\s*(\d{4})""")
    private val phoneNumber = Regex("""(?<!\d)(1[3-9]\d{9})(?!\d)""")
    private val floorLabel = Regex("""楼层\s*[:：]?\s*(\d{1,3})""")
    private val floorSuffix = Regex("""(?<![\dA-Za-z])(\d{1,3})\s*(?:楼|层|F|f)(?!\s*号)""")
    private val roomLabel = Regex(
        """(?:房号|房间号|房间)\s*[:：]?\s*([A-Za-z0-9][A-Za-z0-9-]{1,11})""",
        RegexOption.IGNORE_CASE,
    )
    private val roomDashed = Regex("""(?<![\dA-Za-z])([A-Za-z]?\d{1,3}-\d{3,4})(?![\dA-Za-z])""")
    private val roomSuffix = Regex("""(?<![\dA-Za-z])(\d{3,4})\s*室""")
    private val maleWords = Regex("""先生|男士|帅哥""")
    private val femaleWords = Regex("""女士|小姐|太太|美女""")

    fun parse(raw: String): ParsedOrder {
        val text = raw.take(MAX_INPUT)
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
        if (text.isBlank()) return ParsedOrder()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedOrder(
            location = parseLocation(lines),
            orderNo = parseOrderNo(text),
            phoneTail = parsePhoneTail(text),
            floor = parseFloor(text),
            roomNo = parseRoom(text),
            gender = parseGender(text),
        )
    }

    private fun parseLocation(lines: List<String>): String? {
        lines.forEach { line ->
            addressLabel.find(line)?.groupValues?.getOrNull(1)?.let { value ->
                cleanLocation(value)?.let { return it }
            }
        }
        lines.forEach { line ->
            if (line.length in 2..60 &&
                addressKeyword.containsMatchIn(line) &&
                !line.contains("订单") &&
                !line.contains("尾号") &&
                !phoneNumber.containsMatchIn(line)
            ) {
                cleanLocation(line)?.let { return it }
            }
        }
        return null
    }

    private fun cleanLocation(raw: String): String? {
        val value = raw
            .replace(
                Regex("""^(?:收货地址|送餐地址|配送地址|地址|送到)\s*[:：]?\s*"""),
                "",
            )
            .substringBefore("尾号")
            .substringBefore("手机")
            .substringBefore("电话")
            .substringBefore("  ")
            .trim()
            .trimStart('：', ':', '#', '-', ' ')
            .trim('，', ',', '。', '；', ';')
        return value.takeIf { it.length in 2..60 }
    }

    private fun parseOrderNo(text: String): String? =
        (orderLabel.find(text) ?: genericOrder.find(text))
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.length in 3..32 }

    private fun parsePhoneTail(text: String): String? =
        phoneTail.find(text)?.groupValues?.getOrNull(1)
            ?: phoneNumber.find(text)?.value?.takeLast(4)

    private fun parseFloor(text: String): String? =
        floorLabel.find(text)?.groupValues?.getOrNull(1)
            ?: floorSuffix.find(text)?.groupValues?.getOrNull(1)

    private fun parseRoom(text: String): String? =
        roomLabel.find(text)?.groupValues?.getOrNull(1)
            ?: roomDashed.find(text)?.groupValues?.getOrNull(1)
            ?: roomSuffix.find(text)?.groupValues?.getOrNull(1)

    private fun parseGender(text: String): Gender? = when {
        maleWords.containsMatchIn(text) -> Gender.MALE
        femaleWords.containsMatchIn(text) -> Gender.FEMALE
        else -> null
    }
}
