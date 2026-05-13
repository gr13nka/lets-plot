/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

import org.jetbrains.letsPlot.core.plot.base.render.RendererFactory
import org.jetbrains.letsPlot.core.plot.base.render.primitive.SvgRenderer
import org.jetbrains.letsPlot.core.plot.base.theme.Theme

fun createRendererFactory(theme: Theme): RendererFactory {
    return if (theme.comic().enabled()) {
        { root -> ComicRenderer(SvgRenderer(root), XkcdWobble()) }
    } else {
        ::SvgRenderer
    }
}
