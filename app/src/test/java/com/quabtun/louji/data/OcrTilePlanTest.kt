package com.quabtun.louji.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTilePlanTest {

    @Test
    fun shortScreenshotIsOnePage() {
        assertEquals(listOf(0 to 1200), planOcrTiles(totalHeight = 1200))
        assertEquals(listOf(0 to 2400), planOcrTiles(totalHeight = 2400))
    }

    @Test
    fun tallScreenshotIsTiledWithOverlap() {
        val plan = planOcrTiles(totalHeight = 6000)

        assertTrue(plan.size > 1)
        assertEquals(0, plan.first().first)
        plan.zipWithNext().forEach { (current, next) ->
            val currentEnd = current.first + current.second
            // Consecutive tiles overlap so a glyph on the boundary is readable in one of them.
            assertTrue(currentEnd > next.first)
        }
        val last = plan.last()
        assertEquals(6000, last.first + last.second)
    }

    @Test
    fun veryLongScreenshotIsCapped() {
        val plan = planOcrTiles(totalHeight = 40_000, maxTiles = 8)

        assertEquals(8, plan.size)
    }

    @Test
    fun tileCountCoversWholeImageForModerateCaptures() {
        val plan = planOcrTiles(totalHeight = 5_000)

        val covered = plan.last().let { it.first + it.second }
        assertEquals(5_000, covered)
    }

    @Test
    fun emptyImageProducesNoTiles() {
        assertTrue(planOcrTiles(totalHeight = 0).isEmpty())
    }
}
