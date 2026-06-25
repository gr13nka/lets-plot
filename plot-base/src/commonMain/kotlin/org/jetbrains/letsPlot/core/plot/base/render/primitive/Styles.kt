/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.commons.values.FontFace
import org.jetbrains.letsPlot.core.plot.base.render.linetype.LineType
import org.jetbrains.letsPlot.core.plot.base.render.svg.Text

data class StrokeStyle(
    val color: Color?,
    val alpha: Double?,
    val width: Double,
    val lineType: LineType,
    val miterLimit: Double? = null
)

data class FillStyle(
    val color: Color?,
    val alpha: Double?
)

data class TextStyle(
    val color: Color?,
    val alpha: Double?,
    val sizePx: Double,
    val lineHeight: Double?,
    val family: String?,
    val face: FontFace? = null,
    val hAnchor: Text.HorizontalAnchor = Text.HorizontalAnchor.LEFT,
    val vAnchor: Text.VerticalAnchor = Text.VerticalAnchor.BOTTOM
)
