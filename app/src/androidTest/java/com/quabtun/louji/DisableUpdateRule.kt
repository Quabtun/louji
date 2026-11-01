package com.quabtun.louji

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Runs before the activity is launched so a published GitHub release can never cover the UI
 * under test with an update sheet.
 */
class DisableUpdateRule : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking {
            app.container.settings.setAutoCheckUpdates(false)
            app.container.settings.setLastUpdateCheck(System.currentTimeMillis())
        }
    }

    override fun after() {
        val app = ApplicationProvider.getApplicationContext<LouJiApp>()
        runBlocking { app.container.settings.setAutoCheckUpdates(true) }
    }
}
