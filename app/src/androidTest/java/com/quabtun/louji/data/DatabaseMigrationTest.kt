package com.quabtun.louji.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quabtun.louji.data.db.LouJiDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LouJiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3KeepsRecordAndAddsEmptyClimbNote() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO records (
                    location, orderNo, gender, phoneTail, floor, roomNo,
                    doorIntact, canOpenFromAbove, noStairs, willComeDown,
                    personalityTags, personalityNote, remark, createdAt, updatedAt
                ) VALUES (
                    '阳光花园', 'A1', 'FEMALE', '4821', '7', '301',
                    'YES', 'NO', 'YES', 'YES',
                    '[]', '', '门禁在左手边', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            LouJiDatabase.MIGRATION_2_3,
        )
        database.query("SELECT location, remark, climbNote FROM records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("阳光花园", cursor.getString(0))
            assertEquals("门禁在左手边", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }
    }

    @Test
    fun migrate3To4KeepsRecordAndDefaultsNotArchived() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO records (
                    location, orderNo, gender, phoneTail, floor, roomNo,
                    doorIntact, canOpenFromAbove, noStairs, willComeDown,
                    personalityTags, personalityNote, remark, climbNote, createdAt, updatedAt
                ) VALUES (
                    '江景苑', 'A1', 'FEMALE', '4821', '21', '2103',
                    'YES', 'NO', 'NO', 'YES',
                    '[]', '', '门禁在左手边', '客户要送上门', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            LouJiDatabase.MIGRATION_3_4,
        )
        database.query("SELECT location, climbNote, archived FROM records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("江景苑", cursor.getString(0))
            assertEquals("客户要送上门", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
        }
    }

    @Test
    fun migrate4To5KeepsRecordAndDefaultsNotDeleted() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                """
                INSERT INTO records (
                    location, orderNo, gender, phoneTail, floor, roomNo,
                    doorIntact, canOpenFromAbove, noStairs, willComeDown,
                    personalityTags, personalityNote, remark, climbNote, archived, createdAt, updatedAt
                ) VALUES (
                    '云顶小区', 'A1', 'FEMALE', '4821', '30', '3001',
                    'YES', 'NO', 'NO', 'YES',
                    '[]', '', '门禁在左手边', '客户要送上门', 1, 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            LouJiDatabase.MIGRATION_4_5,
        )
        database.query("SELECT location, archived, deletedAt FROM records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("云顶小区", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
    }

    @Test
    fun migrate5To6AddsColumnsAndSeedsBuildingProfiles() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO records (
                    location, orderNo, gender, phoneTail, floor, roomNo,
                    doorIntact, canOpenFromAbove, noStairs, willComeDown,
                    personalityTags, personalityNote, remark, climbNote, archived, deletedAt,
                    createdAt, updatedAt
                ) VALUES (
                    '江景苑', 'A1', 'FEMALE', '4821', '21', '2103',
                    'YES', 'NO', 'NO', 'YES',
                    '[]', '', '门禁在左手边', '客户要送上门', 0, 0, 1, 2
                )
                """.trimIndent(),
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            LouJiDatabase.MIGRATION_5_6,
        )
        database.query(
            "SELECT phoneExt, accessCode, elevatorType, deliveryMethod, isWarning " +
                "FROM records",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("UNKNOWN", cursor.getString(2))
            assertEquals("UNKNOWN", cursor.getString(3))
            assertEquals(0, cursor.getInt(4))
        }
        database.query(
            "SELECT location, doorIntact, canOpenFromAbove, accessCode " +
                "FROM building_profiles",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("江景苑", cursor.getString(0))
            assertEquals("YES", cursor.getString(1))
            assertEquals("NO", cursor.getString(2))
            assertEquals("", cursor.getString(3))
        }
    }

    private companion object {
        const val TEST_DB = "louji-migration-test.db"
    }
}
