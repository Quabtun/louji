package com.quabtun.louji.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.TriState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {
    private lateinit var database: LouJiDatabase
    private lateinit var recordDao: com.quabtun.louji.data.db.RecordDao
    private lateinit var attachmentDao: com.quabtun.louji.data.db.AttachmentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LouJiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recordDao = database.recordDao()
        attachmentDao = database.attachmentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun relationAndCascadeWork() = runBlocking {
        val id = recordDao.insert(record("阳光花园", "A1"))
        attachmentDao.insertAll(
            listOf(
                attachment(id, "a.jpg"),
                attachment(id, "b.mp4", MediaType.VIDEO),
            ),
        )
        val loaded = recordDao.getRecord(id)
        assertEquals(2, loaded?.attachments?.size)
        assertEquals("阳光花园", loaded?.record?.location)

        recordDao.deleteById(id)
        assertTrue(attachmentDao.getForRecord(id).isEmpty())
    }

    @Test
    fun searchCoversFieldsAndFilters() = runBlocking {
        val noStairs = recordDao.insert(
            record(
                "阳光花园",
                "A12345",
                noStairs = TriState.YES,
                willComeDown = TriState.YES,
                remark = "门禁在左手边",
                climbNote = "客户会下楼拿",
            ),
        )
        val stairs = recordDao.insert(
            record(
                "老小区",
                "B999",
                noStairs = TriState.NO,
                willComeDown = TriState.NO,
                climbNote = "电梯坏了",
            ),
        )
        assertEquals(2, recordDao.observeFiltered("", "ALL", 0).first().size)
        assertEquals(1, recordDao.observeFiltered("A12345", "ALL", 0).first().size)
        assertEquals(1, recordDao.observeFiltered("老小区", "ALL", 0).first().size)
        assertEquals(2, recordDao.observeFiltered("4821", "ALL", 0).first().size)
        assertEquals(1, recordDao.observeFiltered("门禁", "ALL", 0).first().size)
        assertEquals(1, recordDao.observeFiltered("下楼拿", "ALL", 0).first().size)
        assertEquals(1, recordDao.observeFiltered("电梯坏了", "ALL", 0).first().size)
        assertEquals(1, recordDao.observeFiltered("", "NO_STAIRS", 0).first().size)
        assertEquals(
            "老小区",
            recordDao.observeFiltered("", "STAIRS", 0).first().single().record.location,
        )
        assertEquals(1, recordDao.observeFiltered("", "WILL_COME_DOWN", 0).first().size)
        assertTrue(noStairs > 0)
        assertTrue(stairs > 0)
    }

    @Test
    fun archiveHidesFromActiveListAndStats() = runBlocking {
        val active = recordDao.insert(record("活跃", "1"))
        val archivedId = recordDao.insert(record("归档", "2"))
        recordDao.setArchived(archivedId, true)

        assertEquals(1, recordDao.observeFiltered("", "ALL", 0).first().size)
        assertEquals("活跃", recordDao.observeFiltered("", "ALL", 0).first().single().record.location)
        assertEquals(1, recordDao.observeFiltered("", "ALL", 1).first().size)
        assertEquals("归档", recordDao.observeFiltered("", "ALL", 1).first().single().record.location)

        val stats = recordDao.observeStats(0).first()
        assertEquals(1, stats.total)

        recordDao.setArchived(archivedId, false)
        assertEquals(2, recordDao.observeFiltered("", "ALL", 0).first().size)
        assertTrue(active > 0)
    }

    @Test
    fun statsAndRecentLocationsAggregate() = runBlocking {
        recordDao.insert(record("A", "1", noStairs = TriState.YES, willComeDown = TriState.YES))
        recordDao.insert(record("A", "2", noStairs = TriState.NO, willComeDown = TriState.YES))
        recordDao.insert(record("B", "3", noStairs = TriState.YES, willComeDown = TriState.NO))

        val stats = recordDao.observeStats(0).first()
        assertEquals(3, stats.total)
        assertEquals(2, stats.noStairs)
        assertEquals(2, stats.willComeDown)

        val locations = recordDao.observeRecentLocations().first()
        assertEquals(setOf("A", "B"), locations.toSet())
    }

    private fun record(
        location: String,
        orderNo: String,
        noStairs: TriState = TriState.UNKNOWN,
        willComeDown: TriState = TriState.UNKNOWN,
        remark: String = "",
        climbNote: String = "",
        archived: Boolean = false,
    ) = RecordEntity(
        location = location,
        orderNo = orderNo,
        gender = Gender.FEMALE,
        phoneTail = "4821",
        floor = "7",
        roomNo = "301",
        doorIntact = TriState.YES,
        canOpenFromAbove = TriState.NO,
        noStairs = noStairs,
        willComeDown = willComeDown,
        personalityTags = listOf("好沟通"),
        personalityNote = "",
        remark = remark,
        climbNote = climbNote,
        archived = archived,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    private fun attachment(
        recordId: Long,
        name: String,
        type: MediaType = MediaType.IMAGE,
    ) = AttachmentEntity(
        recordId = recordId,
        type = type,
        filePath = "/tmp/$name",
        thumbPath = null,
        mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
        sizeBytes = 10,
        durationMs = 0,
        createdAt = System.currentTimeMillis(),
    )
}
