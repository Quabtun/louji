package com.quabtun.louji.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun recordWithAttachmentsRoundTrips() {
        val record = BackupRecord(
            location = "阳光花园 3 栋",
            orderNo = "A12345",
            gender = "FEMALE",
            phoneTail = "4821",
            floor = "7",
            roomNo = "2-301",
            doorIntact = "YES",
            canOpenFromAbove = "NO",
            noStairs = "YES",
            willComeDown = "YES",
            personalityTags = listOf("好沟通", "爽快"),
            personalityNote = "打电话会接",
            remark = "门禁在左手边",
            climbNote = "客户会下楼拿",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_100_000L,
            attachments = listOf(
                BackupAttachment(
                    type = "IMAGE",
                    file = "media/0_0.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1234,
                    durationMs = 0,
                ),
            ),
        )
        val decoded = json.decodeFromString<BackupRecord>(json.encodeToString(record))
        assertEquals(record, decoded)
    }

    @Test
    fun absentOptionalFieldsAreTolerated() {
        val decoded = json.decodeFromString<BackupRecord>(
            """{"location":"A","createdAt":1,"updatedAt":1}""",
        )
        assertEquals("", decoded.orderNo)
        assertEquals("", decoded.climbNote)
        assertEquals("UNKNOWN", decoded.gender)
        assertTrue(decoded.attachments.isEmpty())
    }

    @Test
    fun manifestCarriesSchemaAndCounts() {
        val manifest = BackupManifest(
            exportedAt = 123L,
            appVersion = "1.0.0",
            recordCount = 2,
            mediaCount = 3,
        )
        val decoded = json.decodeFromString<BackupManifest>(json.encodeToString(manifest))
        assertEquals(1, decoded.schema)
        assertEquals("louji", decoded.app)
        assertEquals(2, decoded.recordCount)
        assertEquals(3, decoded.mediaCount)
    }
}
