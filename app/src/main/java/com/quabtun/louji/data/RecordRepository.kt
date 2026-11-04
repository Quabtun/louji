package com.quabtun.louji.data

import androidx.room.withTransaction
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.DateRange
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.RecordFilter
import com.quabtun.louji.model.RecordInsights
import com.quabtun.louji.model.RecordStats
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RecordRepository(
    private val database: LouJiDatabase,
    private val mediaStore: LocalMediaStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recordDao = database.recordDao()
    private val attachmentDao = database.attachmentDao()

    fun observeRecords(
        query: String,
        filter: RecordFilter,
        archived: Boolean,
        dateRange: DateRange = DateRange(),
    ): Flow<List<RecordWithAttachments>> =
        recordDao.observeFilteredByDate(
            query.trim(),
            filter.name,
            if (archived) 1 else 0,
            dateRange.start,
            dateRange.end,
        ).map { records -> records.map { it.withOrderedAttachments() } }

    fun observeTrashed(): Flow<List<RecordWithAttachments>> =
        recordDao.observeTrashed().map { records -> records.map { it.withOrderedAttachments() } }

    fun observeRecord(id: Long): Flow<RecordWithAttachments?> = recordDao.observeRecord(id)
        .map { it?.withOrderedAttachments() }

    fun observeStats(): Flow<RecordStats> = recordDao.observeStats(startOfMonth(clock()))

    fun observeInsights(): Flow<RecordInsights> =
        combine(recordDao.observeDecisionStats(), recordDao.observeLocationStats()) { decision, locations ->
            RecordInsights(decision = decision, locations = locations)
        }

    fun observeRecentLocations(): Flow<List<String>> = recordDao.observeRecentLocations()

    suspend fun getRecord(id: Long): RecordWithAttachments? =
        recordDao.getRecord(id)?.withOrderedAttachments()

    suspend fun save(
        draft: RecordDraft,
        recordId: Long?,
        pendingMedia: List<MediaDraft>,
        removedAttachmentIds: Set<Long> = emptySet(),
    ): Long {
        val now = clock()
        val removed = if (removedAttachmentIds.isEmpty()) {
            emptyList()
        } else {
            removedAttachmentIds.mapNotNull { attachmentDao.getById(it) }
        }

        val savedId = database.withTransaction {
            val location = draft.location.trim()
            val existing = recordId?.let { recordDao.getRecord(it) }?.record
            val id = if (existing == null) {
                recordDao.insert(
                    RecordEntity(
                        location = location,
                        orderNo = draft.orderNo.trim(),
                        gender = draft.gender,
                        phoneTail = draft.phoneTail.trim(),
                        floor = draft.floor.trim(),
                        roomNo = draft.roomNo.trim(),
                        doorIntact = draft.doorIntact,
                        canOpenFromAbove = draft.canOpenFromAbove,
                        noStairs = draft.noStairs,
                        willComeDown = draft.willComeDown,
                        personalityTags = draft.personalityTags.distinct(),
                        personalityNote = draft.personalityNote.trim(),
                        remark = draft.remark.trim(),
                        climbNote = draft.climbNote.trim(),
                        archived = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                recordDao.update(
                    existing.copy(
                        location = location,
                        orderNo = draft.orderNo.trim(),
                        gender = draft.gender,
                        phoneTail = draft.phoneTail.trim(),
                        floor = draft.floor.trim(),
                        roomNo = draft.roomNo.trim(),
                        doorIntact = draft.doorIntact,
                        canOpenFromAbove = draft.canOpenFromAbove,
                        noStairs = draft.noStairs,
                        willComeDown = draft.willComeDown,
                        personalityTags = draft.personalityTags.distinct(),
                        personalityNote = draft.personalityNote.trim(),
                        remark = draft.remark.trim(),
                        climbNote = draft.climbNote.trim(),
                        updatedAt = now,
                    ),
                )
                existing.id
            }

            if (removedAttachmentIds.isNotEmpty()) {
                val owned = removed.filter { it.recordId == id }
                owned.forEach { attachmentDao.deleteById(it.id) }
            }

            if (pendingMedia.isNotEmpty()) {
                attachmentDao.insertAll(
                    pendingMedia.mapIndexed { index, media ->
                        AttachmentEntity(
                            recordId = id,
                            type = media.type,
                            filePath = media.filePath,
                            thumbPath = media.thumbPath,
                            mimeType = media.mimeType,
                            sizeBytes = media.sizeBytes,
                            durationMs = media.durationMs,
                            createdAt = now + index,
                        )
                    },
                )
            }
            id
        }

        removed.forEach { mediaStore.deleteAttachment(it.filePath, it.thumbPath) }
        return savedId
    }

    suspend fun deleteRecord(id: Long) {
        val record = recordDao.getRecord(id) ?: return
        database.withTransaction { recordDao.deleteById(id) }
        record.attachments.forEach { mediaStore.deleteAttachment(it.filePath, it.thumbPath) }
    }

    suspend fun softDelete(id: Long) {
        recordDao.setDeletedAt(id, clock())
    }

    suspend fun restore(id: Long) {
        recordDao.setDeletedAt(id, 0)
    }

    /**
     * Permanently removes records that have been in the recycle bin for at least [olderThanMs],
     * along with their media files. Called once at startup.
     */
    suspend fun purgeTrashed(olderThanMs: Long) {
        val cutoff = clock() - olderThanMs
        val expired = recordDao.getTrashedBefore(cutoff)
        if (expired.isEmpty()) return
        database.withTransaction {
            expired.forEach { recordDao.deleteById(it.record.id) }
        }
        expired.forEach { record ->
            record.attachments.forEach { mediaStore.deleteAttachment(it.filePath, it.thumbPath) }
        }
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        recordDao.setArchived(id, archived)
    }

    suspend fun setArchivedBatch(ids: List<Long>, archived: Boolean) {
        if (ids.isEmpty()) return
        recordDao.setArchivedForIds(ids, archived)
    }

    suspend fun softDeleteBatch(ids: List<Long>) {
        if (ids.isEmpty()) return
        recordDao.setDeletedAtForIds(ids, clock())
    }

    suspend fun deleteAttachment(attachmentId: Long) {
        val attachment = attachmentDao.getById(attachmentId) ?: return
        database.withTransaction { attachmentDao.deleteById(attachmentId) }
        mediaStore.deleteAttachment(attachment.filePath, attachment.thumbPath)
    }

    suspend fun clearAll() {
        val attachments = attachmentDao.getAllOnce()
        database.withTransaction {
            attachmentDao.deleteAll()
            recordDao.deleteAll()
        }
        attachments.forEach { mediaStore.deleteAttachment(it.filePath, it.thumbPath) }
    }

    suspend fun duplicateDraft(id: Long): RecordDraft? {
        val record = recordDao.getRecord(id)?.record ?: return null
        return RecordDraft(
            location = record.location,
            orderNo = "",
            gender = record.gender,
            phoneTail = record.phoneTail,
            floor = record.floor,
            roomNo = record.roomNo,
            doorIntact = record.doorIntact,
            canOpenFromAbove = record.canOpenFromAbove,
            noStairs = record.noStairs,
            willComeDown = record.willComeDown,
            personalityTags = record.personalityTags,
            personalityNote = record.personalityNote,
            remark = record.remark,
            climbNote = record.climbNote,
        )
    }

    suspend fun referencedMediaPaths(): Set<String> {
        val paths = mutableSetOf<String>()
        attachmentDao.getAllOnce().forEach { attachment ->
            paths += attachment.filePath
            attachment.thumbPath?.let(paths::add)
        }
        return paths
    }

    suspend fun allRecords(): List<RecordWithAttachments> =
        recordDao.getAllOnce()
            .filter { it.record.deletedAt == 0L }
            .map { it.withOrderedAttachments() }

    suspend fun countBySignature(location: String, orderNo: String, createdAt: Long): Int =
        recordDao.countBySignature(location, orderNo, createdAt)

    suspend fun insertImported(
        entity: RecordEntity,
        media: List<Pair<MediaDraft, Long>>,
    ): Long = database.withTransaction {
        val id = recordDao.insert(entity)
        if (media.isNotEmpty()) {
            attachmentDao.insertAll(
                media.mapIndexed { index, (draft, createdAt) ->
                    AttachmentEntity(
                        recordId = id,
                        type = draft.type,
                        filePath = draft.filePath,
                        thumbPath = draft.thumbPath,
                        mimeType = draft.mimeType,
                        sizeBytes = draft.sizeBytes,
                        durationMs = draft.durationMs,
                        createdAt = createdAt + index,
                    )
                },
            )
        }
        id
    }

    /**
     * Imports a batch atomically. With [replaceAll] the old rows are removed inside the same
     * transaction, so a failed import leaves the previous data untouched.
     */
    suspend fun insertImportedBatch(
        items: List<Pair<RecordEntity, List<Pair<MediaDraft, Long>>>>,
        replaceAll: Boolean,
    ): Int = database.withTransaction {
        if (replaceAll) {
            attachmentDao.deleteAll()
            recordDao.deleteAll()
        }
        items.forEach { (entity, media) ->
            val id = recordDao.insert(entity)
            if (media.isNotEmpty()) {
                attachmentDao.insertAll(
                    media.mapIndexed { index, (draft, createdAt) ->
                        AttachmentEntity(
                            recordId = id,
                            type = draft.type,
                            filePath = draft.filePath,
                            thumbPath = draft.thumbPath,
                            mimeType = draft.mimeType,
                            sizeBytes = draft.sizeBytes,
                            durationMs = draft.durationMs,
                            createdAt = createdAt + index,
                        )
                    },
                )
            }
        }
        items.size
    }

    suspend fun clearDatabaseOnly() {
        database.withTransaction {
            attachmentDao.deleteAll()
            recordDao.deleteAll()
        }
    }

    private fun RecordWithAttachments.withOrderedAttachments(): RecordWithAttachments =
        if (attachments.size < 2) {
            this
        } else {
            copy(attachments = attachments.sortedWith(compareBy({ it.createdAt }, { it.id })))
        }

    private fun startOfMonth(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
