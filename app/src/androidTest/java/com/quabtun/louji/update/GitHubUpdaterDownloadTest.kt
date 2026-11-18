package com.quabtun.louji.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
                            .setBody(okio.Buffer().write(apkBytes.copyOfRange(start, apkBytes.size)))
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
        partial.writeBytes(apkBytes.copyOfRange(0, 10))
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
                    .setBody(okio.Buffer().write(apkBytes.copyOfRange(12, apkBytes.size)))
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
        partialFile("louji-9.9.9.apk.part").writeBytes(apkBytes.copyOfRange(0, 10))
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
        partial.writeBytes(apkBytes.copyOfRange(0, 10))
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
        partialFile("louji-9.9.9.apk.part").writeBytes(apkBytes.copyOfRange(0, 8))

        val cached = updater.inspectCachedUpdate("2.2.0")

        assertTrue(cached is CachedUpdate.Partial)
        assertEquals(8L, (cached as CachedUpdate.Partial).downloadedBytes)
        assertEquals(apkBytes.size.toLong(), cached.info.apkSize)
    }

    private fun partialFile(name: String): File = File(cacheRoot, name)

    private fun ByteArray.toFile(name: String): File =
        File(cacheRoot, name).apply { writeBytes(this@toFile) }

    private fun runBlockingDispatch(block: suspend () -> File): File =
        kotlinx.coroutines.runBlocking { block() }
}
