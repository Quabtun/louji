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
    val recognizedFields: List<String> = emptyList(),
    /** Raw OCR lines kept for the "OCR 原文候选" picker when parsing found nothing usable. */
    val ocrLines: List<String> = emptyList(),
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
                var sharedImageFailed = false
                if (shared != null) {
                    shareIntake.consume()
                    shared.text?.let { draft = draft.mergedWith(ClipboardOrderParser.parse(it)) }
                    // Reuse OCR/media produced by the receiving screen: the external URI may
                    // already be revoked, and a second OCR pass would double the cost.
                    val ocr = shared.ocr
                    if (ocr != null && ocr.visualLines.isNotEmpty()) {
                        draft = draft.mergedWith(ClipboardOrderParser.parse(ocr.rawText))
                    }
                    if (ocr != null && ocr.errorReason != null) {
                        _ui.update { it.copy(message = "识别未完成：${ocr.errorReason}") }
                    }
                    val media = shared.imageMedia ?: shared.imageUri?.let { uri ->
                        runCatching { mediaStore.importFromUri(uri) }.getOrNull()
                    }
                    if (media != null) {
                        _ui.update {
                            it.copy(
                                pendingMedia = it.pendingMedia + media.copy(
                                    category = AttachmentCategory.ORDER_SCREENSHOT,
                                ),
                                ocrLines = ocr?.visualLines.orEmpty(),
                            )
                        }
                    } else if (shared.imageUri != null) {
                        sharedImageFailed = true
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
                        message = if (sharedImageFailed) "分享的图片无法读取，可重新添加" else it.message,
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
            var firstImageSeen = false
            for (uri in uris) {
                if (count >= MAX_MEDIA) {
                    failure = "一条记录最多 $MAX_MEDIA 个附件"
                    break
                }
                try {
                    val draft = mediaStore.importFromUri(uri)
                    // Photo Picker URIs commonly have no file extension, so the imported type —
                    // not `lastPathSegment` — decides what counts as the order screenshot.
                    val isFirstImage = !firstImageSeen &&
                        draft.type == com.quabtun.louji.model.MediaType.IMAGE
                    if (isFirstImage) firstImageSeen = true
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
        _ui.update { state -> state.copy(pendingMedia = state.pendingMedia - media) }
    }

    /** Swaps a draft for its annotated version, keeping position and category. */
    fun replacePending(old: MediaDraft, annotated: MediaDraft) {
        val index = _ui.value.pendingMedia.indexOf(old)
        if (index < 0) {
            mediaStore.deleteDraft(annotated)
            return
        }
        mediaStore.deleteDraft(old)
        _ui.update { state ->
            val next = state.pendingMedia.toMutableList()
            next[index] = annotated
            state.copy(pendingMedia = next)
        }
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

    /**
     * OCR a screenshot, attach it as the order screenshot and fill whatever parsed cleanly.
     *
     * When [onParsed] is provided the caller owns the write (so the UI can show one shared
     * overwrite confirmation for clipboard, OCR and voice); the parsed fields are not applied
     * here.
     */
    fun recognizeImage(
        uri: Uri,
        attach: Boolean = true,
        onParsed: ((com.quabtun.louji.util.ParsedOrder) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(recognizing = true, message = null) }
            // The screenshot is attached first: a failed parse must never lose the rider's image.
            var importFailure: String? = null
            if (attach && mediaCount() < MAX_MEDIA) {
                val media = runCatching { mediaStore.importFromUri(uri) }.getOrNull()
                if (media != null) {
                    val asOrder = media.copy(category = AttachmentCategory.ORDER_SCREENSHOT)
                    _ui.update { state ->
                        state.copy(pendingMedia = state.pendingMedia + asOrder)
                    }
                } else {
                    importFailure = "截图无法读取，未挂到记录上"
                }
            }

            val result = runCatching { recognizer.analyze(uri) }
                .getOrElse { error ->
                    com.quabtun.louji.data.OcrResult(
                        errorReason = error.message ?: "识别失败",
                    )
                }
            if (result.rawText.isBlank()) {
                _ui.update {
                    it.copy(
                        recognizing = false,
                        ocrLines = result.visualLines,
                        message = listOfNotNull(result.errorReason, importFailure)
                            .firstOrNull()
                            ?.let { reason -> "识别未完成：$reason" }
                            ?: "没识别到文字，可手动填写",
                    )
                }
                return@launch
            }

            val parsed = ClipboardOrderParser.parse(result.rawText)
            if (parsed.isEmpty) {
                _ui.update {
                    it.copy(
                        recognizing = false,
                        ocrLines = result.visualLines,
                        message = "没识别到订单字段，可从下方 OCR 原文拾取" +
                            (importFailure?.let { "（$it）" } ?: ""),
                    )
                }
                return@launch
            }
            if (onParsed != null) {
                _ui.update {
                    it.copy(
                        recognizing = false,
                        recognizedFields = parsed.labels(),
                        ocrLines = result.visualLines,
                        message = "已识别 ${parsed.labels().size} 项，确认后填入",
                    )
                }
                onParsed(parsed)
                return@launch
            }
            updateDraft { mergedWith(parsed) }
            _ui.update {
                it.copy(
                    recognizing = false,
                    recognizedFields = parsed.labels(),
                    ocrLines = result.visualLines,
                    message = "已识别并填入：" + parsed.labels().joinToString("、"),
                )
            }
        }
    }

    /** Applies one raw OCR line to a field the rider picked from the candidate panel. */
    fun applyOcrLine(line: String, field: OcrField) {
        val value = line.trim().take(120)
        if (value.isEmpty()) return
        when (field) {
            OcrField.LOCATION -> {
                val address = ClipboardOrderParser.splitAddress(value)
                updateDraft {
                    copy(
                        location = (address.location ?: value).take(60),
                        floor = address.floor ?: floor,
                        roomNo = address.roomNo ?: roomNo,
                    )
                }
            }
            OcrField.FLOOR -> {
                val digits = Regex("""\d{1,3}""").find(value)?.value
                if (digits != null) updateDraft { copy(floor = digits) }
            }
            OcrField.ROOM -> {
                val room = Regex("""[A-Za-z]?\d{1,3}-?\d{0,4}""").find(value)?.value
                if (room != null) updateDraft { copy(roomNo = room) }
            }
            OcrField.PHONE_TAIL -> {
                val digits = value.filter(Char::isDigit)
                if (digits.length >= 4) updateDraft { copy(phoneTail = digits.takeLast(4)) }
            }
            OcrField.PHONE_EXT -> {
                val digits = value.filter(Char::isDigit)
                if (digits.isNotEmpty()) updateDraft { copy(phoneExt = digits.take(8)) }
            }
            OcrField.REMARK -> updateDraft { copy(remark = value.take(300)) }
        }
    }

    fun clearOcrLines() {
        _ui.update { it.copy(ocrLines = emptyList()) }
    }

    fun applyParsed(parsed: com.quabtun.louji.util.ParsedOrder, overwrite: Boolean) {
        updateDraft {
            val base = if (overwrite) this else this
            base.mergedWith(parsed, onlyBlank = !overwrite)
        }
        _ui.update { it.copy(recognizedFields = parsed.labels()) }
    }

    fun applyVoiceText(text: String) {
        val parsed = ClipboardOrderParser.parse(text)
        updateDraft { mergedWith(parsed, onlyBlank = false) }
        applyVoiceRemarks(text)
    }

    /**
     * Voice-only extras: climb hints and the spoken text itself. Kept separate from field filling
     * so the edit screen can confirm field overwrites first and then append the remark.
     */
    fun applyVoiceRemarks(text: String) {
        updateDraft {
            var next = this
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

    /**
     * Applies a building profile without destroying what the rider already typed: empty access
     * codes and UNKNOWN tri-states stay as they were, and a direct lift suggests "免爬".
     */
    fun applyBuilding(profile: BuildingProfileEntity) {
        updateDraft {
            copy(
                elevatorType = profile.elevatorType.takeIf { it != ElevatorType.UNKNOWN }
                    ?: elevatorType,
                accessCode = profile.accessCode.takeIf { it.isNotBlank() } ?: accessCode,
                doorIntact = profile.doorIntact.takeIf { it != TriState.UNKNOWN } ?: doorIntact,
                canOpenFromAbove = profile.canOpenFromAbove.takeIf { it != TriState.UNKNOWN }
                    ?: canOpenFromAbove,
                noStairs = when {
                    noStairs != TriState.UNKNOWN -> noStairs
                    profile.elevatorType == ElevatorType.STAIRS_ONLY -> TriState.NO
                    profile.elevatorType == ElevatorType.DIRECT && !isWarning -> TriState.YES
                    else -> noStairs
                },
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

/** Fields the rider can fill from a raw OCR line. */
enum class OcrField(val label: String) {
    LOCATION("位置"),
    FLOOR("楼层"),
    ROOM("房号"),
    PHONE_TAIL("尾号"),
    PHONE_EXT("分机号"),
    REMARK("备注"),
}
