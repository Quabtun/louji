package com.quabtun.louji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLineComposerTest {

    @Test
    fun joinsBlocksSharingABaselineLeftToRight() {
        val lines = OcrLineComposer.compose(
            listOf(
                OcrLineBox("1804室", left = 320, centerY = 500, height = 40),
                OcrLineBox("阳光花园3栋", left = 40, centerY = 505, height = 42),
                OcrLineBox("尾号4821", left = 40, centerY = 600, height = 40),
            ),
        )

        assertEquals(listOf("阳光花园3栋 1804室", "尾号4821"), lines)
    }

    @Test
    fun collapsesDuplicateAndConsecutiveRows() {
        val lines = OcrLineComposer.compose(
            listOf(
                OcrLineBox("订单号 D5566", left = 10, centerY = 100, height = 30),
                // Same row detected twice by two overlapping tiles.
                OcrLineBox("订单号 D5566", left = 10, centerY = 102, height = 30),
                // Overlap repeats the whole row on the next tile.
                OcrLineBox("订单号 D5566", left = 10, centerY = 300, height = 30),
                OcrLineBox("收货地址 江景苑2栋", left = 10, centerY = 400, height = 30),
            ),
        )

        assertEquals(listOf("订单号 D5566", "收货地址 江景苑2栋"), lines)
    }

    @Test
    fun emitsSingleSpacesOnly() {
        val lines = OcrLineComposer.compose(
            listOf(
                OcrLineBox("江景苑", left = 0, centerY = 100, height = 40),
                OcrLineBox("2栋", left = 100, centerY = 102, height = 40),
                OcrLineBox("1804", left = 200, centerY = 101, height = 40),
            ),
        )

        assertEquals(listOf("江景苑 2栋 1804"), lines)
        assertTrue(lines.single().none { it == '\t' })
        assertTrue(!lines.single().contains("  "))
    }

    @Test
    fun keepsSeparateRowsSeparate() {
        val lines = OcrLineComposer.compose(
            listOf(
                OcrLineBox("楼层 21", left = 0, centerY = 100, height = 30),
                OcrLineBox("房号 2103", left = 0, centerY = 140, height = 30),
            ),
        )

        assertEquals(listOf("楼层 21", "房号 2103"), lines)
    }
}
