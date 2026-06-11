/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler.Companion.resample
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.util.ArrowSpec
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil.toLocation
import org.jetbrains.letsPlot.core.plot.base.geom.util.TargetCollectorHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.drawLineWithArrow
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot


class SegmentGeom : GeomBase() {

    var arrowSpec: ArrowSpec? = null
    var animation: Any? = null
    var flat: Boolean = false
    var geodesic: Boolean = false
    var spacer: Double = 0.0    // additional space to shorten segment by moving the start/end

    override val legendKeyElementFactory: LegendKeyElementFactory
        get() = HLineGeom.LEGEND_KEY_ELEMENT_FACTORY

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val tooltipHelper = TargetCollectorHelper(GeomKind.SEGMENT, ctx)
        val geomHelper = GeomHelper(pos, coord, ctx)
        val renderer = ctx.rendererFactory(root)
        val resamplingEnabled = !coord.isLinear && !flat

        for (p in aesthetics.dataPoints()) {
            val start = p.toLocation(Aes.X, Aes.Y) ?: continue
            val end = p.toLocation(Aes.XEND, Aes.YEND) ?: continue

            val clientLine = if (resamplingEnabled) {
                resample(listOf(start, end), AdaptiveResampler.PIXEL_PRECISION) { geomHelper.toClient(it, p) }
            } else {
                listOf(geomHelper.toClient(start, p) ?: continue, geomHelper.toClient(end, p) ?: continue)
            }
            if (clientLine.size < 2) continue

            val geometry = drawLineWithArrow(renderer, clientLine, p, arrowSpec, spacer)
            tooltipHelper.addLine(geometry, p)
        }
    }

    companion object {
        const val HANDLES_GROUPS = false
    }
}
