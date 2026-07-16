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
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode

// The shared draw recipes over the Renderer: how a family of geoms turns computed client-space
// geometry into placed nodes. Geometry factories live in GeomHelper/LinesHelper; paint comes from
// AesStyling; these functions own only the emit order and shape conventions.

// A straight two-point segment renders as renderer.line (an SVG <line> in crisp mode); anything
// longer (a resampled polyline under bending coords) as an open path.
internal fun lineNode(renderer: Renderer, geometry: List<DoubleVector>, stroke: StrokeStyle, seed: Int): SvgNode =
    if (geometry.size == 2) renderer.line(geometry[0], geometry[1], stroke, seed = seed)
    else renderer.path(geometry, stroke, closed = false, seed = seed)

// buildLineWithArrow's result: the nodes to place, plus the un-wobbled, no-arrow geometry for the
// tooltip collector.
internal data class LineWithArrow(val nodes: List<SvgNode>, val tooltipGeometry: List<DoubleVector>)

// Shared draw path for geom_segment, geom_curve and geom_spoke: a line/curve plus optional
// arrowheads. Curves arrive pre-densified as a polyline (see createCurveGeometry).
internal fun buildLineWithArrow(
    renderer: Renderer,
    lineString: List<DoubleVector>,
    p: DataPointAesthetics,
    arrowSpec: ArrowSpec?,
    spacer: Double
): LineWithArrow {
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

    val nodes = mutableListOf<SvgNode>()

    if (arrowPadded.size >= 2) {
        nodes.add(renderer.path(arrowPadded, strokeFor(p), closed = false, seed = p.index()))
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
            // Arrowheads stay crisp in every mode (sharp tips intentional) — drawn through renderer.crisp
            // rather than the themed renderer, so the exemption is explicit here.
            nodes.add(renderer.crisp.path(head, stroke, fill, closed = closed))
        }
    }

    val tooltipGeometry = padLineString(lineString, padding(atStart = true, withArrow = false), padding(atStart = false, withArrow = false))
    return LineWithArrow(nodes, tooltipGeometry)
}

// The banded-area render recipe, owned in one place. A geom builds its band and boundary geometry
// (via LinesHelper.createBandData / createPathData, or any other source packaged as PathData) and then
// declares it here:
//
//   - fill pass: each band as a closed polygon filled by its aes, with no stroke;
//   - line pass: each line as an open path stroked by its aes with an unfaded outline
//                (alpha aes not applied), painted over the fill.
//
// Every fill is emitted before any line, so the outlines always paint over the band. The nodes are
// placed on `root` here, in the given order, so the caller cannot forget to add them.
//
// `lines` are the band edges for most geoms; for geom_smooth they are the fitted line drawn over the
// confidence band. Callers keep their own scoping loops (per group / quantile split / ridge / data point)
// and their own extras (quantile lines, tooltip hints) - only the shared emit recipe lives here.
internal fun drawBands(root: SvgRoot, renderer: Renderer, bands: List<PathData>, lines: List<PathData>) {
    for (band in bands) {
        root.add(renderer.path(band.coordinates, stroke = null, fill = fillFor(band.aes), closed = true, seed = band.aes.index()))
    }
    for (line in lines) {
        root.add(renderer.path(line.coordinates, outlineStrokeFor(line.aes), closed = false, seed = line.aes.index()))
    }
}
