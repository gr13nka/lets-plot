/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hand-drawn style: every primitive becomes a `<path>` resampled and pushed off its true course by
 * a smoothly wandering normal offset. Deterministic in the data point index, so a point wobbles the
 * same way on every redraw, and self-densifying (at [SAMPLE_STEP]), so a two-point line comes out
 * curved without the caller asking.
 */
object XkcdStyle : DrawingStyle {

    override fun path(subPaths: List<List<DoubleVector>>, ring: Boolean, dataPointIndex: Int): SvgPathElement {
        // Each sub-path wobbles on its own; the index shift keeps a polygon's rings out of lockstep.
        return svgPath(
            subPaths.mapIndexed { subPathIndex, subPath ->
                wobble(subPath, ring, dataPointIndex + subPathIndex)
            },
            ring
        )
    }

    override fun line(start: DoubleVector, end: DoubleVector, dataPointIndex: Int): SvgShape {
        return path(listOf(listOf(start, end)), ring = false, dataPointIndex = dataPointIndex)
    }

    override fun rect(rect: DoubleRectangle, dataPointIndex: Int): SvgShape {
        return path(
            listOf(roundedCornerOutline(rect, CORNER_RADIUS_PX)),
            ring = true,
            dataPointIndex = dataPointIndex
        )
    }

    /**
     * Round each corner with a quarter-circle tangent to both edges, so there is no angular join
     * and the wobble flows round it. A rect too small for the full radius takes what fits.
     */
    private fun roundedCornerOutline(rect: DoubleRectangle, radius: Double): List<DoubleVector> {
        // DoubleRectangle.points is TL, TR, BR, BL, TL - drop the repeated closing point.
        val corners = rect.points.dropLast(1)
        val out = ArrayList<DoubleVector>(corners.size * (CORNER_ARC_SEGMENTS + 1) + 1)
        for (cornerIdx in corners.indices) {
            val corner = corners[cornerIdx]
            val incoming = corner.subtract(corners[(cornerIdx + corners.size - 1) % corners.size])
            val outgoing = corners[(cornerIdx + 1) % corners.size].subtract(corner)
            val r = minOf(radius, incoming.length() / 2.0, outgoing.length() / 2.0)

            val arcStart = corner.subtract(unit(incoming).mul(r))              // where the incoming edge stops
            val center = arcStart.add(unit(incoming).orthogonal().mul(r))      // fillet centre, rect interior side
            val spoke = arcStart.subtract(center)                              // radius vector to the arc start
            for (step in 0..CORNER_ARC_SEGMENTS) {
                val angle = PI / 2.0 * step / CORNER_ARC_SEGMENTS
                val c = cos(angle)
                val s = sin(angle)
                out.add(center.add(DoubleVector(spoke.x * c - spoke.y * s, spoke.x * s + spoke.y * c)))
            }
        }
        out.add(out.first()) // close, so the wobble reaches the last edge
        return out
    }

    private fun unit(v: DoubleVector): DoubleVector {
        val len = v.length()
        return if (len > 0.0) v.mul(1.0 / len) else v
    }

