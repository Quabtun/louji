package com.quabtun.louji

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.TriState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuildingFlowTest {
    @get:Rule(order = 0)
    val disableUpdateRule = DisableUpdateRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun savingRecordCreatesBuildingProfileAndWikiOpens() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking {
            app.container.repository.clearAll()
            app.container.repository.save(
                draft = RecordDraft(
                    location = "江景苑 2 栋",
                    orderNo = "B1",
                    noStairs = TriState.NO,
                    elevatorType = ElevatorType.STAIRS_ONLY,
                    accessCode = "2580",
                    doorIntact = TriState.YES,
                    canOpenFromAbove = TriState.NO,
                ),
                recordId = null,
                pendingMedia = emptyList(),
            )
            app.container.repository.rebuildBuildingProfiles()
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tab_buildings").performClick()
        compose.waitUntil(6_000) {
            compose.onAllNodesWithTag("building_card_江景苑 2 栋").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("building_card_江景苑 2 栋").assertIsDisplayed()
        compose.onNodeWithText("无电梯步梯", substring = true).assertExists()

        compose.onNodeWithTag("building_card_江景苑 2 栋").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("本栋历史房号（1）").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("#2580", substring = true).assertExists()
        compose.onNodeWithTag("record_at_building").assertExists()
    }
}
