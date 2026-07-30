/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape

/** The default style: geometry passes through, so a `<line>` or `<rect>` rather than a `<path>`. */
object CrispStyle : DrawingStyle {
    override fun path(subPaths: List<List<DoubleVector>>, ring: Boolean, dataPointIndex: Int): SvgPathElement {
        return svgPath(subPaths, ring)
    }

    override fun line(start: DoubleVector, end: DoubleVector, dataPointIndex: Int): SvgShape {
        return SvgLineElement(start.x, start.y, end.x, end.y)
    }

    override fun rect(rect: DoubleRectangle, dataPointIndex: Int): SvgShape {
        return SvgRectElement(rect)
    }
}
