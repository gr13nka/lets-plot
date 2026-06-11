/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.legend.VLineLegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.TargetCollectorHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.strokeFor
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot

class VLineGeom : GeomBase() {

    override val legendKeyElementFactory: LegendKeyElementFactory
        get() = LEGEND_KEY_ELEMENT_FACTORY

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val tooltipHelper = TargetCollectorHelper(GeomKind.V_LINE, ctx)
        val geomHelper = GeomHelper(pos, coord, ctx)
        val helper = geomHelper.createSvgElementHelper()
        helper.setStrokeAlphaEnabled(true)
        helper.setResamplingEnabled(!coord.isLinear)
        val renderer = ctx.rendererFactory(root)

        val viewPort = overallAesBounds(ctx)

        for (p in aesthetics.dataPoints()) {
            val intercept = p.interceptX() ?: continue
            if (intercept !in viewPort.xRange()) continue

            // line
            val start = DoubleVector(intercept, viewPort.top)
            val end = DoubleVector(intercept, viewPort.bottom)

            if (coord.isLinear) {
                val c1 = geomHelper.toClient(start, p) ?: continue
                val c2 = geomHelper.toClient(end, p) ?: continue
                renderer.drawLine(c1, c2, strokeFor(p))
                tooltipHelper.addLine(listOf(c1, c2), p)
            } else {
                // Non-linear coords: a constant-x line is curved in client space, so resample into a
                // path (drawLine is linear-only).
                val geometry = helper.createLineGeometry(start, end, p) ?: continue
                tooltipHelper.addLine(geometry, p)
                renderer.drawPath(geometry, strokeFor(p), closed = false)
            }
        }
    }

    companion object {
        const val HANDLES_GROUPS = false
        val LEGEND_KEY_ELEMENT_FACTORY: LegendKeyElementFactory = VLineLegendKeyElementFactory()
    }
}
