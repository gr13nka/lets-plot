/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil
import org.jetbrains.letsPlot.core.plot.base.geom.util.LinesHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.QuantilesHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.TargetCollectorHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.fillFor
import org.jetbrains.letsPlot.core.plot.base.geom.util.strokeFor
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.stat.BaseYDensityStat

class ViolinGeom : GeomBase() {
    var quantiles: List<Double> = BaseYDensityStat.DEF_QUANTILES
    var quantileLines: Boolean = DEF_QUANTILE_LINES
    var showHalf: Double = DEF_SHOW_HALF
    private val negativeSign: Double
        get() = if (showHalf > 0.0) 0.0 else -1.0
    private val positiveSign: Double
        get() = if (showHalf < 0.0) 0.0 else 1.0

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        buildLines(root, aesthetics, pos, coord, ctx)
    }

    private fun buildLines(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        GeomUtil.withDefined(aesthetics.dataPoints(), Aes.X, Aes.Y, Aes.VIOLINWIDTH, Aes.WIDTH)
            .groupBy(DataPointAesthetics::x)
            .map { (x, nonOrderedPoints) -> x to GeomUtil.ordered_Y(nonOrderedPoints, false) }
            .forEach { (_, dataPoints) -> buildViolin(root, dataPoints, pos, coord, ctx) }
    }

    private fun buildViolin(
        root: SvgRoot,
        dataPoints: Iterable<DataPointAesthetics>,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val helper = LinesHelper(pos, coord, ctx)
        val renderer = ctx.rendererFactory(root)
        val quantilesHelper = QuantilesHelper(pos, coord, ctx, quantiles, Aes.X)
        val leftBoundTransform = toLocationBound(negativeSign, ctx)
        val rightBoundTransform = toLocationBound(positiveSign, ctx)

        quantilesHelper.splitByQuantiles(dataPoints, Aes.Y).forEach { points ->
            val bands = helper.createBandData(points, leftBoundTransform, rightBoundTransform)
            for (band in bands) {
                renderer.drawPath(band.coordinates, stroke = null, fill = fillFor(band.aes), closed = true)
            }

            // Left and right edges: opaque-by-aes strokes (the old setAlphaEnabled(false) behavior).
            for (line in helper.createPathData(points, leftBoundTransform)) {
                renderer.drawPath(line.coordinates, strokeFor(line.aes, applyAlpha = false), closed = false)
            }
            for (line in helper.createPathData(points, rightBoundTransform)) {
                renderer.drawPath(line.coordinates, strokeFor(line.aes, applyAlpha = false), closed = false)
            }

            if (showHalf <= 0.0) {
                buildHints(points, ctx, helper, leftBoundTransform)
            }
            if (showHalf >= 0.0) {
                buildHints(points, ctx, helper, rightBoundTransform)
            }
        }

        if (quantileLines) {
            createQuantileLines(dataPoints, quantilesHelper, ctx).forEach { (p, geometry) ->
                renderer.drawPath(geometry, strokeFor(p, applyAlpha = false), closed = false)
            }
        }
    }

    private fun createQuantileLines(
        dataPoints: Iterable<DataPointAesthetics>,
        quantilesHelper: QuantilesHelper,
        ctx: GeomContext
    ): List<Pair<DataPointAesthetics, List<DoubleVector>>> {
        val toLocationBoundStart: (DataPointAesthetics) -> DoubleVector = { p ->
            DoubleVector(toLocationBound(negativeSign, ctx)(p).x, p.y()!!)
        }
        val toLocationBoundEnd: (DataPointAesthetics) -> DoubleVector = { p ->
            DoubleVector(toLocationBound(positiveSign, ctx)(p).x, p.y()!!)
        }
        return quantilesHelper.getQuantileLineSegments(dataPoints, Aes.Y, toLocationBoundStart, toLocationBoundEnd)
    }

    private fun toLocationBound(
        sign: Double,
        ctx: GeomContext
    ): (p: DataPointAesthetics) -> DoubleVector {
        return fun(p: DataPointAesthetics): DoubleVector {
            val x = p.x()!! + ctx.getResolution(Aes.X) / 2 * sign * p.width()!! * p.violinwidth()!!
            val y = p.y()!!
            return DoubleVector(x, y)
        }
    }

    private fun buildHints(
        dataPoints: Iterable<DataPointAesthetics>,
        ctx: GeomContext,
        helper: LinesHelper,
        boundTransform: (p: DataPointAesthetics) -> DoubleVector
    ) {
        val pathDataList = helper.createPaths(dataPoints, boundTransform)
        val targetCollectorHelper = TargetCollectorHelper(GeomKind.VIOLIN, ctx)
        targetCollectorHelper.addPaths(pathDataList)
    }

    companion object {
        const val DEF_QUANTILE_LINES = false
        const val DEF_SHOW_HALF = 0.0

        const val HANDLES_GROUPS = true
    }

}