package com.quabtun.louji.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    @Test
    fun remindsWhenWeekElapsedOrNeverBackedUp() {
        val now = 1_700_000_000_000L
        assertTrue(BackupReminder.shouldRemind(now - BackupReminder.WEEK_MS, now, enabled = true))
        assertTrue(BackupReminder.shouldRemind(0L, now, enabled = true))
    }

    @Test
    fun doesNotRemindWhenRecentOrDisabled() {
        val now = 1_700_000_000_000L
        assertFalse(BackupReminder.shouldRemind(now, now, enabled = true))
        assertFalse(BackupReminder.shouldRemind(now - BackupReminder.WEEK_MS + 1, now, enabled = true))
        assertFalse(BackupReminder.shouldRemind(0L, now, enabled = false))
    }
}
