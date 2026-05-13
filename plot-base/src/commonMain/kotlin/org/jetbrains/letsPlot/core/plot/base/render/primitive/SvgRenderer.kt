/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.aes.AesInitValue
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.render.svg.StrokeDashArraySupport
import org.jetbrains.letsPlot.core.plot.base.render.svg.lineString
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape

class SvgRenderer(private val root: SvgRoot) : Renderer {
    override fun drawLine(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle) {
        val el = SvgLineElement(p1.x, p1.y, p2.x, p2.y)
        applyStroke(el, stroke)
        root.add(el)
    }

    override fun drawPath(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean) {
        val builder = SvgPathDataBuilder().lineString(points)
        if (closed) builder.closePath()
        val el = SvgPathElement(builder.build())
        if (stroke != null) applyStroke(el, stroke)
        if (fill != null) applyFill(el, fill) else el.fill().set(SvgColors.NONE)
        root.add(el)
    }

    override fun drawRect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle?, radius: Double) =
        TODO("SvgRenderer.drawRect")

    override fun drawCircle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle?) =
        TODO("SvgRenderer.drawCircle")

    override fun drawText(origin: DoubleVector, text: String, style: TextStyle): Nothing =
        TODO("SvgRenderer.drawText")

    private fun applyStroke(shape: SvgShape, s: StrokeStyle) {
        shape.strokeColor().set(s.color)
        if (s.alpha != null && s.alpha != AesInitValue.DEFAULT_ALPHA) {
            shape.strokeOpacity().set(s.alpha)
        }
        shape.strokeWidth().set(s.width)
        StrokeDashArraySupport.apply(shape, s.width, s.lineType)
    }

    private fun applyFill(shape: SvgShape, f: FillStyle) {
        shape.fillColor().set(f.color)
        if (f.alpha != null && f.alpha != AesInitValue.DEFAULT_ALPHA) {
            shape.fillOpacity().set(f.alpha)
        }
    }
}
