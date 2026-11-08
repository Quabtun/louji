package com.quabtun.louji.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorMathTest {
    @Test
    fun parsesNumericFloorFormats() {
        assertEquals(6, FloorMath.climbedFloor("6"))
        assertEquals(6, FloorMath.climbedFloor("6F"))
        assertEquals(12, FloorMath.climbedFloor("12楼"))
        assertEquals(18, FloorMath.climbedFloor("18层"))
        assertEquals(3, FloorMath.climbedFloor(" 3 "))
    }

    @Test
    fun skipsBasementsAndUnknown() {
        assertNull(FloorMath.climbedFloor("B1"))
        assertNull(FloorMath.climbedFloor("-1"))
        assertNull(FloorMath.climbedFloor("地下1层"))
        assertNull(FloorMath.climbedFloor(""))
        assertNull(FloorMath.climbedFloor("低层"))
    }

    @Test
    fun sumsFloorsAndComputesHeight() {
        assertEquals(21, FloorMath.totalFloors(listOf("7", "6F", "8", "B1", "无")))
        assertEquals(63.0, FloorMath.metres(21), 0.01)
        assertEquals(0.063, FloorMath.kilometres(21), 0.001)
        assertTrue(FloorMath.kilocalories(21) > 0)
    }

    @Test
    fun achievementScalesWithHeight() {
        assertTrue(FloorMath.achievement(0).contains("还没开始"))
        assertTrue(FloorMath.achievement(5).contains("热身"))
        assertTrue(FloorMath.achievement(100).contains("写字楼"))
        assertTrue(FloorMath.achievement(300).contains("泰山") ||
            FloorMath.achievement(300).contains("铁塔"))
    }
}
