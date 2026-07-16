/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.geometry.DoubleSegment
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.geom.DimensionUnit
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.datamodel.svg.dom.*

object BoxHelper {
    // The full box width along X in client px: WIDTH aes scaled by the width-unit resolution
    fun boxWidth(p: DataPointAesthetics, widthUnit: DimensionUnit, geomHelper: GeomHelper): Double? =
        p.finiteOrNull(Aes.WIDTH)?.let { it * geomHelper.getUnitResolution(widthUnit, Aes.X) }

    fun buildStraightMidlines(
        root: SvgRoot,
        aesthetics: Aesthetics,
        widthUnit: DimensionUnit,
        geomHelper: GeomHelper,
        fatten: Double
    ) {
        // Boxplot midline: the box is a px-space clientRect and cannot curve, so the midline stays
        // straight to match. (buildCurvedMidlines below follows the coord, arcing with the crossbar's box.)
        val elementHelper = geomHelper.createLineGeometryHelper().withoutResampling()
        for (p in aesthetics.dataPoints()) {
            val x = p.finiteOrNull(Aes.X) ?: continue
            val middle = p.finiteOrNull(Aes.MIDDLE) ?: continue
            val width = boxWidth(p, widthUnit, geomHelper) ?: continue

            val geometry = elementHelper.createLineGeometry(
                DoubleVector(x - width / 2, middle),
                DoubleVector(x + width / 2, middle),
                p
            ) ?: continue

            val stroke = outlineStrokeFor(p).copy(width = AesScaling.strokeWidth(p) * fatten)
            root.add(geomHelper.ctx.renderer.path(geometry, stroke, closed = false, seed = p.index()))
        }
    }

    fun buildCurvedMidlines(
        aesthetics: Aesthetics,
        fatten: Double,
        geomHelper: GeomHelper,
        lineFactory: (DataPointAesthetics) -> DoubleSegment?,
        handler: (DataPointAesthetics, SvgNode, DoubleSegment) -> Unit
    ) {
        val elementHelper = geomHelper.createLineGeometryHelper()
        aesthetics.dataPoints().forEach { p ->
            lineFactory(p)?.let { segment ->
                val line = elementHelper.createPaddedLineGeometry(segment.start, segment.end, p)
                    ?: return@let
                val stroke = outlineStrokeFor(p) { AesScaling.strokeWidth(it) * fatten }
                handler(p, lineNode(geomHelper.ctx.renderer, line, stroke, seed = p.index()), DoubleSegment(line[0], line[1]))
            }
        }
    }

    fun legendFactory(whiskers: Boolean, showMidline: Boolean): LegendKeyElementFactory =
        BoxLegendKeyElementFactory(whiskers, showMidline)
}

private class BoxLegendKeyElementFactory(val whiskers: Boolean, val showMidline: Boolean) :
    LegendKeyElementFactory {

    override fun createKeyElement(p: DataPointAesthetics, size: DoubleVector): SvgGElement {
        val whiskerSize = .2

        val strokeWidth = AesScaling.strokeWidth(p)
        val width = (size.x - strokeWidth) * .8 // a bit narrower
        val height = size.y - strokeWidth
        val x = (size.x - width) / 2
        val y = strokeWidth / 2


        // box
        var boxHeight = height
        var boxY = y
        if (whiskers) {
            boxHeight = height * (1 - 2 * whiskerSize)
            boxY = y + height * whiskerSize
        }

        val rect = SvgRectElement(
            x,
            boxY,
            width,
            boxHeight
        )
        GeomHelper.decorate(rect, p)

        // lines
        val middleY = y + height * .5
        val middle = SvgLineElement(x, middleY, x + width, middleY)
        GeomHelper.decorate(middle, p)

        val g = SvgGElement()
        g.children().add(rect)
        if (showMidline) {
            g.children().add(middle)
        }

        if (whiskers) {
            val middleX = x + width * .5
            val lowerWhisker =
                SvgLineElement(middleX, y + height * (1 - whiskerSize), middleX, y + height)
            GeomHelper.decorate(lowerWhisker, p)
            val upperWhisker = SvgLineElement(middleX, y, middleX, y + height * whiskerSize)
            GeomHelper.decorate(upperWhisker, p)
            g.children().add(lowerWhisker)
            g.children().add(upperWhisker)
        }

        return g
    }
}

