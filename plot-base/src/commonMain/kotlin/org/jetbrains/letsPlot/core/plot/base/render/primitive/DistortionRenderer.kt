/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// A style as one polyline function: rect/circle/line are turned into client-space outlines, distorted,
// then drawn as (optionally smoothed) paths — the skeleton every distortion-family style shares. The
// style supplies only the point transform `distort(points, seed)`. The caller's per-shape `seed` fixes
// the distortion; when absent it falls back to a hash of the first outline point (see distorted() /
// stableSeed). Non-distortion styles implement Renderer directly.
class DistortionRenderer(
    private val distort: (points: List<DoubleVector>, seed: Int) -> List<DoubleVector>,
    private val rectCornerRadius: Double? = null,
    private val fontFamily: String? = null,
    smooth: Boolean = true,
) : Renderer {
    private val backend = SvgNodeRenderer(smooth)

    // Apply the style's transform, seeded by the caller's per-shape `seed`; when null, fall back to a
    // hash of the first outline point (see stableSeed). The single place seeding happens.
    private fun distorted(outline: List<DoubleVector>, seed: Int?): List<DoubleVector> =
        if (outline.isEmpty()) outline else distort(outline, seed ?: stableSeed(outline.first()))

    override fun line(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle, seed: Int?): SvgNode =
        backend.path(distorted(listOf(p1, p2), seed), stroke, closed = false)

    override fun path(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean, seed: Int?): SvgPathElement =
        backend.path(distorted(points, seed), stroke, fill, closed)

    override fun rect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle?, seed: Int?): SvgNode {
        // Distorted closed path: rounded-corner outline when a radius is set, else the plain rect outline.
        val outline = rectCornerRadius?.let { roundedCornerOutline(rect, it) } ?: rect.points
        return backend.path(distorted(outline, seed), stroke, fill, closed = true)
    }

    override fun circle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle?, seed: Int?): SvgNode =
        backend.path(distorted(circleOutline(center, radius), seed), stroke, fill, closed = true)

    override fun polygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle?, seed: Int?): SvgNode =
        // Distort each ring independently so holes are preserved, backend keeps them in one path.
        backend.polygon(rings.map { distorted(it, seed) }, stroke, fill)

    override fun text(origin: DoubleVector, text: String, style: TextStyle): SvgNode =
        backend.text(origin, text, fontFamily?.let { style.copy(family = it) } ?: style)

    private fun circleOutline(center: DoubleVector, radius: Double): List<DoubleVector> =
        arcPoints(center, radius, 0.0, 2.0 * PI)

    // Segment count scales with arc length so large circles aren't faceted. No AdaptiveResampler:
    // the wobble amplitude dwarfs sub-pixel sampling error, so a plain angular step is enough.
    private fun arcPoints(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double): List<DoubleVector> {
        val segments = max(8, ceil(abs(radius * (endAngle - startAngle)) / SAMPLE_LENGTH_PX).toInt())
        return (0..segments).map { i ->
            val a = startAngle + (endAngle - startAngle) * i / segments
            DoubleVector(center.x + radius * cos(a), center.y + radius * sin(a))
        }
    }

    // Round each corner with a quarter-circle tangent to both edges: stop the incoming edge a radius
    // short of the vertex, arc smoothly round to the outgoing edge, resume. Tangent means no angular
    // join, so it reads as a soft hand-drawn corner (and the wobble flows round it with no spur).
    private fun roundedCornerOutline(rect: DoubleRectangle, radius: Double): List<DoubleVector> {
        val corners = rect.points.dropLast(1) // TL, TR, BR, BL, without the repeated closing point
        val out = ArrayList<DoubleVector>(corners.size * (CORNER_ARC_SEGMENTS + 1) + 1)
        for (i in corners.indices) {
            val cur = corners[i]
            val incoming = cur.subtract(corners[(i + corners.size - 1) % corners.size])
            val outgoing = corners[(i + 1) % corners.size].subtract(cur)
            val r = minOf(radius, incoming.length() / 2.0, outgoing.length() / 2.0)
            val start = cur.subtract(unit(incoming).mul(r))            // where the incoming edge stops
            val center = start.add(unit(incoming).orthogonal().mul(r)) // fillet center, rect interior side
            val v = start.subtract(center)                            // radius vector to the arc start (len r)
            for (k in 0..CORNER_ARC_SEGMENTS) {                       // sweep the quarter turn to the outgoing edge
                val a = PI / 2.0 * k / CORNER_ARC_SEGMENTS
                val c = cos(a)
                val s = sin(a)
                out.add(center.add(DoubleVector(v.x * c - v.y * s, v.x * s + v.y * c)))
            }
        }
        out.add(out.first()) // repeat the first point so the wobble reaches the closing edge too
        return out
    }

    private fun unit(v: DoubleVector): DoubleVector {
        val len = v.length()
        return if (len > 0.0) v.mul(1.0 / len) else v
    }

    companion object {
        private const val SAMPLE_LENGTH_PX = Renderer.ARC_SAMPLE_LENGTH_PX
        private const val CORNER_ARC_SEGMENTS = 8 // points sampled along each corner's quarter-circle

        // Fallback seed when a caller passes none: a hash of the first outline point. Stable across
        // re-renders of the same view, but under pan the client point moves and the shape re-randomizes,
        // so callers that need a stable look pass an explicit `seed` (DataPointAesthetics.index()).
        private fun stableSeed(first: DoubleVector): Int = 31 * first.x.hashCode() + first.y.hashCode()
    }
}
