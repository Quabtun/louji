package com.quabtun.louji.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureVisibilitySettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultsKeepTheLedgerLean() = runBlocking {
        val store = SettingsStore(context)

        val settings = store.settings.first()

        // 低频入口默认隐藏；秒存与自动保存保持默认可用。
        assertFalse(settings.showBuildingTab)
        assertFalse(settings.showInsightsTab)
        assertTrue(settings.showQuickSaveBar)
        assertFalse(settings.enableBatchAndArchive)
        assertFalse(settings.enableFullFormFields)
        assertFalse(settings.enableMediaProTools)
        assertFalse(settings.showEmptyDetailFields)
        assertTrue(settings.autoSaveOrders)
    }

    @Test
    fun togglesPersistAcrossStoreInstances() = runBlocking {
        val store = SettingsStore(context)
        store.setShowBuildingTab(true)
        store.setEnableFullFormFields(true)
        store.setShowQuickSaveBar(false)

        val reloaded = SettingsStore(context).settings.first()
        assertTrue(reloaded.showBuildingTab)
        assertTrue(reloaded.enableFullFormFields)
        assertFalse(reloaded.showQuickSaveBar)
        // Untouched switches stay at their defaults.
        assertFalse(reloaded.showInsightsTab)
        assertFalse(reloaded.enableMediaProTools)

        // Restore defaults for other tests.
        store.setShowBuildingTab(false)
        store.setEnableFullFormFields(false)
        store.setShowQuickSaveBar(true)
    }

    @Test
    fun everyToggleRoundTrips() = runBlocking {
        val store = SettingsStore(context)
        try {
            store.setShowInsightsTab(true)
            store.setEnableBatchAndArchive(true)
            store.setEnableMediaProTools(true)
            store.setShowEmptyDetailFields(true)

            val settings = store.settings.first()
            assertTrue(settings.showInsightsTab)
            assertTrue(settings.enableBatchAndArchive)
            assertTrue(settings.enableMediaProTools)
            assertTrue(settings.showEmptyDetailFields)
        } finally {
            store.setShowInsightsTab(false)
            store.setEnableBatchAndArchive(false)
            store.setEnableMediaProTools(false)
            store.setShowEmptyDetailFields(false)
        }
        assertEquals(false, store.settings.first().showInsightsTab)
    }
}
