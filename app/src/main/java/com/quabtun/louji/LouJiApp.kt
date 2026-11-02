package com.quabtun.louji

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LouJiApp : Application() {
    lateinit var container: AppContainer
        private set

    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        maintenanceScope.launch {
            runCatching {
                container.mediaStore.sweepOrphans(
                    keepPaths = container.repository.referencedMediaPaths(),
                    minAgeMs = 5 * 60 * 1000L,
                )
            }
        }
    }
}
