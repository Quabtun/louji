package com.quabtun.louji.data

import androidx.room.withTransaction
import com.quabtun.louji.data.db.AttachmentEntity
import com.quabtun.louji.data.db.BuildingProfileEntity
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.data.db.RecordEntity
import com.quabtun.louji.data.db.RecordWithAttachments
import com.quabtun.louji.model.AttachmentCategory
import com.quabtun.louji.model.BuildingRank
import com.quabtun.louji.model.BuildingStats
import com.quabtun.louji.model.ClimbTotals
import com.quabtun.louji.model.CustomerProfile
import com.quabtun.louji.model.DayBucket
import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.ElevatorType
import com.quabtun.louji.model.MediaDraft
import com.quabtun.louji.model.DateRange
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.model.RecordFilter
import com.quabtun.louji.model.RecordInsights
import com.quabtun.louji.model.RecordMerger
import com.quabtun.louji.model.RecordStats
import com.quabtun.louji.model.TriState
import java.util.Calendar
import java.util.TimeZone
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

    fun recordPagingSource(
        query: String,
        filter: RecordFilter,
        archived: Boolean,
        dateRange: DateRange,
    ): androidx.paging.PagingSource<Int, RecordWithAttachments> =
        recordDao.loadFilteredPagedSource(
            query.trim(),
            filter.name,
            if (archived) 1 else 0,
            dateRange.start,
            dateRange.end,
        )

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
        syncBuildingProfile: Boolean = false,
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
                        phoneExt = draft.phoneExt.trim(),
                        accessCode = draft.accessCode.trim(),
                        elevatorType = draft.elevatorType,
                        deliveryMethod = draft.deliveryMethod,
                        isWarning = draft.isWarning,
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
                        phoneExt = draft.phoneExt.trim(),
                        accessCode = draft.accessCode.trim(),
                        elevatorType = draft.elevatorType,
                        deliveryMethod = draft.deliveryMethod,
                        isWarning = draft.isWarning,
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
                            category = media.category,
                        )
                    },
                )
            }

            if (location.isNotBlank()) {
                upsertBuildingProfile(location, draft, overwrite = syncBuildingProfile, now = now)
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

    suspend fun findDuplicateGroups(): List<List<RecordWithAttachments>> =
        RecordMerger.findDuplicateGroups(
            recordDao.getActiveOnce().map { it.withOrderedAttachments() },
        )

    suspend fun mergeGroup(group: List<RecordWithAttachments>) {
        if (group.size < 2) return
        val base = group.first()
        val others = group.drop(1)
        val merged = RecordMerger.mergedFields(base.record, others.map { it.record })
        val fromIds = others.map { it.record.id }
        database.withTransaction {
            recordDao.update(merged)
            if (fromIds.isNotEmpty()) {
                attachmentDao.reparentAttachments(fromIds, base.record.id)
            }
            recordDao.setDeletedAtForIds(fromIds, clock())
        }
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
            phoneExt = record.phoneExt,
            accessCode = record.accessCode,
            elevatorType = record.elevatorType,
            deliveryMethod = record.deliveryMethod,
            isWarning = record.isWarning,
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

    // ---------------------------------------------------------------------
    // 2.0 building library · radar · insights
    // ---------------------------------------------------------------------

    private val buildingDao = database.buildingDao()

    suspend fun allBuildings(): List<BuildingProfileEntity> = buildingDao.allOnce()

    /** Imports building profiles; with [replaceAll] the local library is replaced first. */
    suspend fun importBuildings(items: List<BuildingProfileEntity>, replaceAll: Boolean) {
        if (items.isEmpty() && !replaceAll) return
        database.withTransaction {
            if (replaceAll) buildingDao.deleteAll()
            items.forEach { profile ->
                val existing = buildingDao.findByLocation(profile.location)
                if (existing == null) {
                    buildingDao.insertIgnore(profile)
                } else {
                    buildingDao.update(
                        profile.copy(id = existing.id, createdAt = existing.createdAt),
                    )
                }
            }
        }
    }

    fun observeBuildings(): Flow<List<BuildingProfileEntity>> = buildingDao.observeAll()

    fun observeBuilding(location: String): Flow<BuildingProfileEntity?> =
        buildingDao.observeByLocation(location)

    suspend fun findBuilding(location: String): BuildingProfileEntity? =
        location.trim().takeIf { it.isNotEmpty() }?.let { buildingDao.findByLocation(it) }

    suspend fun findBuildingMatch(query: String): BuildingProfileEntity? {
        val trimmed = query.trim()
        if (trimmed.length < 2) return null
        return buildingDao.findBestMatch(trimmed)
    }

    fun observeBuildingStats(location: String): Flow<BuildingStats> =
        buildingDao.observeStats(location)

    suspend fun buildingStats(location: String): BuildingStats =
        buildingDao.statsOnce(location)

    fun observeBuildingRankings(): Flow<List<BuildingRank>> = buildingDao.observeRankings()

    suspend fun saveBuilding(profile: BuildingProfileEntity) {
        val existing = buildingDao.findByLocation(profile.location)
        if (existing == null) {
            buildingDao.insertIgnore(profile.copy(updatedAt = clock()))
        } else {
            buildingDao.update(
                profile.copy(
                    id = existing.id,
                    createdAt = existing.createdAt,
                    updatedAt = clock(),
                ),
            )
        }
    }

    suspend fun deleteBuilding(id: Long) {
        buildingDao.deleteById(id)
    }

    suspend fun setBuildingPinned(id: Long, pinned: Boolean) {
        buildingDao.setPinned(id, pinned, clock())
    }

    /** Creates a profile for [location] or folds non-empty draft values into the existing one. */
    suspend fun upsertBuildingProfile(
        location: String,
        draft: RecordDraft,
        overwrite: Boolean,
        now: Long = clock(),
    ) {
        val key = location.trim()
        if (key.isEmpty()) return
        val existing = buildingDao.findByLocation(key)
        if (existing == null) {
            buildingDao.insertIgnore(
                BuildingProfileEntity(
                    location = key,
                    elevatorType = draft.elevatorType,
                    accessCode = draft.accessCode.trim(),
                    doorIntact = draft.doorIntact,
                    canOpenFromAbove = draft.canOpenFromAbove,
                    lockerInfo = if (draft.deliveryMethod == DeliveryMethod.LOCKER) {
                        draft.remark.trim()
                    } else {
                        ""
                    },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            buildingDao.update(existing.foldFrom(draft, overwrite).copy(updatedAt = now))
        }
    }

    private fun BuildingProfileEntity.foldFrom(draft: RecordDraft, overwrite: Boolean) = copy(
        elevatorType = if (overwrite || elevatorType == ElevatorType.UNKNOWN) {
            draft.elevatorType.takeIf { it != ElevatorType.UNKNOWN } ?: elevatorType
        } else {
            elevatorType
        },
        accessCode = if (overwrite || accessCode.isBlank()) {
            draft.accessCode.trim().ifBlank { accessCode }
        } else {
            accessCode
        },
        doorIntact = if (overwrite || doorIntact == TriState.UNKNOWN) {
            draft.doorIntact.takeIf { it != TriState.UNKNOWN } ?: doorIntact
        } else {
            doorIntact
        },
        canOpenFromAbove = if (overwrite || canOpenFromAbove == TriState.UNKNOWN) {
            draft.canOpenFromAbove.takeIf { it != TriState.UNKNOWN } ?: canOpenFromAbove
        } else {
            canOpenFromAbove
        },
    )

    /** Rebuilds profiles from history — used after importing a v1 backup that has no profiles. */
    suspend fun rebuildBuildingProfiles() {
        val records = recordDao.getActiveOnce().map { it.record }
        records.filter { it.location.isNotBlank() }
            .groupBy { it.location.trim() }
            .forEach { (location, list) ->
                val newest = list.maxByOrNull { it.updatedAt } ?: return@forEach
                val latestWithElevator = list.filter { it.elevatorType != ElevatorType.UNKNOWN }
                    .maxByOrNull { it.updatedAt }
                val latestWithCode = list.filter { it.accessCode.isNotBlank() }
                    .maxByOrNull { it.updatedAt }
                val existing = buildingDao.findByLocation(location)
                val merged = (existing ?: BuildingProfileEntity(
                    location = location,
                    createdAt = list.minOf { it.createdAt },
                    updatedAt = newest.updatedAt,
                )).copy(
                    elevatorType = existing?.elevatorType?.takeIf { it != ElevatorType.UNKNOWN }
                        ?: latestWithElevator?.elevatorType ?: ElevatorType.UNKNOWN,
                    accessCode = existing?.accessCode?.takeIf { it.isNotBlank() }
                        ?: latestWithCode?.accessCode.orEmpty(),
                    doorIntact = existing?.doorIntact?.takeIf { it != TriState.UNKNOWN }
                        ?: newest.doorIntact,
                    canOpenFromAbove = existing?.canOpenFromAbove?.takeIf { it != TriState.UNKNOWN }
                        ?: newest.canOpenFromAbove,
                    updatedAt = newest.updatedAt,
                )
                if (existing == null) buildingDao.insertIgnore(merged) else buildingDao.update(merged)
            }
    }

    suspend fun customerProfile(
        phoneTail: String,
        location: String,
        roomNo: String,
    ): CustomerProfile {
        val tail = phoneTail.trim()
        val place = location.trim()
        val room = roomNo.trim()
        val agg = when {
            tail.length == 4 -> recordDao.customerAggregateByTail(tail)
            place.isNotEmpty() && room.isNotEmpty() -> recordDao.customerAggregateByPlace(place, room)
            else -> return CustomerProfile()
        }
        if (agg.total <= 0) return CustomerProfile()
        val last = if (tail.length == 4) {
            recordDao.lastRecordByTail(tail)
        } else {
            recordDao.lastRecordByPlace(place, room)
        }
        return CustomerProfile(
            total = agg.total,
            noStairs = agg.noStairs,
            willComeDown = agg.willComeDown,
            warnings = agg.warnings,
            lastNoStairs = last?.noStairs ?: TriState.UNKNOWN,
            lastNote = last?.climbNote.orEmpty(),
            lastRemark = last?.remark.orEmpty(),
            tags = last?.personalityTags ?: emptyList(),
            lastAt = last?.createdAt ?: 0L,
        )
    }

    fun observeClimbTotals(since: Long, until: Long): Flow<ClimbTotals> =
        combine(
            recordDao.observeTotalsForRange(since, until),
            recordDao.observeClimbedFloorsForRange(since, until),
        ) { totals, floors ->
            totals.copy(
                floorsClimbed = floors.sumOf { row ->
                    com.quabtun.louji.model.FloorMath.climbedFloor(row.floor) ?: 0
                },
            )
        }

    fun observeDayBuckets(since: Long): Flow<List<DayBucket>> {
        val dayMs = 24L * 60 * 60 * 1000
        val offset = TimeZone.getDefault().getOffset(clock()).toLong()
        return recordDao.observeDayBuckets(since, dayMs, offset)
    }

    suspend fun addAttachment(recordId: Long, media: MediaDraft): Long =
        attachmentDao.insertAll(
            listOf(
                AttachmentEntity(
                    recordId = recordId,
                    type = media.type,
                    filePath = media.filePath,
                    thumbPath = media.thumbPath,
                    mimeType = media.mimeType,
                    sizeBytes = media.sizeBytes,
                    durationMs = media.durationMs,
                    createdAt = clock(),
                    category = media.category,
                ),
            ),
        ).firstOrNull() ?: 0L

    suspend fun attachment(attachmentId: Long): AttachmentEntity? = attachmentDao.getById(attachmentId)

    suspend fun setAttachmentCategory(attachmentId: Long, category: AttachmentCategory) {
        attachmentDao.setCategory(attachmentId, category.name)
    }

    fun observeRecordsAtLocation(location: String): Flow<List<RecordWithAttachments>> =
        recordDao.observeAtLocation(location.trim()).map { list ->
            list.map { it.withOrderedAttachments() }
        }

    suspend fun recordsAtLocation(location: String): List<RecordWithAttachments> =
        recordDao.recordsAtLocation(location.trim()).map { record ->
            RecordWithAttachments(record, attachmentDao.getForRecord(record.id))
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
