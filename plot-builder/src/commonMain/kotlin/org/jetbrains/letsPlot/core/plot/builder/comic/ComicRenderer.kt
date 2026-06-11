/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.render.primitive.FillStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.SvgRenderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.TextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class ComicRenderer(root: SvgRoot, private val wobble: ComicStylize) : Renderer {
    private val inner = SvgRenderer(root, smooth = true)

    override fun drawLine(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle) {
        inner.drawPath(wobble.apply(listOf(p1, p2)), stroke, closed = false)
    }

    override fun drawPath(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean) =
        inner.drawPath(wobble.apply(points), stroke, fill, closed)

    override fun drawArrowhead(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean) =
        inner.drawArrowhead(points, stroke, fill, closed)

    override fun drawRect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle?, radius: Double) =
        // Wobbled closed path, corner radius dropped (comic rects are hand-drawn).
        inner.drawPath(wobble.apply(rect.points), stroke, fill, closed = true)

    override fun drawCircle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle?) =
        inner.drawPath(wobble.apply(circleOutline(center, radius)), stroke, fill, closed = true)

    override fun drawPolygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle?) =
        // Wobble each ring independently so holes are preserved, inner keeps them in one path.
        inner.drawPolygon(rings.map { wobble.apply(it) }, stroke, fill)

    override fun drawSector(center: DoubleVector, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double, fill: FillStyle?) =
        inner.drawPath(wobble.apply(sectorOutline(center, innerRadius, outerRadius, startAngle, endAngle)), stroke = null, fill = fill, closed = true)

    override fun drawArc(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double, stroke: StrokeStyle) =
        // Trace the arc as a wobbled open path.
        inner.drawPath(wobble.apply(arcPoints(center, radius, startAngle, endAngle)), stroke, closed = false)

    override fun drawText(origin: DoubleVector, text: String, style: TextStyle) =
        inner.drawText(origin, text, style.copy(family = wobble.fontFamily))

    // Outer arc forward, then back along the inner arc (or to the center tip when there is no hole),
    // the radii are the straight hops drawPath draws between those two runs of points.
    private fun sectorOutline(center: DoubleVector, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double): List<DoubleVector> {
        val outer = arcPoints(center, outerRadius, startAngle, endAngle)
        return when {
            // A full sweep with no hole is a disc, adding the center tip would draw a spike into the middle.
            innerRadius <= 0.0 && abs(endAngle - startAngle) >= 2.0 * PI - FULL_SWEEP_EPS -> outer
            innerRadius <= 0.0 -> outer + center
            else -> outer + arcPoints(center, innerRadius, endAngle, startAngle)
        }
    }

    private fun circleOutline(center: DoubleVector, radius: Double): List<DoubleVector> =
        arcPoints(center, radius, 0.0, 2.0 * PI)

    // Segment count scales with arc length so large circles/arcs aren't faceted. No AdaptiveResampler:
    // the wobble amplitude dwarfs sub-pixel sampling error, so a plain angular step is enough.
    private fun arcPoints(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double): List<DoubleVector> {
        val segments = max(8, ceil(abs(radius * (endAngle - startAngle)) / SAMPLE_LENGTH_PX).toInt())
        return (0..segments).map { i ->
            val a = startAngle + (endAngle - startAngle) * i / segments
            DoubleVector(center.x + radius * cos(a), center.y + radius * sin(a))
        }
    }

    companion object {
        private const val SAMPLE_LENGTH_PX = 4.0
        private const val FULL_SWEEP_EPS = 1e-6
    }
}
