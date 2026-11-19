package com.quabtun.louji.update

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * Test helper: a MockWebServer dispatcher whose 206 response body is gated behind a latch, so
 * an instrumentation test can minimize a download while it is deterministically still in
 * flight, then release the latch to let it complete. Payload bytes are supplied by the caller.
 */
class GatedResumeDispatcher(
    private val payload: ByteArray,
    private val gate: CountDownLatch,
) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        gate.await(30, TimeUnit.SECONDS)
        val header = request.getHeader("Range")
        val offset = header?.removePrefix("bytes=")
            ?.substringBefore('-')?.toIntOrNull()?.coerceIn(0, payload.size) ?: 0
        val remaining = payload.size - offset
        val remainder = ByteArray(remaining)
        System.arraycopy(payload, offset, remainder, 0, remaining)
        val contentRange = "bytes " + offset + "-" + (payload.size - 1) + "/" + payload.size
        return MockResponse().setResponseCode(206)
            .setHeader("Content-Range", contentRange)
            .setBody(okio.Buffer().write(remainder))
    }

    fun release() {
        gate.countDown()
    }
}
