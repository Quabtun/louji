package com.quabtun.louji.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.quabtun.louji.model.RecordStats
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Transaction
    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordWithAttachments>>

    @Transaction
    @Query(
        """
        SELECT * FROM records
        WHERE (
            :query = ''
            OR location LIKE '%' || :query || '%'
            OR orderNo LIKE '%' || :query || '%'
            OR phoneTail LIKE '%' || :query || '%'
            OR floor LIKE '%' || :query || '%'
            OR roomNo LIKE '%' || :query || '%'
            OR remark LIKE '%' || :query || '%'
            OR personalityNote LIKE '%' || :query || '%'
            OR personalityTags LIKE '%' || :query || '%'
        )
        AND (
            :filter = 'ALL'
            OR (:filter = 'STAIRS' AND noStairs = 'NO')
            OR (:filter = 'NO_STAIRS' AND noStairs = 'YES')
            OR (:filter = 'WILL_COME_DOWN' AND willComeDown = 'YES')
            OR (:filter = 'WITH_MEDIA' AND EXISTS(
                SELECT 1 FROM attachments WHERE attachments.recordId = records.id
            ))
        )
        ORDER BY createdAt DESC
        """,
    )
    fun observeFiltered(query: String, filter: String): Flow<List<RecordWithAttachments>>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id")
    fun observeRecord(id: Long): Flow<RecordWithAttachments?>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecord(id: Long): RecordWithAttachments?

    @Transaction
    @Query("SELECT * FROM records ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<RecordWithAttachments>

    @Query(
        """
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN noStairs = 'YES' THEN 1 ELSE 0 END), 0) AS noStairs,
            COALESCE(SUM(CASE WHEN willComeDown = 'YES' THEN 1 ELSE 0 END), 0) AS willComeDown
        FROM records
        WHERE createdAt >= :since
        """,
    )
    fun observeStats(since: Long): Flow<RecordStats>

    @Query(
        """
        SELECT location FROM records
        WHERE location != ''
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

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM records")
    suspend fun count(): Int
}
