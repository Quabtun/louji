package com.quabtun.louji.ui.update

import com.quabtun.louji.update.UpdateInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallTargetTest {
    private val info = UpdateInfo(
        versionName = "1.0.3",
        notes = "test",
        apkUrl = "https://github.com/Quabtun/louji/releases/download/v1.0.3/louji.apk",
        apkSize = 1,
        sha256Url = null,
    )
    private val file = File("louji-1.0.3.apk")

    @Test
    fun readyAndNeedsPermissionBothResolve() {
        assertEquals(info to file, installTarget(UpdateUiState.Ready(info, file)))
        assertEquals(info to file, installTarget(UpdateUiState.NeedsPermission(info, file)))
    }

    @Test
    fun nonInstallStatesDoNotResolve() {
        assertNull(installTarget(UpdateUiState.Hidden))
        assertNull(installTarget(UpdateUiState.Checking))
        assertNull(installTarget(UpdateUiState.UpToDate(manual = true)))
        assertNull(installTarget(UpdateUiState.Error("offline", manual = true)))
    }
}
