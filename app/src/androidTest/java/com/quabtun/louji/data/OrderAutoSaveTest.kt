package com.quabtun.louji.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.TriState
import com.quabtun.louji.util.ClipboardOrderParser
import com.quabtun.louji.util.ParsedOrder
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderAutoSaveTest {

    private lateinit var database: LouJiDatabase
    private lateinit var mediaStore: LocalMediaStore
    private lateinit var repository: RecordRepository
    private lateinit var autoSaver: OrderAutoSaver
    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LouJiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempRoot = File(context.cacheDir, "order-autosave-${UUID.randomUUID()}").apply { mkdirs() }
        mediaStore = LocalMediaStore(
            context,
            File(tempRoot, "media"),
            File(tempRoot, "thumbs"),
        )
        repository = RecordRepository(database, mediaStore)
        autoSaver = OrderAutoSaver(repository, mediaStore)
    }

    @After
    fun tearDown() {
        database.close()
        tempRoot.deleteRecursively()
    }

    @Test
    fun plainTextCreatesRecordWithParsedFields() = runBlocking {
        val text = """
            订单号：D5566
            收货地址：江景苑 2 栋
            楼层：21层
            房号：2103
            王女士 尾号 2145
        """.trimIndent()

        val saved = autoSaver.save(
            parsed = ClipboardOrderParser.parse(text),
            sourceLabel = "剪贴板",
            rawText = text,
        )

        val record = repository.getRecord(saved.recordId)?.record
        assertNotNull(record)
        assertEquals("江景苑 2 栋", record!!.location)
        assertEquals("D5566", record.orderNo)
        assertEquals("2145", record.phoneTail)
        assertEquals("21", record.floor)
        assertEquals("2103", record.roomNo)
        assertEquals(TriState.UNKNOWN, record.noStairs)
        assertTrue(repository.getRecord(saved.recordId)!!.attachments.isEmpty())
    }

    @Test
    fun screenshotIsCopiedAndAttachedAsOrderScreenshot() = runBlocking {
        val image = File(tempRoot, "order.png").apply {
            FileOutputStream(this).use { output ->
                Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    .apply { eraseColor(Color.WHITE) }
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        val text = "阳光花园3号楼2单元501室\n李先生 13812344821"

        val saved = autoSaver.save(
            parsed = ClipboardOrderParser.parse(text),
            screenshotUri = Uri.fromFile(image),
            sourceLabel = "订单截图",
            rawText = text,
        )

        assertEquals("5", saved.draft.floor)
        assertEquals("501", saved.draft.roomNo)
        val attachments = repository.getRecord(saved.recordId)!!.attachments
        assertEquals(1, attachments.size)
        assertEquals(AttachmentCategory.ORDER_SCREENSHOT, attachments[0].category)
        assertTrue(File(attachments[0].filePath).exists())
    }

    @Test
    fun buildingProfileIsInheritedAndDirectElevatorMeansNoStairs() = runBlocking {
        val now = System.currentTimeMillis()
        repository.saveBuilding(
            BuildingProfileEntity(
                location = "江景苑 2 栋",
                elevatorType = ElevatorType.DIRECT,
                accessCode = "8899",
                doorIntact = TriState.YES,
                canOpenFromAbove = TriState.NO,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val text = "订单号：D5566\n收货地址：江景苑 2 栋\n楼层：21层\n房号：2103"

        val saved = autoSaver.save(
            parsed = ClipboardOrderParser.parse(text),
            sourceLabel = "剪贴板",
            rawText = text,
        )

        assertEquals("8899", saved.draft.accessCode)
        assertEquals(ElevatorType.DIRECT, saved.draft.elevatorType)
        assertEquals(TriState.YES, saved.draft.doorIntact)
        assertEquals(TriState.NO, saved.draft.canOpenFromAbove)
        assertEquals(TriState.YES, saved.draft.noStairs)

        val record = repository.getRecord(saved.recordId)!!.record
        assertEquals("8899", record.accessCode)
        assertEquals(ElevatorType.DIRECT, record.elevatorType)
        assertEquals(TriState.YES, record.noStairs)
    }

    @Test
    fun stairsKeywordsOverrideAnUnknownBuilding() = runBlocking {
        val saved = autoSaver.save(
            parsed = ParsedOrder(location = "老城小区", floor = "7", roomNo = "702"),
            sourceLabel = "订单截图",
            rawText = "老城小区 7楼 702 步梯房 无电梯",
        )

        assertEquals(TriState.NO, saved.draft.noStairs)
    }

    @Test
    fun missingLocationFallsBackToFirstLine() = runBlocking {
        val saved = autoSaver.save(
            parsed = ParsedOrder(orderNo = "D1"),
            sourceLabel = "分享文字",
            rawText = "临时地址 5 号楼\n订单号 D1",
        )

        assertEquals("临时地址 5 号楼", saved.draft.location)
        assertEquals("D1", saved.draft.orderNo)
    }

    @Test
    fun missingLocationUsesPlaceholderWithoutCreatingABuilding() = runBlocking {
        val saved = autoSaver.save(
            parsed = ParsedOrder(orderNo = "D2"),
            sourceLabel = "分享文字",
            rawText = null,
        )

        assertEquals(OrderAutoSaver.UNKNOWN_LOCATION, saved.draft.location)
        assertEquals("D2", saved.draft.orderNo)
        assertTrue(repository.findBuilding(OrderAutoSaver.UNKNOWN_LOCATION) == null)
    }
}
