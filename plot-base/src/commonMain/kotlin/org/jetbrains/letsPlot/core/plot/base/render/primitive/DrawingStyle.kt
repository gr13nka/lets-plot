/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape

/**
 * Turns client-space geometry into an unpainted SVG primitive. Never sees a colour, an aesthetic
 * or a theme - it may vary geometry and nothing else. Only `geom/util/` may name one.
 *
 * `dataPointIndex` must be `DataPointAesthetics.index()`: a seed derived from client-space
 * coordinates would re-scribble the plot on every resize.
 */
interface DrawingStyle {
    /**
     * Polyline, given as its sub-paths. A distorting style must distort each on its own, or one
     * ring smears into the next.
     *
     * [ring] is authoritative - the caller decided (see [isRing]); do not re-derive it. A ring may
     * or may not repeat its first point as its last.
     */
    fun path(subPaths: List<List<DoubleVector>>, ring: Boolean, dataPointIndex: Int): SvgPathElement

    /** Straight segment. Crisp emits `<line>`; a distorting style emits `<path>`. */
    fun line(start: DoubleVector, end: DoubleVector, dataPointIndex: Int): SvgShape

    /** Axis-aligned rect. Crisp emits `<rect>`; a distorting style emits `<path>`. */
    fun rect(rect: DoubleRectangle, dataPointIndex: Int): SvgShape
}

/**
 * Whether a client-space outline closes on itself - the one owner of that judgement.
 *
 * Answer it on the *final* geometry: padding, Douglas-Peucker and style-splitting all run after
 * the ring is built and any can re-open it. A tolerance, not equality - the same domain point can
 * reach here differing in the last bits.
 */
internal fun isRing(points: List<DoubleVector>): Boolean =
    points.size > 2 && points.first().subtract(points.last()).length() < CLOSING_EPS_PX

/** first ~= last within this many client px => the same point, so the outline is a ring. */
private const val CLOSING_EPS_PX = 1e-3

/**
 * Emits sub-paths as one `<path>`; both styles end here. An empty sub-path contributes nothing -
 * `closePath` would otherwise emit a stray `Z`.
 *
 * The first point is emitted twice (`M p0 L p0 L p1 ...`). Do not "simplify" to
 * `SvgPathDataBuilder.lineString`: that zero-length segment changes the join at the start vertex,
 * and removing it moved pixels in 38 platf-awt reference images.
 */
internal fun svgPath(subPaths: List<List<DoubleVector>>, ring: Boolean): SvgPathElement {
    val builder = SvgPathDataBuilder()
    for (subPath in subPaths) {
        if (subPath.isEmpty()) continue
        builder.moveTo(subPath.first())
        subPath.forEach(builder::lineTo)
        if (ring) {
            builder.closePath()
        }
    }
    return SvgPathElement(builder.build())
}

/** Every element a DrawingStyle emits is an `SvgGraphicsElement`, so it is also an [SvgNode]. */
internal fun SvgShape.asNode(): SvgNode = this as SvgNode
