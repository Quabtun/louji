package com.quabtun.louji.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.AppContainer
import com.quabtun.louji.BuildConfig
import com.quabtun.louji.update.UpdateInfo
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val manual: Boolean) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUiState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Error(val message: String, val manual: Boolean) : UpdateUiState
}

class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    private var startupChecked = false
    private var downloadJob: Job? = null
    private var downloadGeneration = 0

    fun checkOnStartup() {
        if (startupChecked) return
        startupChecked = true
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            if (!settings.autoCheckUpdates) return@launch
            val elapsed = System.currentTimeMillis() - settings.lastUpdateCheck
            if (settings.lastUpdateCheck > 0 && elapsed < CHECK_INTERVAL_MS) return@launch
            runCheck(manual = false)
        }
    }

    fun checkNow() {
        if (_state.value is UpdateUiState.Checking || _state.value is UpdateUiState.Downloading) return
        viewModelScope.launch { runCheck(manual = true) }
    }

    fun download() {
        val info = (_state.value as? UpdateUiState.Available)?.info ?: return
        val generation = ++downloadGeneration
        downloadJob?.cancel()
        _state.value = UpdateUiState.Downloading(info, 0f)
        downloadJob = viewModelScope.launch {
            try {
                val file = container.updater.download(info) { progress ->
                    if (generation == downloadGeneration) {
                        _state.value = UpdateUiState.Downloading(info, progress)
                    }
                }
                if (generation == downloadGeneration) {
                    _state.value = UpdateUiState.Ready(info, file)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == downloadGeneration) {
                    _state.value = UpdateUiState.Error(
                        message = error.message ?: "下载失败，请重试",
                        manual = true,
                    )
                }
            }
        }
    }

    fun install() {
        val target = installTarget(_state.value) ?: return
        if (!container.updater.canInstallPackages()) {
            _state.value = UpdateUiState.NeedsPermission(target.first, target.second)
            return
        }
        try {
            container.updater.install(target.second)
        } catch (error: Throwable) {
            _state.value = UpdateUiState.Error(
                message = error.message ?: "无法打开安装器",
                manual = true,
            )
        }
    }

    fun openInstallPermissionSettings() {
        try {
            container.updater.openInstallPermissionSettings()
        } catch (error: Throwable) {
            _state.value = UpdateUiState.Error(
                message = error.message ?: "无法打开系统设置",
                manual = true,
            )
        }
    }

    fun dismiss() {
        downloadGeneration++
        downloadJob?.cancel()
        _state.value = UpdateUiState.Hidden
    }

    fun consumeUpToDate(): Boolean {
        val current = _state.value
        if (current is UpdateUiState.UpToDate && current.manual) {
            _state.value = UpdateUiState.Hidden
            return true
        }
        return false
    }

    fun consumeError(): String? {
        val current = _state.value
        if (current is UpdateUiState.Error && current.manual) {
            _state.value = UpdateUiState.Hidden
            return current.message
        }
        return null
    }

    private suspend fun runCheck(manual: Boolean) {
        _state.value = UpdateUiState.Checking
        try {
            val info = container.updater.check(BuildConfig.VERSION_NAME)
            container.settings.setLastUpdateCheck(System.currentTimeMillis())
            _state.value = if (info == null) {
                if (manual) UpdateUiState.UpToDate(manual = true) else UpdateUiState.Hidden
            } else {
                UpdateUiState.Available(info)
            }
        } catch (error: Throwable) {
            _state.value = if (manual) {
                UpdateUiState.Error(error.message ?: "检查更新失败", manual = true)
            } else {
                UpdateUiState.Hidden
            }
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}

/** The retry button lives in NeedsPermission, so both states must resolve to an install target. */
internal fun installTarget(state: UpdateUiState): Pair<UpdateInfo, File>? = when (state) {
    is UpdateUiState.Ready -> state.info to state.file
    is UpdateUiState.NeedsPermission -> state.info to state.file
    else -> null
}
