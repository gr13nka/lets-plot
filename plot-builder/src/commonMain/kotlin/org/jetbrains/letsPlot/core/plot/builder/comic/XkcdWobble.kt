/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

private const val SAMPLE_STEP = 2.0

class XkcdWobble(
    private val amplitude: Double = 2.4,
    private val length: Double = 100.0,
    private val randomness: Double = 62.0
) : ComicStylize {
    override fun apply(points: List<DoubleVector>, seed: Int?): List<DoubleVector> {
        if (points.size < 2) return points

        val cumLen = DoubleArray(points.size)
        for (pointIdx in 1 until points.size) {
            cumLen[pointIdx] = cumLen[pointIdx - 1] + points[pointIdx].subtract(points[pointIdx - 1]).length()
        }
        val total = cumLen[points.size - 1]
        if (total < 2.0) return points

        val first = points.first()
        val rng = Lcg(seed ?: (31 * first.x.hashCode() + first.y.hashCode()))

        val segCount = ceil(total / length).toInt()
        val anchorArcLen = DoubleArray(segCount + 2)
        val anchorOffset = DoubleArray(segCount + 2)
        for (segIdx in 0 until segCount) {
            val segStart = segIdx * length
            val segEnd = minOf((segIdx + 1) * length, total)
            anchorArcLen[segIdx + 1] = segStart + (segEnd - segStart) * (0.1 + 0.8 * rng.nextDouble())
            val sign = if (rng.nextDouble() < 0.5) -1.0 else 1.0
            val variation = 1.0 + (rng.nextDouble() * 2.0 - 1.0) / randomness
            anchorOffset[segIdx + 1] = sign * amplitude * variation
        }
        anchorArcLen[segCount + 1] = total

        val sampleCount = ceil(total / SAMPLE_STEP).toInt()
        val out = ArrayList<DoubleVector>(sampleCount + 1)
        var srcSegIdx = 0
        var anchorIdx = 0
        for (sampleIdx in 0..sampleCount) {
            val arcLen = (sampleIdx.toDouble() / sampleCount) * total

            while (srcSegIdx < points.size - 2 && cumLen[srcSegIdx + 1] < arcLen) srcSegIdx++
            val seg = points[srcSegIdx + 1].subtract(points[srcSegIdx])
            val segLen = cumLen[srcSegIdx + 1] - cumLen[srcSegIdx]
            val srcFrac = if (segLen > 0.0) (arcLen - cumLen[srcSegIdx]) / segLen else 0.0
            val base = points[srcSegIdx].add(seg.mul(srcFrac))
            val normal = if (segLen > 0.0) seg.mul(1.0 / segLen).orthogonal() else DoubleVector(0.0, 0.0)

            while (anchorIdx < anchorArcLen.size - 2 && anchorArcLen[anchorIdx + 1] < arcLen) anchorIdx++
            val anchorSpan = anchorArcLen[anchorIdx + 1] - anchorArcLen[anchorIdx]
            val anchorFrac = if (anchorSpan > 0.0) (arcLen - anchorArcLen[anchorIdx]) / anchorSpan else 0.0
            val weight = 0.5 - 0.5 * cos(PI * anchorFrac)
            val offset = anchorOffset[anchorIdx] + (anchorOffset[anchorIdx + 1] - anchorOffset[anchorIdx]) * weight

            out.add(base.add(normal.mul(offset)))
        }
        return out
    }
}