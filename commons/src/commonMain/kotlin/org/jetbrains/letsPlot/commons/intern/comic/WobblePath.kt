/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.commons.intern.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Converts straight line segments into wobbled cubic Bezier curves.
 *
 * Each segment gets two control points displaced perpendicular to the line
 * at approximately 33% and 67% of the segment length. The displacement
 * magnitude is: bowing * random * length/200, clamped to 10% of segment length.
 */
object WobblePath {

    /**
     * Result of wobbling a single line segment from [from] to [to].
     * Represented as a cubic Bezier: C cp1x,cp1y cp2x,cp2y to.x,to.y
     */
    data class WobbledSegment(
        val cp1: DoubleVector,
        val cp2: DoubleVector,
        val to: DoubleVector
    )

    /**
     * Wobble a line segment from [from] to [to].
     */
    fun wobbleSegment(
        from: DoubleVector,
        to: DoubleVector,
        config: ComicConfig,
        random: ComicRandom
    ): WobbledSegment {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = sqrt(dx * dx + dy * dy)

        if (length < 0.1) {
            // Degenerate segment — return a straight Bezier
            return WobbledSegment(from, to, to)
        }

        // Perpendicular unit vector
        val perpX = -dy / length
        val perpY = dx / length

        // Max displacement: bowing * length / 200, clamped to 10% of length
        val maxDisplacement = min(config.bowing * length / 200.0, length * 0.1) * config.roughness

        val offset1 = maxDisplacement * random.nextBipolar()
        val offset2 = maxDisplacement * random.nextBipolar()

        // Control points at ~33% and ~67% along the segment
        val t1 = 0.33 + 0.02 * random.nextBipolar()
        val t2 = 0.67 + 0.02 * random.nextBipolar()

        val cp1 = DoubleVector(
            from.x + dx * t1 + perpX * offset1,
            from.y + dy * t1 + perpY * offset1
        )
        val cp2 = DoubleVector(
            from.x + dx * t2 + perpX * offset2,
            from.y + dy * t2 + perpY * offset2
        )

        // Slightly jitter the endpoint for extra hand-drawn feel
        val endJitter = length * 0.002 * config.roughness
        val wobblyTo = DoubleVector(
            to.x + endJitter * random.nextBipolar(),
            to.y + endJitter * random.nextBipolar()
        )

        return WobbledSegment(cp1, cp2, wobblyTo)
    }

    /**
     * Wobble a polyline (list of points). Returns a list of WobbledSegments,
     * one per consecutive pair of points.
     */
    fun wobblePolyline(
        points: List<DoubleVector>,
        config: ComicConfig,
        random: ComicRandom
    ): List<WobbledSegment> {
        if (points.size < 2) return emptyList()
        return (0 until points.size - 1).map { i ->
            wobbleSegment(points[i], points[i + 1], config, random)
        }
    }

}
