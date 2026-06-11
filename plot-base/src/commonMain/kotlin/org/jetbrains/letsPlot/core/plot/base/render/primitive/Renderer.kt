/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector

interface Renderer {
    fun drawLine(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle)
    fun drawPath(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle? = null, closed: Boolean = false)
    fun drawArrowhead(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle? = null, closed: Boolean = false)
    fun drawRect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle? = null, radius: Double = 0.0)
    fun drawCircle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle? = null)
    fun drawPolygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle? = null)
    fun drawSector(center: DoubleVector, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double, fill: FillStyle?)
    fun drawArc(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double, stroke: StrokeStyle)
    fun drawText(origin: DoubleVector, text: String, style: TextStyle)
}
