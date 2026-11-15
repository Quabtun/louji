package com.quabtun.louji.ui.edit

import com.quabtun.louji.model.DeliveryMethod
import com.quabtun.louji.model.RecordDraft
import com.quabtun.louji.util.ParsedOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared overwrite dialog is driven by this diff, so every import path behaves the same. */
class PendingFillDiffTest {

    @Test
    fun identicalValuesDoNotAskForConfirmation() {
        val draft = RecordDraft(location = "江景苑 2 栋", floor = "21", roomNo = "2103")
        val parsed = ParsedOrder(location = "江景苑 2 栋", floor = "21", roomNo = "2103")

        assertTrue(draft.pendingDiff(parsed).isEmpty())
    }

    @Test
    fun blankFieldsAreFilledWithoutConfirmation() {
        val draft = RecordDraft()
        val parsed = ParsedOrder(location = "江景苑 2 栋", roomNo = "2103")

        assertTrue(draft.pendingDiff(parsed).isEmpty())
    }

    @Test
    fun replacedFieldsProduceOneDiffLineEach() {
        val draft = RecordDraft(
            location = "旧小区",
            roomNo = "101",
            phoneTail = "1111",
            deliveryMethod = DeliveryMethod.LOCKER,
        )
        val parsed = ParsedOrder(
            location = "江景苑 2 栋",
            roomNo = "2103",
            phoneTail = "1111",
            deliveryMethod = DeliveryMethod.DOORSTEP,
        )

        val diff = draft.pendingDiff(parsed)

        assertEquals(3, diff.size)
        assertTrue(diff.any { it.contains("旧小区") && it.contains("江景苑 2 栋") })
        assertTrue(diff.any { it.contains("101") && it.contains("2103") })
        assertTrue(diff.any { it.contains("交付方式") })
    }

    @Test
    fun sameDeliveryMethodIsNotADiff() {
        val draft = RecordDraft(deliveryMethod = DeliveryMethod.DOORSTEP)
        val parsed = ParsedOrder(deliveryMethod = DeliveryMethod.DOORSTEP)

        assertTrue(draft.pendingDiff(parsed).isEmpty())
    }
}
