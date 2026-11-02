package com.quabtun.louji.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.model.FormErrors
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.TriState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordEditUiState(
    val draft: RecordDraft = RecordDraft(),
    val existingMedia: List<AttachmentEntity> = emptyList(),
    val pendingMedia: List<MediaDraft> = emptyList(),
    val removedMediaIds: Set<Long> = emptySet(),
    val errors: FormErrors = FormErrors(),
    val initialized: Boolean = false,
    val isEditing: Boolean = false,
    val saving: Boolean = false,
    val importing: Boolean = false,
    val message: String? = null,
    val loadFailed: Boolean = false,
)

class RecordEditViewModel(
    private val repository: RecordRepository,
    private val mediaStore: LocalMediaStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(RecordEditUiState())
    val ui: StateFlow<RecordEditUiState> = _ui.asStateFlow()

    val recentLocations: StateFlow<List<String>> = repository.observeRecentLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var recordId: Long? = null
    private var initialDraft = RecordDraft()
    private var initialized = false
    private var saved = false

    fun initialize(requestedRecordId: Long?, duplicateId: Long? = null) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val existingRecord = requestedRecordId
                ?.takeIf { duplicateId == null }
                ?.let { repository.getRecord(it) }
            val source = when {
                duplicateId != null -> repository.getRecord(duplicateId)
                requestedRecordId != null -> existingRecord
                else -> null
            }
            if (requestedRecordId != null && duplicateId == null && source == null) {
                _ui.update { it.copy(initialized = true, loadFailed = true) }
                return@launch
            }
            val draft = when {
                duplicateId != null -> source?.let { record ->
                    record.record.toDraft().copy(orderNo = "")
                }
                else -> source?.record?.toDraft()
            } ?: RecordDraft()
            val attachments = if (duplicateId == null) source?.attachments.orEmpty() else emptyList()
            recordId = requestedRecordId?.takeIf { duplicateId == null }
            initialDraft = draft
            _ui.update {
                it.copy(
                    draft = draft,
                    existingMedia = attachments,
                    initialized = true,
                    isEditing = recordId != null,
                    loadFailed = false,
                )
            }
        }
    }

    fun setNoStairs(value: TriState) = updateDraft { copy(noStairs = value) }

    fun setGender(value: Gender) = updateDraft { copy(gender = value) }

    fun setDoorIntact(value: TriState) = updateDraft { copy(doorIntact = value) }

    fun setCanOpenFromAbove(value: TriState) = updateDraft { copy(canOpenFromAbove = value) }

    fun setWillComeDown(value: TriState) = updateDraft { copy(willComeDown = value) }

    fun setLocation(value: String) = updateDraft { copy(location = value.take(60)) }

    fun setOrderNo(value: String) = updateDraft { copy(orderNo = value.take(60)) }

    fun setPhoneTail(value: String) = updateDraft {
        copy(phoneTail = value.filter(Char::isDigit).take(4))
    }

    fun setFloor(value: String) = updateDraft { copy(floor = value.take(12)) }

    fun setRoomNo(value: String) = updateDraft { copy(roomNo = value.take(24)) }

    fun setPersonalityNote(value: String) = updateDraft { copy(personalityNote = value.take(200)) }

    fun setRemark(value: String) = updateDraft { copy(remark = value.take(300)) }

    fun setClimbNote(value: String) = updateDraft { copy(climbNote = value.take(300)) }

    fun toggleTag(tag: String) = updateDraft {
        val tags = if (tag in personalityTags) {
            personalityTags - tag
        } else {
            (personalityTags + tag).take(12)
        }
        copy(personalityTags = tags)
    }

    fun addCaptured(media: MediaDraft) {
        if (mediaCount() >= MAX_MEDIA) {
            _ui.update { it.copy(message = "一条记录最多 $MAX_MEDIA 个附件") }
            mediaStore.deleteDraft(media)
            return
        }
        _ui.update { it.copy(pendingMedia = it.pendingMedia + media, message = null) }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(importing = true, message = null) }
            val added = mutableListOf<MediaDraft>()
            var failure: String? = null
            var count = mediaCount()
            for (uri in uris) {
                if (count >= MAX_MEDIA) {
                    failure = "一条记录最多 $MAX_MEDIA 个附件"
                    break
                }
                try {
                    added += mediaStore.importFromUri(uri)
                    count++
                } catch (error: Throwable) {
                    failure = error.message ?: "有文件导入失败"
                }
            }
            _ui.update {
                it.copy(
                    pendingMedia = it.pendingMedia + added,
                    importing = false,
                    message = failure,
                )
            }
        }
    }

    fun removePending(media: MediaDraft) {
        mediaStore.deleteDraft(media)
        _ui.update { it.copy(pendingMedia = it.pendingMedia - media) }
    }

    fun removeExisting(attachmentId: Long) {
        _ui.update { it.copy(removedMediaIds = it.removedMediaIds + attachmentId) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun save(onSaved: (Long) -> Unit) {
        val state = _ui.value
        if (state.saving) return
        val errors = validate(state)
        if (errors.hasAny) {
            _ui.update { it.copy(errors = errors) }
            return
        }
        _ui.update { it.copy(saving = true, errors = FormErrors(), message = null) }
        viewModelScope.launch {
            try {
                val id = repository.save(
                    draft = state.draft,
                    recordId = recordId,
                    pendingMedia = state.pendingMedia,
                    removedAttachmentIds = state.removedMediaIds,
                )
                saved = true
                _ui.update {
                    it.copy(
                        saving = false,
                        pendingMedia = emptyList(),
                        removedMediaIds = emptySet(),
                    )
                }
                onSaved(id)
            } catch (error: Throwable) {
                _ui.update {
                    it.copy(
                        saving = false,
                        message = error.message ?: "保存失败，请重试",
                    )
                }
            }
        }
    }

    fun discard() {
        _ui.value.pendingMedia.forEach(mediaStore::deleteDraft)
        _ui.update { it.copy(pendingMedia = emptyList()) }
    }

    fun hasUnsavedChanges(): Boolean {
        val state = _ui.value
        return state.initialized &&
            (state.draft != initialDraft ||
                state.pendingMedia.isNotEmpty() ||
                state.removedMediaIds.isNotEmpty())
    }

    fun mediaCount(): Int {
        val state = _ui.value
        return state.existingMedia.count { it.id !in state.removedMediaIds } + state.pendingMedia.size
    }

    private fun updateDraft(transform: RecordDraft.() -> RecordDraft) {
        _ui.update { it.copy(draft = it.draft.transform()) }
    }

    private fun validate(state: RecordEditUiState): FormErrors =
        com.quabtun.louji.model.RecordValidator.validate(
            draft = state.draft,
            mediaCount = mediaCount(),
            maxMedia = MAX_MEDIA,
        )

    override fun onCleared() {
        super.onCleared()
        if (!saved) {
            _ui.value.pendingMedia.forEach(mediaStore::deleteDraft)
        }
    }

    private fun RecordEntity.toDraft(): RecordDraft = RecordDraft(
        location = location,
        orderNo = orderNo,
        gender = gender,
        phoneTail = phoneTail,
        floor = floor,
        roomNo = roomNo,
        doorIntact = doorIntact,
        canOpenFromAbove = canOpenFromAbove,
        noStairs = noStairs,
        willComeDown = willComeDown,
        personalityTags = personalityTags,
        personalityNote = personalityNote,
        remark = remark,
        climbNote = climbNote,
    )

    private companion object {
        const val MAX_MEDIA = 20
    }
}
