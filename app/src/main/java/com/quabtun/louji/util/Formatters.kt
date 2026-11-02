package com.quabtun.louji.util

import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.TriState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))

fun formatFullDateTime(millis: Long): String =
    SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(millis))

fun backupFileName(now: Long = System.currentTimeMillis()): String =
    "楼记备份-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date(now)) + ".zip"

fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun floorDisplay(floor: String): String {
    val value = floor.trim()
    if (value.isEmpty()) return ""
    return if (value.all(Char::isDigit)) "${value}F" else value
}

fun placeDisplay(floor: String, room: String): String {
    val parts = listOf(floorDisplay(floor), room.trim()).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "未填" else parts.joinToString(" · ")
}

fun TriState.climbLabel(): String = when (this) {
    TriState.YES -> "不用爬楼"
    TriState.NO -> "要爬楼"
    TriState.UNKNOWN -> "待确认"
}

fun TriState.shortYesNo(unknown: String = "未知"): String = when (this) {
    TriState.YES -> "是"
    TriState.NO -> "否"
    TriState.UNKNOWN -> unknown
}

fun TriState.doorLabel(): String = when (this) {
    TriState.YES -> "完好"
    TriState.NO -> "有问题"
    TriState.UNKNOWN -> "未知"
}

fun TriState.canOpenLabel(): String = when (this) {
    TriState.YES -> "能开"
    TriState.NO -> "不能开"
    TriState.UNKNOWN -> "未知"
}

fun TriState.comeDownLabel(): String = when (this) {
    TriState.YES -> "会下来拿"
    TriState.NO -> "不会下来"
    TriState.UNKNOWN -> "未知"
}

fun Gender.label(): String = when (this) {
    Gender.MALE -> "男"
    Gender.FEMALE -> "女"
    Gender.UNKNOWN -> "未知"
}

fun RecordWithAttachments.shareText(): String {
    val record = record
    val lines = buildList {
        add("楼记 · ${record.location}")
        if (record.orderNo.isNotBlank()) add("订单：${record.orderNo}")
        add("性别：${record.gender.label()}" + if (record.phoneTail.isNotBlank()) " · 尾号 ${record.phoneTail}" else "")
        val place = placeDisplay(record.floor, record.roomNo)
        if (place != "未填") add("楼层/房号：$place")
        add("不用爬楼：${record.noStairs.shortYesNo()}")
        add("门：${record.doorIntact.doorLabel()} · 上面能开门：${record.canOpenFromAbove.canOpenLabel()}")
        add("会下来拿：${record.willComeDown.comeDownLabel()}")
        if (record.personalityTags.isNotEmpty()) add("性格：${record.personalityTags.joinToString(" / ")}")
        if (record.personalityNote.isNotBlank()) add("性格备注：${record.personalityNote}")
        if (record.remark.isNotBlank()) add("备注：${record.remark}")
        if (attachments.isNotEmpty()) add("现场：${attachments.size} 个附件")
    }
    return lines.joinToString("\n")
}

fun recordSearchSubtitle(item: RecordWithAttachments): String {
    val parts = buildList {
        if (item.record.orderNo.isNotBlank()) add("#${item.record.orderNo}")
        if (item.record.phoneTail.isNotBlank()) add("尾号 ${item.record.phoneTail}")
        if (item.record.gender != Gender.UNKNOWN) add(item.record.gender.label())
    }
    return if (parts.isEmpty()) "未填订单信息" else parts.joinToString(" · ")
}
