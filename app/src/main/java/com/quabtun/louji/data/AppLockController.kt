package com.quabtun.louji.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockState(
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val locked: Boolean = false,
)

class AppLockController(
    settings: Flow<AppSettings>,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    /** Last known feature switch, so unrelated settings writes cannot re-lock the session. */
    private var knownEnabled: Boolean? = null

    init {
        scope.launch {
            settings.collect { value ->
                val enabled = value.appLockEnabled
                val previous = knownEnabled
                knownEnabled = enabled
                _state.update { state ->
                    when {
                        // First emission: adopt the stored switch and lock if it is on.
                        previous == null -> state.copy(
                            ready = true,
                            enabled = enabled,
                            locked = enabled,
                        )
                        // Turning the feature off releases any lock immediately.
                        !enabled -> state.copy(enabled = false, locked = false)
                        // Turning it on from Settings happens behind a device-credential prompt;
                        // the rider is already verified, so do not lock the screen they are on.
                        previous == false -> state.copy(enabled = true)
                        // Same switch value: theme/update/backup writes must not change the lock.
                        else -> state.copy(enabled = true)
                    }
                }
            }
        }
    }

    /** Called when the app moves to the background. Locks only when the feature is enabled. */
    fun lockIfEnabled() {
        if (_state.value.enabled) {
            _state.update { it.copy(locked = true) }
        }
    }

    fun unlock() {
        _state.update { it.copy(locked = false) }
    }
}
