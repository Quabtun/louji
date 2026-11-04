package com.quabtun.louji.util

import com.quabtun.louji.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardOrderParserTest {
    @Test
    fun parsesLabeledOrderText() {
        val parsed = ClipboardOrderParser.parse(
            """
            订单号：D5566
            收货地址：江景苑 2 栋
            楼层：21层
            房号：2103
            王女士 尾号 2145
            """.trimIndent(),
        )

        assertEquals("江景苑 2 栋", parsed.location)
        assertEquals("D5566", parsed.orderNo)
        assertEquals("2145", parsed.phoneTail)
        assertEquals("21", parsed.floor)
        assertEquals("2103", parsed.roomNo)
        assertEquals(Gender.FEMALE, parsed.gender)
        assertFalse(parsed.isEmpty)
    }

    @Test
    fun parsesAddressRoomPhoneAndGenderWithoutFloorFalsePositive() {
        val parsed = ClipboardOrderParser.parse(
            """
            阳光花园3号楼2单元501室
            李先生 13812344821
            """.trimIndent(),
        )

        assertEquals("阳光花园3号楼2单元501室", parsed.location)
        assertNull(parsed.floor)
        assertEquals("501", parsed.roomNo)
        assertEquals("4821", parsed.phoneTail)
        assertEquals(Gender.MALE, parsed.gender)
    }

    @Test
    fun parsesFloorSuffixAndDashedRoom() {
        val parsed = ClipboardOrderParser.parse("7F · 2-301 尾号 4821")

        assertEquals("7", parsed.floor)
        assertEquals("2-301", parsed.roomNo)
        assertEquals("4821", parsed.phoneTail)
    }

    @Test
    fun dateIsNotMistakenForRoom() {
        val parsed = ClipboardOrderParser.parse("日期 2026-11-04")

        assertNull(parsed.roomNo)
        assertTrue(parsed.isEmpty)
    }

    @Test
    fun unrecognizedTextDoesNotInventFields() {
        val parsed = ClipboardOrderParser.parse("今天下午三点送到，辛苦了")

        assertTrue(parsed.isEmpty)
        assertNull(parsed.location)
        assertNull(parsed.orderNo)
    }

    @Test
    fun parsesOcrStyleNoisyText() {
        // OCR 常把冒号后空格吞掉、并引入多余空行/空格。
        val parsed = ClipboardOrderParser.parse(
            "订单号:D5566\n\n收货地址: 江景苑 2 栋\n楼层: 21 层\n房号:2103\n王女士 尾号 2145",
        )

        assertEquals("D5566", parsed.orderNo)
        assertEquals("江景苑 2 栋", parsed.location)
        assertEquals("21", parsed.floor)
        assertEquals("2103", parsed.roomNo)
        assertEquals("2145", parsed.phoneTail)
        assertEquals(Gender.FEMALE, parsed.gender)
    }
}
