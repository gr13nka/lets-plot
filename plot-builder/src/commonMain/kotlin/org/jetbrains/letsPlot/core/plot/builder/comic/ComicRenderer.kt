/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.primitive.FillStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.TextStyle

class ComicRenderer(
    private val inner: Renderer,
    private val wobble: ComicStylize,
) : Renderer {

    override fun drawLine(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle) {
        inner.drawPath(wobble.apply(listOf(p1, p2)), stroke, closed = false)
    }

    override fun drawPath(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean) =
        inner.drawPath(points, stroke, fill, closed)

    override fun drawRect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle?, radius: Double) =
        inner.drawRect(rect, stroke, fill, radius)

    override fun drawCircle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle?) =
        inner.drawCircle(center, radius, stroke, fill)

    override fun drawText(origin: DoubleVector, text: String, style: TextStyle) =
        inner.drawText(origin, text, style)
}
