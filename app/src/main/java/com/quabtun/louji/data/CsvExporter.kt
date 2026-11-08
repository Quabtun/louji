package com.quabtun.louji.data

import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.TriState
import com.quabtun.louji.model.deliveryLabel
import com.quabtun.louji.model.elevatorLabel
import com.quabtun.louji.util.canOpenLabel
import com.quabtun.louji.util.doorLabel
import com.quabtun.louji.util.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports the ledger as CSV. UTF-8 + BOM so Windows Excel opens Chinese correctly.
 * Desensitised mode hides the order number and the first two digits of the phone tail.
 */
object CsvExporter {
    private val headers = listOf(
        "创建时间", "位置", "订单号", "性别", "手机尾号", "分机号", "楼层", "房号",
        "门禁密码", "电梯类型", "门是否完好", "上面能否开门", "不用爬楼", "会下来拿",
        "交付方式", "爬楼说明", "备注", "客户性格", "性格备注", "附件数", "雷区",
    )

    fun build(records: List<RecordWithAttachments>, desensitised: Boolean): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val builder = StringBuilder()
        builder.append('\uFEFF')
        builder.append(headers.joinToString(",") { escape(it) }).append("\r\n")
        records.sortedByDescending { it.record.createdAt }.forEach { item ->
            val r = item.record
            val tail = when {
                r.phoneTail.isBlank() -> ""
                desensitised && r.phoneTail.length >= 2 ->
                    "**" + r.phoneTail.drop(2)
                else -> r.phoneTail
            }
            val row = listOf(
                formatter.format(Date(r.createdAt)),
                r.location,
                if (desensitised) "***" else r.orderNo,
                r.gender.label(),
                tail,
                r.phoneExt,
                r.floor,
                r.roomNo,
                r.accessCode,
                if (r.elevatorType == ElevatorType.UNKNOWN) "" else r.elevatorType.elevatorLabel(),
                r.doorIntact.doorLabel(),
                r.canOpenFromAbove.canOpenLabel(),
                if (r.noStairs == TriState.YES) "是" else if (r.noStairs == TriState.NO) "否" else "",
                if (r.willComeDown == TriState.YES) "会" else if (r.willComeDown == TriState.NO) "不会" else "",
                if (r.deliveryMethod == DeliveryMethod.UNKNOWN) "" else r.deliveryMethod.deliveryLabel(),
                r.climbNote,
                r.remark,
                r.personalityTags.joinToString(" / "),
                r.personalityNote,
                item.attachments.size.toString(),
                if (r.isWarning) "是" else "",
            )
            builder.append(row.joinToString(",") { escape(it) }).append("\r\n")
        }
        return builder.toString()
    }

    private fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val cleaned = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$cleaned\"" else cleaned
    }
}
