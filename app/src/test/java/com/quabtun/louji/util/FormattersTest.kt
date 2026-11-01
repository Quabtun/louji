package com.quabtun.louji.util

import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.TriState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {
    @Test
    fun floorDisplayAddsFOnlyForNumbers() {
        assertEquals("7F", floorDisplay("7"))
        assertEquals("B1", floorDisplay("B1"))
        assertEquals("3A", floorDisplay("3A"))
        assertEquals("", floorDisplay(""))
    }

    @Test
    fun placeDisplayUsesDashWhenEmpty() {
        assertEquals("7F · 2-301", placeDisplay("7", "2-301"))
        assertEquals("7F", placeDisplay("7", ""))
        assertEquals("未填", placeDisplay("", ""))
    }

    @Test
    fun durationIsMinuteColonSecond() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:04", formatDuration(4_000))
        assertEquals("1:05", formatDuration(65_000))
    }

    @Test
    fun shareTextIncludesAllImportantFields() {
        val record = RecordEntity(
            id = 1,
            location = "阳光花园",
            orderNo = "A1",
            gender = Gender.FEMALE,
            phoneTail = "4821",
            floor = "7",
            roomNo = "301",
            doorIntact = TriState.YES,
            canOpenFromAbove = TriState.NO,
            noStairs = TriState.YES,
            willComeDown = TriState.YES,
            personalityTags = listOf("爽快"),
            personalityNote = "放门口",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val attachment = AttachmentEntity(
            id = 1,
            recordId = 1,
            type = MediaType.IMAGE,
            filePath = "/tmp/a.jpg",
            thumbPath = null,
            mimeType = "image/jpeg",
            sizeBytes = 1,
            durationMs = 0,
            createdAt = 1,
        )
        val text = RecordWithAttachments(record, listOf(attachment)).shareText()
        assertTrue(text.contains("阳光花园"))
        assertTrue(text.contains("A1"))
        assertTrue(text.contains("4821"))
        assertTrue(text.contains("7F · 301"))
        assertTrue(text.contains("不用爬楼：是"))
        assertTrue(text.contains("门：完好"))
        assertTrue(text.contains("爽快"))
        assertTrue(text.contains("1 个附件"))
    }
}
