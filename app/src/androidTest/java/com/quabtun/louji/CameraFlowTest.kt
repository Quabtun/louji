package com.quabtun.louji

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraFlowTest {
    @get:Rule(order = 0)
    val updateRule = DisableUpdateRule()

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule(order = 2)
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
    fun capturePhotoAddsPendingAttachment() {
        openMediaSheet()
        compose.onNodeWithTag("media_photo").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("camera_ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("camera_shutter").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("pending_media_0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("pending_media_0").assertExists()
    }

    @Test
    fun captureVideoAddsPendingAttachment() {
        openMediaSheet()
        compose.onNodeWithTag("media_video").performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("camera_ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("camera_shutter").performClick()
        Thread.sleep(2_000)
        compose.onNodeWithTag("camera_shutter").performClick()
        compose.waitUntil(25_000) {
            compose.onAllNodesWithTag("pending_media_0").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("pending_media_0").assertExists()
    }

    private fun openMediaSheet() {
        compose.onNodeWithTag("create_record").performClick()
        compose.onNodeWithTag("field_location").performTextInput("Camera Test")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("form_scroll").performScrollToNode(hasTestTag("add_media"))
        compose.onNodeWithTag("add_media").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("media_photo").fetchSemanticsNodes().isNotEmpty()
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
