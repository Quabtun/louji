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

        assertEquals("阳光花园3号楼2单元", parsed.location)
        assertEquals("5", parsed.floor)
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
    fun parsesSingleLineLabeledOrder() {
        val parsed = ClipboardOrderParser.parse(
            "收货地址：江景苑 2 栋 楼层：21层 房号：2103 订单号：D5566 王女士 尾号 2145",
        )

        assertEquals("江景苑 2 栋", parsed.location)
        assertEquals("21", parsed.floor)
        assertEquals("2103", parsed.roomNo)
        assertEquals("D5566", parsed.orderNo)
        assertEquals("2145", parsed.phoneTail)
        assertEquals(Gender.FEMALE, parsed.gender)
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

    @Test
    fun extractsVirtualNumberExtension() {
        val a = ClipboardOrderParser.parse("138****1234转5678 阳光花园3栋")
        assertEquals("1234", a.phoneTail)
        assertEquals("5678", a.phoneExt)

        val b = ClipboardOrderParser.parse("13812349876-5678")
        assertEquals("9876", b.phoneTail)
        assertEquals("5678", b.phoneExt)

        val c = ClipboardOrderParser.parse("手机尾号4821 分机号 902")
        assertEquals("4821", c.phoneTail)
        assertEquals("902", c.phoneExt)
    }

    @Test
    fun splitsCompoundAddressIntoBuildingFloorRoom() {
        val a = ClipboardOrderParser.parse("阳光花园三期12栋2单元1804室")
        assertEquals("阳光花园三期12栋2单元", a.location)
        assertEquals("18", a.floor)
        assertEquals("1804", a.roomNo)

        val b = ClipboardOrderParser.parse("锦绣公寓B座7楼702")
        assertEquals("锦绣公寓B座", b.location)
        assertEquals("7", b.floor)
        assertEquals("702", b.roomNo)

        val c = ClipboardOrderParser.parse("老城小区3号楼B1-105")
        assertEquals("老城小区3号楼", c.location)
        assertEquals("B1", c.floor)
        assertEquals("B1-105", c.roomNo)
    }

    @Test
    fun mapsDeliveryPreferencesAndRemarkHints() {
        assertEquals(
            com.quabtun.louji.model.DeliveryMethod.LOCKER,
            ClipboardOrderParser.parse("放外卖柜").deliveryMethod,
        )
        assertEquals(
            com.quabtun.louji.model.DeliveryMethod.COME_DOWN,
            ClipboardOrderParser.parse("客户会下楼拿").deliveryMethod,
        )
        assertEquals(
            com.quabtun.louji.model.DeliveryMethod.DOORSTEP,
            ClipboardOrderParser.parse("放门口，别敲门").deliveryMethod,
        )
        val hint = ClipboardOrderParser.parse("放鞋架 别按铃 家里有狗").remarkHint
        assertEquals("别按铃、家里有狗", hint)
    }
}
