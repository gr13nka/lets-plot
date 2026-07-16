/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.svg.Label
import org.jetbrains.letsPlot.core.plot.base.render.svg.StrokeDashArraySupport
import org.jetbrains.letsPlot.core.plot.base.render.svg.smoothString
import org.jetbrains.letsPlot.core.plot.base.render.svg.lineString
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape

// The SVG-emitting backend: the single bridge from render primitives to the SVG datamodel — the only
// place that constructs concrete Svg*Element / path-data. CrispRenderer draws through this directly;
// DistortionRenderer draws through it after transforming the outline.
// `smooth` (straight segments vs. cubic-bezier paths) is an internal knob set only by DistortionRenderer.
// `seed` is ignored here: this backend draws plain — only a distortion style consumes it.
internal class SvgNodeRenderer(private val smooth: Boolean = false) : Renderer {
    override fun line(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle, seed: Int?): SvgNode {
        val el = SvgLineElement(p1.x, p1.y, p2.x, p2.y)
        el.applyStroke(stroke)
        return el
    }

    override fun path(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean, seed: Int?): SvgPathElement {
        val builder = if (smooth) {
            SvgPathDataBuilder().smoothString(points)
        } else {
            SvgPathDataBuilder().lineString(points)
        }
        if (closed) builder.closePath()
        val el = SvgPathElement(builder.build())
        el.applyStyles(stroke, fill)
        // miterLimit is path-only (SvgShape has no such attribute).
        stroke?.miterLimit?.let { el.strokeMiterLimit().set(it) }
        return el
    }

    override fun rect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle?, seed: Int?): SvgNode =
        SvgRectElement(rect).also { it.applyStyles(stroke, fill) }

    override fun circle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle?, seed: Int?): SvgNode {
        val el = SvgCircleElement(center, radius)
        el.applyStyles(stroke, fill)
        return el
    }

    override fun polygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle?, seed: Int?): SvgNode {
        // One path with a closed subpath per ring, SVG even-odd fill then cuts holes.
        val builder = SvgPathDataBuilder()
        for (ring in rings) {
            if (smooth) builder.smoothString(ring) else builder.lineString(ring)
            builder.closePath()
        }
        val el = SvgPathElement(builder.build())
        el.applyStyles(stroke, fill)
        return el
    }

    override fun text(origin: DoubleVector, text: String, style: TextStyle): SvgNode {
        // Reuse Label (the RichText-backed component the text geoms build) for multi-line text, the
        // font-family registry and canvas-safe text attributes. The only Renderer-side text emission.
        val label = Label(text)
        label.textColor().set(style.color)
        label.setTextOpacity(style.alpha)
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
}
