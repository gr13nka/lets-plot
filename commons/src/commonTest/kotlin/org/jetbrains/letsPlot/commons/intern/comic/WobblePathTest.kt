/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.commons.intern.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WobblePathTest {

    private val config = ComicConfig()
    private val random = ComicRandom(42)

    @Test
    fun wobbleSegmentProducesControlPointsBetweenEndpoints() {
        val from = DoubleVector(0.0, 0.0)
        val to = DoubleVector(100.0, 0.0)
        val seg = WobblePath.wobbleSegment(from, to, config, random)

        // Control points should be roughly between from and to on x-axis
        assertTrue(seg.cp1.x > 0.0 && seg.cp1.x < 100.0, "cp1.x should be between endpoints")
        assertTrue(seg.cp2.x > 0.0 && seg.cp2.x < 100.0, "cp2.x should be between endpoints")
        // Endpoint should be close to the target
        assertTrue(kotlin.math.abs(seg.to.x - to.x) < 1.0, "endpoint x should be close to target")
        assertTrue(kotlin.math.abs(seg.to.y - to.y) < 1.0, "endpoint y should be close to target")
    }

    @Test
    fun wobbleSegmentIsDeterministicWithSameSeed() {
        val from = DoubleVector(0.0, 0.0)
        val to = DoubleVector(100.0, 50.0)

        val random1 = ComicRandom(123)
        val seg1 = WobblePath.wobbleSegment(from, to, config, random1)

        val random2 = ComicRandom(123)
        val seg2 = WobblePath.wobbleSegment(from, to, config, random2)

        assertEquals(seg1.cp1.x, seg2.cp1.x, "Same seed should produce same cp1.x")
        assertEquals(seg1.cp1.y, seg2.cp1.y, "Same seed should produce same cp1.y")
        assertEquals(seg1.cp2.x, seg2.cp2.x, "Same seed should produce same cp2.x")
        assertEquals(seg1.cp2.y, seg2.cp2.y, "Same seed should produce same cp2.y")
    }

    @Test
    fun wobbleSegmentDifferentSeedsProduceDifferentResults() {
        val from = DoubleVector(0.0, 0.0)
        val to = DoubleVector(100.0, 50.0)

        val seg1 = WobblePath.wobbleSegment(from, to, config, ComicRandom(1))
        val seg2 = WobblePath.wobbleSegment(from, to, config, ComicRandom(999))

        // Extremely unlikely for two different seeds to produce identical output
        val same = seg1.cp1.x == seg2.cp1.x && seg1.cp1.y == seg2.cp1.y
        assertTrue(!same, "Different seeds should produce different control points")
    }

    @Test
    fun degenerateSegmentReturnsStraightBezier() {
        val point = DoubleVector(50.0, 50.0)
        val seg = WobblePath.wobbleSegment(point, point, config, random)

        assertEquals(point.x, seg.cp1.x)
        assertEquals(point.y, seg.cp1.y)
        assertEquals(point.x, seg.to.x)
        assertEquals(point.y, seg.to.y)
    }

    @Test
    fun wobblePolylineReturnsCorrectSegmentCount() {
        val points = listOf(
            DoubleVector(0.0, 0.0),
            DoubleVector(50.0, 0.0),
            DoubleVector(100.0, 0.0),
            DoubleVector(100.0, 50.0)
        )

        val segments = WobblePath.wobblePolyline(points, config, random)
        assertEquals(3, segments.size, "Should produce one segment per consecutive pair")
    }

    @Test
    fun wobblePolylineEmptyOrSinglePoint() {
        assertEquals(0, WobblePath.wobblePolyline(emptyList(), config, random).size)
        assertEquals(0, WobblePath.wobblePolyline(listOf(DoubleVector.ZERO), config, random).size)
    }

    @Test
    fun higherRoughnessIncreasesDisplacement() {
        val from = DoubleVector(0.0, 0.0)
        val to = DoubleVector(200.0, 0.0)

        val lowRoughness = ComicConfig(roughness = 0.1)
        val highRoughness = ComicConfig(roughness = 5.0)

        // Run multiple trials and compare average displacement
        var lowTotal = 0.0
        var highTotal = 0.0
        val trials = 20
        val lowRand = ComicRandom(42)
        val highRand = ComicRandom(42)

        for (i in 0 until trials) {
            val segLow = WobblePath.wobbleSegment(from, to, lowRoughness, lowRand)
            val segHigh = WobblePath.wobbleSegment(from, to, highRoughness, highRand)
            lowTotal += kotlin.math.abs(segLow.cp1.y)
            highTotal += kotlin.math.abs(segHigh.cp1.y)
        }

        assertTrue(highTotal > lowTotal, "Higher roughness should produce larger displacements")
    }

}
