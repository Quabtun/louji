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
            RecordSort.UPDATED -> items.sortedByDescending { it.record.createdAt }
            RecordSort.FLOOR -> items.sortedWith(
                compareBy<RecordWithAttachments> { floorKey(it.record.floor).first }
                    .thenBy { floorKey(it.record.floor).second }
                    .thenBy { it.record.createdAt },
            )
            RecordSort.LOCATION -> items.sortedWith(
                compareBy<RecordWithAttachments> { it.record.location.lowercase() }
                    .thenByDescending { it.record.createdAt },
            )
        }

    private fun floorKey(floor: String): Pair<Int, String> {
        val number = Regex("""\d+""").find(floor)?.value?.toIntOrNull() ?: Int.MAX_VALUE
        return number to floor.trim()
    }
}
