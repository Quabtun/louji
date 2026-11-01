package com.quabtun.louji

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
        compose.onNodeWithText("阳光花园 3 栋").performClick()
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
