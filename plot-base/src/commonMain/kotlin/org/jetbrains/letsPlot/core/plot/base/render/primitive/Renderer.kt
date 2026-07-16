/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement

/**
 * Node factory: turns geometry + already-resolved styles into SVG nodes. Geoms and chrome
 * (axis, grid, legend, tooltip) hand it primitives — line/path/rect/circle/polygon/text — with a
 * [StrokeStyle]/[FillStyle], and get back one SvgNode to place. Drawing is owned here; callers
 * never build Svg*Element themselves. Composite shapes (pie sectors, arrowheads) are assembled
 * from these primitives by their callers, not by the renderer.
 *
 * ## Implementations (plain → shaky)
 * - [CrispRenderer] — default plain style; an `object` delegating to [SvgNodeRenderer]`(smooth = false)`.
 * - [SvgNodeRenderer] — the ONLY SVG-datamodel backend (the single place that builds concrete
 *   Svg*Element / path-data). Every other renderer ultimately draws through it.
 * - [DistortionRenderer] — the reusable "distortion" engine: outline → `distort(seed)` → backend.
 *   Carries no look of its own.
 * - `XkcdRenderer` (plot-builder) — the only concrete distortion *style*: `Renderer by
 *   DistortionRenderer(::wobble, …)`, contributing just the wobble, a corner radius and a font.
 *   So "xkcd" and "DistortionRenderer" are one style over its engine, not two renderers.
 * - [crisp] — the always-plain variant, so a themed geom can keep chosen primitives sharp
 *   (arrowhead tips) while the rest is distorted.
 *
 * ## Selection → injection
 * `theme(renderer = "crisp" | "xkcd")` → `RendererStyles.forName` yields a `RendererStyle`
 * (createRenderer + chrome adaptations + theme overlay) → `DefaultTheme` exposes `theme.renderer`
 * and `theme.chromeAdaptations`. The frame injects `theme.renderer` into the `GeomContext`
 * (`ctx.renderer`) and also hands it to axis/grid/legend/tooltip. A geom never looks a renderer
 * up — it calls `ctx.renderer.rect(…, seed = p.index())`.
 *
 * ## Styling — one source, three sinks
 * `AesStyling` (`strokeFor`/`fillFor`/`textStyleFor`) is the single aes→style owner. All three
 * paint paths pull from it and converge on `Styles.applyStyles`:
 *  1. primitive — `ctx.renderer.<primitive>(stroke, fill)`
 *  2. legacy    — `GeomHelper.decorate` (geom built the SvgShape itself)
 *  3. slim      — `GeomHelper.decorateSlimShape` (large-N)
 *
 * ## `seed`
 * A stable per-shape *identity*, NOT an RNG seed: it makes a distortion style distort the same
 * shape the same way across re-renders. Geoms pass `p.index()`, single chrome elements pass `0`.
 * Only [DistortionRenderer] reads it; plain styles ignore it. When null it falls back to hashing
 * the first outline point (stable per view, but re-randomizes under pan — hence the explicit seed).
 *
 * ## Chrome adaptations
 * A style may also ask chrome to bend to it (`ChromeAdaptation`): xkcd requests
 * `SPLIT_AXIS_LINE_AT_TICKS` (the axis is drawn per tick-gap) and `HAND_DRAWN_TOOLTIP_OUTLINE`
 * (the tooltip wobbles its outline). Font / legend-border defaults ride in as an ordinary
 * `themeOverlay`, merged UNDER user options.
 */
interface Renderer {
    fun line(p1: DoubleVector, p2: DoubleVector, stroke: StrokeStyle, seed: Int? = null): SvgNode
    // Returns exactly one SvgPathElement (one node per primitive); consumers may reuse its path data
    // (TooltipBox reads its `d`), so implementations must keep this.
    fun path(points: List<DoubleVector>, stroke: StrokeStyle?, fill: FillStyle? = null, closed: Boolean = false, seed: Int? = null): SvgPathElement
    fun rect(rect: DoubleRectangle, stroke: StrokeStyle?, fill: FillStyle? = null, seed: Int? = null): SvgNode
    fun circle(center: DoubleVector, radius: Double, stroke: StrokeStyle?, fill: FillStyle? = null, seed: Int? = null): SvgNode
    fun polygon(rings: List<List<DoubleVector>>, stroke: StrokeStyle?, fill: FillStyle? = null, seed: Int? = null): SvgNode
    fun text(origin: DoubleVector, text: String, style: TextStyle): SvgNode

    // The always-crisp variant of this renderer, for a primitive a geom draws crisp even under a themed
    // style (arrowhead tips — see RenderRecipes.buildLineWithArrow). Routing through this keeps the
    // exemption visible at the call site. A style overrides only if it needs its own crisp backend.
    val crisp: Renderer get() = CrispRenderer

    companion object {
        // Chord length for sampling circular arcs into the polylines handed to path()/polygon(),
        // shared by every arc producer (XkcdRenderer circles, PieGeom sectors) so curvature quality matches.
        const val ARC_SAMPLE_LENGTH_PX = 4.0
    }
}
