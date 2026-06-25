/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.defaultTheme

import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.SvgRenderer
import org.jetbrains.letsPlot.core.plot.builder.defaultTheme.values.ThemeOption
import org.jetbrains.letsPlot.core.plot.builder.xkcd.XkcdRenderer

internal object Renderers {
    fun forName(name: String?): Renderer = when (name) {
        null, ThemeOption.Renderer.NONE -> SvgRenderer()
        ThemeOption.Renderer.XKCD -> XkcdRenderer()
        else -> throw IllegalArgumentException(
            "Unsupported renderer: '$name'. Expected: ${ThemeOption.Renderer.NONE}|${ThemeOption.Renderer.XKCD}."
        )
    }
}
