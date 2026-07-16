/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.primitive

// The default renderer style: plain, always-straight SVG over the SvgNodeRenderer backend (smoothing
// off). Used by every geom, axis, legend and tooltip when no renderer is chosen, and reached via
// Renderer.crisp for primitives a themed geom keeps crisp (arrowhead tips — see
// RenderRecipes.buildLineWithArrow). A singleton: the style is stateless. Sibling of XkcdRenderer,
// which delegates to DistortionRenderer the same way.
object CrispRenderer : Renderer by SvgNodeRenderer(smooth = false)
