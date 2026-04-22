/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comix

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.ComicStylize
import kotlin.math.sin

class SineWobble(private val amplitude: Double = 2.0) : ComicStylize {
    override fun apply(points: List<DoubleVector>): List<DoubleVector> {
        if (points.size < 2) return points
        return points.mapIndexed { i, p ->
            val dy = amplitude * sin(i * 0.6)
            DoubleVector(p.x, p.y + dy)
        }
    }
}
