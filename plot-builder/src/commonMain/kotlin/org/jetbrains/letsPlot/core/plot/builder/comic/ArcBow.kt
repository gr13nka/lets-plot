/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.ComicStylize
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

private const val SAMPLE_STEP = 6.0

class ArcBow(private val amplitude: Double = 3.0) : ComicStylize {
    override fun apply(points: List<DoubleVector>): List<DoubleVector> {
        if (points.size != 2) return points

        val start = points[0]
        val end = points[1]
        val segment = end.subtract(start)
        val length = segment.length()
        if (length < 2.0 * SAMPLE_STEP) return points

        val unitDirection = segment.mul(1.0 / length)
        val normal = unitDirection.orthogonal()
        val bowDirection = bowDirection(start, end)

        val sampleCount = ceil(length / SAMPLE_STEP).toInt()
        val result = ArrayList<DoubleVector>(sampleCount + 1)
        for (i in 0..sampleCount) {
            val t = i.toDouble() / sampleCount
            val pointOnLine = start.add(segment.mul(t))
            val bowOffset = sin(PI * t) * amplitude * bowDirection
            result.add(pointOnLine.add(normal.mul(bowOffset)))
        }
        return result
    }

    private fun bowDirection(start: DoubleVector, end: DoubleVector): Double {
        val seed = 31 * start.x.hashCode() + start.y.hashCode() +
                   31 * end.x.hashCode()   + end.y.hashCode()
        return if (Lcg(seed).nextDouble() < 0.5) -1.0 else 1.0
    }
}
