package com.quabtun.louji.data

import android.content.Context
import android.net.Uri
import android.os.Build
import com.quabtun.louji.BuildConfig
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Strips control characters that could forge log lines. Never logs customer fields. */
fun sanitizeLogMessage(message: String): String =
    message.replace('\r', ' ').replace('\n', ' ')

/**
 * Local, sanitized diagnostic log. Entries are written to an app-private file (no network, no
 * customer data — only event names, counts and exception stack traces) and can be exported by
 * the user from Settings.
 */
class DiagnosticLog(
    private val context: Context,
    directoryOverride: File? = null,
) {
    private val directory: File =
        (directoryOverride ?: File(context.filesDir, "diagnostics")).apply { mkdirs() }
    private val file: File = File(directory, "diagnostics.log")

    @Synchronized
    fun log(tag: String, message: String) {
        append("${timestamp()} [$tag] ${sanitizeLogMessage(message)}")
    }

    @Synchronized
    fun logCrash(throwable: Throwable) {
        val sb = StringBuilder()
        sb.append("${timestamp()} [CRASH] ${throwable.javaClass.name}: ${throwable.message.orEmpty()}")
        throwable.stackTrace.take(MAX_STACK_LINES).forEach { sb.append("\n  at $it") }
        append(sb.toString())
    }

    @Synchronized
    fun read(): String =
        if (!file.isFile) "" else runCatching { file.readText() }.getOrDefault("")

    @Synchronized
    fun buildReport(): String = buildString {
        appendLine("楼记诊断日志")
        appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("生成时间: ${timestamp()}")
        appendLine("----")
        append(read())
    }

    fun exportTo(uri: Uri): Boolean {
        val output = runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { path ->
                    File(path).parentFile?.mkdirs()
                    File(path).outputStream()
                }
            } else {
                context.contentResolver.openOutputStream(uri, "wt")
            }
        }.getOrNull() ?: return false
        return runCatching {
            output.use { it.write(buildReport().toByteArray(Charsets.UTF_8)) }
            true
        }.getOrDefault(false)
    }

    private fun append(line: String) {
        runCatching {
            file.appendText(line + "\n")
            trimIfNeeded()
        }
    }

    private fun trimIfNeeded() {
        if (file.length() <= MAX_BYTES) return
        runCatching {
            val content = file.readText()
            val keepFrom = (content.length - (MAX_BYTES / 2).toInt()).coerceAtLeast(0)
            file.writeText(content.substring(keepFrom))
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private companion object {
        const val MAX_BYTES = 256L * 1024
        const val MAX_STACK_LINES = 40
    }
}
