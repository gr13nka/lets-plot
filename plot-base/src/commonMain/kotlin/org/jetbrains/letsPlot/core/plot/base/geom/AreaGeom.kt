/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom


import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil.TO_LOCATION_X_Y
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil.TO_LOCATION_X_ZERO
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil.TO_LOCATION_X_ZERO_WITH_FINITE_Y
import org.jetbrains.letsPlot.core.plot.base.geom.util.LinesHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.QuantilesHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.TargetCollectorHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.fillFor
import org.jetbrains.letsPlot.core.plot.base.geom.util.strokeFor
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.stat.DensityStat

open class AreaGeom : GeomBase() {
    var quantiles: List<Double> = DensityStat.DEF_QUANTILES
    var quantileLines: Boolean = DEF_QUANTILE_LINES
    var flat: Boolean = false

    override fun rangeIncludesZero(aes: Aes<*>): Boolean = (aes == Aes.Y)

    override fun prepareDataPoints(dataPoints: Iterable<DataPointAesthetics>): Iterable<DataPointAesthetics> {
        val data = GeomUtil.with_X(dataPoints)
        return GeomUtil.ordered_X(data)
    }

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val helper = LinesHelper(pos, coord, ctx)
        helper.setResamplingEnabled(!coord.isLinear && !flat)

        val renderer = ctx.rendererFactory(root)
        val quantilesHelper = QuantilesHelper(pos, coord, ctx, quantiles)
        val targetCollectorHelper = TargetCollectorHelper(tooltipsGeomKind(), ctx)

        val dataPoints = dataPoints(aesthetics)
        val closePath = helper.meetsRadarPlotReq()
        dataPoints.sortedByDescending(DataPointAesthetics::group).groupBy(DataPointAesthetics::group)
            .forEach { (_, groupDataPoints) ->
                quantilesHelper.splitByQuantiles(groupDataPoints, Aes.X).forEach { points ->
                    val bands = helper.createBandData(
                        points,
                        TO_LOCATION_X_Y,
                        TO_LOCATION_X_ZERO_WITH_FINITE_Y,
                        closePath = closePath
                    )
                    for (band in bands) {
                        renderer.drawPath(band.coordinates, stroke = null, fill = fillFor(band.aes), closed = true)
                    }

                    val upperPoints = helper.createPathData(points, TO_LOCATION_X_Y, closePath)
                    for (line in upperPoints) {
                        renderer.drawPath(line.coordinates, strokeFor(line.aes, applyAlpha = false), closed = false)
                    }
                    targetCollectorHelper.addVariadicPaths(upperPoints)
                }

                if (quantileLines) {
                    createQuantileLines(groupDataPoints, quantilesHelper).forEach { (p, geometry) ->
                        renderer.drawPath(geometry, strokeFor(p, applyAlpha = false), closed = false)
                    }
                }
            }
    }

    private fun createQuantileLines(
        dataPoints: Iterable<DataPointAesthetics>,
        quantilesHelper: QuantilesHelper
    ): List<Pair<DataPointAesthetics, List<DoubleVector>>> {
        val toLocationBoundStart: (DataPointAesthetics) -> DoubleVector = { p -> TO_LOCATION_X_Y(p)!! }
        val toLocationBoundEnd: (DataPointAesthetics) -> DoubleVector = { p -> TO_LOCATION_X_ZERO(p)!! }
        return quantilesHelper.getQuantileLineSegments(dataPoints, Aes.X, toLocationBoundStart, toLocationBoundEnd)
    }

    protected open fun tooltipsGeomKind() = GeomKind.AREA

    companion object {
        const val DEF_QUANTILE_LINES = false
        const val HANDLES_GROUPS = true
    }
}
