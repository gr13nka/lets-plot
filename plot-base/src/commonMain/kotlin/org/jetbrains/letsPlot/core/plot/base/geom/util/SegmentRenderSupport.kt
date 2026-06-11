/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.intern.util.ArrowSupport
import org.jetbrains.letsPlot.commons.intern.util.padLineString
import org.jetbrains.letsPlot.core.plot.base.DataPointAesthetics
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.geom.util.ArrowSpec.Companion.toArrowAes
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer

// Shared draw path for geom_segment, geom_curve and geom_spoke: a line/curve plus optional
// arrowheads, through the Renderer (not the legacy SvgElementHelper) so comic mode can wobble it.
// renderGeometry flattens the curve control points to a B-spline before drawing, arrowheads still
// come off the control line. Returns the un-wobbled, no-arrow geometry for the tooltip collector.
internal fun drawLineWithArrow(
    renderer: Renderer,
    lineString: List<DoubleVector>,
    p: DataPointAesthetics,
    arrowSpec: ArrowSpec?,
    spacer: Double,
    renderGeometry: (List<DoubleVector>) -> List<DoubleVector> = { it }
): List<DoubleVector> {
    val strokeWidth = AesScaling.strokeWidth(p)

    fun padding(atStart: Boolean, withArrow: Boolean): Double {
        val target = if (atStart) AesScaling.targetStartSize(p) else AesScaling.targetEndSize(p)
        val arrow = if (withArrow && arrowSpec != null) {
            ArrowSupport.arrowPadding(arrowSpec.angle, arrowSpec.isOnFirstEnd, arrowSpec.isOnLastEnd, atStart, strokeWidth)
        } else {
            0.0
        }
        return spacer + target + arrow
    }

    val arrowPadded = padLineString(lineString, padding(atStart = true, withArrow = true), padding(atStart = false, withArrow = true))

    val drawn = renderGeometry(arrowPadded)
    if (drawn.size >= 2) {
        renderer.drawPath(drawn, strokeFor(p), closed = false)
    }

    arrowSpec?.let { spec ->
        val closed = spec.type == ArrowSpec.Type.CLOSED
        val (startHead, endHead) = ArrowSupport.createArrowHeads(
            lineString = arrowPadded,
            angle = spec.angle,
            arrowLength = spec.length,
            onStart = spec.isOnFirstEnd,
            onEnd = spec.isOnLastEnd,
            closed = closed,
            minTailLength = ArrowSupport.MIN_TAIL_LENGTH,
            minHeadLength = ArrowSupport.MIN_HEAD_LENGTH
        )
        val arrowAes = spec.toArrowAes(p)
        val stroke = strokeFor(arrowAes).copy(miterLimit = ArrowSupport.miterLength(spec.angle, strokeWidth) * 2)
        val fill = if (closed) fillFor(arrowAes) else null
        listOf(startHead, endHead).filter { it.size >= 2 }.forEach { head ->
            renderer.drawArrowhead(head, stroke, fill, closed = closed)
        }
    }

    return renderGeometry(padLineString(lineString, padding(atStart = true, withArrow = false), padding(atStart = false, withArrow = false)))
}
