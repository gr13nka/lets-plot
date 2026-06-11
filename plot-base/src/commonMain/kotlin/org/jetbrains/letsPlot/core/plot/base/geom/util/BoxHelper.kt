/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleSegment
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.datamodel.svg.dom.*

object BoxHelper {
    fun buildBoxes(
        renderer: Renderer,
        aesthetics: Aesthetics,
        clientRectFactory: (DataPointAesthetics) -> DoubleRectangle?
    ) {
        aesthetics.dataPoints().forEach { p ->
            clientRectFactory(p)?.let { renderer.drawRect(it, strokeFor(p, applyAlpha = false), fillFor(p)) }
        }
    }

    // Draws each midline through the Renderer (so comic mode wobbles it). `fatten` scales the stroke
    // width, onMidline reports the client segment for annotations.
    fun buildMidlines(
        renderer: Renderer,
        aesthetics: Aesthetics,
        fatten: Double,
        geomHelper: GeomHelper,
        lineFactory: (DataPointAesthetics) -> DoubleSegment?,
        onMidline: (DataPointAesthetics, DoubleSegment) -> Unit = { _, _ -> }
    ) {
        val elementHelper = geomHelper.createSvgElementHelper()
        aesthetics.dataPoints().forEach { p ->
            lineFactory(p)?.let { segment ->
                val geometry = elementHelper.createLineGeometry(segment.start, segment.end, p) ?: return@let
                renderer.drawPath(geometry, strokeFor(p, applyAlpha = false).copy(width = AesScaling.strokeWidth(p) * fatten), closed = false)
                onMidline(p, DoubleSegment(geometry.first(), geometry.last()))
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

