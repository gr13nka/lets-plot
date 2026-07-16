/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.legend.HLineLegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.TargetCollectorHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.strokeFor
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot

class HLineGeom : GeomBase() {

    override val legendKeyElementFactory: LegendKeyElementFactory
        get() = LEGEND_KEY_ELEMENT_FACTORY

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val tooltipHelper = TargetCollectorHelper(GeomKind.H_LINE, ctx)
        val geomHelper = GeomHelper(pos, coord, ctx)
        val helper = geomHelper.createLineGeometryHelper()

        val viewPort = overallAesBounds(ctx)

        for (p in aesthetics.dataPoints()) {
            val intercept = p.interceptY() ?: continue
            if (intercept !in viewPort.yRange()) continue

            // line
            val start = DoubleVector(viewPort.left, intercept)
            val end = DoubleVector(viewPort.right, intercept)

            if (coord.isLinear) {
                val c1 = geomHelper.toClient(start, p) ?: continue
                val c2 = geomHelper.toClient(end, p) ?: continue
                root.add(ctx.renderer.line(c1, c2, strokeFor(p), seed = p.index()))
                tooltipHelper.addLine(listOf(c1, c2), p)
            } else {
                // Non-linear coords: a constant-y line is curved in client space, so resample into a
                // path (renderer.line is straight-only).
                val linestring = helper.createLineGeometry(start, end, p) ?: continue
                tooltipHelper.addLine(linestring, p)
                root.add(ctx.renderer.path(linestring, strokeFor(p), closed = false, seed = p.index()))
            }
        }
    }

    companion object {
        const val HANDLES_GROUPS = false
        val LEGEND_KEY_ELEMENT_FACTORY: LegendKeyElementFactory = HLineLegendKeyElementFactory()
    }
}
