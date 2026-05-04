/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.svg.lineString
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathData
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape

private fun unfilledPath(d: SvgPathData): SvgPathElement = SvgPathElement(d).apply { fill().set(SvgColors.NONE) }

interface ComicStylize {
    fun apply(points: List<DoubleVector>): List<DoubleVector>
}

fun ComicStylize?.rectPolygon(rect: DoubleRectangle): List<DoubleVector> {
    val tl = DoubleVector(rect.left, rect.top)
    val tr = DoubleVector(rect.right, rect.top)
    val br = DoubleVector(rect.right, rect.bottom)
    val bl = DoubleVector(rect.left, rect.bottom)
    if (this == null) return listOf(tl, tr, br, bl, tl)

    val result = mutableListOf<DoubleVector>()
    result += apply(listOf(tl, tr))
    result += apply(listOf(tr, br)).drop(1)
    result += apply(listOf(br, bl)).drop(1)
    result += apply(listOf(bl, tl)).drop(1)
    return result
}

fun ComicStylize?.applyRect(rect: DoubleRectangle): SvgShape {
    if (this == null) return SvgRectElement(rect)
    return SvgPathElement(SvgPathDataBuilder().lineString(rectPolygon(rect)).closePath().build())
}

fun ComicStylize?.applyLine(p0: DoubleVector, p1: DoubleVector): SvgShape {
    if (this == null) return SvgLineElement(p0.x, p0.y, p1.x, p1.y)
    return unfilledPath(SvgPathDataBuilder().lineString(apply(listOf(p0, p1))).build())
}

fun ComicStylize?.applyPolyline(points: List<DoubleVector>): SvgShape = when {
    this == null && points.size == 2 ->
        SvgLineElement(points[0].x, points[0].y, points[1].x, points[1].y)
    this == null ->
        unfilledPath(SvgPathDataBuilder().lineString(points).build())
    else ->
        unfilledPath(SvgPathDataBuilder().lineString(apply(points)).build())
}
