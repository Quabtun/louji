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

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val BACKUP_REMINDER_ENABLED = booleanPreferencesKey("backup_reminder_enabled")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }
}
