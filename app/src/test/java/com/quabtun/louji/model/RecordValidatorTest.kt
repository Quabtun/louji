package com.quabtun.louji.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordValidatorTest {
    @Test
    fun blankLocationIsRejected() {
        val errors = RecordValidator.validate(RecordDraft(), mediaCount = 0)
        assertNotNull(errors.location)
        assertTrue(errors.hasAny)
    }

    @Test
    fun aCompleteDraftPasses() {
        val draft = RecordDraft(
            location = "阳光花园 3 栋",
            orderNo = "A12345",
            gender = Gender.FEMALE,
            phoneTail = "4821",
            floor = "7",
            roomNo = "2-301",
            doorIntact = TriState.YES,
            canOpenFromAbove = TriState.NO,
            noStairs = TriState.YES,
            willComeDown = TriState.YES,
            personalityTags = listOf("好沟通"),
            personalityNote = "打电话会接",
        )
        val errors = RecordValidator.validate(draft, mediaCount = 2)
        assertFalse(errors.hasAny)
        assertNull(errors.location)
    }

    @Test
    fun phoneTailMustBeFourDigits() {
        val draft = RecordDraft(location = "A", phoneTail = "123")
        assertNotNull(RecordValidator.validate(draft, 0).phoneTail)
        val four = RecordDraft(location = "A", phoneTail = "4821")
        assertNull(RecordValidator.validate(four, 0).phoneTail)
        val empty = RecordDraft(location = "A", phoneTail = "")
        assertNull(RecordValidator.validate(empty, 0).phoneTail)
    }

    @Test
    fun mediaCountIsCapped() {
        val errors = RecordValidator.validate(RecordDraft(location = "A"), mediaCount = 21)
        assertNotNull(errors.media)
        assertTrue(errors.hasAny)
        assertEquals(null, RecordValidator.validate(RecordDraft(location = "A"), 20).media)
    }

    @Test
    fun draftDoesNotCountAsContentWhenOnlyUnknownDefaults() {
        assertFalse(RecordDraft().hasMeaningfulContent())
        assertTrue(RecordDraft(location = "A").hasMeaningfulContent())
        assertTrue(RecordDraft(noStairs = TriState.YES).hasMeaningfulContent())
        assertTrue(RecordDraft(remark = "门禁在左手边").hasMeaningfulContent())
    }
}
