package com.quabtun.louji.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A record that was just moved to the recycle bin and can still be undone. */
data class PendingDelete(
    val recordId: Long,
    val nonce: Int,
)

/**
 * Tiny cross-screen signal so the detail screen can announce a soft-delete and the list screen
 * can show an undo Snackbar. The [nonce] increments on every announcement so re-trashes of the
 * same record still trigger a fresh Snackbar.
 */
class DeleteUndoController {
    private val _pending = MutableStateFlow<PendingDelete?>(null)
    val pending: StateFlow<PendingDelete?> = _pending.asStateFlow()

    fun announce(recordId: Long) {
        val next = (_pending.value?.nonce ?: 0) + 1
        _pending.value = PendingDelete(recordId = recordId, nonce = next)
    }

    fun consume() {
        _pending.value = null
    }
}
