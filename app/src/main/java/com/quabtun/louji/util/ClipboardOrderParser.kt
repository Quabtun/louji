package com.quabtun.louji.util

import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.deliveryLabel
import com.quabtun.louji.model.Gender

data class ParsedOrder(
    val location: String? = null,
    val orderNo: String? = null,
    val phoneTail: String? = null,
    val phoneExt: String? = null,
    val floor: String? = null,
    val roomNo: String? = null,
    val gender: Gender? = null,
    val deliveryMethod: DeliveryMethod? = null,
    val remarkHint: String? = null,
) {
    val isEmpty: Boolean
        get() = location == null && orderNo == null && phoneTail == null && phoneExt == null &&
            floor == null && roomNo == null && gender == null && deliveryMethod == null

    fun labels(): List<String> = buildList {
        if (location != null) add("位置")
        if (orderNo != null) add("订单号")
        if (phoneTail != null) add("尾号")
        if (phoneExt != null) add("分机号")
        if (floor != null) add("楼层")
        if (roomNo != null) add("房号")
        if (gender != null) add("性别")
        if (deliveryMethod != null && deliveryMethod != DeliveryMethod.UNKNOWN) {
            add(deliveryMethod.deliveryLabel())
        }
    }

    /** Fills values missing here from a second parse (shared text + OCR of the same order). */
    fun fillMissingFrom(other: ParsedOrder): ParsedOrder = copy(
        location = location ?: other.location,
        orderNo = orderNo ?: other.orderNo,
        phoneTail = phoneTail ?: other.phoneTail,
        phoneExt = phoneExt ?: other.phoneExt,
        floor = floor ?: other.floor,
        roomNo = roomNo ?: other.roomNo,
        gender = gender ?: other.gender,
        deliveryMethod = deliveryMethod?.takeIf { it != DeliveryMethod.UNKNOWN }
            ?: other.deliveryMethod,
        remarkHint = remarkHint ?: other.remarkHint,
    )

    fun summary(): String = buildList {
        location?.let { add("位置：$it") }
        orderNo?.let { add("订单号：$it") }
        phoneTail?.let { add("尾号：$it") }
        phoneExt?.let { add("分机号：$it") }
        floor?.let { add("楼层：$it") }
        roomNo?.let { add("房号：$it") }
        gender?.let { add("性别：${it.label()}") }
        deliveryMethod?.takeIf { it != DeliveryMethod.UNKNOWN }?.let {
            add("交付方式：${it.deliveryLabel()}")
        }
        remarkHint?.let { add("建议备注：$it") }
    }.joinToString("\n")
}

/**
 * Best-effort extraction of the fields riders usually copy from a delivery platform.
 * It never invents a value: anything not confidently found is left null for manual entry.
 *
 * 2.0 adds masked virtual numbers with extensions, compound Chinese address splitting and
 * delivery-method / remark hints.
 */
object ClipboardOrderParser {
    private const val MAX_INPUT = 12_000

