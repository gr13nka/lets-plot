/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.svg

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder

fun SvgPathDataBuilder.lineString(points: List<DoubleVector>): SvgPathDataBuilder {
    if (points.isEmpty()) return this

    moveTo(points.first())
    points.asSequence().drop(1).forEach(::lineTo)
    return this
}

// Smooths a polyline using only cubic curves (moveTo + curveTo), so the hand-rolled raster path parser
// (no Q/S support) can replay it.
fun SvgPathDataBuilder.cardinalString(points: List<DoubleVector>): SvgPathDataBuilder {
    if (points.size < 3) return lineString(points)

    fun midpoint(p: DoubleVector, q: DoubleVector) = p.add(q).mul(0.5)
    fun quadAsCubic(start: DoubleVector, control: DoubleVector, end: DoubleVector) {
        curveTo(
            start.add(control.subtract(start).mul(2.0 / 3.0)),
            end.add(control.subtract(end).mul(2.0 / 3.0)),
            end
        )
    }

    moveTo(points.first())
    var start = midpoint(points[0], points[1])
    lineTo(start)
    for (i in 1 until points.size - 1) {
        val end = if (i == points.size - 2) points.last() else midpoint(points[i], points[i + 1])
        quadAsCubic(start, points[i], end)
        start = end
    }
    return this
}
