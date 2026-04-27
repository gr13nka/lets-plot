/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.svg.lineString
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape

fun interface ComicStylize {
    fun apply(points: List<DoubleVector>): List<DoubleVector>

    companion object {
        val IDENTITY: ComicStylize = ComicStylize { it }
    }
}

fun ComicStylize.applyRect(rect: DoubleRectangle): SvgShape {
    if (this === ComicStylize.IDENTITY) return SvgRectElement(rect)

    val corners = listOf(
        DoubleVector(rect.left, rect.top),
        DoubleVector(rect.right, rect.top),
        DoubleVector(rect.right, rect.bottom),
        DoubleVector(rect.left, rect.bottom),
    )
    val out = ArrayList<DoubleVector>()
    for (i in 0..3) {
        val edge = apply(listOf(corners[i], corners[(i + 1) % 4]))
        if (i == 0) out.addAll(edge) else out.addAll(edge.drop(1))
    }
    return SvgPathElement(SvgPathDataBuilder().lineString(out).closePath().build())
}
