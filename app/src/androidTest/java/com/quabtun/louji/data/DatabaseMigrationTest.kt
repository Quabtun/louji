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

    private companion object {
        const val TEST_DB = "louji-migration-test.db"
    }
}
