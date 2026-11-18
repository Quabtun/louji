package com.quabtun.louji.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM: ABI selection is passed in explicitly, so no Android framework is needed. */
class ApkAbiMatcherTest {

    private fun assets(vararg names: String) = names.map { ApkAbiMatcher.AssetName(it) }

    @Test
    fun selectsArm64WhenDeviceSupportsIt() {
        val name = ApkAbiMatcher.select(
            assets(
                "louji-v3.0.0-universal.apk",
                "louji-v3.0.0-arm64-v8a.apk",
                "louji-v3.0.0-armeabi-v7a.apk",
            ),
            supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a", "x86_64"),
        )

        assertEquals("louji-v3.0.0-arm64-v8a.apk", name)
    }

    @Test
    fun selectsArmeabiOn32BitDevice() {
        val name = ApkAbiMatcher.select(
            assets("louji-v3.0.0-arm64-v8a.apk", "louji-v3.0.0-armeabi-v7a.apk"),
            supportedAbis = arrayOf("armeabi-v7a"),
        )

        assertEquals("louji-v3.0.0-armeabi-v7a.apk", name)
    }

    @Test
    fun prefersFirstSupportedAbiInDeviceOrder() {
        val name = ApkAbiMatcher.select(
            assets("louji-v3.0.0-arm64-v8a.apk", "louji-v3.0.0-armeabi-v7a.apk"),
            supportedAbis = arrayOf("armeabi-v7a", "arm64-v8a"),
        )

        assertEquals("louji-v3.0.0-armeabi-v7a.apk", name)
    }

    @Test
    fun fallsBackToUniversalWhenNoAbiMatches() {
        val name = ApkAbiMatcher.select(
            assets("louji-v3.0.0-universal.apk", "louji-v3.0.0-arm64-v8a.apk"),
            supportedAbis = arrayOf("x86_64"),
        )

        assertEquals("louji-v3.0.0-universal.apk", name)
    }

    @Test
    fun fallsBackToSuffixlessApk() {
        val name = ApkAbiMatcher.select(
            assets("louji-v3.0.0.apk"),
            supportedAbis = arrayOf("x86_64"),
        )

        assertEquals("louji-v3.0.0.apk", name)
    }

    @Test
    fun returnsNullWithoutApkAssets() {
        assertNull(ApkAbiMatcher.select(emptyList()))
        assertNull(
            ApkAbiMatcher.select(assets("louji-v3.0.0.apk.sha256"), supportedAbis = arrayOf("arm64-v8a")),
        )
    }

    @Test
    fun ignoresChecksumAssetsWhenPicking() {
        val name = ApkAbiMatcher.select(
            assets(
                "louji-v3.0.0-arm64-v8a.apk.sha256",
                "louji-v3.0.0-arm64-v8a.apk",
                "louji-v3.0.0.apk.sha256",
            ),
            supportedAbis = arrayOf("arm64-v8a"),
        )

        assertEquals("louji-v3.0.0-arm64-v8a.apk", name)
    }

    @Test
    fun abiSpecificDetectionIgnoresCase() {
        assertTrue(ApkAbiMatcher.isAbiSpecific("Louji-v1.0-ARM64-V8A.apk"))
        assertFalse(ApkAbiMatcher.isAbiSpecific("louji-v1.0-universal.apk"))
        assertFalse(ApkAbiMatcher.isAbiSpecific("louji-v1.0.apk"))
    }
}
