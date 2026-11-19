package com.quabtun.louji.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quabtun.louji.ui.update.UpdateUiState
import com.quabtun.louji.ui.update.UpdateViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.withTimeout

@RunWith(AndroidJUnit4::class)
class GitHubUpdaterDownloadTest {
    private lateinit var server: MockWebServer
    private lateinit var updater: GitHubUpdater
    private lateinit var cacheRoot: File

    private val apkBytes = "LOUJI-APK-PAYLOAD-0123456789ABCDEF".toByteArray()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Point cacheRoot at the updater's own directory so metadata/paths match real behavior.
        cacheRoot = File(context.cacheDir, "updates").apply { mkdirs() }
        // MockWebServer speaks cleartext HTTP; the production client keeps https-only in real use.
        // Proxy.NO_PROXY makes sure the request really lands on the in-process mock server.
        val testClient = OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        updater = GitHubUpdater(
            context,
            testClient,
            trustedHostOverrides = setOf(server.hostName),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        updater.clearCache()
    }

    private fun info(size: Long = apkBytes.size.toLong(), sha: String? = null) = UpdateInfo(
        versionName = "9.9.9",
        notes = "",
        apkUrl = server.url("/apk/louji-9.9.9.apk").toString(),
        apkSize = size,
        sha256Url = null,
        sha256 = sha,
    )

    private fun clearMeta() {
        File(cacheRoot, "update-meta.json").delete()
    }

