/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comix

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.ComicStylize
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

class SineWobble(private val amplitude: Double = 1.5) : ComicStylize {
    override fun apply(points: List<DoubleVector>): List<DoubleVector> {
        if (points.size < 2) return points

        val cumLen = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumLen[i] = cumLen[i - 1] + points[i].subtract(points[i - 1]).length()
        }
        val totalLen = cumLen[points.size - 1]
        if (totalLen < 2.0 * SAMPLE_STEP) return points

        val seedPhase = points.first().x * 0.13 + points.first().y * 0.07

        val m = ceil(totalLen / SAMPLE_STEP).toInt()
        val out = ArrayList<DoubleVector>(m + 1)

        var j = 0
        for (k in 0..m) {
            val s = (k.toDouble() / m) * totalLen
            while (j < points.size - 2 && cumLen[j + 1] < s) j++

            val segLen = cumLen[j + 1] - cumLen[j]
            val t = if (segLen > 0.0) (s - cumLen[j]) / segLen else 0.0
            val seg = points[j + 1].subtract(points[j])
            val base = points[j].add(seg.mul(t))
            val tangent = if (segLen > 0.0) seg.mul(1.0 / segLen) else DoubleVector(1.0, 0.0)
            val normal = tangent.orthogonal()

            val f = sin(K1 * s + seedPhase) + 0.5 * sin(K2 * s + PHI2 + seedPhase)
            val taper = smoothstep(s / TAPER_LEN) * smoothstep((totalLen - s) / TAPER_LEN)

            out.add(base.add(normal.mul(amplitude * f * taper)))
        }
        return out
    }

    private fun smoothstep(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3.0 - 2.0 * c)
    }

    companion object {
        private const val SAMPLE_STEP = 2.0
        private const val WAVELENGTH = 150.0
        private val K1 = 2.0 * PI / WAVELENGTH
        private val K2 = K1 * 1.7
        private const val PHI2 = 1.3
        private const val TAPER_LEN = 8.0
    }
}
