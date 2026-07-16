/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.commons.values.FontFace
import org.jetbrains.letsPlot.core.plot.base.render.linetype.LineType
import org.jetbrains.letsPlot.core.plot.base.render.svg.StrokeDashArraySupport
import org.jetbrains.letsPlot.core.plot.base.render.svg.Text
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgUtils
import org.jetbrains.letsPlot.datamodel.svg.dom.slim.SvgSlimShape

// The opacity a color carries on its own: its alpha channel (1.0 for a null color). This is the
// default for every style below, so rgba colors render translucent on every paint path; AesStyling
// layers the `alpha` aesthetic override on top of it.
fun ownOpacity(color: Color?): Double = SvgUtils.alpha2opacity(color?.alpha ?: 255)

data class StrokeStyle(
    val color: Color?,
    val alpha: Double = ownOpacity(color),
    val width: Double,
    val lineType: LineType,
    val miterLimit: Double? = null
)

data class FillStyle(
    val color: Color?,
    val alpha: Double = ownOpacity(color)
)

data class TextStyle(
    val color: Color?,
    val alpha: Double = ownOpacity(color),
    val sizePx: Double,
    val lineHeight: Double?,
    val family: String?,
    val face: FontFace? = null,
    val hAnchor: Text.HorizontalAnchor = Text.HorizontalAnchor.LEFT,
    val vAnchor: Text.VerticalAnchor = Text.VerticalAnchor.BOTTOM
)

// The single stroke/fill application step, shared by CrispRenderer (primitive path) and GeomHelper.decorate
// (legacy path) so the two pipelines paint identically. Opacity is always set explicitly rather than
// leaning on the SvgShape color setters stamping it as a side effect (SvgUtils.colorAttributeTransform).
internal fun SvgShape.applyStroke(s: StrokeStyle) {
    strokeColor().set(s.color)
    strokeOpacity().set(s.alpha)
    strokeWidth().set(s.width)
    StrokeDashArraySupport.apply(this, s.width, s.lineType)
}

internal fun SvgShape.applyFill(f: FillStyle) {
    fillColor().set(f.color)
    fillOpacity().set(f.alpha)
}

internal fun SvgShape.applyStyles(stroke: StrokeStyle?, fill: FillStyle?) {
    stroke?.let { applyStroke(it) }
    // Default a missing fill to NONE, otherwise SVG paints the interior black.
    if (fill != null) applyFill(fill) else fill().set(SvgColors.NONE)
}

// The same computed Stroke/Fill applied to a slim shape (the large-N path). The slim setters take the
// opacity as an explicit argument (SlimBase strips the alpha channel from the color string itself), so
// the resolved alpha is the only carrier here.
// Unlike the SvgShape overload, a null fill color is left unset rather than defaulted to NONE:
// the slim setters take a non-null Color and the only caller (large-N rects) always has a fill.
internal fun SvgSlimShape.applyStyles(stroke: StrokeStyle, fill: FillStyle) {
    fill.color?.let { setFill(it, fill.alpha) }
    stroke.color?.let { setStroke(it, stroke.alpha) }
    setStrokeWidth(stroke.width)
    StrokeDashArraySupport.apply(this, stroke.width, stroke.lineType)
}
