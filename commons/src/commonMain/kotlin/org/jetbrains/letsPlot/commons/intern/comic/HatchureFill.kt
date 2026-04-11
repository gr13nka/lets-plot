/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.commons.intern.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import kotlin.math.*

/**
 * Generates hatching lines to fill a polygon, as an alternative to solid fills
 * for comic rendering.
 *
 * Algorithm:
 * 1. Rotate all polygon vertices by -angle
 * 2. Sweep horizontal scanlines at hatchGap intervals across the bounding box
 * 3. Find edge intersections using even-odd rule to get line segment pairs
 * 4. Rotate intersection points back by +angle
 * 5. Optionally wobble each hatch line
 */
object HatchureFill {

    data class HatchLine(val from: DoubleVector, val to: DoubleVector)

    /**
     * Generate hatch lines for a polygon defined by [vertices].
     * The polygon is implicitly closed (last vertex connects to first).
     */
    fun hatchPolygon(
        vertices: List<DoubleVector>,
        config: ComicConfig
    ): List<HatchLine> {
        if (vertices.size < 3) return emptyList()

        val angleRad = config.hatchAngle * PI / 180.0
        val gap = config.hatchGap

        // Step 1: Rotate vertices by -angle
        val rotated = vertices.map { rotate(it, -angleRad) }

        // Step 2: Compute vertical extent of rotated vertices for scanline sweep
        val minY = rotated.minOf { it.y }
        val maxY = rotated.maxOf { it.y }

        // Step 3: Sweep scanlines and find intersections
        val lines = mutableListOf<HatchLine>()
        var y = minY + gap / 2.0
        while (y < maxY) {
            val intersections = findIntersections(rotated, y)
            // Sort intersections by x-coordinate
            intersections.sort()

            // Step 4: Pair intersections (even-odd rule)
            var i = 0
            while (i + 1 < intersections.size) {
                val x1 = intersections[i]
                val x2 = intersections[i + 1]

                // Rotate back and add
                val from = rotate(DoubleVector(x1, y), angleRad)
                val to = rotate(DoubleVector(x2, y), angleRad)
                lines.add(HatchLine(from, to))
                i += 2
            }

            y += gap
        }

        return lines
    }

    /**
     * Generate hatch lines for a rectangle.
     */
    fun hatchRect(
        x: Double, y: Double, width: Double, height: Double,
        config: ComicConfig
    ): List<HatchLine> {
        val vertices = listOf(
            DoubleVector(x, y),
            DoubleVector(x + width, y),
            DoubleVector(x + width, y + height),
            DoubleVector(x, y + height)
        )
        return hatchPolygon(vertices, config)
    }

    /**
     * Generate hatch lines for a circle approximated as a polygon.
     */
    fun hatchCircle(
        cx: Double, cy: Double, r: Double,
        config: ComicConfig,
        segments: Int = 32
    ): List<HatchLine> {
        val vertices = (0 until segments).map { i ->
            val theta = 2.0 * PI * i / segments
            DoubleVector(cx + r * cos(theta), cy + r * sin(theta))
        }
        return hatchPolygon(vertices, config)
    }

    private fun rotate(p: DoubleVector, angleRad: Double): DoubleVector {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return DoubleVector(
            p.x * cosA - p.y * sinA,
            p.x * sinA + p.y * cosA
        )
    }

    /**
     * Find x-coordinates where horizontal scanline at [y] intersects polygon edges.
     * The polygon is implicitly closed.
     */
    private fun findIntersections(vertices: List<DoubleVector>, y: Double): MutableList<Double> {
        val intersections = mutableListOf<Double>()
        val n = vertices.size
        for (i in 0 until n) {
            val v1 = vertices[i]
            val v2 = vertices[(i + 1) % n]

            // Check if scanline crosses this edge
            if ((v1.y <= y && v2.y > y) || (v2.y <= y && v1.y > y)) {
                // Linear interpolation to find x at this y
                val t = (y - v1.y) / (v2.y - v1.y)
                intersections.add(v1.x + t * (v2.x - v1.x))
            }
        }
        return intersections
    }
}
