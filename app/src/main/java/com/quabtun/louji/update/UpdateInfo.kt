package com.quabtun.louji.update

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256Url: String?,
    /** Asset file name ("louji-v3.0.0.apk"), used to pick a per-ABI build. */
    val apkName: String = "",
    /** Resolved checksum once known; reused offline instead of refetching. */
    val sha256: String? = null,
    /** ETag of the release asset, guards a resume against a changed remote file. */
    val eTag: String? = null,
)
