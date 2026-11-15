package com.quabtun.louji.data

import android.net.Uri
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.Gender
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.OrderInference
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.TriState
import com.quabtun.louji.util.ParsedOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns parsed order text (from the clipboard, OCR or a system share) into a saved record in one
 * step. The database write is the repository's single transaction; the screenshot file is copied
 * into app-private media before it, and removed again if the write fails.
 */
class OrderAutoSaver(
    private val repository: RecordRepository,
    private val mediaStore: LocalMediaStore,
) {

    data class Saved(
        val recordId: Long,
        val draft: RecordDraft,
        val sourceLabel: String,
    ) {
        /** "阳光花园 3 栋 · 18-1804" — used by the success feedback. */
        fun label(): String = buildString {
            append(draft.location)
            val place = listOf(draft.floor, draft.roomNo)
                .filter { it.isNotBlank() }
                .joinToString("-")
            if (place.isNotBlank()) append(" · ").append(place)
        }
    }

    suspend fun save(
        parsed: ParsedOrder,
        screenshotUri: Uri? = null,
        sourceLabel: String,
        rawText: String? = null,
        preImportedMedia: MediaDraft? = null,
    ): Saved = withContext(Dispatchers.IO) {
        val location = resolveLocation(parsed, rawText)
        val unresolved = location == UNKNOWN_LOCATION
        val building = if (unresolved) {
            null
        } else {
            repository.findBuildingMatch(location)
        }
        val warningAtBuilding = if (unresolved) {
            false
        } else {
            runCatching {
                repository.recordsAtLocation(location).any { item ->
                    item.record.isWarning && item.record.deletedAt == 0L && !item.record.archived
                }
            }.getOrDefault(false)
        }
        val draft = RecordDraft(
            location = location,
            orderNo = parsed.orderNo.orEmpty().trim(),
            gender = parsed.gender ?: Gender.UNKNOWN,
            phoneTail = parsed.phoneTail.orEmpty().trim(),
            floor = parsed.floor.orEmpty().trim(),
            roomNo = parsed.roomNo.orEmpty().trim(),
            doorIntact = building?.doorIntact ?: TriState.UNKNOWN,
            canOpenFromAbove = building?.canOpenFromAbove ?: TriState.UNKNOWN,
            noStairs = OrderInference.inferNoStairs(
                deliveryMethod = parsed.deliveryMethod,
                elevatorType = building?.elevatorType,
                floor = parsed.floor,
                rawText = rawText,
                warningAtBuilding = warningAtBuilding,
            ),
            willComeDown = if (parsed.deliveryMethod == DeliveryMethod.COME_DOWN) {
                TriState.YES
            } else {
                TriState.UNKNOWN
            },
            remark = parsed.remarkHint.orEmpty().trim().ifBlank {
                // Surface the building's fixed access hints when the order text has none.
                building?.let { profile ->
                    buildList {
                        if (profile.lockerInfo.isNotBlank()) add("柜点 ${profile.lockerInfo.take(20)}")
                        profile.accessTags.take(3).forEach { add(it) }
                        if (profile.notes.isNotBlank()) add(profile.notes.take(24))
                    }.joinToString(" · ").take(300)
                }.orEmpty()
            },
            phoneExt = parsed.phoneExt.orEmpty().trim(),
            accessCode = building?.accessCode.orEmpty().trim(),
            elevatorType = building?.elevatorType ?: ElevatorType.UNKNOWN,
            deliveryMethod = parsed.deliveryMethod ?: DeliveryMethod.UNKNOWN,
        )

        var media: MediaDraft? = null
        val owned = preImportedMedia == null
        try {
            media = preImportedMedia?.copy(
                category = AttachmentCategory.ORDER_SCREENSHOT,
            ) ?: screenshotUri?.let {
                mediaStore.importFromUri(it).copy(
                    category = AttachmentCategory.ORDER_SCREENSHOT,
                )
            }
            val id = repository.save(
                draft = draft,
                recordId = null,
                pendingMedia = listOfNotNull(media),
                syncBuildingProfile = false,
                syncProfile = false,
            )
            Saved(recordId = id, draft = draft, sourceLabel = sourceLabel)
        } catch (error: Throwable) {
            if (owned) media?.let(mediaStore::deleteDraft)
            throw error
        }
    }

    /**
     * Only accepts a parsed location or a raw line that actually looks like an address. The first
     * line of a screenshot is routinely "美团外卖" or an order number, so it is never used blindly;
     * without a location the record is saved as [UNKNOWN_LOCATION] for the rider to finish.
     */
    private fun resolveLocation(parsed: ParsedOrder, rawText: String?): String {
        parsed.location?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(60) }
        rawText?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.firstOrNull { line ->
                line.length in 2..60 &&
                    ADDRESS_HINT.containsMatchIn(line) &&
                    !NON_ADDRESS_LINE.containsMatchIn(line)
            }
            ?.let { return it.take(60) }
        return UNKNOWN_LOCATION
    }

    companion object {
        const val UNKNOWN_LOCATION = "未知楼栋-待确认"

        private val ADDRESS_HINT = Regex(
            "小区|花园|公寓|大厦|苑|号楼|栋|单元|路|街|道|巷|广场|城|村|居|府|公馆",
        )
        private val NON_ADDRESS_LINE = Regex(
            "^(美团|饿了么|闪送|达达|顺丰|京东|订单|流水|取货|取餐|尾号|备注|#|￥|¥|\\d+$)",
        )
    }
}
