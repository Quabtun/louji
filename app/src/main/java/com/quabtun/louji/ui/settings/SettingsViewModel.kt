package com.quabtun.louji.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.AppContainer
import com.quabtun.louji.data.AppSettings
import com.quabtun.louji.data.ImportMode
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val running: Boolean = false,
    val progress: Float? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _storage = MutableStateFlow<LocalMediaStore.StorageUsage?>(null)
    val storage: StateFlow<LocalMediaStore.StorageUsage?> = _storage.asStateFlow()

    private val _backup = MutableStateFlow(BackupUiState())
    val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

    init {
        refreshStorage()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setThemeMode(mode) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch { container.settings.setAutoCheckUpdates(enabled) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.setAppLockEnabled(enabled) }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = container.mediaStore.storageUsage()
        }
    }

    fun export(uri: Uri, password: String?) {
        viewModelScope.launch {
            _backup.value = BackupUiState(running = true, progress = 0f)
            try {
                val manifest = container.backup.exportBackup(uri, password) { done, total ->
                    _backup.value = BackupUiState(
                        running = true,
                        progress = (done.toFloat() / maxOf(total, 1)).coerceIn(0f, 1f),
                    )
                }
                _backup.value = BackupUiState(
                    message = "已导出 ${manifest.recordCount} 条记录、${manifest.mediaCount} 个附件",
                )
                refreshStorage()
            } catch (error: Throwable) {
                _backup.value = BackupUiState(
                    message = error.message ?: "导出失败",
                    isError = true,
                )
            }
        }
    }

    fun import(uri: Uri, mode: ImportMode, password: String? = null) {
        viewModelScope.launch {
            _backup.value = BackupUiState(running = true, progress = 0f)
            try {
                val summary = container.backup.importBackup(uri, mode, password) { done, total ->
                    _backup.value = BackupUiState(
                        running = true,
                        progress = (done.toFloat() / maxOf(total, 1)).coerceIn(0f, 1f),
                    )
                }
                val skipped = if (summary.skipped > 0) "，跳过 ${summary.skipped} 条重复" else ""
                _backup.value = BackupUiState(
                    message = "已导入 ${summary.imported} 条记录、${summary.media} 个附件$skipped",
                )
                refreshStorage()
            } catch (error: Throwable) {
                _backup.value = BackupUiState(
                    message = error.message ?: "导入失败",
                    isError = true,
                )
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _backup.value = BackupUiState(running = true, progress = null)
            try {
                container.repository.clearAll()
                container.mediaStore.sweepOrphans(emptySet())
                _backup.value = BackupUiState(message = "所有记录已清除")
                refreshStorage()
            } catch (error: Throwable) {
                _backup.value = BackupUiState(
                    message = error.message ?: "清除失败",
                    isError = true,
                )
            }
        }
    }

    fun dismissBackupMessage() {
        _backup.value = _backup.value.copy(message = null, isError = false)
    }
}
