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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * State of a cached update, derived from `cacheDir/updates/` without touching the network.
 */
sealed class CachedUpdate {
    /** Nothing usable on disk. */
    data object None : CachedUpdate()

    /** A complete, checksum-verified APK for a newer version — installable offline. */
    data class Ready(val info: UpdateInfo, val file: File) : CachedUpdate()

    /** A partial download of a newer version, resumable from [downloadedBytes]. */
    data class Partial(val info: UpdateInfo, val file: File, val downloadedBytes: Long) : CachedUpdate()
}

/**
 * Metadata stored next to the APK so a restart can resume or install without network.
 */
@Serializable
private data class CachedUpdateMeta(
    val versionName: String = "",
    val notes: String = "",
    val apkUrl: String = "",
    val apkSize: Long = 0,
    val sha256: String = "",
    val sha256Url: String = "",
    val eTag: String = "",
    val fileName: String = "",
    val downloadedAt: Long = 0,
)

class GitHubUpdater(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
    /** Optional acceleration hosts, appended after the official hosts. Order = preference. */
    private val mirrorHosts: List<String> = emptyList(),
    /** Test-only escape hatch: adds hosts to the trust list without shipping a mirror. */
    private val trustedHostOverrides: Set<String> = emptySet(),
    /** Test-only escape hatch: replaces the release API endpoint (MockWebServer). */
    private val apiUrlOverride: String? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val updatesDir: File get() = File(context.cacheDir, "updates").apply { mkdirs() }
    private val metaFile: File get() = File(updatesDir, META_FILE)

    /**
     * Returns the newest release when it is newer than [currentVersion], null otherwise.
     * Asset selection prefers a build matching the device ABI, falling back to universal.
     */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrlOverride ?: BuildConfig.UPDATE_API)
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
            val info = selectUpdate(release)
                ?: throw IOException("最新版本没有 APK 文件")
            val checksum = release.assets.firstOrNull {
                it.name.endsWith(".sha256", ignoreCase = true)
            }
            verifyTrustedUrl(info.apkUrl)
            checksum?.let { verifyTrustedUrl(it.downloadUrl) }
            info
        }
    }

    private fun selectUpdate(release: ReleaseDto): UpdateInfo? {
        val selectedName = ApkAbiMatcher.select(
            release.assets.map { ApkAbiMatcher.AssetName(it.name) },
        ) ?: return null
        val apk = release.assets.first { it.name == selectedName }
        val info = UpdateInfo(
            versionName = release.tagName.removePrefix("v").removePrefix("V"),
            notes = release.body.trim(),
            apkUrl = apk.downloadUrl,
            apkSize = apk.size,
            sha256Url = checksumUrlFor(release.assets, apk.name),
            apkName = apk.name,
        )
        verifyTrustedUrl(info.apkUrl)
        info.sha256Url?.let { verifyTrustedUrl(it) }
        return info
    }

    /**
     * Prefers `"<apk.name>.sha256"` (per-ABI checksums); falls back to a combined checksum file
     * whose text is matched line-by-line against the apk file name. Never blindly trusts the
     * first `.sha256` asset when several builds are published.
     */
    private fun checksumUrlFor(assets: List<AssetDto>, apkName: String): String? {
        val checksumAssets = assets.filter { it.name.endsWith(".sha256", ignoreCase = true) }
        if (checksumAssets.isEmpty()) return null
        checksumAssets.firstOrNull { it.name.equals("$apkName.sha256", ignoreCase = true) }
            ?.let { return it.downloadUrl }
        // A combined file is only usable when it mentions our exact apk name; otherwise the
        // checksum would be ambiguous across per-ABI builds.
        return checksumAssets
            .firstOrNull { !ApkAbiMatcher.isAbiSpecific(it.name.removeSuffix(".sha256")) }
            ?.downloadUrl
    }

    // ------------------------------------------------------------- cache inspection

    private fun readMeta(): CachedUpdateMeta? = runCatching {
        if (!metaFile.isFile) return null
        json.decodeFromString<CachedUpdateMeta>(metaFile.readText())
    }.getOrNull()

    private fun writeMeta(meta: CachedUpdateMeta) {
        runCatching {
            metaFile.writeText(json.encodeToString(CachedUpdateMeta.serializer(), meta))
        }
    }

    fun cachedInfoOrNull(): UpdateInfo? = readMeta()?.let { meta ->
        UpdateInfo(
            versionName = meta.versionName,
            notes = meta.notes,
            apkUrl = meta.apkUrl,
            apkSize = meta.apkSize,
            // The checksum URL survives restarts so a resumed download can still verify.
            sha256Url = meta.sha256Url.takeIf { it.isNotBlank() },
            sha256 = meta.sha256.takeIf { it.length == 64 },
            eTag = meta.eTag.takeIf { it.isNotBlank() },
        )
    }

    private fun saveMeta(info: UpdateInfo, fileName: String) {
        writeMeta(
            CachedUpdateMeta(
                versionName = info.versionName,
                notes = info.notes.take(4000),
                apkUrl = info.apkUrl,
                apkSize = info.apkSize,
                sha256 = info.sha256.orEmpty(),
                sha256Url = info.sha256Url.orEmpty(),
                eTag = info.eTag.orEmpty(),
                fileName = fileName,
                downloadedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Inspects the update cache without any network access. Expired files (current or older
     * versions) are deleted; a verified full APK of a newer version becomes [CachedUpdate.Ready]
     * and a partial one becomes [CachedUpdate.Partial] so the UI can offer "继续下载".
     */
    fun inspectCachedUpdate(currentVersion: String): CachedUpdate {
        val meta = readMeta()
        if (meta == null) {
            sweepUnknownFiles()
            return CachedUpdate.None
        }
        val version = meta.versionName
        if (version.isBlank() || !AppVersion.isNewer(version, currentVersion)) {
            clearCache()
            return CachedUpdate.None
        }
        val info = cachedInfoOrNull() ?: run {
            clearCache()
            return CachedUpdate.None
        }
        val expectedFile = File(
            updatesDir,
            meta.fileName.takeIf { it.isNotBlank() } ?: "louji-${safeVersionName(version)}.apk",
        )
        val partial = File(updatesDir, expectedFile.name + PART_SUFFIX)

        val fullValid = expectedFile.isFile && expectedFile.length() > 0 &&
            (info.apkSize <= 0 || expectedFile.length() == info.apkSize) &&
            (info.sha256 == null || sha256(expectedFile).equals(info.sha256, true))
        if (fullValid) {
            return CachedUpdate.Ready(info, expectedFile)
        }
        // A full file that fails verification is worthless; keep only a resumable partial.
        if (expectedFile.isFile) expectedFile.delete()

        if (partial.isFile && partial.length() > 0 &&
            (info.apkSize <= 0 || partial.length() < info.apkSize)
        ) {
            return CachedUpdate.Partial(info, partial, partial.length())
        }
        if (partial.isFile && partial.length() <= 0) partial.delete()
        return CachedUpdate.None
    }

    /** Deletes every apk/part in the cache that is not covered by current metadata. */
    private fun sweepUnknownFiles() {
        updatesDir.listFiles()?.forEach { file ->
            if (file.name != META_FILE &&
                (file.name.endsWith(".apk", true) || file.name.endsWith(PART_SUFFIX, true))
            ) {
                file.delete()
            }
        }
    }

    /** Same sweep, but keeps an explicit set of live names (used mid-download). */
    private fun sweepForeignFiles(keepNames: Set<String>) {
        updatesDir.listFiles()?.forEach { file ->
            if (file.name !in keepNames &&
                (file.name.endsWith(".apk", true) || file.name.endsWith(PART_SUFFIX, true))
            ) {
                file.delete()
            }
        }
    }

    fun clearCache() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    /** Cache-layout accessors used by instrumentation to seed resume state. */
    fun metaPath(): File = metaFile

    fun partialPath(versionName: String): File =
        File(updatesDir, "louji-${safeVersionName(versionName)}.apk$PART_SUFFIX")

    fun fullPath(versionName: String): File =
        File(updatesDir, "louji-${safeVersionName(versionName)}.apk")

    // ------------------------------------------------------------------- download

    /**
     * Downloads (or resumes) the APK into the cache. Cancellation and IO failures keep the
     * `.part` file so the next attempt can continue; only a checksum mismatch, a size overrun
     * or an explicit reset deletes it.
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        forceRestart: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        if (info.apkSize > MAX_APK_BYTES) {
            throw IOException("安装包过大，已停止下载")
        }
        val version = safeVersionName(info.versionName)
        val target = File(updatesDir, "louji-$version.apk")
        val partial = File(updatesDir, "louji-$version.apk.part")

        // Checksum first: reuse target must not skip verification when only the URL is known.
        val resolved = resolveChecksum(info)
        // Security baseline: when the release PUBLISHES a .sha256 asset, the bytes may only be
        // installed against that hash. A failed fetch (offline/hiccup) is a refusal, never a
        // silent size-only pass.
        if (resolved.sha256 == null && info.sha256Url != null) {
            throw IOException("校验文件不可用，已停止下载；请联网后重试")
        }
        val verifiedInfo = info.copy(sha256 = resolved.sha256)

        // Already-complete file wins: never download the same version twice.
        if (!forceRestart && target.isFile && isVerified(target, verifiedInfo)) {
            saveMeta(verifiedInfo, target.name)
            onProgress(target.length(), info.apkSize)
            return@withContext target
        }

        val resumeFrom = if (forceRestart) {
            partial.delete()
            0L
        } else {
            partial.takeIf { it.isFile && it.length() > 0 }?.length() ?: 0L
        }
        var offset = resumeFrom
        if (info.apkSize > 0 && offset > info.apkSize) {
            // Partial grew past the expected size: the bytes are junk, restart at zero and
            // make sure the request does NOT carry the stale offset.
            partial.delete()
            offset = 0L
        }

        // Persist metadata BEFORE any bytes flow, so a killed process still leaves enough on
        // disk for `inspectCachedUpdate` to see the partial and offer "继续下载".
        saveMeta(verifiedInfo, target.name)
        // A previous version's partial/apk files are garbage once the meta points here.
        sweepForeignFiles(keepNames = setOf(target.name, partial.name, META_FILE))

        var written = offset
        // Captured from the live response; the final saveMeta must keep it, otherwise the
        // next resume would lose its If-Range anchor.
        var capturedTag: String? = null
        try {
            val response = openDownload(verifiedInfo, offset, resolved.eTag)
            var append = false
            response.use { resp ->
                val code = resp.code
                // Capture the CDN validators now: they are what makes a later resume possible.
                val remoteTag = resp.header("ETag") ?: resp.header("Last-Modified")
                capturedTag = remoteTag
                when {
                    code == 206 -> {
                        val start = contentRangeStart(resp.header("Content-Range"))
                        if (start != offset) {
                            // Server resumed from somewhere else: restart from zero safely.
                            partial.delete()
                            written = 0L
                        } else {
                            append = true
                        }
                    }
                    code == 200 -> written = 0L // Overwrite from scratch.
                    code == 416 -> {
                        val already = partial.length()
                        if (info.apkSize > 0 && already >= info.apkSize) {
                            // The partial is actually complete; fall through to verification.
                        } else {
                            partial.delete()
                            throw IOException("断点信息已过期，请重新下载")
                        }
                    }
                    !resp.isSuccessful -> throw IOException("下载失败：HTTP $code")
                }
                // A complete 416 partial skips the body entirely.
                val body = resp.body ?: throw IOException("下载失败：没有内容")

                val total = when {
                    code == 206 -> info.apkSize.takeIf { it > 0 }
                        ?: (offset + body.contentLength())
                    else -> body.contentLength().takeIf { it > 0 } ?: info.apkSize
                }
                if (total > MAX_APK_BYTES) throw IOException("安装包过大，已停止下载")

                // Refresh the validators as soon as the server answers; the meta file then
                // carries a real ETag for the next resume attempt.
                saveMeta(verifiedInfo.copy(eTag = remoteTag), target.name)

                body.byteStream().use { input ->
                    // Append for a matching 206 resume, overwrite for anything else.
                    java.io.FileOutputStream(partial, append).use { raw ->
                        val output = java.io.BufferedOutputStream(raw, BUFFER_BYTES)
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            written += read
                            if (total > 0 && written > total) {
                                throw IOException("文件超出预期大小，已停止下载")
                            }
                            output.write(buffer, 0, read)
                            if (total > 0) {
                                onProgress(written, total)
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (info.apkSize > 0 && partial.length() != info.apkSize) {
                throw IOException("下载不完整，请重试")
            }
            if (resolved.sha256 != null && !sha256(partial).equals(resolved.sha256, true)) {
                // Checksum mismatch: the bytes cannot be trusted, drop the partial.
                partial.delete()
                throw IOException("安装包校验失败，请重新下载")
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
            saveMeta(verifiedInfo.copy(eTag = capturedTag ?: verifiedInfo.eTag), target.name)
            onProgress(target.length(), info.apkSize)
            target
        } catch (error: Throwable) {
            // Keep the .part for cancellation / IO failures so the download can resume.
            if (error is IOException && error.message?.contains("校验失败") == true) {
                partial.delete()
            }
            throw error
        }
    }

    private class Resolved(val sha256: String?, val eTag: String?)

    /** Uses the cached checksum when present; otherwise fetches and remembers it. */
    private suspend fun resolveChecksum(info: UpdateInfo): Resolved = withContext(Dispatchers.IO) {
        val cached = info.sha256?.takeIf { it.length == 64 }
        if (cached != null) return@withContext Resolved(cached, info.eTag)
        val url = info.sha256Url ?: return@withContext Resolved(null, info.eTag)
        Resolved(fetchChecksum(url, info.apkName), info.eTag)
    }

    /**
     * Opens the APK stream, trying the official URL first and then any whitelisted mirror host.
     * Every attempt is still pinned to https + explicit host whitelist, and the final bytes are
     * verified against the official `.sha256` before install.
     */
    private fun openDownload(info: UpdateInfo, resumeFrom: Long, eTag: String?): Response {
        if (!isTrustedHost(info.apkUrl)) throw IOException("更新地址不受信任")
        val candidates = ArrayList<String>()
        candidates.add(info.apkUrl)
        mirrorHosts
            .filter { it.isNotBlank() && it.lowercase() in ALLOWED_MIRROR_HOSTS }
            .forEach { mirror ->
                withHost(info.apkUrl, mirror)?.let { candidate ->
                    if (isTrustedHost(candidate) && !candidates.contains(candidate)) {
                        candidates.add(candidate)
                    }
                }
            }
        var lastError: IOException? = null
        candidates.forEach { candidate ->
            try {
                val request = Request.Builder()
                    .url(candidate)
                    .header("User-Agent", "LouJi/${BuildConfig.VERSION_NAME}")
                    .apply {
                        // Resume only when we can anchor the partial to the remote file (ETag).
                        // Without it a 206 could append bytes of a different release.
                        if (resumeFrom > 0 && !eTag.isNullOrBlank()) {
                            header("Range", "bytes=$resumeFrom-")
                            header("If-Range", eTag)
                        }
                    }
                    .build()
                val response = client.newCall(request).execute()
                verifyTrustedUrl(response.request.url.toString())
                return response
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("下载失败：无法连接")
    }

    private fun withHost(url: String, host: String): String? {
        val parsed = Uri.parse(url)
        if (host.isBlank() || parsed.host.equals(host, true)) return url
        return url.toHttpUrlOrNull()?.newBuilder()?.host(host)?.build()?.toString()
    }

    private fun contentRangeStart(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        // Format: bytes 1234-5678/9100 or bytes */9100
        return Regex("""bytes\s+(\d+)-""").find(header)?.groupValues?.get(1)?.toLongOrNull()
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

    private fun isVerified(file: File, info: UpdateInfo): Boolean {
        if (info.apkSize > 0 && file.length() != info.apkSize) return false
        val sha = info.sha256?.takeIf { it.length == 64 } ?: return true
        return sha256(file).equals(sha, true)
    }

    /**
     * Parses a `.sha256` asset. A per-APK file is just "hash"; a combined file is matched
     * line-by-line against [apkName]. A multi-line file that does NOT mention [apkName] is
     * ambiguous across builds → returns null (refuse) instead of blindly taking the first hash.
     */
    private fun fetchChecksum(url: String, apkName: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LouJi/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            verifyTrustedUrl(response.request.url.toString())
            if (!response.isSuccessful) return null
            val text = response.body?.string().orEmpty()
            val valid: (String) -> Boolean = { token ->
                token.length == 64 && token.all { char -> char.isDigit() || char.lowercaseChar() in 'a'..'f' }
            }
            val lines = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()
            // Combined format: "<hash>  <fileName>" per line — match our exact apk name.
            lines.firstOrNull { line ->
                val parts = line.split(Regex("\\s+"))
                parts.size >= 2 && parts[1].equals(apkName, ignoreCase = true) && valid(parts[0])
            }?.let { return it.split(Regex("\\s+")).first() }
            // Single-checksum file: exactly one line whose first token is a hash.
            if (lines.size == 1) {
                return lines.single().split(Regex("\\s+")).firstOrNull()?.takeIf(valid)
            }
            // Multi-line but no match for our build: ambiguous, refuse rather than guess.
            return null
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
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

    /** Only https on an explicitly whitelisted host may be contacted. */
    private fun isTrustedHost(url: String): Boolean {
        val parsed = Uri.parse(url)
        val host = parsed.host?.lowercase() ?: return false
        if (parsed.scheme != "https" && host !in trustedHostOverrides) return false
        return host in trustedHosts()
    }

    private fun verifyTrustedUrl(url: String) {
        if (!isTrustedHost(url)) throw IOException("更新地址不受信任")
    }

    private fun trustedHosts(): Set<String> = OFFICIAL_HOSTS + trustedHostOverrides

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

    companion object {
        const val META_FILE = "update-meta.json"
        const val PART_SUFFIX = ".part"
        const val BUFFER_BYTES = 64 * 1024
        const val MAX_APK_BYTES = 250L * 1024 * 1024

        /** Hard-coded official download hosts. */
        private val OFFICIAL_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        /**
         * Explicit mirror whitelist. A host not in this list can never be contacted via the
         * `mirrorHosts` constructor argument. Empty for now — no third-party mirror is trusted.
         */
        val ALLOWED_MIRROR_HOSTS: Set<String> = emptySet()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()
    }
}
