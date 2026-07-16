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
        val helper = geomHelper.createLineGeometryHelper()

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
                root.add(ctx.renderer.line(c1, c2, strokeFor(p), seed = p.index()))
                tooltipHelper.addLine(listOf(c1, c2), p)
            } else {
                // Non-linear coords: a constant-x line is curved in client space, so resample into a
                // path (renderer.line is straight-only).
                val geometry = helper.createLineGeometry(start, end, p) ?: continue
                tooltipHelper.addLine(geometry, p)
                root.add(ctx.renderer.path(geometry, strokeFor(p), closed = false, seed = p.index()))
            }
        }
    }

    companion object {
        const val HANDLES_GROUPS = false
        val LEGEND_KEY_ELEMENT_FACTORY: LegendKeyElementFactory = VLineLegendKeyElementFactory()
    }
}
