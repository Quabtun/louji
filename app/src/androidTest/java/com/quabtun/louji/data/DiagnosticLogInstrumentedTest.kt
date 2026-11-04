package com.quabtun.louji.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticLogInstrumentedTest {
    @Test
    fun logRoundTripAndCrashCapture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "diag-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val log = DiagnosticLog(context, dir)
            log.log("app_start", "version=1.0.0")
            log.log("backup_export", "records=2\nmedia=3")
            log.logCrash(RuntimeException("boom"))

            val text = log.read()
            assertTrue(text.contains("[app_start] version=1.0.0"))
            assertTrue(text.contains("records=2 media=3"))
            assertTrue(text.contains("[CRASH] java.lang.RuntimeException: boom"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