    private val addressLabel = Regex(
        """(?:收货地址|送餐地址|配送地址|顾客地址|地址|送到)\s*[:：]\s*(.+)""",
        RegexOption.IGNORE_CASE,
    )
    private val addressKeyword = Regex(
        """(小区|花园|公寓|大厦|苑|号楼|栋|单元|路|街|道|巷|广场|中心|城|村|里|居|府|庭|境|公馆|名邸|院|弄|幢|(?<!日)(?<!星)(?<!学)期|SOHO|A座|B座|C座|D座)""",
        RegexOption.IGNORE_CASE,
    )
    private val orderLabel = Regex(
        """(?:订单号|订单编号|单号|流水号|取货码|取餐码|取餐号)\s*[:：#]?\s*([A-Za-z0-9][A-Za-z0-9-]{1,31})""",
        RegexOption.IGNORE_CASE,
    )
    private val hashOrder = Regex("""(?<![A-Za-z0-9])#\s*([A-Za-z0-9]{2,32})""")
    private val longOrderDigits = Regex(
        """(?:订单号|订单编号|单号)\s*[:：#]?\s*((?:\d\s*){6,})""",
    )
    private val genericOrder = Regex(
        """(?:^|\s)订单\s*[:：#]?\s*([A-Za-z0-9][A-Za-z0-9-]{2,31})""",
        RegexOption.IGNORE_CASE,
    )
    private val phoneTail = Regex("""尾\s*号\s*[:：]?\s*((?:\d\s*){4})""")
    private val maskedPhone = Regex(
        """(?<!\d)1[3-9]\d{0,2}[\s*＊×xX•·]{2,8}(\d{4})(?!\d)""",
    )
    private val phoneNumber = Regex("""(?<!\d)(1[3-9](?:\s*\d){9})(?!\d)""")
    private val phoneWithDashExt = Regex(
        """(?<!\d)1[3-9](?:\s*\d){9}\s*[-－—]\s*(\d{2,6})(?!\d)""",
    )
    private val extLabel = Regex("""(?:转接号|分机号?|转)\s*[:：]?\s*(\d{2,6})""")
    private val floorLabel = Regex("""楼\s*层\s*[:：]?\s*((?:\d\s*){1,3})""")
    private val floorSuffix = Regex("""(?<![\dA-Za-z])(\d{1,3})\s*(?:楼|层|F|f)(?!\s*号)""")
    private val basementFloor = Regex("""(?:地下\s*(\d{1,2})\s*层?)|(?:B\s*(\d{1,2})\s*(?:楼|层|F))""")
    private val roomLabel = Regex(
        """房\s*(?:号|间)\s*[:：]?\s*((?:[A-Za-z0-9]\s*){2,12})""",
        RegexOption.IGNORE_CASE,
    )
    private val roomDashed = Regex("""(?<![\dA-Za-z])([A-Za-z]?\d{1,3}-\d{3,4})(?![\dA-Za-z])""")
    private val roomSuffix = Regex("""(\d{3,4})\s*(?:室|号房)""")
    private val trailingRoomDigits = Regex("""(\d{3,4})\s*$""")
    private val buildingMarker = Regex("""(栋|号楼|座|单元|层|楼)""")
    private val maleWords = Regex("""先生|男士|帅哥""")
    private val femaleWords = Regex("""女士|小姐|太太|美女""")

    /** A line that is only a field label, with or without a trailing colon. */
    private val bareLabel = Regex(
        """^(收货地址|送餐地址|配送地址|顾客地址|地址|送到|订单号|订单编号|单号|流水号|取货码)\s*[:：]?$""",
        RegexOption.IGNORE_CASE,
    )

    /** UI chrome that OCR picks up next to the real value. */
    private val noiseWords = listOf(
        "查看地图", "拨打电话", "打电话", "导航", "联系顾客", "联系商家", "联系骑手",
        "复制", "呼叫", "一键拨号", "查看详情", "展开",
    )

    /** Content that never starts an address, even if it happens to contain a keyword char. */
    private val nonAddressLine = Regex(
        """^(日期|时间|备注|订单|温馨提示|预计|送达|骑手|商家|顾客|电话|手机|编号|金额|支付)""",
    )

    /** Label markers that end the location part, tolerant of OCR spacing inside the label. */
    private val locationStopLabel = Regex(
        "尾\\s*号|手\\s*机|电\\s*话|楼\\s*层|房\\s*号|房\\s*间|订\\s*单|分\\s*机|备\\s*注" +
            "|查看地图|拨打电话|打电话|导航|联系顾客|联系商家|复制|呼叫" +
            "|先生|女士|小姐|1[3-9]\\d{9}|1[3-9][\\s*＊×•·]{2,}\\d{4}" +
            "|\\d{3,4}\\s*(?:室|号房)",
    )

    fun parse(raw: String): ParsedOrder {
        val text = raw.take(MAX_INPUT)
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
        if (text.isBlank()) return ParsedOrder()
        val lines = mergeLabelLines(text.lines().map { it.trim() }.filter { it.isNotEmpty() })
        val normalized = lines.joinToString("\n")
        val address = splitAddress(parseRawLocation(lines))
        val room = parseRoom(normalized) ?: address.roomNo
        return ParsedOrder(
            location = address.location,
            orderNo = parseOrderNo(normalized),
            phoneTail = parsePhoneTail(normalized),
            phoneExt = parsePhoneExt(normalized),
            floor = parseFloor(normalized) ?: address.floor ?: deriveFloor(room),
            roomNo = room,
            gender = parseGender(normalized),
            deliveryMethod = parseDelivery(normalized),
            remarkHint = parseRemarkHint(normalized),
        )
    }

