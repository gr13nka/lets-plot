/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleSegment
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.interval.DoubleSpan
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.annotation.BarAnnotation
import org.jetbrains.letsPlot.core.plot.base.geom.annotation.CrossBarAnnotation
import org.jetbrains.letsPlot.core.plot.base.geom.util.BoxHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.HintColorUtil
import org.jetbrains.letsPlot.core.plot.base.geom.util.RectangleTooltipHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.SvgRectHelper
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.tooltip.TipLayoutHint

class CrossBarGeom : GeomBase(), WithWidth {

    var fattenMidline: Double = 2.5
    var widthUnit: DimensionUnit = DEF_WIDTH_UNIT

    override val legendKeyElementFactory: LegendKeyElementFactory
        get() = BoxHelper.legendFactory(whiskers = false, showMidline = fattenMidline != 0.0)

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val geomHelper = GeomHelper(pos, coord, ctx)
        val tooltipHelper = RectangleTooltipHelper(
            pos = pos,
            coord = coord,
            ctx = ctx,
            hintAesList = listOf(Aes.YMIN, Aes.Y, Aes.YMAX),
            tooltipKind = TipLayoutHint.Kind.CURSOR_TOOLTIP,
            fillColorMapper = { HintColorUtil.colorWithAlpha(it) }
        )

        SvgRectHelper.box(aesthetics, pos, coord, ctx, rectByDataPoint(geomHelper))
            .drawTo(root, tooltipHelper)

        val midLines = HashMap<Int, DoubleSegment>()
        BoxHelper.buildMidlines(
            aesthetics,
            fatten = fattenMidline,
            geomHelper,
            midLineByDataPoint(geomHelper)
        ) { aes, svgNode, segment ->
            root.add(svgNode)
            midLines[aes.index()] = segment
        }

        ctx.annotation?.let {
            // Annotations need the client-space rectangles; the rect drawing above doesn't expose them,
            // so compute them here (only when annotations are enabled).
            val rectFactory = rectByDataPoint(geomHelper)
            val rectangles = HashMap<DataPointAesthetics, DoubleRectangle>()
            aesthetics.dataPoints().forEach { p ->
                rectFactory(p)?.let { geomHelper.toClient(it, p) }?.let { rectangles[p] = it }
            }
            CrossBarAnnotation.build(
                root,
                rectangles,
                midLines,
                fattenMidline,
                coord,
                ctx
            )
        }
    }

    override fun widthSpan(
        p: DataPointAesthetics,
        coordAes: Aes<Double>,
        resolution: Double,
        isDiscrete: Boolean
    ): DoubleSpan? {
        return DimensionsUtil.dimensionSpan(p, coordAes, Aes.WIDTH, resolution, widthUnit)
    }

    private fun rectByDataPoint(geomHelper: GeomHelper): (DataPointAesthetics) -> DoubleRectangle? {
        fun factory(p: DataPointAesthetics): DoubleRectangle? {
            val x = p.finiteOrNull(Aes.X) ?: return null
            val ymin = p.finiteOrNull(Aes.YMIN) ?: return null
            val ymax = p.finiteOrNull(Aes.YMAX) ?: return null
            val w = p.finiteOrNull(Aes.WIDTH) ?: return null

            val width = w * geomHelper.getUnitResolution(widthUnit, Aes.X)
            val origin = DoubleVector(x - width / 2, ymin)
            val dimension = DoubleVector(width, ymax - ymin)
            return DoubleRectangle(origin, dimension)
        }

        return ::factory
    }

    private fun midLineByDataPoint(geomHelper: GeomHelper): (DataPointAesthetics) -> DoubleSegment? {
        fun factory(p: DataPointAesthetics): DoubleSegment? {
            val x = p.finiteOrNull(Aes.X) ?: return null
            val y = p.finiteOrNull(Aes.Y) ?: return null
            val w = p.finiteOrNull(Aes.WIDTH) ?: return null

            val width = w * geomHelper.getUnitResolution(widthUnit, Aes.X)

            return DoubleSegment(
                DoubleVector(x - width / 2, y),
                DoubleVector(x + width / 2, y)
            )
        }

        return ::factory
    }

    companion object {
        const val HANDLES_GROUPS = false
        private val DEF_WIDTH_UNIT: DimensionUnit = DimensionUnit.RESOLUTION
    }
}
