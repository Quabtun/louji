package com.quabtun.louji.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.AttachmentEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnnotationViewModel(
    private val repository: RecordRepository,
    private val mediaStore: LocalMediaStore,
) : ViewModel() {
    private val _attachment = MutableStateFlow<AttachmentEntity?>(null)
    val attachment: StateFlow<AttachmentEntity?> = _attachment.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load(attachmentId: Long) {
        if (_attachment.value?.id == attachmentId) return
        viewModelScope.launch { _attachment.value = repository.attachment(attachmentId) }
    }

    /** Registers the annotated file (already written by the screen) as a new attachment. */
    fun saveAnnotated(recordId: Long, file: java.io.File, onSaved: () -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            try {
                val draft = mediaStore.completeImage(file, "image/jpeg")
                // Keep the original attachment's category instead of always falling back to OTHER.
                val category = _attachment.value?.category
                    ?: com.quabtun.louji.model.AttachmentCategory.OTHER
                repository.addAttachment(recordId, draft.copy(category = category))
                _message.value = "标注图已保存到附件"
                onSaved()
            } catch (error: Throwable) {
                _message.value = error.message ?: "保存标注失败"
            } finally {
                _saving.value = false
            }
        }
    }

    /**
     * Completes an annotation made on a not-yet-saved draft. The new file becomes the draft's
     * image, keeping its `ORDER_SCREENSHOT` / other category intact.
     */
    fun saveAnnotatedPending(
        source: com.quabtun.louji.model.MediaDraft,
        file: java.io.File,
        onSaved: (com.quabtun.louji.model.MediaDraft) -> Unit,
    ) {
        viewModelScope.launch {
            _saving.value = true
            try {
                val media = mediaStore.completeImage(file, "image/jpeg")
                    .copy(category = source.category)
                onSaved(media)
                _message.value = "标注图已替换待保存附件"
            } catch (error: Throwable) {
                _message.value = error.message ?: "保存标注失败"
            } finally {
                _saving.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