    /**
     * Joins a label-only line ("收货地址" with no value) to the next meaningful line so screenshots
     * that wrap the label and the value onto separate rows still parse.
     */
    private fun mergeLabelLines(lines: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val label = bareLabel.find(line)?.groupValues?.getOrNull(1)
            if (label != null) {
                var next = index + 1
                while (next < lines.size && isNoiseLine(lines[next])) next++
                val candidate = lines.getOrNull(next)
                if (candidate != null && bareLabel.find(candidate) == null) {
                    merged += "$label：$candidate"
                    index = next + 1
                    continue
                }
            }
            merged += line
            index++
        }
        return merged
    }

    private fun isNoiseLine(line: String): Boolean {
        val trimmed = line.trim(' ', '：', ':', '|', '-', '·')
        if (trimmed.isEmpty()) return true
        return noiseWords.any { trimmed == it || trimmed.startsWith(it) }
    }

    private fun parseRawLocation(lines: List<String>): String? {
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
                !line.contains("房号") &&
                !line.contains("备注") &&
                !phoneNumber.containsMatchIn(line) &&
                !nonAddressLine.containsMatchIn(line) &&
                !isNoiseLine(line)
            ) {
                cleanLocation(line)?.let { return it }
            }
        }
        return null
    }

    private fun cleanLocation(raw: String): String? {
        var value = raw.replace(
            Regex("""^(?:收货地址|送餐地址|配送地址|顾客地址|地址|送到)\s*[:：]?\s*"""),
            "",
        )
        // Semantic boundaries only: a double space is how OCR separates columns on the same row,
        // so cutting there would throw away a valid "小区 3栋" location.
        val stop = locationStopLabel.find(value)
        if (stop != null) value = value.substring(0, stop.range.first)
        value = value
            .trim()
            .trimStart('：', ':', '#', '-', ' ')
            .trim('，', ',', '。', '；', ';', '·', '|')
            .replace(Regex("""\s{2,}"""), " ")
        return value.takeIf { it.length in 2..60 }
    }

    data class AddressParts(
        val location: String? = null,
        val floor: String? = null,
        val roomNo: String? = null,
    )

    /**
     * Splits a compound Chinese address: `阳光花园三期12栋2单元1804室` → building + 18F + 1804,
     * `锦绣公寓B座7楼702` → building + 7F + 702, `老城小区3号楼B1-105` → building + B1 + B1-105.
     */
    fun splitAddress(raw: String?): AddressParts {
        if (raw.isNullOrBlank()) return AddressParts()
        var rest = raw.trim().trim('，', ',', '。', '；', ';')
        var room: String? = null
        var floor: String? = null

        // 1) dashed room such as B1-105 / 2-301
        val dashed = Regex("""([Bb]?\d{1,3}-\d{3,4})\s*$""").find(rest)
        if (dashed != null) {
            room = dashed.groupValues[1]
            rest = rest.substring(0, dashed.range.first).trim()
            if (room.startsWith("B", ignoreCase = true)) {
                floor = room.substringBefore('-').uppercase()
            }
        }

        // 2) room with 室/号房 suffix
        if (room == null) {
            roomSuffix.find(rest)?.let { match ->
                room = match.groupValues[1]
                rest = rest.substring(0, match.range.first).trim()
            }
        }

        // 3) bare trailing room digits: `3栋1804` / `B座702` / `2单元501`. Only 3-4 digit numbers
        //    in a plausible room range count, so building numbers (`12号院`, `3号楼`) stay in the
        //    location instead of being mistaken for rooms.
        if (room == null && buildingMarker.containsMatchIn(rest)) {
            trailingRoomDigits.find(rest)?.let { match ->
                val candidate = match.groupValues[1]
                val value = candidate.toIntOrNull()
                if (value != null && value in 101..9999) {
                    room = candidate
                    rest = rest.substring(0, match.range.first).trim()
                }
            }
        }

        // 4) floor phrase left in the remainder
        if (floor == null) {
            basementFloor.find(rest)?.let { match ->
                val digits = match.groupValues[1].ifBlank { match.groupValues[2] }
                if (digits.isNotBlank()) floor = "B$digits"
            }
            if (floor == null) {
                floorSuffix.find(rest)?.let { floor = it.groupValues[1] }
            }
        }
        if (floor != null) {
            rest = basementFloor.replace(rest, " ")
            rest = Regex("""(?<![\dA-Za-z])\d{1,3}\s*(?:楼|层|F|f)(?!\s*号)""").replace(rest, " ")
            rest = rest.replace(Regex("""\s+"""), " ").trim().trim('，', ',', '。', '；', ';')
        }

        // 5) derive floor from a 3-4 digit room number (1804 → 18F, 702 → 7F)
        val roomValue = room
        if (floor == null && roomValue != null) {
            val digits = Regex("""\d{3,4}""").find(roomValue)?.value
            if (digits != null && digits.length >= 3) {
                val derived = digits.dropLast(2).toIntOrNull()
                if (derived != null && derived in 1..99) floor = derived.toString()
            }
        }

        val location = rest.takeIf { it.length in 1..60 }
        return AddressParts(location = location, floor = floor, roomNo = room)
    }

    private fun parseOrderNo(text: String): String? {
        orderLabel.find(text)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.let { return it.takeIf { value -> value.length in 2..32 } }
        longOrderDigits.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
            val digits = raw.filter(Char::isDigit)
            if (digits.length in 6..32) return digits
        }
        hashOrder.find(text)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.let { return it.takeIf { value -> value.length in 2..32 } }
        return genericOrder.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.length in 3..32 }
            ?.takeIf { candidate ->
                // A bare short number after the word "订单" is usually a count, not an id.
                candidate.any { !it.isDigit() } || candidate.length >= 6
            }
    }

    private fun parsePhoneTail(text: String): String? =
        phoneTail.find(text)?.groupValues?.getOrNull(1)?.filter(Char::isDigit)
            ?: maskedPhone.find(text)?.groupValues?.getOrNull(1)
            ?: phoneNumber.find(text)?.value?.filter(Char::isDigit)?.takeLast(4)

    private fun parsePhoneExt(text: String): String? {
        extLabel.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        phoneWithDashExt.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private fun parseFloor(text: String): String? =
        floorLabel.find(text)?.groupValues?.getOrNull(1)?.filter(Char::isDigit)
            ?.takeIf { it.isNotBlank() }
            ?: basementFloor.find(text)?.let { match ->
                val digits = match.groupValues[1].ifBlank { match.groupValues[2] }
                if (digits.isBlank()) null else "B$digits"
            }
            ?: floorSuffix.find(text)?.groupValues?.getOrNull(1)

    private fun parseRoom(text: String): String? =
        roomLabel.find(text)?.groupValues?.getOrNull(1)
            ?.filterNot(Char::isWhitespace)
            ?.takeIf { it.isNotBlank() }
            ?: roomDashed.find(text)?.groupValues?.getOrNull(1)
            ?: roomSuffix.find(text)?.groupValues?.getOrNull(1)

    /** 1804 → 18, 702 → 7, 501 → 5. */
    private fun deriveFloor(room: String?): String? {
        val digits = room?.let { Regex("""\d{3,4}""").find(it)?.value } ?: return null
        val derived = digits.dropLast(2).toIntOrNull() ?: return null
        return derived.takeIf { it in 1..99 }?.toString()
    }

    private fun parseGender(text: String): Gender? = when {
        maleWords.containsMatchIn(text) -> Gender.MALE
        femaleWords.containsMatchIn(text) -> Gender.FEMALE
        else -> null
    }

    /** Maps remark keywords to a delivery preference; most specific wins. */
    fun parseDelivery(text: String): DeliveryMethod? = when {
        Regex("""外卖柜|丰巢|快递柜|驿站|自提柜""").containsMatchIn(text) ->
            DeliveryMethod.LOCKER
        Regex("""下楼拿|下来拿|下楼取|下来取|下楼来拿""").containsMatchIn(text) ->
            DeliveryMethod.COME_DOWN
        Regex("""放门口|放鞋架|挂门把|门把手|放门边|放地上|送到门口|送至门口""")
            .containsMatchIn(text) -> DeliveryMethod.DOORSTEP
        Regex("""当面交|当面给|亲手交|当面""").containsMatchIn(text) ->
            DeliveryMethod.HAND_TO_HAND
        else -> null
    }

    /** Collects handling warnings verbatim so the rider can paste them into the note. */
    fun parseRemarkHint(text: String): String? {
        val hits = listOf(
            "别敲门", "不要敲门", "别按铃", "不要按铃", "家里有狗", "有恶犬",
            "电梯坏", "电梯维修", "轻点敲", "放门卫",
        ).filter { text.contains(it) }
        return hits.take(3).takeIf { it.isNotEmpty() }?.joinToString("、")
    }
}
