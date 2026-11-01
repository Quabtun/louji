package com.quabtun.louji.model

data class FormErrors(
    val location: String? = null,
    val phoneTail: String? = null,
    val orderNo: String? = null,
    val floor: String? = null,
    val roomNo: String? = null,
    val note: String? = null,
    val media: String? = null,
) {
    val hasAny: Boolean
        get() = listOf(location, phoneTail, orderNo, floor, roomNo, note, media).any { it != null }
}

object RecordValidator {
    fun validate(
        draft: RecordDraft,
        mediaCount: Int,
        maxMedia: Int = 20,
    ): FormErrors = FormErrors(
        location = when {
            draft.location.isBlank() -> "先填位置，方便下次找到"
            draft.location.length > 60 -> "位置最多 60 个字"
            else -> null
        },
        phoneTail = if (draft.phoneTail.isNotEmpty() && draft.phoneTail.length != 4) {
            "尾号填 4 位数字"
        } else {
            null
        },
        orderNo = if (draft.orderNo.length > 60) "订单号最多 60 个字" else null,
        floor = if (draft.floor.length > 12) "楼层最多 12 个字" else null,
        roomNo = if (draft.roomNo.length > 24) "房号最多 24 个字" else null,
        note = if (draft.personalityNote.length > 200) "备注最多 200 个字" else null,
        media = if (mediaCount > maxMedia) "一条记录最多 $maxMedia 个附件" else null,
    )
}
