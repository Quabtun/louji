package com.quabtun.louji.ui.stats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.TriState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsPeriodTest {
    private lateinit var database: LouJiDatabase
    private lateinit var repository: RecordRepository
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LouJiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = File(context.cacheDir, "stats-period-${UUID.randomUUID()}").apply { mkdirs() }
        repository = RecordRepository(
            database,
            LocalMediaStore(context, File(root, "media"), File(root, "thumbs")),
        )
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun decisionStatsAndRankingsFollowTheSelectedPeriod() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        suspend fun insert(location: String, stairs: TriState, createdAt: Long, archived: Boolean = false) {
            database.recordDao().insert(
                RecordEntity(
                    location = location,
                    orderNo = "S$createdAt",
                    gender = Gender.UNKNOWN,
                    phoneTail = "",
                    floor = "7",
                    roomNo = "701",
                    doorIntact = TriState.UNKNOWN,
                    canOpenFromAbove = TriState.UNKNOWN,
                    noStairs = stairs,
                    willComeDown = TriState.UNKNOWN,
                    personalityTags = emptyList(),
                    personalityNote = "",
                    remark = "",
                    climbNote = "",
                    archived = archived,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )
        }
        insert("今日花园", TriState.YES, now - 60_000)
        insert("本周公寓", TriState.NO, now - 2 * day)
        insert("上月小区", TriState.NO, now - 40 * day)
        insert("归档大厦", TriState.YES, now - 60_000, archived = true)

        val viewModel = StatsViewModel(repository)

        viewModel.setPeriod(StatsPeriod.TODAY)
        val today = withTimeout(10_000) {
            viewModel.data.first { it.period == StatsPeriod.TODAY && it.decision.total > 0 }
        }
        assertEquals(1, today.decision.total)
        assertEquals(1, today.decision.noStairs)
        assertEquals(0, today.decision.stairs)

        viewModel.setPeriod(StatsPeriod.ALL)
        val all = withTimeout(10_000) {
            viewModel.data.first { it.period == StatsPeriod.ALL && it.decision.total >= 3 }
        }
        assertEquals(3, all.decision.total)
        assertEquals(1, all.decision.noStairs)
        assertEquals(2, all.decision.stairs)
        assertEquals(
            3,
            withTimeout(10_000) { viewModel.rankings.first { it.size == 3 } }.sumOf { it.total },
        )
    }
}
