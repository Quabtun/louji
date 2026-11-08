package com.quabtun.louji.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecordEntity::class, AttachmentEntity::class, BuildingProfileEntity::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LouJiDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun buildingDao(): BuildingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN remark TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN climbNote TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN phoneExt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE records ADD COLUMN accessCode TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE records ADD COLUMN elevatorType TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
                db.execSQL(
                    "ALTER TABLE records ADD COLUMN deliveryMethod TEXT NOT NULL DEFAULT 'UNKNOWN'",
                )
                db.execSQL("ALTER TABLE records ADD COLUMN isWarning INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `building_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `location` TEXT NOT NULL,
                        `elevatorType` TEXT NOT NULL,
                        `accessCode` TEXT NOT NULL,
                        `doorIntact` TEXT NOT NULL,
                        `canOpenFromAbove` TEXT NOT NULL,
                        `lockerInfo` TEXT NOT NULL,
                        `accessTags` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `coverPhotoPath` TEXT,
                        `pinned` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_building_profiles_location` " +
                        "ON `building_profiles` (`location`)",
                )

                // Seed the building library from existing history so upgraded riders get it free.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO building_profiles (
                        location, elevatorType, accessCode, doorIntact, canOpenFromAbove,
                        lockerInfo, accessTags, notes, coverPhotoPath, pinned, createdAt, updatedAt
                    )
                    SELECT TRIM(location), 'UNKNOWN', '', 'UNKNOWN', 'UNKNOWN',
                        '', '[]', '', NULL, 0, MIN(createdAt), MAX(updatedAt)
                    FROM records
                    WHERE deletedAt = 0 AND TRIM(location) != ''
                    GROUP BY TRIM(location)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE building_profiles SET
                        elevatorType = COALESCE((
                            SELECT r.elevatorType FROM records r
                            WHERE TRIM(r.location) = building_profiles.location
                              AND r.deletedAt = 0 AND r.elevatorType != 'UNKNOWN'
                            ORDER BY r.updatedAt DESC LIMIT 1
                        ), elevatorType),
                        accessCode = COALESCE((
                            SELECT r.accessCode FROM records r
                            WHERE TRIM(r.location) = building_profiles.location
                              AND r.deletedAt = 0 AND r.accessCode != ''
                            ORDER BY r.updatedAt DESC LIMIT 1
                        ), accessCode),
                        doorIntact = COALESCE((
                            SELECT r.doorIntact FROM records r
                            WHERE TRIM(r.location) = building_profiles.location
                              AND r.deletedAt = 0 AND r.doorIntact != 'UNKNOWN'
                            ORDER BY r.updatedAt DESC LIMIT 1
                        ), doorIntact),
                        canOpenFromAbove = COALESCE((
                            SELECT r.canOpenFromAbove FROM records r
                            WHERE TRIM(r.location) = building_profiles.location
                              AND r.deletedAt = 0 AND r.canOpenFromAbove != 'UNKNOWN'
                            ORDER BY r.updatedAt DESC LIMIT 1
                        ), canOpenFromAbove)
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): LouJiDatabase =
            Room.databaseBuilder(context, LouJiDatabase::class.java, "louji.db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                .build()
    }
}
