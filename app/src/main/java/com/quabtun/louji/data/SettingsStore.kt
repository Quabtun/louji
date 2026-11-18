package com.quabtun.louji.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quabtun.louji.model.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "louji_settings",
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val appLockEnabled: Boolean = false,
    val onboardingSeen: Boolean = false,
    val backupReminderEnabled: Boolean = true,
    val lastBackupAt: Long = 0L,
    val autoSaveOrders: Boolean = true,
    // --- 简化模式功能显隐（默认全部隐藏非高频项，仅 UI 开关，不动数据） ---
    val showBuildingTab: Boolean = false,
    val showInsightsTab: Boolean = false,
    val showQuickSaveBar: Boolean = true,
    val enableBatchAndArchive: Boolean = false,
    val enableFullFormFields: Boolean = false,
    val enableMediaProTools: Boolean = false,
    val showEmptyDetailFields: Boolean = false,
)

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            AppSettings(
                themeMode = preferences[THEME_MODE].let { value ->
                    runCatching { ThemeMode.valueOf(value ?: "") }.getOrDefault(ThemeMode.SYSTEM)
                },
                autoCheckUpdates = preferences[AUTO_CHECK_UPDATES] ?: true,
                lastUpdateCheck = preferences[LAST_UPDATE_CHECK] ?: 0L,
                appLockEnabled = preferences[APP_LOCK_ENABLED] ?: false,
                onboardingSeen = preferences[ONBOARDING_SEEN] ?: false,
                backupReminderEnabled = preferences[BACKUP_REMINDER_ENABLED] ?: true,
                lastBackupAt = preferences[LAST_BACKUP_AT] ?: 0L,
                autoSaveOrders = preferences[AUTO_SAVE_ORDERS] ?: true,
                showBuildingTab = preferences[SHOW_BUILDING_TAB] ?: false,
                showInsightsTab = preferences[SHOW_INSIGHTS_TAB] ?: false,
                showQuickSaveBar = preferences[SHOW_QUICK_SAVE_BAR] ?: true,
                enableBatchAndArchive = preferences[ENABLE_BATCH_AND_ARCHIVE] ?: false,
                enableFullFormFields = preferences[ENABLE_FULL_FORM_FIELDS] ?: false,
                enableMediaProTools = preferences[ENABLE_MEDIA_PRO_TOOLS] ?: false,
                showEmptyDetailFields = preferences[SHOW_EMPTY_DETAIL_FIELDS] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun setLastUpdateCheck(timestamp: Long) {
        context.settingsDataStore.edit { it[LAST_UPDATE_CHECK] = timestamp }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.settingsDataStore.edit { it[ONBOARDING_SEEN] = seen }
    }

    suspend fun setBackupReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[BACKUP_REMINDER_ENABLED] = enabled }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.settingsDataStore.edit { it[LAST_BACKUP_AT] = timestamp }
    }

    suspend fun setAutoSaveOrders(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_SAVE_ORDERS] = enabled }
    }

    suspend fun setShowBuildingTab(enabled: Boolean) {
        context.settingsDataStore.edit { it[SHOW_BUILDING_TAB] = enabled }
    }

    suspend fun setShowInsightsTab(enabled: Boolean) {
        context.settingsDataStore.edit { it[SHOW_INSIGHTS_TAB] = enabled }
    }

    suspend fun setShowQuickSaveBar(enabled: Boolean) {
        context.settingsDataStore.edit { it[SHOW_QUICK_SAVE_BAR] = enabled }
    }

    suspend fun setEnableBatchAndArchive(enabled: Boolean) {
        context.settingsDataStore.edit { it[ENABLE_BATCH_AND_ARCHIVE] = enabled }
    }

    suspend fun setEnableFullFormFields(enabled: Boolean) {
        context.settingsDataStore.edit { it[ENABLE_FULL_FORM_FIELDS] = enabled }
    }

    suspend fun setEnableMediaProTools(enabled: Boolean) {
        context.settingsDataStore.edit { it[ENABLE_MEDIA_PRO_TOOLS] = enabled }
    }

    suspend fun setShowEmptyDetailFields(enabled: Boolean) {
        context.settingsDataStore.edit { it[SHOW_EMPTY_DETAIL_FIELDS] = enabled }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val BACKUP_REMINDER_ENABLED = booleanPreferencesKey("backup_reminder_enabled")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val AUTO_SAVE_ORDERS = booleanPreferencesKey("auto_save_orders")
        val SHOW_BUILDING_TAB = booleanPreferencesKey("show_building_tab")
        val SHOW_INSIGHTS_TAB = booleanPreferencesKey("show_insights_tab")
        val SHOW_QUICK_SAVE_BAR = booleanPreferencesKey("show_quick_save_bar")
        val ENABLE_BATCH_AND_ARCHIVE = booleanPreferencesKey("enable_batch_and_archive")
        val ENABLE_FULL_FORM_FIELDS = booleanPreferencesKey("enable_full_form_fields")
        val ENABLE_MEDIA_PRO_TOOLS = booleanPreferencesKey("enable_media_pro_tools")
        val SHOW_EMPTY_DETAIL_FIELDS = booleanPreferencesKey("show_empty_detail_fields")
    }
}
