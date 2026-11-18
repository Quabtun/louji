package com.quabtun.louji.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.AppContainer
import com.quabtun.louji.BuildConfig
import com.quabtun.louji.update.CachedUpdate
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

    /** A newer APK is fully cached — installable without any network. */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState

    /** Download stopped (paused / minimized / killed) with `.part` bytes kept on disk. */
    data class Paused(val info: UpdateInfo, val downloadedBytes: Long, val totalBytes: Long) :
        UpdateUiState

    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : UpdateUiState

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
    private var downloadPaused = false

    /**
     * Looks at the local cache first. A verified full APK of a newer version jumps straight to
     * [UpdateUiState.Ready] with no network; a partial download surfaces as [UpdateUiState.Paused].
     * Returns true when the cache decided the state.
     */
    fun inspectCache(): Boolean {
        return try {
            when (val cached = container.updater.inspectCachedUpdate(BuildConfig.VERSION_NAME)) {
                is CachedUpdate.Ready -> {
                    _state.value = UpdateUiState.Ready(cached.info, cached.file)
                    true
                }
                is CachedUpdate.Partial -> {
                    _state.value = UpdateUiState.Paused(
                        info = cached.info,
                        downloadedBytes = cached.downloadedBytes,
                        totalBytes = cached.info.apkSize,
                    )
                    true
                }
                CachedUpdate.None -> false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun checkOnStartup() {
        if (startupChecked) return
        startupChecked = true
        // A cached full package must win over a silent network check: install first.
        if (inspectCache()) return
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
        // The cache is authoritative when it already holds a newer package.
        if (inspectCache()) return
        viewModelScope.launch { runCheck(manual = true) }
    }

    fun download(forceRestart: Boolean = false) {
        val current = _state.value
        val info = when (current) {
            is UpdateUiState.Available -> current.info
            is UpdateUiState.Paused -> current.info
            else -> return
        }
        val generation = ++downloadGeneration
        downloadPaused = false
        val startedBytes = if (forceRestart) {
            0L
        } else {
            (current as? UpdateUiState.Paused)?.downloadedBytes ?: 0L
        }
        _state.value = UpdateUiState.Downloading(info, 0f, startedBytes, info.apkSize)
        downloadJob = viewModelScope.launch {
            try {
                val file = container.updater.download(
                    info = info,
                    forceRestart = forceRestart,
                    onProgress = { progress: Float ->
                        if (generation == downloadGeneration && !downloadPaused) {
                            _state.value = UpdateUiState.Downloading(
                                info = info,
                                progress = progress,
                                downloadedBytes = (progress * info.apkSize).toLong(),
                                totalBytes = info.apkSize,
                            )
                        }
                    },
                )
                if (generation == downloadGeneration) {
                    _state.value = UpdateUiState.Ready(info, file)
                }
            } catch (error: CancellationException) {
                // Pause / minimize: the updater keeps the .part so nothing is lost.
                if (generation == downloadGeneration && downloadPaused) {
                    _state.value = UpdateUiState.Paused(
                        info = info,
                        downloadedBytes = currentPartialBytes(info),
                        totalBytes = info.apkSize,
                    )
                }
                throw error
            } catch (error: Throwable) {
                if (generation == downloadGeneration) {
                    if (error.message?.contains("校验失败") == true) {
                        _state.value = UpdateUiState.Error(
                            message = error.message ?: "下载失败，请重试",
                            manual = true,
                        )
                    } else {
                        // Keep the partial bytes visible as paused, not as a dead end.
                        _state.value = UpdateUiState.Paused(
                            info = info,
                            downloadedBytes = currentPartialBytes(info),
                            totalBytes = info.apkSize,
                        )
                    }
                }
            }
        }
    }

    /** Pauses the running download without deleting anything on disk. */
    fun pauseDownload() {
        val downloading = _state.value as? UpdateUiState.Downloading ?: return
        downloadPaused = true
        _state.value = UpdateUiState.Paused(
            info = downloading.info,
            downloadedBytes = currentPartialBytes(downloading.info),
            totalBytes = downloading.info.apkSize,
        )
        downloadGeneration++
        downloadJob?.cancel()
    }

    private fun currentPartialBytes(info: UpdateInfo): Long = runCatching {
        val version = info.versionName.filter { it.isLetterOrDigit() || it in "._-" }.take(64)
        File(container.appContext.cacheDir, "updates")
            .resolve("louji-$version.apk.part")
            .takeIf { it.isFile }?.length() ?: 0L
    }.getOrDefault(0L)

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

    /**
     * Minimizes the sheet. A running download keeps going in the background; a paused one keeps
     * its bytes for later. Nothing is deleted here.
     */
    fun minimize() {
        _state.value = UpdateUiState.Hidden
    }

    fun dismiss() = minimize()

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
