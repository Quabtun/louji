package com.quabtun.louji

import android.content.Context
import com.quabtun.louji.data.BackupManager
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.SettingsStore
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.update.GitHubUpdater

class AppContainer(context: Context) {
    val database: LouJiDatabase = LouJiDatabase.build(context)
    val mediaStore = LocalMediaStore(context)
    val repository = RecordRepository(database, mediaStore)
    val settings = SettingsStore(context)
    val backup = BackupManager(context, repository, mediaStore)
    val updater = GitHubUpdater(context)
}