    private fun setUpDispatcher(resumeOffset: Int? = null, restart: Boolean = false, sha: String? = null) {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                return when {
                    range == null -> MockResponse().setResponseCode(200)
                        .setBody(okio.Buffer().write(apkBytes))
                    else -> {
                        // Clamp the client-supplied offset into the payload bounds; there is no
                        // filesystem access here, but staying in-range keeps the mock honest.
                        val requested = Regex("""bytes=(\d+)-""").find(range)
                            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val start = requested.coerceIn(0, apkBytes.size)
                        MockResponse().setResponseCode(206)
                            .setHeader(
                                "Content-Range",
                                "bytes $start-${apkBytes.size - 1}/${apkBytes.size}",
                            )
                            .setBody(okio.Buffer().write(sliceBytes(apkBytes, start, apkBytes.size)))
                    }
                }
            }
        }
    }

    @Test
    fun fullDownloadWritesAndVerifiesThenBecomesReady() {
        val sha = updater.sha256(apkBytes.toFile("full.png"))
        setUpDispatcher()

        val file = runBlockingDispatch {
            updater.download(info(sha = sha))
        }

        assertEquals(apkBytes.size.toLong(), file.length())
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Range"))
        // Full + verified file passes cache inspection as Ready.
        val cached = updater.inspectCachedUpdate("2.2.0")
        assertTrue(cached is CachedUpdate.Ready)
    }

    @Test
    fun resumeSendsRangeAndAppendsOn206() {
        // Seed a partial file manually (as if a previous download was killed).
        val partial = partialFile("louji-9.9.9.apk.part")
        partial.writeBytes(sliceBytes(apkBytes, 0, 10))
        clearMeta()
        setUpDispatcher()

        // An ETag anchors the partial to the remote file; without it resume is refused.
        val sha = updater.sha256(apkBytes.toFile("seed.png"))
        val info = info(sha = sha).copy(eTag = "abc123")

        val file = runBlockingDispatch {
            updater.download(info)
        }

        val recorded = server.takeRequest()
        assertEquals("bytes=10-", recorded.getHeader("Range"))
        assertEquals("abc123", recorded.getHeader("If-Range"))
        assertEquals(apkBytes.size.toLong(), file.length())
        assertEquals(apkBytes.decodeToString(), file.readBytes().decodeToString())
    }

    @Test
    fun noEtagMeansFreshDownloadInsteadOfUnsafeResume() {
        partialFile("louji-9.9.9.apk.part").writeBytes("STALE-STALE-".toByteArray())
        clearMeta()
        setUpDispatcher()

        val sha = updater.sha256(apkBytes.toFile("seed3.png"))

        val file = runBlockingDispatch {
            updater.download(info(sha = sha))
        }

        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals(apkBytes.decodeToString(), file.readBytes().decodeToString())
    }

    @Test
    fun metaFileIsWrittenBeforeBytesFlow() {
        // The response body stalls forever; if meta were only written at 100%, nothing would
        // be on disk while the download is running. The pre-flight saveMeta must already have
        // persisted the ETag + size by the time the first byte is requested.
        clearMeta()
        var sawRequest = false
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                sawRequest = true
                // By the time the server sees the request, meta must exist (it is written
                // before openDownload is called).
                val metaOnDisk = File(cacheRoot, "update-meta.json")
                assertTrue("meta must exist before first byte", metaOnDisk.isFile)
                assertTrue(metaOnDisk.readText().contains("9.9.9"))
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("ETag", "\"abc-etag\"")
                    .setBody(okio.Buffer().write(apkBytes))
            }
        }
        val sha = updater.sha256(apkBytes.toFile("preflight.png"))
        runBlockingDispatch { updater.download(info(sha = sha)) }
        assertTrue(sawRequest)
    }

    @Test
    fun metaFileExistsAfterPartialDownloadAndInspectReturnsPartial() {
        clearMeta()
        setUpDispatcher()
        // The resume flow writes meta before the first byte; simulate a killed process by
        // copying only the pre-flight meta + a truncated .part (exactly what crash leaves).
        val sha = updater.sha256(apkBytes.toFile("meta-seed.png"))
        val partial = partialFile("louji-9.9.9.apk.part")
        partial.writeBytes(sliceBytes(apkBytes, 0, 8))
        val meta = """
            {"versionName":"9.9.9","notes":"","apkUrl":"${server.url("/apk/louji-9.9.9.apk")}",
             "apkSize":${apkBytes.size},"sha256":"$sha",
             "eTag":"\"abc-etag\"","fileName":"louji-9.9.9.apk"}
        """.trimIndent()
        File(cacheRoot, "update-meta.json").writeText(meta)

        val cached = updater.inspectCachedUpdate("2.2.0")

        assertTrue(cached is CachedUpdate.Partial)
        // The resumed request carries BOTH the real byte offset and the stored validator.
        val file = runBlockingDispatch { updater.download((cached as CachedUpdate.Partial).info) }
        val recorded = server.takeRequest()
        assertEquals("bytes=8-", recorded.getHeader("Range"))
        assertEquals("\"abc-etag\"", recorded.getHeader("If-Range"))
        assertEquals(apkBytes.size.toLong(), file.length())
    }

    @Test
    fun responseEtagIsPersistedIntoMetaAfterSuccess() {
        clearMeta()
        // Serve the bytes with a validator, then require it to land in update-meta.json.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("ETag", "\"resp-etag-123\"")
                    .setBody(okio.Buffer().write(apkBytes))
        }
        val sha = updater.sha256(apkBytes.toFile("etag-seed.png"))

        val file = runBlockingDispatch { updater.download(info(sha = sha)) }

        assertTrue(file.isFile)
        val metaText = File(cacheRoot, "update-meta.json").readText()
        // The response validator (or its Last-Modified fallback) must reach the meta file —
        // this is what makes the next resume send Range + If-Range.
        assertTrue(metaText.contains("resp-etag-123"))
    }

    @Test
    fun serverWithoutRangeSupportOverwritesFromScratch() {
        val partial = partialFile("louji-9.9.9.apk.part")
        partial.writeBytes("STALE-STALE-".toByteArray())
        clearMeta()

        val sha = updater.sha256(apkBytes.toFile("seed2.png"))
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                // Simulate a server that ignores Range and always answers 200 with the full file.
                if (request.getHeader("If-Range") != null) {
                    return MockResponse().setResponseCode(200).setBody(okio.Buffer().write(apkBytes))
                }
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes 12-${apkBytes.size - 1}/${apkBytes.size}")
                    .setBody(okio.Buffer().write(sliceBytes(apkBytes, 12, apkBytes.size)))
            }
        }

        val info = info(sha = sha).copy(eTag = "tag-v2")
        val file = runBlockingDispatch {
            updater.download(info)
        }

        assertEquals("bytes=12-", server.takeRequest().getHeader("Range"))
        assertEquals(apkBytes.decodeToString(), file.readBytes().decodeToString())
    }

    @Test
    fun checksumMismatchDeletesPartial() {
        partialFile("louji-9.9.9.apk.part").writeBytes(sliceBytes(apkBytes, 0, 10))
        clearMeta()
        setUpDispatcher()

        val run = kotlin.runCatching {
            runBlockingDispatch { updater.download(info(sha = "ff".repeat(32))) }
        }

        assertTrue(run.isFailure)
        assertFalse(partialFile("louji-9.9.9.apk.part").isFile)
    }

    @Test
    fun cancelledDownloadKeepsThePartialFile() {
        val partial = partialFile("louji-9.9.9.apk.part")
        partial.writeBytes(sliceBytes(apkBytes, 0, 10))
        clearMeta()
        // A body larger than apkSize makes the size guard throw mid-download.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200)
                    .setBody(okio.Buffer().write(apkBytes + apkBytes))
        }

        val run = kotlin.runCatching {
            runBlockingDispatch { updater.download(info(sha = null)) }
        }

        assertTrue(run.isFailure)
        assertTrue(partial.isFile)
        assertTrue(partial.length() > 0)
    }

    @Test
    fun expiredCacheVersionIsSwept() {
        // Cache a file that claims to be an OLD version: inspection must delete it.
        val meta = """
            {"versionName":"1.0.0","notes":"","apkUrl":"https://github.com/x.apk",
             "apkSize":${apkBytes.size},"sha256":"","eTag":"","fileName":"louji-1.0.0.apk"}
        """.trimIndent()
        File(cacheRoot, "update-meta.json").writeText(meta)
        val oldApk = File(cacheRoot, "louji-1.0.0.apk")
        oldApk.writeBytes(apkBytes)

        val cached = updater.inspectCachedUpdate("2.2.0")

        assertTrue(cached is CachedUpdate.None)
        assertFalse(oldApk.isFile)
    }

    @Test
    fun verifiedNewerFullApkBecomesReadyWithoutNetwork() {
        val meta = """
            {"versionName":"9.9.9","notes":"","apkUrl":"${server.url("/x.apk")}",
             "apkSize":${apkBytes.size},"sha256":"${updater.sha256(apkBytes.toFile("s.png"))}",
             "eTag":"","fileName":"louji-9.9.9.apk"}
        """.trimIndent()
        File(cacheRoot, "update-meta.json").writeText(meta)
        File(cacheRoot, "louji-9.9.9.apk").writeBytes(apkBytes)

        val requestsBefore = server.requestCount
        val cached = updater.inspectCachedUpdate("2.2.0")

        assertTrue(cached is CachedUpdate.Ready)
        assertEquals(apkBytes.size.toLong(), (cached as CachedUpdate.Ready).file.length())
        // No network call was made to decide this.
        assertEquals(requestsBefore, server.requestCount)
    }

    @Test
    fun partialNewerApkBecomesPaused() {
        val meta = """
            {"versionName":"9.9.9","notes":"","apkUrl":"${server.url("/x.apk")}",
             "apkSize":${apkBytes.size},"sha256":"","eTag":"","fileName":"louji-9.9.9.apk"}
        """.trimIndent()
        File(cacheRoot, "update-meta.json").writeText(meta)
        partialFile("louji-9.9.9.apk.part").writeBytes(sliceBytes(apkBytes, 0, 8))

        val cached = updater.inspectCachedUpdate("2.2.0")

        assertTrue(cached is CachedUpdate.Partial)
        assertEquals(8L, (cached as CachedUpdate.Partial).downloadedBytes)
        assertEquals(apkBytes.size.toLong(), cached.info.apkSize)
    }

    @Test
    fun refusesInstallWhenChecksumAssetExistsButIsUnfetchable() {
        // The release publishes a .sha256 asset (sha256Url set) but the fetch fails — the
        // download must refuse rather than fall back to a size-only pass.
        partialFile("louji-9.9.9.apk.part").writeBytes(sliceBytes(apkBytes, 0, 10))
        val unreachable = "https://github.com/Quabtun/louji/releases/download/none/louji.sha256"
        val info = UpdateInfo(
            versionName = "9.9.9",
            notes = "",
            apkUrl = server.url("/apk/louji-9.9.9.apk").toString(),
            apkSize = apkBytes.size.toLong(),
            sha256Url = unreachable,
        )
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody(okio.Buffer().write(apkBytes))
        }

        val run = kotlin.runCatching {
            runBlockingDispatch { updater.download(info) }
        }

        // Refused: the apk bytes were served but never finalized into a target file.
        assertTrue(run.isFailure)
        assertFalse(File(cacheRoot, "louji-9.9.9.apk").isFile)
    }

    @Test
    fun multiLineChecksumFileWithoutOurApkNameIsAmbiguousAndRefused() {
        // A combined checksum file listing other builds must not silently verify ours.
        clearMeta()
        val otherSha = "ab".repeat(32)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                if (request.path!!.endsWith(".sha256")) {
                    return MockResponse().setResponseCode(200)
                        .setBody("$otherSha  louji-9.9.9-arm64-v8a.apk\n")
                }
                return MockResponse().setResponseCode(200).setBody(okio.Buffer().write(apkBytes))
            }
        }
        val info = UpdateInfo(
            versionName = "9.9.9",
            notes = "",
            apkUrl = server.url("/apk/louji-9.9.9-armeabi-v7a.apk").toString(),
            apkSize = apkBytes.size.toLong(),
            sha256Url = server.url("/checksums.sha256").toString(),
            apkName = "louji-9.9.9-armeabi-v7a.apk",
        )

        val run = kotlin.runCatching {
            runBlockingDispatch { updater.download(info) }
        }

        // The .sha256 asset exists, so an ambiguous line match is a refusal (no unverified install).
        assertTrue(run.isFailure)
        assertFalse(File(cacheRoot, "louji-9.9.9-armeabi-v7a.apk").isFile)
    }

    @Test
    fun perApkChecksumFileMatchesByExactName() {
        clearMeta()
        val ourSha = updater.sha256(apkBytes.toFile("ours.png"))
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                if (request.path!!.endsWith(".sha256")) {
                    return MockResponse().setResponseCode(200).setBody("$ourSha  ${info().apkName}\n")
                }
                return MockResponse().setResponseCode(200).setBody(okio.Buffer().write(apkBytes))
            }
        }
        val info = UpdateInfo(
            versionName = "9.9.9",
            notes = "",
            apkUrl = server.url("/apk/louji-9.9.9-arm64-v8a.apk").toString(),
            apkSize = apkBytes.size.toLong(),
            sha256Url = server.url("/louji-9.9.9-arm64-v8a.apk.sha256").toString(),
            apkName = "louji-9.9.9-arm64-v8a.apk",
        )

        val file = runBlockingDispatch { updater.download(info) }

        assertEquals(apkBytes.size.toLong(), file.length())
        assertTrue(updater.inspectCachedUpdate("2.2.0") is CachedUpdate.Ready)
    }

    @Test
    fun staleForeignPartialFromOlderVersionIsSweptOnNewDownload() {
        // An older version's leftovers must not survive the pre-flight of a new download.
        partialFile("louji-2.2.1.apk.part").writeBytes("old-version-bytes".toByteArray())
        File(cacheRoot, "louji-2.2.1.apk").writeBytes("old-full".toByteArray())
        clearMeta()
        setUpDispatcher()
        val sha = updater.sha256(apkBytes.toFile("sweep-seed.png"))

        val file = runBlockingDispatch { updater.download(info(sha = sha)) }

        assertEquals(apkBytes.size.toLong(), file.length())
        assertFalse(partialFile("louji-2.2.1.apk.part").isFile)
        assertFalse(File(cacheRoot, "louji-2.2.1.apk").isFile)
    }

    private fun partialFile(name: String): File = File(cacheRoot, name)

    private fun ByteArray.toFile(name: String): File =
        File(cacheRoot, name).apply { writeBytes(this@toFile) }

    private fun runBlockingDispatch(block: suspend () -> File): File =
        kotlinx.coroutines.runBlocking { block() }
}

