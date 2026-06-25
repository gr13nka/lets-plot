/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.xkcd

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.primitive.FillStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.SvgRenderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.TextStyle
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Draws each primitive in an xkcd-style hand-drawn look: the outline points are wobbled and then handed
// to a smoothing SvgRenderer. The wobble (`apply`) displaces points along a sine-eased path between
// random anchors, so a straight edge becomes a gently shaky line.
internal class XkcdRenderer : Renderer {
    private val amplitude: Double = 4.0
    private val length: Double = 100.0
    private val randomness: Double = 62.0
    private val fontFamily: String = "Comic Sans MS"

    private val inner = SvgRenderer(smooth = true)

    override fun line(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle): SvgNode =
        inner.path(apply(listOf(p1, p2)), stroke, closed = false)

    override fun path(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean): SvgNode =
        inner.path(apply(points), stroke, fill, closed)

    override fun arrowhead(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle?, closed: Boolean): SvgNode =
        inner.arrowhead(points, stroke, fill, closed)

    override fun rect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle?, radius: Double): SvgNode =
        // Wobbled closed path with corners cut back to clean rounded bends (the radius arg is dropped).
        inner.path(apply(roundedCornerOutline(rect)), stroke, fill, closed = true)

    override fun circle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle?): SvgNode =
        inner.path(apply(circleOutline(center, radius)), stroke, fill, closed = true)

    override fun polygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle?): SvgNode =
        // Wobble each ring independently so holes are preserved, inner keeps them in one path.
        inner.polygon(rings.map { apply(it) }, stroke, fill)

    override fun sector(center: DoubleVector, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double, fill: FillStyle?): SvgNode =
        inner.path(apply(sectorOutline(center, innerRadius, outerRadius, startAngle, endAngle)), stroke = null, fill = fill, closed = true)

    override fun arc(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double, stroke: StrokeStyle): SvgNode =
        // Trace the arc as a wobbled open path.
        inner.path(apply(arcPoints(center, radius, startAngle, endAngle)), stroke, closed = false)

    override fun text(origin: DoubleVector, text: String, style: TextStyle): SvgNode =
        inner.text(origin, text, style.copy(family = fontFamily))

    // Outer arc forward, then back along the inner arc (or to the center tip when there is no hole),
    // the radii are the straight hops drawPath draws between those two runs of points.
    private fun sectorOutline(center: DoubleVector, innerRadius: Double, outerRadius: Double, startAngle: Double, endAngle: Double): List<DoubleVector> {
        val outer = arcPoints(center, outerRadius, startAngle, endAngle)
        return when {
            // A full sweep with no hole is a disc, adding the center tip would draw a spike into the middle.
            innerRadius <= 0.0 && abs(endAngle - startAngle) >= 2.0 * PI - FULL_SWEEP_EPS -> outer
            innerRadius <= 0.0 -> outer + center
            else -> outer + arcPoints(center, innerRadius, endAngle, startAngle)
        }
    }

    private fun circleOutline(center: DoubleVector, radius: Double): List<DoubleVector> =
        arcPoints(center, radius, 0.0, 2.0 * PI)

    // Segment count scales with arc length so large circles/arcs aren't faceted. No AdaptiveResampler:
    // the wobble amplitude dwarfs sub-pixel sampling error, so a plain angular step is enough.
    private fun arcPoints(center: DoubleVector, radius: Double, startAngle: Double, endAngle: Double): List<DoubleVector> {
        val segments = max(8, ceil(abs(radius * (endAngle - startAngle)) / SAMPLE_LENGTH_PX).toInt())
        return (0..segments).map { i ->
            val a = startAngle + (endAngle - startAngle) * i / segments
            DoubleVector(center.x + radius * cos(a), center.y + radius * sin(a))
        }
    }

    // Displace the outline along a sine-eased path between random per-segment anchors, easing the
    // wobble to zero near sharp corners so meeting edges don't spur or notch where the normal flips.
    private fun apply(points: List<DoubleVector>, seed: Int? = null): List<DoubleVector> {
        if (points.size < 2) return points

        val cumLen = DoubleArray(points.size)
        for (pointIdx in 1 until points.size) {
            cumLen[pointIdx] = cumLen[pointIdx - 1] + points[pointIdx].subtract(points[pointIdx - 1]).length()
        }
        val total = cumLen[points.size - 1]
        if (total < 2.0) return points

        // Vertices that turn more than ~45 deg are real corners (not curve sampling); the wobble is
        // tapered to zero around them below so the two meeting edges land on the vertex.
        val sharp = BooleanArray(points.size)
        for (i in 1 until points.size - 1) {
            val a = points[i].subtract(points[i - 1])
            val b = points[i + 1].subtract(points[i])
            val la = a.length()
            val lb = b.length()
            if (la > 0.0 && lb > 0.0 && (a.x * b.x + a.y * b.y) / (la * lb) <= COS_SHARP) sharp[i] = true
        }

        val first = points.first()
        val rng = Random(seed ?: (31 * first.x.hashCode() + first.y.hashCode()))

        val segCount = ceil(total / length).toInt()
        val anchorArcLen = DoubleArray(segCount + 2)
        val anchorOffset = DoubleArray(segCount + 2)
        for (segIdx in 0 until segCount) {
            val segStart = segIdx * length
            val segEnd = minOf((segIdx + 1) * length, total)
            anchorArcLen[segIdx + 1] = segStart + (segEnd - segStart) * (0.1 + 0.8 * rng.nextDouble())
            val sign = if (rng.nextDouble() < 0.5) -1.0 else 1.0
            val variation = 1.0 + (rng.nextDouble() * 2.0 - 1.0) / randomness
            anchorOffset[segIdx + 1] = sign * amplitude * variation
        }
        anchorArcLen[segCount + 1] = total

        val sampleCount = ceil(total / SAMPLE_STEP).toInt()
        val out = ArrayList<DoubleVector>(sampleCount + 1)
        var srcSegIdx = 0
        var anchorIdx = 0
        for (sampleIdx in 0..sampleCount) {
            val arcLen = (sampleIdx.toDouble() / sampleCount) * total

            while (srcSegIdx < points.size - 2 && cumLen[srcSegIdx + 1] < arcLen) srcSegIdx++
            val seg = points[srcSegIdx + 1].subtract(points[srcSegIdx])
            val segLen = cumLen[srcSegIdx + 1] - cumLen[srcSegIdx]
            val srcFrac = if (segLen > 0.0) (arcLen - cumLen[srcSegIdx]) / segLen else 0.0
            val base = points[srcSegIdx].add(seg.mul(srcFrac))
            val normal = if (segLen > 0.0) seg.mul(1.0 / segLen).orthogonal() else DoubleVector(0.0, 0.0)

            while (anchorIdx < anchorArcLen.size - 2 && anchorArcLen[anchorIdx + 1] < arcLen) anchorIdx++
            val anchorSpan = anchorArcLen[anchorIdx + 1] - anchorArcLen[anchorIdx]
            val anchorFrac = if (anchorSpan > 0.0) (arcLen - anchorArcLen[anchorIdx]) / anchorSpan else 0.0
            val weight = 0.5 - 0.5 * cos(PI * anchorFrac)
            val offset = anchorOffset[anchorIdx] + (anchorOffset[anchorIdx + 1] - anchorOffset[anchorIdx]) * weight

            var taper = 1.0
            if (sharp[srcSegIdx]) taper = minOf(taper, cornerEase(arcLen - cumLen[srcSegIdx]))
            if (sharp[srcSegIdx + 1]) taper = minOf(taper, cornerEase(cumLen[srcSegIdx + 1] - arcLen))

            out.add(base.add(normal.mul(offset * taper)))
        }
        return out
    }

    private fun cornerEase(dist: Double): Double =
        if (dist >= CORNER_TAPER_PX) 1.0 else 0.5 - 0.5 * cos(PI * dist / CORNER_TAPER_PX)

    // Round each corner with a quarter-circle tangent to both edges: stop the incoming edge a radius
    // short of the vertex, arc smoothly round to the outgoing edge, resume. Tangent means no angular
    // join, so it reads as a soft hand-drawn corner (and the wobble flows round it with no spur).
    private fun roundedCornerOutline(rect: DoubleRectangle): List<DoubleVector> {
        val corners = rect.points.dropLast(1) // TL, TR, BR, BL, without the repeated closing point
        val out = ArrayList<DoubleVector>(corners.size * (CORNER_ARC_SEGMENTS + 1) + 1)
        for (i in corners.indices) {
            val cur = corners[i]
            val incoming = cur.subtract(corners[(i + corners.size - 1) % corners.size])
            val outgoing = corners[(i + 1) % corners.size].subtract(cur)
            val r = minOf(CORNER_RADIUS_PX, incoming.length() / 2.0, outgoing.length() / 2.0)
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
        out.add(out.first()) // repeat the first point so apply() wobbles the closing edge too
        return out
    }

    private fun unit(v: DoubleVector): DoubleVector {
        val len = v.length()
        return if (len > 0.0) v.mul(1.0 / len) else v
    }

    companion object {
        private const val SAMPLE_LENGTH_PX = 4.0
        private const val FULL_SWEEP_EPS = 1e-6
        private const val SAMPLE_STEP = 2.0
        private const val CORNER_RADIUS_PX = 6.0 // fillet radius: smaller = more squared, larger = rounder
        private const val CORNER_ARC_SEGMENTS = 8 // points sampled along each corner's quarter-circle
        private const val CORNER_TAPER_PX = 6.0 // arc-length window over which wobble eases to 0 at a corner
        private const val COS_SHARP = 0.707    // cos(45 deg): vertices turning more than this are corners
    }
}
