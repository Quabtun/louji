package com.quabtun.louji

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Runs before the activity is launched so a published GitHub release can never cover the UI
 * under test with an update sheet, and so the first-run onboarding screen never blocks tests
 * that assert against the main list.
 *
 * It also enables every advanced feature switch: the existing UI tests were written against
 * the full surface (多选 / 归档 / 楼栋库 / 洞察 / 完整字段 / 媒体工具), and the simplified
 * defaults would hide those entry points.
 */
class DisableUpdateRule : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking {
            app.container.settings.setAutoCheckUpdates(false)
            app.container.settings.setLastUpdateCheck(System.currentTimeMillis())
            app.container.settings.setOnboardingSeen(true)
            app.container.settings.setBackupReminderEnabled(false)
            // Advanced surface on: legacy tests assert against the full UI.
            app.container.settings.setShowBuildingTab(true)
            app.container.settings.setShowInsightsTab(true)
            app.container.settings.setEnableBatchAndArchive(true)
            app.container.settings.setEnableFullFormFields(true)
            app.container.settings.setEnableMediaProTools(true)
            app.container.settings.setShowEmptyDetailFields(true)
        }
    }

    override fun after() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking {
            app.container.settings.setAutoCheckUpdates(true)
            app.container.settings.setShowBuildingTab(false)
            app.container.settings.setShowInsightsTab(false)
            app.container.settings.setEnableBatchAndArchive(false)
            app.container.settings.setEnableFullFormFields(false)
            app.container.settings.setEnableMediaProTools(false)
            app.container.settings.setShowEmptyDetailFields(false)
        }
    }
}