    /**
     * Displace the outline perpendicular to its local direction by a gently wandering sine:
     * anchors ~half a wavelength apart carry alternating offsets, raised-cosine interpolated.
     *
     * An open path eases to zero at each end so it lands on its true endpoints - tooltip geometry
     * depends on that. A ring reuses its first offset at the seam and stays shut.
     */
    private fun wobble(points: List<DoubleVector>, ring: Boolean, seed: Int): List<DoubleVector> {
        if (points.size < 2) return points

        val cumLen = DoubleArray(points.size)
        for (pointIdx in 1 until points.size) {
            cumLen[pointIdx] = cumLen[pointIdx - 1] + points[pointIdx].subtract(points[pointIdx - 1]).length()
        }
        val total = cumLen[points.size - 1]
        if (total < MIN_LENGTH_PX) return points

        val rng = Random(seed)

        // Anchors ~half a wavelength apart, jittered spacing, alternating amplitude-jittered
        // offset. Pinned at 0 and `total` so the blend spans the whole outline.
        val anchorArcLen = ArrayList<Double>()
        val anchorOffset = ArrayList<Double>()
        var sign = if (rng.nextBoolean()) 1.0 else -1.0
        anchorArcLen.add(0.0)
        anchorOffset.add(sign * AMPLITUDE_PX * amplitudeJitter(rng))
        var pos = 0.0
        while (true) {
            pos += HALF_WAVELENGTH_PX * (1.0 + SPACING_JITTER * (2.0 * rng.nextDouble() - 1.0))
            if (pos >= total) break
            sign = -sign
            anchorArcLen.add(pos)
            anchorOffset.add(sign * AMPLITUDE_PX * amplitudeJitter(rng))
        }
        anchorArcLen.add(total)
        anchorOffset.add(if (ring) anchorOffset.first() else -sign * AMPLITUDE_PX * amplitudeJitter(rng))

        val sampleCount = ceil(total / SAMPLE_STEP).toInt()
        val out = ArrayList<DoubleVector>(sampleCount + 1)
        var srcSegIdx = 0
        var anchorIdx = 0
        // A ring's seam is one point reached from two directions, so sampling it twice would give
        // two different normals. Sample it once and repeat that sample to close the ring exactly.
        val lastSampleIdx = if (ring) sampleCount - 1 else sampleCount
        for (sampleIdx in 0..lastSampleIdx) {
            val arcLen = (sampleIdx.toDouble() / sampleCount) * total

            while (srcSegIdx < points.size - 2 && cumLen[srcSegIdx + 1] < arcLen) srcSegIdx++
            val seg = points[srcSegIdx + 1].subtract(points[srcSegIdx])
            val segLen = cumLen[srcSegIdx + 1] - cumLen[srcSegIdx]
            val srcFrac = if (segLen > 0.0) (arcLen - cumLen[srcSegIdx]) / segLen else 0.0
            val base = points[srcSegIdx].add(seg.mul(srcFrac))
            val normal = if (segLen > 0.0) seg.mul(1.0 / segLen).orthogonal() else DoubleVector.ZERO

            // Raised-cosine blend between the surrounding anchors.
            while (anchorIdx < anchorArcLen.size - 2 && anchorArcLen[anchorIdx + 1] < arcLen) anchorIdx++
            val anchorSpan = anchorArcLen[anchorIdx + 1] - anchorArcLen[anchorIdx]
            val anchorFrac = if (anchorSpan > 0.0) (arcLen - anchorArcLen[anchorIdx]) / anchorSpan else 0.0
            val weight = 0.5 - 0.5 * cos(PI * anchorFrac)
            var offset = anchorOffset[anchorIdx] + (anchorOffset[anchorIdx + 1] - anchorOffset[anchorIdx]) * weight

            if (!ring) offset *= endpointEnvelope(arcLen, total)

            out.add(base.add(normal.mul(offset)))
        }
        if (ring) out.add(out.first())
        return out
    }

    /** Per-anchor amplitude jitter: the wobble breathes. */
    private fun amplitudeJitter(rng: Random): Double = 1.0 + AMPLITUDE_JITTER * (2.0 * rng.nextDouble() - 1.0)

    /** Raised-cosine ramp pinning an open path's endpoints; capped so the two ramps never overlap. */
    private fun endpointEnvelope(arcLen: Double, total: Double): Double {
        val ramp = minOf(ENDPOINT_RAMP_PX, total / 2.0)
        val dist = minOf(arcLen, total - arcLen)
        return if (dist >= ramp) 1.0 else 0.5 - 0.5 * cos(PI * dist / ramp)
    }

    private const val AMPLITUDE_PX = 2.0          // wobble displacement at an anchor
    private const val HALF_WAVELENGTH_PX = 90.0   // arc-length between anchors: long, lazy ~180px waves
    private const val SPACING_JITTER = 0.45       // +- fraction the anchor spacing wanders
    private const val AMPLITUDE_JITTER = 0.35     // +- fraction the per-anchor amplitude varies
    private const val ENDPOINT_RAMP_PX = 35.0     // arc-length an open path's wobble eases in/out over
    private const val MIN_LENGTH_PX = 2.0         // below this an outline is left un-wobbled
    private const val SAMPLE_STEP = 2.0           // arc-length between resampled points
    private const val CORNER_RADIUS_PX = 6.0      // rect fillet radius
    private const val CORNER_ARC_SEGMENTS = 8     // points sampled along each corner's quarter-circle
}
