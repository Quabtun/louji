package com.quabtun.louji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteUndoControllerTest {
    @Test
    fun announceConsumeCycle() {
        val controller = DeleteUndoController()
        assertNull(controller.pending.value)

        controller.announce(1L)
        assertEquals(1L, controller.pending.value?.recordId)
        assertEquals(1, controller.pending.value?.nonce)

        controller.consume()
        assertNull(controller.pending.value)
    }

    @Test
    fun nonceIncrementsOnRepeatAnnouncement() {
        val controller = DeleteUndoController()
        controller.announce(7L)
        controller.announce(7L)

        assertEquals(7L, controller.pending.value?.recordId)
        assertEquals(2, controller.pending.value?.nonce)
    }
}
