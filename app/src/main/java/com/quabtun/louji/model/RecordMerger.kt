package com.quabtun.louji.model

import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.data.db.RecordWithAttachments

/**
 * Detects and merges duplicate records. A duplicate is two or more active records that share
 * either the same location + order number, or (when the order number is blank) the same
 * location + floor + room number.
 */
object RecordMerger {
    fun mergeKey(record: RecordEntity): String? {
        val location = record.location.trim()
        if (location.isEmpty()) return null
        val order = record.orderNo.trim()
        if (order.isNotEmpty()) return "order|$location|$order"
        val floor = record.floor.trim()
        val room = record.roomNo.trim()
        if (floor.isNotEmpty() && room.isNotEmpty()) return "place|$location|$floor|$room"
        return null
    }

    fun findDuplicateGroups(records: List<RecordWithAttachments>): List<List<RecordWithAttachments>> =
        records.groupBy { mergeKey(it.record) }
            .filterKeys { it != null }
            .values
            .filter { it.size > 1 }
            .map { group -> group.sortedByDescending { it.attachments.size } }

    /**
     * Merges the fields of [others] into [base]: unions tags, fills blank text fields, and
     * upgrades UNKNOWN tri-state/gender fields from the first record that has a concrete value.
     */
    fun mergedFields(base: RecordEntity, others: List<RecordEntity>): RecordEntity {
        var result = base
        others.forEach { other ->
            result = result.copy(
                orderNo = result.orderNo.ifBlank { other.orderNo },
                phoneTail = result.phoneTail.ifBlank { other.phoneTail },
                phoneExt = result.phoneExt.ifBlank { other.phoneExt },
                accessCode = result.accessCode.ifBlank { other.accessCode },
                floor = result.floor.ifBlank { other.floor },
                roomNo = result.roomNo.ifBlank { other.roomNo },
                personalityTags = (result.personalityTags + other.personalityTags).distinct(),
                personalityNote = result.personalityNote.ifBlank { other.personalityNote },
                remark = result.remark.ifBlank { other.remark },
                climbNote = result.climbNote.ifBlank { other.climbNote },
                gender = if (result.gender == Gender.UNKNOWN) other.gender else result.gender,
                noStairs = if (result.noStairs == TriState.UNKNOWN) other.noStairs else result.noStairs,
                willComeDown = if (result.willComeDown == TriState.UNKNOWN) other.willComeDown else result.willComeDown,
                doorIntact = if (result.doorIntact == TriState.UNKNOWN) other.doorIntact else result.doorIntact,
                canOpenFromAbove = if (result.canOpenFromAbove == TriState.UNKNOWN) other.canOpenFromAbove else result.canOpenFromAbove,
                elevatorType = if (result.elevatorType == ElevatorType.UNKNOWN) other.elevatorType else result.elevatorType,
                deliveryMethod = if (result.deliveryMethod == DeliveryMethod.UNKNOWN) other.deliveryMethod else result.deliveryMethod,
                isWarning = result.isWarning || other.isWarning,
            )
        }
        return result
    }
}
