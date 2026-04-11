/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.commons.intern.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HatchureFillTest {

    private val config = ComicConfig(hatchAngle = 0.0, hatchGap = 10.0)

    @Test
    fun hatchRectProducesHorizontalLines() {
        // Horizontal hatch (angle=0) across a 100x100 rect at gap=10
        val lines = HatchureFill.hatchRect(0.0, 0.0, 100.0, 100.0, config)

        assertTrue(lines.isNotEmpty(), "Should produce hatch lines")
        // At gap=10 across height=100, expect ~9-10 lines (starting at gap/2=5)
        assertTrue(lines.size in 8..12, "Expected ~10 lines, got ${lines.size}")

        // All lines should span the width of the rect
        for (line in lines) {
            assertTrue(line.from.x <= 1.0, "Line start x should be near 0, was ${line.from.x}")
            assertTrue(line.to.x >= 99.0, "Line end x should be near 100, was ${line.to.x}")
        }
    }

    @Test
    fun hatchRectWithAngledLines() {
        val angledConfig = ComicConfig(hatchAngle = 45.0, hatchGap = 10.0)
        val lines = HatchureFill.hatchRect(0.0, 0.0, 50.0, 50.0, angledConfig)

        assertTrue(lines.isNotEmpty(), "Angled hatching should produce lines")
        // Lines at 45 degrees should have different from/to y-coordinates
        for (line in lines) {
            // For 45-degree hatch, from and to should generally have different y
            // (unless the line is very short at a corner)
            val dy = kotlin.math.abs(line.to.y - line.from.y)
            val dx = kotlin.math.abs(line.to.x - line.from.x)
            assertTrue(dy > 0 || dx > 0, "Hatch lines should have non-zero length")
        }
    }

    @Test
    fun hatchCircleProducesLines() {
        val lines = HatchureFill.hatchCircle(50.0, 50.0, 40.0, config)

        assertTrue(lines.isNotEmpty(), "Should produce hatch lines for circle")
        // Circle with r=40, diameter=80, gap=10 → expect ~7-8 lines
        assertTrue(lines.size in 5..12, "Expected ~8 lines for circle, got ${lines.size}")
    }

    @Test
    fun hatchPolygonTriangle() {
        val triangle = listOf(
            DoubleVector(50.0, 0.0),
            DoubleVector(100.0, 100.0),
            DoubleVector(0.0, 100.0)
        )
        val lines = HatchureFill.hatchPolygon(triangle, config)

        assertTrue(lines.isNotEmpty(), "Should produce hatch lines for triangle")
        // Hatch lines should be within the triangle's bounding box
        for (line in lines) {
            assertTrue(line.from.x >= -1.0 && line.from.x <= 101.0)
            assertTrue(line.to.x >= -1.0 && line.to.x <= 101.0)
        }
    }

    @Test
    fun emptyPolygonProducesNoLines() {
        assertEquals(0, HatchureFill.hatchPolygon(emptyList(), config).size)
        assertEquals(0, HatchureFill.hatchPolygon(listOf(DoubleVector.ZERO), config).size)
        assertEquals(0, HatchureFill.hatchPolygon(
            listOf(DoubleVector.ZERO, DoubleVector(1.0, 0.0)), config).size)
    }

    @Test
    fun largerGapProducesFewerLines() {
        val smallGap = ComicConfig(hatchAngle = 0.0, hatchGap = 5.0)
        val largeGap = ComicConfig(hatchAngle = 0.0, hatchGap = 20.0)

        val linesSmall = HatchureFill.hatchRect(0.0, 0.0, 100.0, 100.0, smallGap)
        val linesLarge = HatchureFill.hatchRect(0.0, 0.0, 100.0, 100.0, largeGap)

        assertTrue(linesSmall.size > linesLarge.size,
            "Smaller gap should produce more lines: ${linesSmall.size} vs ${linesLarge.size}")
    }
}
