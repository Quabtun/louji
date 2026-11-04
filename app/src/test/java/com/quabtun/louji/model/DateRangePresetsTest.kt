package com.quabtun.louji.model

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateRangePresetsTest {
    @Test
    fun lastDaysUsesRollingWindow() {
        val now = 1_700_000_000_000L
        val range = DateRangePresets.lastDays(now, 7)
        assertEquals(now - 7 * DateRangePresets.DAY_MS, range.start)
        assertEquals(0L, range.end)
        assertTrue(range.isActive)
    }

    @Test
    fun emptyRangeIsInactive() {
        assertFalse(DateRange().isActive)
        assertFalse(DateRange(start = 0L, end = 0L).isActive)
        assertTrue(DateRange(start = 1L).isActive)
        assertTrue(DateRange(end = 1L).isActive)
    }

    @Test
    fun startOfDayResetsTimeToLocalMidnight() {
        val utc = TimeZone.getTimeZone("UTC")
        val input = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.JANUARY, 15, 15, 30, 0)
        }.timeInMillis
        val expected = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.JANUARY, 15, 0, 0, 0)
        }.timeInMillis
        assertEquals(expected, DateRangePresets.startOfDay(input, utc))
    }

    @Test
    fun startOfDayConvertsAcrossZones() {
        val utc = TimeZone.getTimeZone("UTC")
        val gmtPlus8 = TimeZone.getTimeZone("GMT+08:00")
        val utcMidnight = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.JANUARY, 15, 0, 0, 0)
        }.timeInMillis
        // Local midnight in GMT+8 for that calendar day is 16:00 UTC the previous day.
        val expected = utcMidnight - 8 * 60 * 60 * 1000L
        assertEquals(expected, DateRangePresets.startOfDay(utcMidnight, gmtPlus8))
    }

    @Test
    fun nextDayStartAddsOneDay() {
        assertEquals(DateRangePresets.DAY_MS, DateRangePresets.nextDayStart(0L))
    }
}
