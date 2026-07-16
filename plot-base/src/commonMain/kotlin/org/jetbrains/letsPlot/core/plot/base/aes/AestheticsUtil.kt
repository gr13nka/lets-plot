/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.aes

import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.core.plot.base.DataPointAesthetics
import org.jetbrains.letsPlot.core.plot.base.render.point.UpdatableShape
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgTransform
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgUtils

object AestheticsUtil {
    // Default for `applyAlphaToAll`: when false, the `alpha` aesthetic fades fill only and the
    // stroke stays opaque (bar/smooth/area/ribbon rely on this); pass true to fade the stroke too.
    internal const val DEFAULT_APPLY_ALPHA_TO_ALL = false

    fun fill(filled: Boolean, solid: Boolean, p: DataPointAesthetics): Color {
        if (filled) {
            return p.fill()!!
        } else if (solid) {
            return p.color()!!
        }
        return Color.TRANSPARENT
    }

    fun decorate(
        shape: UpdatableShape,
        filled: Boolean,
        solid: Boolean,
        p: DataPointAesthetics,
        strokeWidth: Double,
        transform: SvgTransform?
    ) {
        val fill = fill(filled, solid, p)
        val stroke = p.color()!!

        var fillAlpha = 0.0
        if (filled || solid) {
            fillAlpha = alpha(fill, p)
        }

        var strokeAlpha = 0.0
        if (strokeWidth > 0) {
            strokeAlpha = alpha(stroke, p)
        }

        shape.update(fill, fillAlpha, stroke, strokeAlpha, strokeWidth, transform)
    }

    fun alpha(color: Color, p: DataPointAesthetics): Double {
        return if (p.alpha() != AesInitValue.DEFAULT_ALPHA) {  //  apply only custom 'aes' alpha
            p.alpha()!!
        } else {                                               // else, override with color's alpha
            SvgUtils.alpha2opacity(color.alpha)
        }
    }

    fun strokeWidth(p: DataPointAesthetics) = AesScaling.strokeWidth(p)

    fun pieDiameter(p: DataPointAesthetics) = AesScaling.pieDiameter(p)

    fun pointStrokeWidth(
        p: DataPointAesthetics,
        strokeGetter: (DataPointAesthetics) -> Double? = DataPointAesthetics::stroke
    ) = AesScaling.strokeWidth(p, strokeGetter)

    fun circleDiameter(
        p: DataPointAesthetics,
        sizeGetter: (DataPointAesthetics) -> Double? = DataPointAesthetics::size
    ) = AesScaling.circleDiameter(p, sizeGetter)

    fun textSize(p: DataPointAesthetics) = AesScaling.textSize(p)

}
