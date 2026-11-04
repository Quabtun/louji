package com.quabtun.louji

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule(order = 0)
    val disableUpdateRule = DisableUpdateRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private fun setOnboarding(seen: Boolean) {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking { app.container.settings.setOnboardingSeen(seen) }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test
    fun onboardingShowsAndDismissesToMain() {
        setOnboarding(false)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("开始使用").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("record_list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("record_list").assertExists()
    }

    @Test
    fun onboardingSkippedOnceSeen() {
        setOnboarding(true)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("record_list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("onboarding_screen").assertCountEquals(0)
    }
}
