package com.quabtun.louji.model

import com.quabtun.louji.data.db.RecordWithAttachments

enum class RecordSort {
    UPDATED,
    FLOOR,
    LOCATION,
}

object RecordSorter {
    fun sort(items: List<RecordWithAttachments>, sort: RecordSort): List<RecordWithAttachments> =
        when (sort) {
            // "最近更新" means the record was last touched, matching the DAO paging order.
            RecordSort.UPDATED -> items.sortedWith(
                compareByDescending<RecordWithAttachments> { it.record.updatedAt }
                    .thenByDescending { it.record.id },
            )
            RecordSort.FLOOR -> items.sortedWith(
                compareBy<RecordWithAttachments> { floorKey(it.record.floor).first }
                    .thenBy { floorKey(it.record.floor).second }
                    .thenByDescending { it.record.updatedAt },
            )
            RecordSort.LOCATION -> items.sortedWith(
                compareBy<RecordWithAttachments> { it.record.location.lowercase() }
                    .thenByDescending { it.record.updatedAt },
            )
        }

    private fun floorKey(floor: String): Pair<Int, String> {
        val number = Regex("""\d+""").find(floor)?.value?.toIntOrNull() ?: Int.MAX_VALUE
        return number to floor.trim()
    }
}
