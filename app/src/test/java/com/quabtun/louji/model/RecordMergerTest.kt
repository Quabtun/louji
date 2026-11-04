package com.quabtun.louji.model

import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordMergerTest {
    @Test
    fun mergeKeyPrefersOrderNumber() {
        assertEquals(
            "order|阳光花园|A1",
            RecordMerger.mergeKey(entity(location = "阳光花园", orderNo = "A1")),
        )
    }

    @Test
    fun mergeKeyFallsBackToPlaceWhenOrderBlank() {
        assertEquals(
            "place|老小区|7|301",
            RecordMerger.mergeKey(entity(location = "老小区", orderNo = "", floor = "7", roomNo = "301")),
        )
    }

    @Test
    fun mergeKeyIsNullForLocationOnly() {
        assertNull(RecordMerger.mergeKey(entity(location = "只有位置", orderNo = "")))
    }

    @Test
    fun findDuplicateGroupsGroupsByKey() {
        val a1 = withAttachments(entity(1, "阳光花园", "A1"))
        val a2 = withAttachments(entity(2, "阳光花园", "A1"))
        val b = withAttachments(entity(3, "老小区", "B9"))
        val groups = RecordMerger.findDuplicateGroups(listOf(a1, a2, b))
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().size)
    }

    @Test
    fun mergedFieldsUnionsTagsAndFillsBlanks() {
        val base = entity(
            id = 1,
            location = "阳光花园",
            orderNo = "A1",
            personalityTags = listOf("好沟通"),
            remark = "",
            climbNote = "",
            noStairs = TriState.UNKNOWN,
        )
        val other = entity(
            id = 2,
            location = "阳光花园",
            orderNo = "A1",
            phoneTail = "4821",
            personalityTags = listOf("爽快"),
            remark = "门禁在左手边",
            climbNote = "电梯直达",
            noStairs = TriState.YES,
        )
        val merged = RecordMerger.mergedFields(base, listOf(other))
        assertEquals(listOf("好沟通", "爽快"), merged.personalityTags)
        assertEquals("4821", merged.phoneTail)
        assertEquals("门禁在左手边", merged.remark)
        assertEquals("电梯直达", merged.climbNote)
        assertEquals(TriState.YES, merged.noStairs)
    }

    private fun entity(
        id: Long = 0,
        location: String,
        orderNo: String,
        floor: String = "",
        roomNo: String = "",
        phoneTail: String = "",
        personalityTags: List<String> = emptyList(),
        remark: String = "",
        climbNote: String = "",
        noStairs: TriState = TriState.UNKNOWN,
    ) = RecordEntity(
        id = id,
        location = location,
        orderNo = orderNo,
        gender = Gender.UNKNOWN,
        phoneTail = phoneTail,
        floor = floor,
        roomNo = roomNo,
        doorIntact = TriState.UNKNOWN,
        canOpenFromAbove = TriState.UNKNOWN,
        noStairs = noStairs,
        willComeDown = TriState.UNKNOWN,
        personalityTags = personalityTags,
        personalityNote = "",
        createdAt = id,
        updatedAt = id,
        remark = remark,
        climbNote = climbNote,
    )

    private fun withAttachments(record: RecordEntity) = RecordWithAttachments(record, emptyList())
}
