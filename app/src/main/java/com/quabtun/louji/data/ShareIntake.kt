package com.quabtun.louji.data

import android.net.Uri
import com.quabtun.louji.model.MediaDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Content shared into the app from another app (screenshot or order text).
 *
 * [ocr] and [imageMedia] carry work already done by the receiving screen so the edit form never
 * has to read a possibly-expired external URI again: the screenshot is copied into app-private
 * storage and OCR runs exactly once per share.
 */
data class SharePayload(
    val text: String? = null,
    val imageUri: Uri? = null,
    val ocr: OcrResult? = null,
    val imageMedia: MediaDraft? = null,
)

/**
 * Single-slot hand-off for ACTION_SEND content. MainActivity deposits it; the new-record screen
 * consumes it exactly once after the app lock is satisfied.
 */
class ShareIntake {
    private val _payload = MutableStateFlow<SharePayload?>(null)
    val payload: StateFlow<SharePayload?> = _payload.asStateFlow()

    fun submit(value: SharePayload) {
        _payload.value = value
    }

    fun consume() {
        _payload.value = null
    }
}
