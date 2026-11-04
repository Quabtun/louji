package com.quabtun.louji.model

/** Pure scheduling logic for the local weekly backup reminder (no cloud, no network). */
object BackupReminder {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    fun shouldRemind(lastBackupAt: Long, now: Long, enabled: Boolean): Boolean =
        enabled && now - lastBackupAt >= WEEK_MS
}
