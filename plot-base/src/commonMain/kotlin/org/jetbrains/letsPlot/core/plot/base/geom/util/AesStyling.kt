/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.commons.values.FontFace
import org.jetbrains.letsPlot.core.plot.base.DataPointAesthetics
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.aes.AestheticsUtil
import org.jetbrains.letsPlot.core.plot.base.render.primitive.FillStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.TextStyle
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgUtils

internal fun strokeFor(p: DataPointAesthetics, applyAlpha: Boolean = true): StrokeStyle {
    val color = p.color()
    return StrokeStyle(
        color = color,
        alpha = color?.let { opacity(it, p, applyAlpha) },
        width = AesScaling.strokeWidth(p),
        lineType = p.lineType()
    )
}

internal fun fillFor(p: DataPointAesthetics): FillStyle {
    val color = p.fill()
    return FillStyle(
        color = color,
        alpha = color?.let { opacity(it, p, applyAlpha = true) }
    )
}

// Mirrors TextUtil.decorate styling.
// Unlike strokeFor/fillFor, text opacity is kept even when fully opaque: the text path sets
// fill-opacity unconditionally.
internal fun textStyleFor(p: DataPointAesthetics, scale: Double = 1.0, applyAlpha: Boolean = true): TextStyle {
    val color = p.color()
    return TextStyle(
        color = color,
        alpha = color?.let { if (applyAlpha) AestheticsUtil.alpha(it, p) else SvgUtils.alpha2opacity(it.alpha) },
        sizePx = TextUtil.fontSize(p, scale),
        lineHeight = TextUtil.lineheight(p, scale),
        family = TextUtil.fontFamily(p),
        face = FontFace.fromString(p.fontface())
    )
}

// Opacity for a styled color: the alpha aes if set, otherwise the color's own alpha channel.
// applyAlpha = false ignores the alpha aes (filled geoms keep an opaque outline) but still
// uses the color's channel. Returns null when fully opaque so the renderer omits the attribute.
private fun opacity(color: Color, p: DataPointAesthetics, applyAlpha: Boolean): Double? {
    val opacity = if (applyAlpha) AestheticsUtil.alpha(color, p) else SvgUtils.alpha2opacity(color.alpha)
    return opacity.takeIf { it < 1.0 }
}
