/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.svg.Label
import org.jetbrains.letsPlot.core.plot.base.render.svg.StrokeDashArraySupport
import org.jetbrains.letsPlot.core.plot.base.render.svg.cardinalString
import org.jetbrains.letsPlot.core.plot.base.render.svg.lineString
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SvgRenderer(private val smooth: Boolean = false) : Renderer {
    override fun line(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle): SvgNode {
        val el = SvgLineElement(p1.x, p1.y, p2.x, p2.y)
        applyStroke(el, stroke)
        return el
    }

    override fun path(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean): SvgNode =
        emitPath(points, stroke, fill, closed, smooth = smooth)

    // Always angular (never cardinal) so the arrowhead tip stays sharp.
    override fun arrowhead(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean): SvgNode =
        emitPath(points, stroke, fill, closed, smooth = false)

    private fun emitPath(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean, smooth: Boolean): SvgNode {
        val builder = if (smooth) {
            SvgPathDataBuilder().cardinalString(points)
        } else {
            SvgPathDataBuilder().lineString(points)
        }
        if (closed) builder.closePath()
        val el = SvgPathElement(builder.build())
        if (stroke != null) {
            applyStroke(el, stroke)
            // miterLimit is path-only (SvgShape has no such attribute).
            stroke.miterLimit?.let { el.strokeMiterLimit().set(it) }
        }
        // SvgPathElement has no fill default, SVG would fall back to black and fill the path
        // interior (open or closed alike), painting over other geoms.
        if (fill != null) applyFill(el, fill) else el.fill().set(SvgColors.NONE)
        return el
    }

    override fun rect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle?, radius: Double): SvgNode {
        return if (radius <= 0.0) {
            SvgRectElement(rect).also { applyStyles(it, stroke, fill) }
        } else {
            // SvgRectElement has no rounded-corner attribute, so emit a path for radius > 0.
            SvgPathElement(roundedRectData(rect, radius).build()).also { applyStyles(it, stroke, fill) }
        }
    }

    override fun circle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle?): SvgNode {
        val el = SvgCircleElement(center, radius)
        applyStyles(el, stroke, fill)
        return el
    }

    override fun polygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle?): SvgNode {
        // One path with a closed subpath per ring, SVG even-odd fill then cuts holes.
        val builder = SvgPathDataBuilder()
        for (ring in rings) {
            if (smooth) builder.cardinalString(ring) else builder.lineString(ring)
            builder.closePath()
        }
        val el = SvgPathElement(builder.build())
        applyStyles(el, stroke, fill)
        return el
    }

    override fun sector(center: DoubleVector, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double, fill: FillStyle?): SvgNode {
        val end = endAngle - fullCircleFix(startAngle, endAngle)
        val largeArc = endAngle - startAngle > PI
        val builder = SvgPathDataBuilder().apply {
            moveTo(arcPoint(center, innerRadius, startAngle))
            lineTo(arcPoint(center, outerRadius, startAngle))
            ellipticalArc(outerRadius, outerRadius, 0.0, largeArc, sweep = true, to = arcPoint(center, outerRadius, end))
            lineTo(arcPoint(center, innerRadius, end))
            ellipticalArc(innerRadius, innerRadius, 0.0, largeArc, sweep = false, to = arcPoint(center, innerRadius, startAngle))
            closePath()
        }
        val el = SvgPathElement(builder.build())
        applyStyles(el, stroke = null, fill = fill)
        return el
    }

    override fun arc(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double, stroke: StrokeStyle): SvgNode {
        val end = endAngle - fullCircleFix(startAngle, endAngle)
        val largeArc = endAngle - startAngle > PI
        val builder = SvgPathDataBuilder().apply {
            moveTo(arcPoint(center, radius, startAngle))
            ellipticalArc(radius, radius, 0.0, largeArc, sweep = true, to = arcPoint(center, radius, end))
        }
        val el = SvgPathElement(builder.build())
        applyStroke(el, stroke)
        el.fill().set(SvgColors.NONE)
        return el
    }

    override fun text(origin: DoubleVector, text: String, style: TextStyle): SvgNode {
        // Reuse Label (the RichText-backed component the text geoms build) for multi-line text, the
        // font-family registry and canvas-safe text attributes. The only Renderer-side text emission.
        val label = Label(text)
        label.textColor().set(style.color)
        style.alpha?.let { label.setTextOpacity(it) }
        label.setFontSize(style.sizePx)
        style.lineHeight?.let { label.setLineHeight(it) }
        style.family?.let { label.setFontFamily(it) }
        style.face?.let {
            if (it.bold) label.setFontWeight("bold")
            if (it.italic) label.setFontStyle("italic")
        }
        label.setHorizontalAnchor(style.hAnchor)
        label.setVerticalAnchor(style.vAnchor)
        label.moveTo(origin)
        return label.rootGroup
    }

    private fun arcPoint(center: DoubleVector, radius: Double, angle: Double) =
        DoubleVector(center.x + radius * cos(angle), center.y + radius * sin(angle))

    private fun fullCircleFix(startAngle: Double, endAngle: Double) =
        if ((endAngle - startAngle) % (2 * PI) == 0.0) 0.0001 else 0.0

    private fun applyStyles(shape: SvgShape, stroke: StrokeStyle?, fill: FillStyle?) {
        if (stroke != null) applyStroke(shape, stroke)
        // Default a missing fill to NONE, otherwise SVG paints the interior black.
        if (fill != null) applyFill(shape, fill) else shape.fill().set(SvgColors.NONE)
    }

    private fun roundedRectData(rect: DoubleRectangle, radius: Double) =
        SvgPathDataBuilder().apply {
            with(rect) {
                val r = minOf(radius, width / 2, height / 2)
                moveTo(right - r, bottom)
                curveTo(right - r, bottom, right, bottom, right, bottom - r)
                lineTo(right, top + r)
                curveTo(right, top + r, right, top, right - r, top)
                lineTo(left + r, top)
                curveTo(left + r, top, left, top, left, top + r)
                lineTo(left, bottom - r)
                curveTo(left, bottom - r, left, bottom, left + r, bottom)
                closePath()
            }
        }

    private fun applyStroke(shape: SvgShape, s: StrokeStyle) {
        shape.strokeColor().set(s.color)
        s.alpha?.let { shape.strokeOpacity().set(it) }
        shape.strokeWidth().set(s.width)
        StrokeDashArraySupport.apply(shape, s.width, s.lineType)
    }

    private fun applyFill(shape: SvgShape, f: FillStyle) {
        shape.fillColor().set(f.color)
        f.alpha?.let { shape.fillOpacity().set(it) }
    }
}
