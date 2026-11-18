package com.quabtun.louji.update

import android.os.Build
import androidx.annotation.VisibleForTesting

/**
 * Picks the APK asset that matches the device's primary ABI. Per-ABI builds are named
 * `…-arm64-v8a.apk` / `…-armeabi-v7a.apk`; anything else (no suffix, `universal`) is the
 * fallback for unknown devices.
 */
object ApkAbiMatcher {
    fun select(assets: List<AssetName>): String? =
        select(assets, Build.SUPPORTED_ABIS ?: emptyArray())

    fun select(assets: List<AssetName>, supportedAbis: Array<String>): String? {
        if (assets.isEmpty()) return null
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        val primary = supportedAbis.firstOrNull { abi ->
            apks.any { it.name.endsWith("-$abi.apk", ignoreCase = true) }
        }
        if (primary != null) {
            val matched = apks.first { it.name.endsWith("-$primary.apk", ignoreCase = true) }
            return matched.name
        }
        val universal = apks.firstOrNull { candidate ->
            val lower = candidate.name.lowercase()
            AbiTags.none { lower.endsWith("-$it.apk") }
        }
        return universal?.name
    }

    /** True when the asset name targets a specific ABI rather than being universal. */
    fun isAbiSpecific(name: String): Boolean =
        AbiTags.any { name.endsWith("-$it.apk", ignoreCase = true) }

    data class AssetName(val name: String)

    private val AbiTags = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
}
