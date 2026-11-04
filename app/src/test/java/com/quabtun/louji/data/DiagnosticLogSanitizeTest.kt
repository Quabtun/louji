package com.quabtun.louji.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticLogSanitizeTest {
    @Test
    fun sanitizeStripsControlCharacters() {
        assertEquals("line1 line2", sanitizeLogMessage("line1\nline2"))
        assertEquals("a b c", sanitizeLogMessage("a\rb\nc"))
        assertEquals("plain", sanitizeLogMessage("plain"))
    }

    @Test
    fun sanitizeKeepsNormalTextIntact() {
        assertEquals("backup_export records=2 media=3", sanitizeLogMessage("backup_export records=2 media=3"))
    }
}
