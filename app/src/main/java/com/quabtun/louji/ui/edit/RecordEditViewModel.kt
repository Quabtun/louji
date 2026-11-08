package com.quabtun.louji.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.OrderTextRecognizer
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.ShareIntake
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.CustomerProfile
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.FormErrors
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.TriState
import com.quabtun.louji.util.ClipboardOrderParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
    val recognizing: Boolean = false,
    val message: String? = null,
    val loadFailed: Boolean = false,
    val syncBuildingProfile: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class RecordEditViewModel(
    private val repository: RecordRepository,
    private val mediaStore: LocalMediaStore,
    private val recognizer: OrderTextRecognizer,
    private val shareIntake: ShareIntake,
) : ViewModel() {
    private val _ui = MutableStateFlow(RecordEditUiState())
    val ui: StateFlow<RecordEditUiState> = _ui.asStateFlow()

    val recentLocations: StateFlow<List<String>> = repository.observeRecentLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Building profile matched from the current location text. */
    val buildingMatch: StateFlow<BuildingProfileEntity?> = _ui
        .map { it.draft.location }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { location ->
            flow { emit(if (location.trim().length >= 2) repository.findBuildingMatch(location) else null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Returning-customer profile matched from phone tail or building + room. */
    val customerMatch: StateFlow<CustomerProfile?> = _ui
        .map { it.draft.phoneTail to (it.draft.location to it.draft.roomNo) }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { (tail, place) ->
            flow {
                val (location, room) = place
                val profile = if (tail.length == 4 || (location.isNotBlank() && room.isNotBlank())) {
                    repository.customerProfile(tail, location, room)
                } else {
                    CustomerProfile()
                }
                emit(profile.takeIf { it.hasHistory })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var recordId: Long? = null
    private var initialDraft = RecordDraft()
    private var initialized = false
    private var saved = false

    fun initialize(
        requestedRecordId: Long?,
        duplicateId: Long? = null,
        initialLocation: String? = null,
    ) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            try {
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
                var draft = when {
                    duplicateId != null -> source?.let { record ->
                        record.record.toDraft().copy(orderNo = "")
                    }
                    else -> source?.record?.toDraft()
                } ?: RecordDraft()

                // Shared text (ACTION_SEND) is parsed once into the blank form.
                val shared = shareIntake.payload.value
                if (shared != null) {
                    shareIntake.consume()
                    shared.text?.let { draft = draft.mergedWith(ClipboardOrderParser.parse(it)) }
                    shared.imageUri?.let { uri ->
                        val text = runCatching { recognizer.recognize(uri) }.getOrDefault("")
                        if (text.isNotBlank()) {
                            draft = draft.mergedWith(ClipboardOrderParser.parse(text))
                        }
                        val media = runCatching { mediaStore.importFromUri(uri) }.getOrNull()
                        if (media != null) {
                            _ui.update {
                                it.copy(
                                    pendingMedia = it.pendingMedia + media.copy(
                                        category = AttachmentCategory.ORDER_SCREENSHOT,
                                    ),
                                )
                            }
                        }
                    }
                }
                // Building prefill ("在此楼栋记一单").
                if (draft.location.isBlank() && !initialLocation.isNullOrBlank()) {
                    val profile = repository.findBuilding(initialLocation)
                    draft = draft.copy(
                        location = initialLocation,
                        elevatorType = profile?.elevatorType ?: ElevatorType.UNKNOWN,
                        accessCode = profile?.accessCode.orEmpty(),
                        doorIntact = profile?.doorIntact ?: TriState.UNKNOWN,
                        canOpenFromAbove = profile?.canOpenFromAbove ?: TriState.UNKNOWN,
                    )
                }
                val attachments = if (duplicateId == null) {
                    source?.attachments.orEmpty()
                } else {
                    emptyList()
                }
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
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _ui.update { it.copy(initialized = true, loadFailed = true) }
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

    fun setPhoneExt(value: String) = updateDraft {
        copy(phoneExt = value.filter(Char::isDigit).take(8))
    }

    fun setAccessCode(value: String) = updateDraft { copy(accessCode = value.take(24)) }

    fun setFloor(value: String) = updateDraft { copy(floor = value.take(12)) }

    fun setRoomNo(value: String) = updateDraft { copy(roomNo = value.take(24)) }

    fun setElevatorType(value: ElevatorType) = updateDraft { copy(elevatorType = value) }

    fun setDeliveryMethod(value: DeliveryMethod) = updateDraft { copy(deliveryMethod = value) }

    fun setWarning(value: Boolean) = updateDraft { copy(isWarning = value) }

    fun setSyncBuildingProfile(value: Boolean) = _ui.update { it.copy(syncBuildingProfile = value) }

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
            val imageUris = uris.filter { uri -> uri.isImage() }
            for (uri in uris) {
                if (count >= MAX_MEDIA) {
                    failure = "一条记录最多 $MAX_MEDIA 个附件"
                    break
                }
                try {
                    val draft = mediaStore.importFromUri(uri)
                    val isFirstImage = imageUris.isNotEmpty() && uri == imageUris.first() &&
                        added.none { it.type == com.quabtun.louji.model.MediaType.IMAGE }
                    added += if (isFirstImage) {
                        draft.copy(category = AttachmentCategory.ORDER_SCREENSHOT)
                    } else {
                        draft
                    }
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

    fun setPendingCategory(media: MediaDraft, category: AttachmentCategory) {
        _ui.update {
            it.copy(
                pendingMedia = it.pendingMedia.map { item ->
                    if (item == media) item.copy(category = category) else item
                },
            )
        }
    }

    fun setExistingCategory(attachmentId: Long, category: AttachmentCategory) {
        viewModelScope.launch { repository.setAttachmentCategory(attachmentId, category) }
        _ui.update {
            it.copy(
                existingMedia = it.existingMedia.map { item ->
                    if (item.id == attachmentId) item.copy(category = category) else item
                },
            )
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null) }
    }

    /** OCR a screenshot, fill the form and attach it as the order screenshot. */
    fun recognizeImage(uri: Uri, attach: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(recognizing = true, message = null) }
            try {
                val text = recognizer.recognize(uri)
                if (text.isBlank()) {
                    _ui.update { it.copy(recognizing = false, message = "没识别到文字，可手动填写") }
                    return@launch
                }
                val parsed = ClipboardOrderParser.parse(text)
                if (parsed.isEmpty) {
                    _ui.update {
                        it.copy(recognizing = false, message = "截图里没识别到订单信息，可手动填写")
                    }
                    return@launch
                }
                if (attach && mediaCount() < MAX_MEDIA) {
                    val draft = runCatching { mediaStore.importFromUri(uri) }.getOrNull()
                    if (draft != null) {
                        val asOrder = draft.copy(category = AttachmentCategory.ORDER_SCREENSHOT)
                        _ui.update { state ->
                            state.copy(pendingMedia = state.pendingMedia + asOrder)
                        }
                    }
                }
                updateDraft { mergedWith(parsed) }
                _ui.update {
                    it.copy(
                        recognizing = false,
                        message = "已识别并填入：" + parsed.labels().joinToString("、"),
                    )
                }
            } catch (error: Throwable) {
                _ui.update {
                    it.copy(recognizing = false, message = error.message ?: "识别失败，可手动填写")
                }
            }
        }
    }

    fun applyParsed(parsed: com.quabtun.louji.util.ParsedOrder, overwrite: Boolean) {
        updateDraft {
            val base = if (overwrite) this else this
            base.mergedWith(parsed, onlyBlank = !overwrite)
        }
    }

    fun applyVoiceText(text: String) {
        val parsed = ClipboardOrderParser.parse(text)
        updateDraft {
            var next = mergedWith(parsed, onlyBlank = false)
            val lower = text
            if (Regex("免爬|不用爬|电梯直达|放外卖柜").containsMatchIn(lower)) {
                next = next.copy(noStairs = TriState.YES)
            } else if (Regex("要爬|没电梯|步梯|爬楼").containsMatchIn(lower)) {
                next = next.copy(noStairs = TriState.NO)
            }
            val note = next.remark
            next.copy(
                remark = if (note.isBlank()) text.take(300) else (note + " " + text).take(300),
            )
        }
    }

    fun applyBuilding(profile: BuildingProfileEntity) {
        updateDraft {
            copy(
                elevatorType = profile.elevatorType,
                accessCode = profile.accessCode,
                doorIntact = profile.doorIntact,
                canOpenFromAbove = profile.canOpenFromAbove,
            )
        }
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
                    syncBuildingProfile = state.syncBuildingProfile,
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
                    it.copy(saving = false, message = error.message ?: "保存失败，请重试")
                }
            }
        }
    }

    fun discard() {
        if (_ui.value.saving) return
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
        if (!saved && !_ui.value.saving) {
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
        phoneExt = phoneExt,
        accessCode = accessCode,
        elevatorType = elevatorType,
        deliveryMethod = deliveryMethod,
        isWarning = isWarning,
    )

    private companion object {
        const val MAX_MEDIA = 20
    }
}

private fun RecordDraft.mergedWith(
    parsed: com.quabtun.louji.util.ParsedOrder,
    onlyBlank: Boolean = true,
): RecordDraft {
    fun pick(current: String, incoming: String?): String =
        if (!onlyBlank || current.isBlank()) incoming ?: current else current
    return copy(
        location = pick(location, parsed.location),
        orderNo = pick(orderNo, parsed.orderNo),
        phoneTail = pick(phoneTail, parsed.phoneTail),
        phoneExt = pick(phoneExt, parsed.phoneExt),
        floor = pick(floor, parsed.floor),
        roomNo = pick(roomNo, parsed.roomNo),
        gender = if (onlyBlank && gender != Gender.UNKNOWN) gender else parsed.gender ?: gender,
        deliveryMethod = if (onlyBlank && deliveryMethod != DeliveryMethod.UNKNOWN) {
            deliveryMethod
        } else {
            parsed.deliveryMethod ?: deliveryMethod
        },
        remark = if (parsed.remarkHint.isNullOrBlank()) {
            remark
        } else if (remark.isBlank()) {
            parsed.remarkHint
        } else {
            remark
        },
    )
}

private fun Uri.isImage(): Boolean {
    val path = lastPathSegment?.lowercase().orEmpty()
    return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
        path.endsWith(".webp") || path.endsWith(".heic")
}
