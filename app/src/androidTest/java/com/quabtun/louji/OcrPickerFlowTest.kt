package com.quabtun.louji

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.data.SharePayload
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end check for the "OCR found text but no order fields" fallback: the raw OCR rows must
 * reach the edit screen and a tapped row must fill the field the rider chooses.
 */
@RunWith(AndroidJUnit4::class)
class OcrPickerFlowTest {
    @get:Rule(order = 0)
    val updateRule = DisableUpdateRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        clearData()
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        clearData()
    }

    @Test
    fun rawOcrLineFillsRoomFieldFromThePicker(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        val image = File(app.cacheDir, "ocr-picker-${UUID.randomUUID()}.png").apply {
            writeBytes(textPng("今日特价 18 元 牛肉面"))
        }
        val uri = Uri.fromFile(image)

        val ocr = withTimeout(180_000) { app.container.orderTextRecognizer.analyze(uri) }
        assertTrue(
            "OCR should read the drawn text (reason=${ocr.errorReason})",
            ocr.visualLines.isNotEmpty(),
        )
        val media = app.container.mediaStore.importFromUri(uri)

        // Same payload shape the share pipeline hands over on a parse failure.
        app.container.shareIntake.submit(SharePayload(ocr = ocr, imageMedia = media))

        compose.onNodeWithTag("create_record").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("ocr_line_picker").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("ocr_line_picker").performScrollTo().assertIsDisplayed()

        val row = ocr.visualLines.indexOfFirst { it.contains("18") }.coerceAtLeast(0)
        compose.onNodeWithTag("ocr_line_$row").performScrollTo().performClick()
        compose.onNodeWithTag("ocr_field_ROOM").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("field_room").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("field_room").assertTextContains("18")
    }

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
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun clearData() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking {
            app.container.shareIntake.consume()
            app.container.repository.clearAll()
            app.container.mediaStore.sweepOrphans(emptySet())
        }
        app.container.deleteUndo.consume()
    }
}
