package com.quabtun.louji

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.TriState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {
    @get:Rule(order = 0)
    val disableUpdateRule = DisableUpdateRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun listControlsHaveAccessibleLabels() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking {
            app.container.repository.clearAll()
            app.container.repository.save(
                draft = RecordDraft(
                    location = "阳光花园",
                    orderNo = "A1",
                    noStairs = TriState.NO,
                ),
                recordId = null,
                pendingMedia = emptyList(),
            )
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("阳光花园").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("记一单").assertExists()
        compose.onNodeWithTag("tab_me").assertExists()
        compose.onNodeWithContentDescription("搜索位置、订单号、尾号、房号").assertExists()
        // Status is spoken as text, not color alone.
        compose.onNodeWithText("要爬").assertExists()
        // Filter chips announce their selected state via Material FilterChip semantics.
        compose.onNodeWithText("要爬楼").assertExists()
        compose.onAllNodesWithTag("filter_sheet").assertCountEquals(1)
    }

    @Test
    fun settingsControlsAreLabelled() {
        compose.onNodeWithTag("tab_me").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("合并重复记录").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("应用锁").assertExists()
        compose.onNodeWithText("导出备份").assertExists()
        compose.onNodeWithText("合并重复记录").assertExists()
    }
}
