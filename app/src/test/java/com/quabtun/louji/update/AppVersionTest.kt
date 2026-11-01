package com.quabtun.louji.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun parsesTagWithPrefixAndSuffix() {
        assertEquals("1.0.1", AppVersion.parse("v1.0.1")?.toString())
        assertEquals("1.0.1", AppVersion.parse("V1.0.1")?.toString())
        assertEquals("1.0.1", AppVersion.parse("1.0.1-debug")?.toString())
        assertEquals("1.0.1", AppVersion.parse("1.0.1+build.7")?.toString())
    }

    @Test
    fun treatsMissingComponentsAsZero() {
        assertEquals(AppVersion.parse("1.0"), AppVersion.parse("1.0.0"))
        assertEquals(AppVersion.parse("1"), AppVersion.parse("1.0.0"))
    }

    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
    }

    @Test
    fun isNewerHandlesRealReleaseTags() {
        assertTrue(AppVersion.isNewer("v1.0.1", "1.0.0"))
        assertFalse(AppVersion.isNewer("v1.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("v0.9.9", "1.0.0"))
        assertFalse(AppVersion.isNewer("not-a-version", "1.0.0"))
    }

    @Test
    fun rejectsMalformedVersions() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
        assertNull(AppVersion.parse("1.x"))
        assertNull(AppVersion.parse("1..2"))
    }
}
