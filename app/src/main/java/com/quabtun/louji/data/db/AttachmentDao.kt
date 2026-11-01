package com.quabtun.louji.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insertAll(items: List<AttachmentEntity>): List<Long>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: Long): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE recordId = :recordId ORDER BY createdAt ASC")
    suspend fun getForRecord(recordId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM attachments WHERE recordId = :recordId")
    suspend fun deleteForRecord(recordId: Long)

    @Query("SELECT * FROM attachments")
    suspend fun getAllOnce(): List<AttachmentEntity>

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}
