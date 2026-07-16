/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.theme

import org.jetbrains.letsPlot.core.plot.base.GeomKind
import org.jetbrains.letsPlot.core.plot.base.aes.GeomTheme
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer

interface Theme {
    val fontFamilyRegistry: FontFamilyRegistry

    val exponentFormat: ExponentFormat

    // The renderer that draws this plot's primitives chosen by the `renderer` theme option.
    val renderer: Renderer

    // The chrome-geometry adaptations the renderer style asks for (declared by its RendererStyle
    // registration); read only by the chrome components that must adapt (axis-line splitting for
    // dash-safe anchoring, tooltip outline choice); geoms never see it.
    val chromeAdaptations: Set<ChromeAdaptation> get() = emptySet()

    fun horizontalAxis(flipAxis: Boolean): AxisTheme

    fun verticalAxis(flipAxis: Boolean): AxisTheme

    fun legend(): LegendTheme

    fun facets(): FacetsTheme

    fun plot(): PlotTheme

    fun panel(): PanelTheme

    fun tooltips(): TooltipsTheme

    fun annotations(): AnnotationsTheme

    fun geometries(geomKind: GeomKind): GeomTheme

    fun colors(): ColorTheme
}
