/*
 * Copyright (c) 2024. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.interval.DoubleSpan
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.HintColorUtil
import org.jetbrains.letsPlot.core.plot.base.geom.util.HintsCollection
import org.jetbrains.letsPlot.core.plot.base.geom.util.fillFor
import org.jetbrains.letsPlot.core.plot.base.geom.util.strokeFor
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.tooltip.GeomTargetCollector

/*
  For this geometry 'isVertical' means that it has vertical bounds: ymin and ymax.

  Instead of using this parameter, 'band' could be added to the list of orientable geoms in the LayerConfig::isOrientationApplicable(),
  but it's hard to get the correct behavior with polar coordinates that way.
*/
class BandGeom(private val isVertical: Boolean) : GeomBase() {
    private val yMinAes = if (isVertical) Aes.YMIN else Aes.XMIN
    private val yMaxAes = if (isVertical) Aes.YMAX else Aes.XMAX

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val geomHelper = GeomHelper(pos, coord, ctx)
        val renderer = ctx.rendererFactory(root)
        val svgHelper = GeomHelper(pos, coord, ctx).createSvgElementHelper()
            .setResamplingEnabled(!coord.isLinear)

        val viewPort = overallAesBounds(ctx).flipIf(!isVertical)

        for (p in aesthetics.dataPoints()) {
            val yMin = p.finiteOrNull(yMinAes) ?: continue
            val yMax = p.finiteOrNull(yMaxAes) ?: continue
            if (yMin > yMax) continue
            val rect = DoubleRectangle.hvRange(viewPort.xRange(), DoubleSpan(yMin, yMax))
            val (topSide, _, _, bottomSide) = rect.parts.toList()

            svgHelper.createLineGeometry(rect.flipIf(!isVertical).points, p)?.let { ring ->
                renderer.drawPath(ring, stroke = null, fill = fillFor(p), closed = true)
            }
            val topSeg = topSide.flipIf(!isVertical)
            svgHelper.createLineGeometry(topSeg.start, topSeg.end, p)?.let { line ->
                renderer.drawPath(line, strokeFor(p, applyAlpha = false), closed = false)
            }
            val bottomSeg = bottomSide.flipIf(!isVertical)
            svgHelper.createLineGeometry(bottomSeg.start, bottomSeg.end, p)?.let { line ->
                renderer.drawPath(line, strokeFor(p, applyAlpha = false), closed = false)
            }

            // tooltip
            val tooltipParams = GeomTargetCollector.TooltipParams(
                tipLayoutHints = HintsCollection(p, geomHelper).hints,
                markerColors = HintColorUtil.createColorMarkerMapper(GeomKind.BAND, ctx)(p)
            )

            geomHelper.toClient(rect.flipIf(!isVertical), p)?.let { r ->
                ctx.targetCollector.addPolygon(r.points, p.index(), tooltipParams)
            }
        }
    }

    companion object {
        const val HANDLES_GROUPS = false
    }
}