package com.quabtun.louji.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.model.DateRangePresets
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

    @Test
    fun trashHidesFromActiveListAndIsObservableAndRestorable() = runBlocking {
        val activeId = recordDao.insert(record("活跃", "1"))
        val trashedId = recordDao.insert(record("回收", "2"))
        recordDao.setDeletedAt(trashedId, 1000L)

        assertEquals(1, recordDao.observeFiltered("", "ALL", 0).first().size)
        assertEquals("活跃", recordDao.observeFiltered("", "ALL", 0).first().single().record.location)

        val trashed = recordDao.observeTrashed().first()
        assertEquals(1, trashed.size)
        assertEquals("回收", trashed.single().record.location)

        // Trashed records are excluded from stats and recent-location suggestions.
        assertEquals(1, recordDao.observeStats(0).first().total)
        assertEquals(listOf("活跃"), recordDao.observeRecentLocations().first())

        // Purge query returns only records deleted at or before the cutoff.
        assertEquals(1, recordDao.getTrashedBefore(2000L).size)
        assertEquals(0, recordDao.getTrashedBefore(500L).size)

        recordDao.setDeletedAt(trashedId, 0L)
        assertEquals(2, recordDao.observeFiltered("", "ALL", 0).first().size)
        assertEquals(0, recordDao.observeTrashed().first().size)
        assertTrue(activeId > 0)
    }

    @Test
    fun insightsAggregateDecisionsAndLocations() = runBlocking {
        recordDao.insert(record("阳光花园", "1", noStairs = TriState.YES, willComeDown = TriState.YES))
        recordDao.insert(record("阳光花园", "2", noStairs = TriState.NO, willComeDown = TriState.NO))
        recordDao.insert(record("老小区", "3", noStairs = TriState.NO, willComeDown = TriState.YES))
        val archivedId = recordDao.insert(record("归档小区", "9", noStairs = TriState.YES))
        recordDao.setArchived(archivedId, true)

        val decision = recordDao.observeDecisionStats().first()
        assertEquals(3, decision.total)
        assertEquals(1, decision.noStairs)
        assertEquals(2, decision.stairs)
        assertEquals(0, decision.unknown)
        assertEquals(2, decision.willComeDown)
        assertEquals(1, decision.wontComeDown)

        val locations = recordDao.observeLocationStats().first()
        assertEquals(listOf("阳光花园", "老小区"), locations.map { it.location })
        val sunny = locations.first { it.location == "阳光花园" }
        assertEquals(2, sunny.total)
        assertEquals(1, sunny.noStairs)
        assertEquals(1, sunny.willComeDown)
    }

    @Test
    fun dateRangeFilterLimitsByCreatedAt() = runBlocking {
        val base = 1_700_000_000_000L
        val oldId = recordDao.insert(record("旧记录", "1").copy(createdAt = base - 10 * DateRangePresets.DAY_MS))
        val recentId = recordDao.insert(record("新记录", "2").copy(createdAt = base - 2 * DateRangePresets.DAY_MS))

        // Unbounded range returns everything.
        assertEquals(2, recordDao.observeFilteredByDate("", "ALL", 0, 0L, 0L).first().size)

        val last7 = DateRangePresets.lastDays(base, 7)
        val in7 = recordDao.observeFilteredByDate("", "ALL", 0, last7.start, last7.end).first()
        assertEquals(listOf("新记录"), in7.map { it.record.location })

        val last30 = DateRangePresets.lastDays(base, 30)
        assertEquals(2, recordDao.observeFilteredByDate("", "ALL", 0, last30.start, last30.end).first().size)

        val explicitStart = base - 3 * DateRangePresets.DAY_MS
        val explicitEnd = base - 1 * DateRangePresets.DAY_MS
        val inExplicit = recordDao.observeFilteredByDate("", "ALL", 0, explicitStart, explicitEnd).first()
        assertEquals(listOf("新记录"), inExplicit.map { it.record.location })
        assertTrue(oldId > 0)
        assertTrue(recentId > 0)
    }

    @Test
    fun batchArchiveAndSoftDeleteWork() = runBlocking {
        val a = recordDao.insert(record("A", "1"))
        val b = recordDao.insert(record("B", "2"))
        recordDao.insert(record("C", "3"))

        recordDao.setArchivedForIds(listOf(a, b), true)
        assertEquals(listOf("C"), recordDao.observeFiltered("", "ALL", 0).first().map { it.record.location })
        assertEquals(2, recordDao.observeFiltered("", "ALL", 1).first().size)

        recordDao.setArchivedForIds(listOf(a, b), false)
        assertEquals(3, recordDao.observeFiltered("", "ALL", 0).first().size)

        recordDao.setDeletedAtForIds(listOf(a, b), 1000L)
        assertEquals(listOf("C"), recordDao.observeFiltered("", "ALL", 0).first().map { it.record.location })
        assertEquals(2, recordDao.observeTrashed().first().size)
    }

    @Test
    fun reparentAttachmentsWorks() = runBlocking {
        val a = recordDao.insert(record("A", "1"))
        val b = recordDao.insert(record("B", "2"))
        attachmentDao.insertAll(
            listOf(attachment(a, "a.jpg"), attachment(a, "b.jpg"), attachment(b, "c.jpg")),
        )
        assertEquals(2, attachmentDao.getForRecord(a).size)
        assertEquals(1, attachmentDao.getForRecord(b).size)

        attachmentDao.reparentAttachments(listOf(b), a)
        assertEquals(3, attachmentDao.getForRecord(a).size)
        assertEquals(0, attachmentDao.getForRecord(b).size)
    }

    @Test
    fun pagingSourceLoadsPagesWithoutDuplicates() = runBlocking {
        val base = 1_000_000L
        repeat(25) { i ->
            recordDao.insert(record("位置$i", "订单$i").copy(createdAt = base - i))
        }

        val source = recordDao.loadFilteredPagedSource("", "ALL", 0, 0L, 0L)
        val seen = mutableListOf<Long>()
        var key: Int? = 0
        var guard = 0
        while (guard < 6) {
            val result = source.load(
                androidx.paging.PagingSource.LoadParams.Refresh(
                    key = key,
                    loadSize = 10,
                    placeholdersEnabled = false,
                ),
            )
            val page = result as? androidx.paging.PagingSource.LoadResult.Page ?: break
            seen += page.data.map { it.record.id }
            key = page.nextKey
            if (page.data.isEmpty() || page.nextKey == null) break
            guard++
        }
        assertEquals(25, seen.size)
        assertEquals(25, seen.toSet().size)
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
