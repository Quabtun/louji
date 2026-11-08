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
        assertEquals(2, decoded.schema)
        assertEquals("louji", decoded.app)
        assertEquals(2, decoded.recordCount)
        assertEquals(3, decoded.mediaCount)
    }

    @Test
    fun schema2FieldsRoundTrip() {
        val record = BackupRecord(
            location = "云顶小区",
            orderNo = "B2",
            phoneTail = "1234",
            phoneExt = "5678",
            accessCode = "2580",
            elevatorType = "CARD_CONTROL",
            deliveryMethod = "LOCKER",
            isWarning = true,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val decoded = json.decodeFromString<BackupRecord>(json.encodeToString(record))
        assertEquals("5678", decoded.phoneExt)
        assertEquals("2580", decoded.accessCode)
        assertEquals("CARD_CONTROL", decoded.elevatorType)
        assertEquals("LOCKER", decoded.deliveryMethod)
        assertTrue(decoded.isWarning)
    }

    @Test
    fun schema1RecordsStillDeserialize() {
        val legacy = """
            {"location":"老小区","orderNo":"A1","gender":"MALE","phoneTail":"4821",
             "floor":"6","roomNo":"601","doorIntact":"YES","canOpenFromAbove":"NO",
             "noStairs":"NO","willComeDown":"NO","personalityTags":["好沟通"],
             "personalityNote":"","remark":"","climbNote":"电梯坏了",
             "archived":true,"createdAt":100,"updatedAt":200,
             "attachments":[{"type":"IMAGE","file":"media/0_0.jpg","mimeType":"image/jpeg",
             "sizeBytes":10,"durationMs":0}]}
        """.trimIndent()
        val decoded = json.decodeFromString<BackupRecord>(legacy)
        assertEquals("老小区", decoded.location)
        assertEquals("电梯坏了", decoded.climbNote)
        assertTrue(decoded.archived)
        assertEquals("", decoded.phoneExt)
        assertEquals("UNKNOWN", decoded.elevatorType)
        assertEquals("OTHER", decoded.attachments.single().category)
    }

    @Test
    fun buildingProfileRoundTrips() {
        val building = BackupBuilding(
            location = "江景苑",
            elevatorType = "HALF_FLOOR",
            accessCode = "2580",
            doorIntact = "NO",
            canOpenFromAbove = "YES",
            lockerInfo = "门口丰巢",
            accessTags = listOf("有恶犬"),
            notes = "西侧小门上楼更快",
            pinned = true,
            createdAt = 1,
            updatedAt = 2,
        )
        val decoded = json.decodeFromString<BackupBuilding>(json.encodeToString(building))
        assertEquals(building, decoded)
    }
}
