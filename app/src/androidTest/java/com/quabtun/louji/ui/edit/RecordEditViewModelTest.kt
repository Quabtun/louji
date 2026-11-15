package com.quabtun.louji.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.OrderTextRecognizer
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.ShareIntake
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.model.AttachmentCategory
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordEditViewModelTest {
    private lateinit var database: LouJiDatabase
    private lateinit var mediaStore: LocalMediaStore
    private lateinit var repository: RecordRepository
    private lateinit var viewModel: RecordEditViewModel
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LouJiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = File(context.cacheDir, "edit-vm-${UUID.randomUUID()}").apply { mkdirs() }
        mediaStore = LocalMediaStore(
            context,
            File(root, "media"),
            File(root, "thumbs"),
        )
        repository = RecordRepository(database, mediaStore)
        viewModel = RecordEditViewModel(
            repository = repository,
            mediaStore = mediaStore,
            recognizer = OrderTextRecognizer(context),
            shareIntake = ShareIntake(),
        )
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun photoPickerUriWithoutExtensionStillBecomesOrderScreenshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A Photo Picker style URI: no file extension, and the resolver reports no MIME type.
        val first = File(root, "picker-${UUID.randomUUID()}").apply {
            writeBytes(whitePng())
        }
        val second = File(root, "picker-${UUID.randomUUID()}").apply {
            writeBytes(whitePng())
        }

        viewModel.importUris(listOf(Uri.fromFile(first), Uri.fromFile(second)))

        val state = withTimeout(15_000) {
            viewModel.ui.first { !it.importing && it.pendingMedia.size == 2 }
        }
        assertEquals(
            AttachmentCategory.ORDER_SCREENSHOT,
            state.pendingMedia[0].category,
        )
        assertEquals(AttachmentCategory.OTHER, state.pendingMedia[1].category)
        assertTrue(File(state.pendingMedia[0].filePath).isFile)
        assertTrue(context.contentResolver.getType(Uri.fromFile(first)) == null)
    }

    @Test
    fun failedOcrStillAttachesTheScreenshot() = runBlocking {
        // A blank white image has no order text, so parsing must fail — the screenshot still has
        // to be attached as the order screenshot before OCR even runs.
        val blank = File(root, "blank.png").apply { writeBytes(whitePng()) }

        viewModel.recognizeImage(Uri.fromFile(blank), attach = true)

        val state = withTimeout(120_000) {
            viewModel.ui.first { !it.recognizing && it.pendingMedia.isNotEmpty() }
        }
        assertEquals(1, state.pendingMedia.size)
        assertEquals(
            AttachmentCategory.ORDER_SCREENSHOT,
            state.pendingMedia.single().category,
        )
        assertTrue(File(state.pendingMedia.single().filePath).isFile)
        // A blank image must not invent OCR lines or parsed fields.
        assertTrue(state.ocrLines.isEmpty())
        assertTrue(state.recognizedFields.isEmpty())
    }

    @Test
    fun unrelatedChineseTextKeepsOcrLinesForManualPick() = runBlocking {
        val image = File(root, "unrelated.png").apply {
            writeBytes(textPng("今日特价 18 元 牛肉面"))
        }

        viewModel.recognizeImage(Uri.fromFile(image), attach = true)

        val state = withTimeout(180_000) {
            viewModel.ui.first { !it.recognizing && it.ocrLines.isNotEmpty() }
        }
        assertEquals(1, state.pendingMedia.size)
        assertEquals(
            AttachmentCategory.ORDER_SCREENSHOT,
            state.pendingMedia.single().category,
        )
        // Unrelated text parses to no order fields, but the raw OCR lines survive for the picker.
        assertTrue(state.recognizedFields.isEmpty())
        val line = state.ocrLines.first { it.contains("18") }
        assertTrue(line.isNotBlank())

        viewModel.applyOcrLine(line, OcrField.ROOM)
        assertEquals("18", viewModel.ui.value.draft.roomNo)
    }

    @Test
    fun parsedOcrDefersTheWriteSoTheUiCanConfirmOverwrites() = runBlocking {
        viewModel.setLocation("旧小区")
        val image = File(root, "order-text.png").apply {
            writeBytes(textPng("收货地址 江景苑2栋 房号2103"))
        }
        var handed: com.quabtun.louji.util.ParsedOrder? = null

        viewModel.recognizeImage(Uri.fromFile(image), attach = true) { handed = it }

        withTimeout(180_000) {
            viewModel.ui.first { !it.recognizing && handed != null }
        }
        assertEquals(handed?.summary(), "江景苑2栋", handed?.location)
        assertEquals(handed?.summary(), "2103", handed?.roomNo)
        // The caller owns the write now: the existing location must survive until the shared
        // overwrite dialog is confirmed.
        assertEquals("旧小区", viewModel.ui.value.draft.location)
    }

    private fun whitePng(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        return bitmap.toPngBytes()
    }

    /** Renders readable text so the real ML Kit pipeline has something to recognise. */
    private fun textPng(text: String): ByteArray {
        val bitmap = Bitmap.createBitmap(960, 280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 68f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(text, 40f, 170f, paint)
        return bitmap.toPngBytes()
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, output)
        recycle()
        return output.toByteArray()
    }
}
