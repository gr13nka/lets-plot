/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.xkcd

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.base.render.primitive.DistortionRenderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.random.Random

// The xkcd geometry identity is `wobble()`: it displaces outline points along a sine-eased path between
// random anchors, so a straight edge becomes a gently shaky line. Everything else — turning rect/circle/
// line into outlines, distorting them and drawing (smoothed) paths — is the generic distortion skeleton,
// which lives in DistortionRenderer; this class only supplies the wobble, the corner fillet radius and
// the font.
internal class XkcdRenderer : Renderer by DistortionRenderer(
    distort = ::wobble,
    rectCornerRadius = CORNER_RADIUS_PX,
    fontFamily = FONT_FAMILY,
    smooth = true,
) {
    companion object {
        // The xkcd look's font, for geom text drawn via DistortionRenderer.text() and — via the
        // RendererStyles xkcd theme overlay — the theme-value default for all chrome text.
        // A CSS-style fallback list: the authentic xkcd handwriting comes from the "xkcd Script"
        // font (github.com/ipython/xkcd-font), which the user installs themselves — it ships under
        // a NonCommercial license and so is not bundled. "xkcd" is that project's alternate family;
        // "Comic Sans MS" is the last-resort look-alike when neither is installed. The browser SVG
        // backend honours the whole list; the canvas backends (AWT, ImageMagick) take the first name.
        internal const val FONT_FAMILY = "xkcd Script, xkcd, Comic Sans MS"
    }
}

// Displace the outline perpendicular to its local direction by a gently wandering sine — the xkcd wobble.
// Control "anchors" ~half a wavelength apart carry an alternating, amplitude-jittered offset; raised-cosine
// interpolation between them gives a smooth, confident wiggle (not independent per-anchor noise). Open paths
// ease the offset to zero over one ramp at each end, so a line lands exactly on its true endpoints with no
// beak (axis/tick geometry relies on pinned endpoints). A closed ring has no real endpoint: it carries the
// wobble continuously across the seam (matching seam offset) so there is no flat spot, and stays shut because
// the drawn path is closePath()'d back to its first point.
// Deterministic given `seed`: the same shape wobbles identically frame to frame. The caller supplies it —
// geoms pass p.index(), single chrome elements pass 0; DistortionRenderer only falls back to hashing the
// first outline point when seed is null.
private fun wobble(points: List<DoubleVector>, seed: Int): List<DoubleVector> {
    if (points.size < 2) return points

    val cumLen = DoubleArray(points.size)
    for (pointIdx in 1 until points.size) {
        cumLen[pointIdx] = cumLen[pointIdx - 1] + points[pointIdx].subtract(points[pointIdx - 1]).length()
    }
    val total = cumLen[points.size - 1]
    if (total < MIN_LENGTH_PX) return points

    // A ring closes on itself (first ~= last): rect/circle/polygon outlines. It has no endpoint, so it skips
    // the end ramp and relies on a matching seam offset instead. Circles don't repeat the first point exactly
    // (2*PI rounding), so compare within a tolerance rather than for equality.
    val closed = points.first().subtract(points.last()).length() < CLOSING_EPS_PX

    val rng = Random(seed)

    // Anchors along the arc, ~half a wavelength apart with jittered spacing (the wavelength wanders) and an
    // alternating, amplitude-jittered offset (a confident sine, not random-sign noise). Pinned at arc-length 0
    // and `total` so the interpolation spans the whole outline.
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
    // A closed ring reuses its first offset at the seam so the wobble is continuous across it; an open path
    // just continues the alternation (its end ramp drives the offset to zero here anyway).
    anchorOffset.add(if (closed) anchorOffset.first() else -sign * AMPLITUDE_PX * amplitudeJitter(rng))

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

        // Raised-cosine blend between the surrounding anchors — a smooth sinusoid through the offsets.
        while (anchorIdx < anchorArcLen.size - 2 && anchorArcLen[anchorIdx + 1] < arcLen) anchorIdx++
        val anchorSpan = anchorArcLen[anchorIdx + 1] - anchorArcLen[anchorIdx]
        val anchorFrac = if (anchorSpan > 0.0) (arcLen - anchorArcLen[anchorIdx]) / anchorSpan else 0.0
        val weight = 0.5 - 0.5 * cos(PI * anchorFrac)
        var offset = anchorOffset[anchorIdx] + (anchorOffset[anchorIdx + 1] - anchorOffset[anchorIdx]) * weight

        // Open paths ease to zero over one ramp at each end so the line stays anchored on its true endpoints.
        if (!closed) offset *= endpointEnvelope(arcLen, total)

        out.add(base.add(normal.mul(offset)))
    }
    return out
}

// Per-anchor amplitude jitter in [1 - AMPLITUDE_JITTER, 1 + AMPLITUDE_JITTER]: the wobble breathes.
private fun amplitudeJitter(rng: Random): Double = 1.0 + AMPLITUDE_JITTER * (2.0 * rng.nextDouble() - 1.0)

// Raised-cosine ramp easing an open path's wobble from zero at each endpoint up to full over ENDPOINT_RAMP_PX
// (capped so the two ramps meet, never overlap, on a short segment). Keeps endpoints pinned and beak-free.
private fun endpointEnvelope(arcLen: Double, total: Double): Double {
    val ramp = minOf(ENDPOINT_RAMP_PX, total / 2.0)
    val dist = minOf(arcLen, total - arcLen)
    return if (dist >= ramp) 1.0 else 0.5 - 0.5 * cos(PI * dist / ramp)
}

private const val AMPLITUDE_PX = 2.0          // wobble displacement at an anchor (xkcd is subtle)
private const val HALF_WAVELENGTH_PX = 90.0   // arc-length between anchors ≈ half a wobble wavelength (~180px):
                                              // long, lazy, low-frequency waves — not a nervous high-frequency ripple
private const val SPACING_JITTER = 0.45       // ± fraction the anchor spacing (wavelength) wanders
private const val AMPLITUDE_JITTER = 0.35     // ± fraction the per-anchor amplitude varies
private const val ENDPOINT_RAMP_PX = 35.0     // arc-length over which an open path's wobble eases in/out
private const val MIN_LENGTH_PX = 2.0         // below this an outline is left un-wobbled (invisible anyway)
private const val CLOSING_EPS_PX = 1e-3       // first≈last within this ⇒ treat the outline as a closed ring

private const val SAMPLE_STEP = 2.0
private const val CORNER_RADIUS_PX = 6.0      // rect fillet radius: smaller = more squared, larger = rounder
