package com.quabtun.louji.model

import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSorterTest {
    private fun record(id: Long, floor: String, location: String, createdAt: Long) =
        RecordWithAttachments(
            RecordEntity(
                id = id,
                location = location,
                orderNo = "",
                gender = Gender.UNKNOWN,
                phoneTail = "",
                floor = floor,
                roomNo = "",
                doorIntact = TriState.UNKNOWN,
                canOpenFromAbove = TriState.UNKNOWN,
                noStairs = TriState.UNKNOWN,
                willComeDown = TriState.UNKNOWN,
                personalityTags = emptyList(),
                personalityNote = "",
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
            emptyList<AttachmentEntity>(),
        )

    @Test
    fun floorSortIsNumericWithTextFloorsLast() {
        val items = listOf(
            record(1, "12", "A", 3),
            record(2, "7", "B", 2),
            record(3, "B1", "C", 4),
            record(4, "", "D", 1),
        )
        val sorted = RecordSorter.sort(items, RecordSort.FLOOR).map { it.record.floor }
        assertEquals(listOf("B1", "7", "12", ""), sorted)
    }

    @Test
    fun locationSortIsCaseInsensitiveAndStableByTime() {
        val items = listOf(
            record(1, "7", "B楼", 2),
            record(2, "7", "a楼", 1),
            record(3, "7", "A楼", 3),
        )
        val sorted = RecordSorter.sort(items, RecordSort.LOCATION).map { it.record.location }
        assertEquals(listOf("A楼", "a楼", "B楼"), sorted)
    }

    @Test
    fun updatedSortIsNewestFirst() {
        val items = listOf(
            record(1, "7", "A", 1),
            record(2, "7", "B", 3),
            record(3, "7", "C", 2),
        )
        val sorted = RecordSorter.sort(items, RecordSort.UPDATED).map { it.record.createdAt }
        assertEquals(listOf(3L, 2L, 1L), sorted)
    }
}
