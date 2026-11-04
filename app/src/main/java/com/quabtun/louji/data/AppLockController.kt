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

    init {
        scope.launch {
            settings.collect { value ->
                _state.update {
                    it.copy(
                        ready = true,
                        enabled = value.appLockEnabled,
                        locked = value.appLockEnabled,
                    )
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