/** In-memory slice helper (no filesystem involved). */
private fun sliceBytes(source: ByteArray, from: Int, until: Int): ByteArray {
    val out = ByteArray(until - from)
    System.arraycopy(source, from, out, 0, out.size)
    return out
}

/**
 * ViewModel-level checks: the download task and the sheet visibility are decoupled —
 * minimizing hides the UI while bytes keep flowing, and a stale older partial never blocks
 * discovering a newer remote release.
 *
 * Partial state is seeded by driving the REAL download() against a short body: the updater
 * throws "下载不完整" and keeps the `.part` — exactly the crash/kill residue a rider would
 * have. The test never constructs file paths itself; everything goes through the updater.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class UpdateViewModelBehaviorTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var viewModel: UpdateViewModel
    private lateinit var updater: GitHubUpdater

    private val vmApkBytes = "LOUJI-VM-TEST-PAYLOAD-0123456789".toByteArray()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        updater = GitHubUpdater(
            context,
            OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build(),
            trustedHostOverrides = setOf(server.hostName),
            apiUrlOverride = server.url("/releases/latest").toString(),
        )
        viewModel = UpdateViewModel(
            container = com.quabtun.louji.AppContainer(context),
            updaterOverride = updater,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        updater.clearCache()
        Dispatchers.resetMain()
    }

    private fun vmInfo(version: String, apkSize: Long): UpdateInfo = UpdateInfo(
        versionName = version,
        notes = "",
        apkUrl = server.url("/apk/louji-$version.apk").toString(),
        apkSize = apkSize,
        sha256Url = null,
        eTag = "etag-vm",
    )

    /** Runs a real download that ends short, leaving the updater's own `.part` on disk. */
    private fun seedPartialByShortBody(version: String, deliveredBytes: Int) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = okio.Buffer().write(sliceBytes(vmApkBytes, 0, deliveredBytes))
                return MockResponse().setResponseCode(200)
                    .setHeader("ETag", "etag-vm")
                    .setHeader("Content-Length", deliveredBytes.toString())
                    .setBody(body)
            }
        }
        kotlin.runCatching {
            kotlinx.coroutines.runBlocking {
                updater.download(vmInfo(version, apkSize = vmApkBytes.size.toLong()))
            }
        }
    }

    private fun awaitReady() {
        kotlinx.coroutines.runBlocking {
            withTimeout(30_000) {
                while (viewModel.state.value !is UpdateUiState.Ready) {
                    mainDispatcher.scheduler.advanceUntilIdle()
                    if (viewModel.state.value !is UpdateUiState.Ready) Thread.sleep(50)
                }
            }
        }
    }

    /** Waits for an in-flight check() on Dispatchers.IO to settle past the Checking state. */
    private fun awaitStateSettled() {
        kotlinx.coroutines.runBlocking {
            withTimeout(30_000) {
                while (viewModel.state.value is UpdateUiState.Checking) {
                    mainDispatcher.scheduler.advanceUntilIdle()
                    if (viewModel.state.value is UpdateUiState.Checking) Thread.sleep(50)
                }
            }
        }
    }

    @Test
    fun minimizeKeepsDownloadRunningAndSheetReopensOnCompletion() {
        // First pass: a short body leaves the updater's own partial + pre-flight meta behind.
        seedPartialByShortBody(version = "9.9.9", deliveredBytes = 8)
        assertTrue(viewModel.inspectCache())
        assertTrue(viewModel.state.value is UpdateUiState.Paused)

        // Gate the response so the download is deterministically still in flight when the
        // rider minimizes — no race between completion and minimize.
        val gate = java.util.concurrent.CountDownLatch(1)
        server.dispatcher = GatedResumeDispatcher(sliceBytes(vmApkBytes, 0, vmApkBytes.size), gate)

        // Resume: the sheet opens for the resumed download…
        viewModel.download()
        assertTrue(viewModel.sheetVisible.value)

        // …then the rider minimizes while bytes are still being awaited.
        viewModel.minimize()
        assertFalse(viewModel.sheetVisible.value)

        // Release the gate; the background download completes and re-opens the sheet.
        gate.countDown()
        awaitReady()
        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Ready)
        assertEquals(vmApkBytes.size.toLong(), (state as UpdateUiState.Ready).file.length())
        assertTrue(viewModel.sheetVisible.value)
    }

    @Test
    fun lateProgressTickDoesNotReopenMinimizedSheet() {
        seedPartialByShortBody(version = "9.9.9", deliveredBytes = 8)
        assertTrue(viewModel.inspectCache())

        val gate = java.util.concurrent.CountDownLatch(1)
        server.dispatcher = GatedResumeDispatcher(sliceBytes(vmApkBytes, 0, vmApkBytes.size), gate)

        viewModel.download()
        // Minimize while the download is deterministically in flight.
        viewModel.minimize()
        assertFalse(viewModel.sheetVisible.value)

        // A late progress tick (post-minimize) must not re-open the sheet mid-download.
        Thread.sleep(300)
        assertFalse(viewModel.sheetVisible.value)

        // Only the Ready completion is allowed to surface the sheet again.
        gate.countDown()
        awaitReady()
        assertTrue(viewModel.state.value is UpdateUiState.Ready)
        assertTrue(viewModel.sheetVisible.value)
    }

    @Test
    fun staleOlderPartialDoesNotBlockDiscoveringANewerRemoteVersion() {
        // Local cache holds a v2.2.1 partial; remote has v9.9.9 — the remote must win.
        seedPartialByShortBody(version = "2.2.1", deliveredBytes = 5)
        server.dispatcher = object : Dispatcher() {
            private val releaseJson =
                """{"tag_name":"v9.9.9","body":"new",
                    "assets":[{"name":"louji-9.9.9.apk",
                    "browser_download_url":"${server.url("/apk/louji-9.9.9.apk")}",
                    "size":${vmApkBytes.size}}]}"""

            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path!!.endsWith(".apk")) {
                    MockResponse().setResponseCode(200).setBody(okio.Buffer().write(vmApkBytes))
                } else {
                    MockResponse().setResponseCode(200).setBody(releaseJson)
                }
        }

        viewModel.checkNow()
        awaitStateSettled()

        val state = viewModel.state.value
        assertTrue("expected Available, was $state", state is UpdateUiState.Available)
        assertEquals("9.9.9", (state as UpdateUiState.Available).info.versionName)
        // The stale partial is superseded, not silently reused.
        assertTrue(viewModel.sheetVisible.value)
    }

    @Test
    fun remoteWithoutNewerVersionReusesLocalPartial() {
        seedPartialByShortBody(version = "9.9.9", deliveredBytes = 8)
        // Remote publishes the SAME version as the cached partial: check() returns it, but
        // checkNow's version comparison must reuse the local partial instead of re-downloading.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200)
                    .setBody(
                        """{"tag_name":"v9.9.9","body":"",
                            "assets":[{"name":"louji-9.9.9.apk",
                            "browser_download_url":"${server.url("/apk/louji-9.9.9.apk")}",
                            "size":${vmApkBytes.size}}]}""",
                    )
        }
        // Establish the cached Paused state first.
        assertTrue(viewModel.inspectCache())
        assertTrue(viewModel.state.value is UpdateUiState.Paused)

        // Remote tag == cached version: keep the local partial, do not re-download.
        viewModel.checkNow()
        awaitStateSettled()

        val state = viewModel.state.value
        assertTrue(state is UpdateUiState.Paused)
        assertEquals(8L, (state as UpdateUiState.Paused).downloadedBytes)
        // The validator persisted in meta comes back so resume can anchor with If-Range.
        assertEquals("etag-vm", (state as UpdateUiState.Paused).info.eTag)
        assertFalse(state is UpdateUiState.UpToDate)
    }
}
