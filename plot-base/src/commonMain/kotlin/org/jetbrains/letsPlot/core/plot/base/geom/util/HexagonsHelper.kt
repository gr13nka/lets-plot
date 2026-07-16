/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler.Companion.resample
import org.jetbrains.letsPlot.core.commons.geometry.PolylineSimplifier
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.util.HintColorUtil.createColorMarkerMapper
import org.jetbrains.letsPlot.core.plot.base.tooltip.GeomTargetCollector
import org.jetbrains.letsPlot.core.plot.base.tooltip.TipLayoutHint.Kind.CURSOR_TOOLTIP

class HexagonsHelper(
    private val myAesthetics: Aesthetics,
    pos: PositionAdjustment,
    coord: CoordinateSystem,
    ctx: GeomContext,
    private val geometryFactory: (DataPointAesthetics) -> List<DoubleVector>?
) : LinesHelper(pos, coord, ctx) {
    // Client-space hexagon outlines as PolylineData (closed) for the Renderer. Each hexagon is a
    // filled, opaque-bordered polygon, so the geom draws it via renderer.path(points, stroke, fill, closed).
    fun createHexagonData(): List<PolylineData> {
        val pointCount = myAesthetics.dataPointCount()
        val hexagons = mutableListOf<PolylineData>()

        for (index in 0 until pointCount) {
            val p = myAesthetics.dataPointAt(index)
            val hex = geometryFactory(p) ?: continue

            val clientHex = if (myResamplingEnabled) {
                // Resampling of a tiny hexagon still can produce a very small polygon - simplify it.
                simplifySubPixelJitter(resampleToClient(hex, p))
            } else {
                // Correct hexagon should have 7 points, including the closing one.
                hex.mapNotNull { toClient(it, p) }.takeIf { it.size == 7 } ?: continue
            }

            if (clientHex.isEmpty()) continue

            hexagons.add(PolylineData(p, clientHex))
            createTooltips(p, clientHex)
        }
        return hexagons
    }

    private fun createTooltips(p: DataPointAesthetics, hex: List<DoubleVector>) {
        ctx.targetCollector.addPolygon(
            hex,
            p.index(),
            GeomTargetCollector.TooltipParams(
                markerColors = createColorMarkerMapper(null, ctx)(p)
            ),
            tooltipKind = CURSOR_TOOLTIP
        )
    }
}