package com.quabtun.louji

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.MediaType
import com.quabtun.louji.model.RecordDraft
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordFlowTest {
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
    fun createRecordAndInspectAllFields() {
        createRecord()

        compose.onAllNodesWithText("阳光花园 3 栋").assertCountEquals(3)
        compose.onNodeWithText("7F · 2-301").assertIsDisplayed()
        compose.onNodeWithText("门是否完好").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("上面能开门").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("会下来拿").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("好沟通").performScrollTo().assertExists()
        compose.onNodeWithText("Nice, calls before arriving").performScrollTo().assertExists()
        compose.onNodeWithText("门禁在左手边").performScrollTo().assertExists()
        compose.onNodeWithText("爬楼说明").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("客户会下楼拿，电梯直达").performScrollTo().assertExists()
        compose.onAllNodesWithText("不用爬楼").assertCountEquals(2)
    }

    @Test
    fun searchFilterAndDelete() {
        createRecord()
        backToList()

        compose.onNodeWithTag("search_field").performTextInput("A12345")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("阳光花园 3 栋").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("阳光花园 3 栋").assertIsDisplayed()

        compose.onNodeWithTag("search_field").performTextClearance()
        compose.onNodeWithTag("search_field").performTextInput("电梯直达")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("阳光花园 3 栋").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("阳光花园 3 栋").assertIsDisplayed()

        compose.onNodeWithTag("search_field").performTextClearance()
        compose.waitForIdle()
        compose.onNodeWithTag("filter_STAIRS").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("没有匹配的记录").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("没有匹配的记录").assertIsDisplayed()

        compose.onNodeWithTag("filter_NO_STAIRS").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("阳光花园 3 栋").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("filter_ALL").performClick()
        compose.onNodeWithText("备注：门禁在左手边").assertExists()
        compose.onNodeWithText("详细信息").performClick()
        waitForDetail()

        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("删除记录").performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("还没有记录").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("还没有记录").assertIsDisplayed()
    }

    @Test
    fun cardOpensMediaGalleryAndDetailEntry() {
        createRecord()
        backToList()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("阳光花园 3 栋").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("阳光花园 3 栋").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("record_media").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("还没有现场图片或视频").assertIsDisplayed()
        compose.onNodeWithTag("media_details").performClick()
        waitForDetail()
        compose.onNodeWithText("7F · 2-301").assertIsDisplayed()
    }

    @Test
    fun orderThumbnailOpensAndClosesZoom() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        val shot = File(app.container.mediaStore.mediaDirectory(), "order-shot-test.png")
        val bitmap = Bitmap.createBitmap(90, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(255, 200, 61))
        shot.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val recordId = runBlocking {
            app.container.repository.save(
                RecordDraft(
                    location = "截图测试 1 栋",
                    orderNo = "S001",
                    floor = "9",
                    roomNo = "901",
                ),
                null,
                listOf(
                    MediaDraft(
                        type = MediaType.IMAGE,
                        filePath = shot.absolutePath,
                        thumbPath = null,
                        mimeType = "image/png",
                        sizeBytes = shot.length(),
                        durationMs = 0L,
                    ),
                ),
            )
        }

        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("order_thumb_$recordId").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("order_thumb_$recordId").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("order_zoom").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("record_media").assertCountEquals(0)
        compose.onNodeWithTag("order_zoom_image").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("order_zoom").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("order_thumb_$recordId").assertIsDisplayed()
    }

    @Test
    fun editUpdatesTheRecord() {
        createRecord()
        compose.onNodeWithContentDescription("编辑").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("field_location").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("阳光花园 3 栋").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("field_location").performTextClearance()
        compose.onNodeWithTag("field_location").performTextInput("新地址 9 栋")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save_record").performClick()
        waitForDetail()
        compose.onAllNodesWithText("新地址 9 栋").assertCountEquals(3)
        compose.onNodeWithText("7F · 2-301").assertIsDisplayed()
    }

    private fun createRecord() {
        compose.onNodeWithTag("create_record").performClick()
        compose.onNodeWithTag("decision_no_stairs").performClick()
        compose.onNodeWithTag("field_climb_note").performTextInput("客户会下楼拿，电梯直达")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("field_location").performScrollTo().performTextInput("阳光花园 3 栋")
        compose.onNodeWithTag("field_order").performScrollTo().performTextInput("A12345")
        compose.onNodeWithTag("field_tail").performScrollTo().performTextInput("4821")
        compose.onNodeWithTag("gender_FEMALE").performScrollTo().performClick()
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("field_floor").performScrollTo().performTextInput("7")
        compose.onNodeWithTag("field_room").performScrollTo().performTextInput("2-301")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("door_yes").performScrollTo().performClick()
        compose.onNodeWithTag("open_above_no").performScrollTo().performClick()
        compose.onNodeWithTag("come_down_yes").performScrollTo().performClick()
        compose.onNodeWithTag("tag_好沟通").performScrollTo().performClick()
        compose.onNodeWithTag("field_note").performScrollTo()
            .performTextInput("Nice, calls before arriving")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("field_remark").performScrollTo()
            .performTextInput("门禁在左手边")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("save_record").performClick()
        waitForDetail()
    }

    private fun waitForDetail() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("record_detail").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun backToList() {
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("record_list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clearData() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking {
            app.container.repository.clearAll()
            app.container.mediaStore.sweepOrphans(emptySet())
        }
    }
}
