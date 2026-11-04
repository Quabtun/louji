package com.quabtun.louji.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.quabtun.louji.model.DecisionStats
import com.quabtun.louji.model.LocationStat
import com.quabtun.louji.model.RecordStats
import kotlinx.coroutines.flow.Flow

private const val FILTER_WHERE = """
    SELECT * FROM records
    WHERE (
        :query = ''
        OR location LIKE '%' || :query || '%'
        OR orderNo LIKE '%' || :query || '%'
        OR phoneTail LIKE '%' || :query || '%'
        OR floor LIKE '%' || :query || '%'
        OR roomNo LIKE '%' || :query || '%'
        OR remark LIKE '%' || :query || '%'
        OR climbNote LIKE '%' || :query || '%'
        OR personalityNote LIKE '%' || :query || '%'
        OR personalityTags LIKE '%' || :query || '%'
    )
    AND (
        :filter = 'ALL'
        OR (:filter = 'STAIRS' AND noStairs = 'NO')
        OR (:filter = 'NO_STAIRS' AND noStairs = 'YES')
        OR (:filter = 'WILL_COME_DOWN' AND willComeDown = 'YES')
    )
    AND archived = :archived
    AND deletedAt = 0
"""

@Dao
interface RecordDao {
    @Transaction
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordWithAttachments>>

    @Transaction
    @Query(FILTER_WHERE + " ORDER BY createdAt DESC")
    fun observeFiltered(query: String, filter: String, archived: Int): Flow<List<RecordWithAttachments>>

    @Transaction
    @Query(
        FILTER_WHERE +
            " AND (:startTime = 0 OR createdAt >= :startTime)" +
            " AND (:endTime = 0 OR createdAt < :endTime)" +
            " ORDER BY createdAt DESC",
    )
    fun observeFilteredByDate(
        query: String,
        filter: String,
        archived: Int,
        startTime: Long,
        endTime: Long,
    ): Flow<List<RecordWithAttachments>>

    @Transaction
    @Query(
        FILTER_WHERE +
            " AND (:startTime = 0 OR createdAt >= :startTime)" +
            " AND (:endTime = 0 OR createdAt < :endTime)" +
            " ORDER BY createdAt DESC, id DESC",
    )
    fun loadFilteredPagedSource(
        query: String,
        filter: String,
        archived: Int,
        startTime: Long,
        endTime: Long,
    ): androidx.paging.PagingSource<Int, RecordWithAttachments>

    @Transaction
    @Query("SELECT * FROM records WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<RecordWithAttachments>>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id")
    fun observeRecord(id: Long): Flow<RecordWithAttachments?>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecord(id: Long): RecordWithAttachments?

    @Transaction
    @Query("SELECT * FROM records ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<RecordWithAttachments>

    @Transaction
    @Query("SELECT * FROM records WHERE archived = 0 AND deletedAt = 0 ORDER BY createdAt ASC")
    suspend fun getActiveOnce(): List<RecordWithAttachments>

    @Query(
        """
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN noStairs = 'YES' THEN 1 ELSE 0 END), 0) AS noStairs,
            COALESCE(SUM(CASE WHEN willComeDown = 'YES' THEN 1 ELSE 0 END), 0) AS willComeDown
        FROM records
        WHERE createdAt >= :since AND archived = 0 AND deletedAt = 0
        """,
    )
    fun observeStats(since: Long): Flow<RecordStats>

    @Query(
        """
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN noStairs = 'YES' THEN 1 ELSE 0 END), 0) AS noStairs,
            COALESCE(SUM(CASE WHEN noStairs = 'NO' THEN 1 ELSE 0 END), 0) AS stairs,
            COALESCE(SUM(CASE WHEN noStairs = 'UNKNOWN' THEN 1 ELSE 0 END), 0) AS unknown,
            COALESCE(SUM(CASE WHEN willComeDown = 'YES' THEN 1 ELSE 0 END), 0) AS willComeDown,
            COALESCE(SUM(CASE WHEN willComeDown = 'NO' THEN 1 ELSE 0 END), 0) AS wontComeDown
        FROM records
        WHERE archived = 0 AND deletedAt = 0
        """,
    )
    fun observeDecisionStats(): Flow<DecisionStats>

    @Query(
        """
        SELECT location,
            COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN noStairs = 'YES' THEN 1 ELSE 0 END), 0) AS noStairs,
            COALESCE(SUM(CASE WHEN willComeDown = 'YES' THEN 1 ELSE 0 END), 0) AS willComeDown
        FROM records
        WHERE archived = 0 AND deletedAt = 0 AND location != ''
        GROUP BY location
        ORDER BY total DESC, location ASC
        LIMIT 20
        """,
    )
    fun observeLocationStats(): Flow<List<LocationStat>>

    @Query(
        """
        SELECT location FROM records
        WHERE location != '' AND deletedAt = 0
        GROUP BY location
        ORDER BY MAX(updatedAt) DESC
        LIMIT 12
        """,
    )
    fun observeRecentLocations(): Flow<List<String>>

    @Query(
        """
        SELECT COUNT(*) FROM records
        WHERE location = :location AND orderNo = :orderNo AND createdAt = :createdAt
        """,
    )
    suspend fun countBySignature(location: String, orderNo: String, createdAt: Long): Int

    @Insert
    suspend fun insert(record: RecordEntity): Long

    @Update
    suspend fun update(record: RecordEntity)

    @Query("UPDATE records SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE records SET archived = :archived WHERE id IN (:ids)")
    suspend fun setArchivedForIds(ids: List<Long>, archived: Boolean)

    @Query("UPDATE records SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long)

    @Query("UPDATE records SET deletedAt = :deletedAt WHERE id IN (:ids)")
    suspend fun setDeletedAtForIds(ids: List<Long>, deletedAt: Long)

    @Transaction
    @Query("SELECT * FROM records WHERE deletedAt > 0 AND deletedAt <= :cutoff")
    suspend fun getTrashedBefore(cutoff: Long): List<RecordWithAttachments>

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM records")
    suspend fun count(): Int
}
