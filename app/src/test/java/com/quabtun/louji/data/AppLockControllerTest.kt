package com.quabtun.louji.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockControllerTest {
    private fun withController(
        initial: AppSettings,
        block: (MutableStateFlow<AppSettings>, AppLockController) -> Unit,
    ) = runTest {
        val flow = MutableStateFlow(initial)
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val controller = AppLockController(flow, scope)
        try {
            block(flow, controller)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun disabledByDefaultDoesNotLock() = withController(AppSettings()) { _, controller ->
        assertTrue(controller.state.value.ready)
        assertFalse(controller.state.value.enabled)
        assertFalse(controller.state.value.locked)
    }

    @Test
    fun enabledSettingLocksByDefault() = withController(AppSettings(appLockEnabled = true)) { _, controller ->
        assertTrue(controller.state.value.enabled)
        assertTrue(controller.state.value.locked)
    }

    @Test
    fun lockAndUnlockFollowController() = withController(AppSettings(appLockEnabled = true)) { _, controller ->
        controller.unlock()
        assertFalse(controller.state.value.locked)

        controller.lockIfEnabled()
        assertTrue(controller.state.value.locked)

        controller.unlock()
        assertFalse(controller.state.value.locked)
    }

    @Test
    fun disablingFeatureClearsLock() = withController(AppSettings(appLockEnabled = true)) { flow, controller ->
        flow.value = AppSettings(appLockEnabled = false)
        assertFalse(controller.state.value.enabled)
        assertFalse(controller.state.value.locked)
    }
}
