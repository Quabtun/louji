package com.quabtun.louji.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.quabtun.louji.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubUpdater(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the newest release when it is newer than [currentVersion], null otherwise.
     */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LouJi/$currentVersion")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub 返回 ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val release = json.decodeFromString<ReleaseDto>(body)
            if (release.tagName.isBlank() || !AppVersion.isNewer(release.tagName, currentVersion)) {
                return@withContext null
            }
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("最新版本没有 APK 文件")
            val checksum = release.assets.firstOrNull {
                it.name.endsWith(".sha256", ignoreCase = true)
            }
            verifyGitHubUrl(apk.downloadUrl)
            checksum?.let { verifyGitHubUrl(it.downloadUrl) }
            UpdateInfo(
                versionName = release.tagName.removePrefix("v").removePrefix("V"),
                notes = release.body.trim(),
                apkUrl = apk.downloadUrl,
                apkSize = apk.size,
                sha256Url = checksum?.downloadUrl,
            )
        }
    }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        if (info.apkSize > MAX_APK_BYTES) {
            throw IOException("安装包过大，已停止下载")
        }
        val version = safeVersionName(info.versionName)
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "louji-$version.apk")
        val partial = File(directory, "louji-$version.apk.part")
        partial.delete()
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "LouJi/${BuildConfig.VERSION_NAME}")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                verifyGitHubUrl(response.request.url.toString())
                if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
                val body = response.body ?: throw IOException("下载失败：没有内容")
                val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
                if (total > MAX_APK_BYTES) {
                    throw IOException("安装包过大，已停止下载")
                }
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        var lastReported = 0
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            written += read
                            if (written > MAX_APK_BYTES) {
                                throw IOException("安装包过大，已停止下载")
                            }
                            output.write(buffer, 0, read)
                            if (total > 0) {
                                val percent = ((written * 100) / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
                if (info.apkSize > 0 && partial.length() != info.apkSize) {
                    throw IOException("下载不完整，请重试")
                }
                val expected = info.sha256Url?.let {
                    fetchChecksum(it) ?: throw IOException("校验文件不可用，请重试")
                }
                if (expected != null && !sha256(partial).equals(expected, ignoreCase = true)) {
                    throw IOException("安装包校验失败，请重试")
                }
                if (target.exists() && !target.delete()) {
                    throw IOException("无法替换旧安装包")
                }
                if (!partial.renameTo(target)) {
                    throw IOException("下载文件无法保存")
                }
                if (!target.isFile || target.length() == 0L) {
                    throw IOException("下载文件无法保存")
                }
                onProgress(1f)
                target
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun install(apk: File) {
        if (!canInstallPackages()) {
            throw SecurityException("需要允许楼记安装未知应用")
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun fetchChecksum(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LouJi/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            verifyGitHubUrl(response.request.url.toString())
            if (!response.isSuccessful) return null
            val text = response.body?.string().orEmpty().trim()
            return text.split(Regex("\\s+")).firstOrNull()
                ?.takeIf { it.length == 64 && it.all { char -> char.isDigit() || char.lowercaseChar() in 'a'..'f' } }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeVersionName(raw: String): String {
        val safe = raw.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        if (safe.isBlank()) throw IOException("版本号无效")
        return safe.take(64)
    }

    private fun verifyGitHubUrl(url: String) {
        val parsed = Uri.parse(url)
        val host = parsed.host?.lowercase()
        val trustedHosts = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )
        if (parsed.scheme != "https" || host !in trustedHosts) {
            throw IOException("更新地址不受信任")
        }
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String = "",
        val body: String = "",
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0,
    )

    private companion object {
        const val MAX_APK_BYTES = 250L * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
