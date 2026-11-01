package com.quabtun.louji.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RecordEntity::class, AttachmentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LouJiDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        fun build(context: Context): LouJiDatabase =
            Room.databaseBuilder(context, LouJiDatabase::class.java, "louji.db")
                .build()
    }
}
