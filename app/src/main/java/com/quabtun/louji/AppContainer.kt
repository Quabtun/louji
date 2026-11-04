package com.quabtun.louji

import android.content.Context
import com.quabtun.louji.data.AppLockController
import com.quabtun.louji.data.BackupManager
import com.quabtun.louji.data.DeleteUndoController
import com.quabtun.louji.data.DiagnosticLog
import com.quabtun.louji.data.LocalMediaStore
import com.quabtun.louji.data.RecordRepository
import com.quabtun.louji.data.SettingsStore
import com.quabtun.louji.data.db.LouJiDatabase
import com.quabtun.louji.update.GitHubUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: LouJiDatabase = LouJiDatabase.build(context)
    val mediaStore = LocalMediaStore(context)
    val repository = RecordRepository(database, mediaStore)
    val settings = SettingsStore(context)
    val backup = BackupManager(context, repository, mediaStore)
    val updater = GitHubUpdater(context)
    val lock = AppLockController(settings.settings, applicationScope)
    val deleteUndo = DeleteUndoController()
    val diagnostics = DiagnosticLog(context)
}
