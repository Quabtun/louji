package com.quabtun.louji.model

data class RecordDraft(
    val location: String = "",
    val orderNo: String = "",
    val gender: Gender = Gender.UNKNOWN,
    val phoneTail: String = "",
    val floor: String = "",
    val roomNo: String = "",
    val doorIntact: TriState = TriState.UNKNOWN,
    val canOpenFromAbove: TriState = TriState.UNKNOWN,
    val noStairs: TriState = TriState.UNKNOWN,
    val willComeDown: TriState = TriState.UNKNOWN,
    val personalityTags: List<String> = emptyList(),
    val personalityNote: String = "",
    val remark: String = "",
    val climbNote: String = "",
) {
    fun hasMeaningfulContent(): Boolean =
        location.isNotBlank() ||
            orderNo.isNotBlank() ||
            phoneTail.isNotBlank() ||
            floor.isNotBlank() ||
            roomNo.isNotBlank() ||
            personalityNote.isNotBlank() ||
            remark.isNotBlank() ||
            climbNote.isNotBlank() ||
            personalityTags.isNotEmpty() ||
            noStairs != TriState.UNKNOWN ||
            doorIntact != TriState.UNKNOWN ||
            canOpenFromAbove != TriState.UNKNOWN ||
            willComeDown != TriState.UNKNOWN
}
