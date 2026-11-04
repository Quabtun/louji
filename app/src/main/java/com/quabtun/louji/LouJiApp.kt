package com.quabtun.louji

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
        container.diagnostics.log("app_start", "version=${com.quabtun.louji.BuildConfig.VERSION_NAME}")
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { container.diagnostics.logCrash(throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
        maintenanceScope.launch {
            runCatching {
                container.mediaStore.sweepOrphans(
                    keepPaths = container.repository.referencedMediaPaths(),
                    minAgeMs = 5 * 60 * 1000L,
                )
            }
            runCatching {
                container.repository.purgeTrashed(7 * 24 * 60 * 60 * 1000L)
            }.onFailure {
                container.diagnostics.log("purge_trashed", "failed: ${it.message.orEmpty()}")
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                container.lock.lockIfEnabled()
            }
        })
    }
}
