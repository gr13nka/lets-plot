/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode

// A node factory: each method builds one SVG node for a primitive and returns it (a plain element, or
// an xkcd-stylized path). The caller places the node, so geoms keep handing nodes to the root exactly
// as before. The `renderer` theme option picks the implementation (plain SvgRenderer or XkcdRenderer).
interface Renderer {
    fun line(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle): SvgNode
    fun path(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle? = null, closed: Boolean = false): SvgNode
    fun arrowhead(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle? = null, closed: Boolean = false): SvgNode
    fun rect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle? = null, radius: Double = 0.0): SvgNode
    fun circle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle? = null): SvgNode
    fun polygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle? = null): SvgNode
    fun sector(center: DoubleVector, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double, fill: FillStyle?): SvgNode
    fun arc(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double, stroke: StrokeStyle): SvgNode
    fun text(origin: DoubleVector, text: String, style: TextStyle): SvgNode
}
