/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.commons.values.FontFace
import org.jetbrains.letsPlot.core.plot.base.DataPointAesthetics
import org.jetbrains.letsPlot.core.plot.base.aes.AesInitValue
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.render.primitive.FillStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.TextStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.ownOpacity

internal fun strokeFor(
    p: DataPointAesthetics,
    applyAlpha: Boolean = true,
    strokeScaler: (DataPointAesthetics) -> Double = AesScaling::strokeWidth
): StrokeStyle {
    return StrokeStyle(
        color = p.color(),
        alpha = resolvedAlpha(p.color(), p, applyAlpha),
        width = strokeScaler(p),
        lineType = p.lineType()
    )
}

// Stroke for the outline of a filled shape: drops the alpha aesthetic so the fill can be
// translucent while its outline stays unfaded.
internal fun outlineStrokeFor(
    p: DataPointAesthetics,
    strokeScaler: (DataPointAesthetics) -> Double = AesScaling::strokeWidth
): StrokeStyle = strokeFor(p, applyAlpha = false, strokeScaler)

internal fun fillFor(p: DataPointAesthetics): FillStyle {
    return FillStyle(
        color = p.fill(),
        alpha = resolvedAlpha(p.fill(), p, applyAes = true)
    )
}

// Mirrors TextUtil.decorate styling.
internal fun textStyleFor(p: DataPointAesthetics, scale: Double = 1.0, applyAlpha: Boolean = true): TextStyle {
    val color = p.color()
    return TextStyle(
        color = color,
        alpha = resolvedAlpha(color, p, applyAlpha),
        sizePx = TextUtil.fontSize(p, scale),
        lineHeight = TextUtil.lineheight(p, scale),
        family = TextUtil.fontFamily(p),
        face = FontFace.fromString(p.fontface())
    )
}

// The one opacity rule for every paint path (Renderer, legacy decorate, slim): 
// the alpha = aesthetic when explicitly set, else the color's own alpha channel (so rgba colors 
//render translucent). applyAes = false drops the aes half for outlines so filled geoms keep their
// outline unfaded while the fill fades.
private fun resolvedAlpha(color: Color?, p: DataPointAesthetics, applyAes: Boolean): Double {
    val aes = p.alpha()
    return if (applyAes && aes != null && aes != AesInitValue.DEFAULT_ALPHA) aes else ownOpacity(color)
}
