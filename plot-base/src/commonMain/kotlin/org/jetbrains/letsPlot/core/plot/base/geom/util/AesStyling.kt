/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.values.FontFace
import org.jetbrains.letsPlot.core.plot.base.DataPointAesthetics
import org.jetbrains.letsPlot.core.plot.base.aes.AesInitValue
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.aes.AestheticsUtil
import org.jetbrains.letsPlot.core.plot.base.render.primitive.FillStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.TextStyle
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgUtils

internal fun strokeFor(p: DataPointAesthetics, applyAlpha: Boolean = true): StrokeStyle {
    return StrokeStyle(
        color = p.color(),
        alpha = alphaAesOpacity(p, applyAlpha),
        width = AesScaling.strokeWidth(p),
        lineType = p.lineType()
    )
}

internal fun fillFor(p: DataPointAesthetics): FillStyle {
    return FillStyle(
        color = p.fill(),
        alpha = alphaAesOpacity(p, applyAlpha = true)
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

// Opacity from the `alpha` aesthetic only, mirroring AestheticsUtil.updateStroke/updateFill so the
// renderer path stays identical to `decorate`: the color's own alpha channel is intentionally ignored
// here (honoring rgba colors is a separate, deferred change). applyAlpha = false drops the alpha for
// the outline so filled geoms keep an opaque stroke.
private fun alphaAesOpacity(p: DataPointAesthetics, applyAlpha: Boolean): Double? =
    if (applyAlpha) p.alpha().takeIf { it != AesInitValue.DEFAULT_ALPHA } else null
