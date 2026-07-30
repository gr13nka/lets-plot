/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.defaultTheme

import org.jetbrains.letsPlot.core.plot.base.render.primitive.CrispStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.DrawingStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.XkcdStyle

/**
 * The set of `theme(geom_style=...)` names, and the only place a name maps to a DrawingStyle.
 * Single owner of both facts mirrored into the `geom_style` docstring in `theme_.py`:
 * - the name set: adding one takes two edits — an entry here, and that docstring;
 * - the coverage contract: styles apply only to geometry routed through the geom/util
 *   helpers' DrawingStyle seam; paths built directly or slim-svg rendering bypass it.
 *   Keep `theme_.py`'s coverage sentence in sync.
 */
object DrawingStyles {
    const val CRISP = "crisp"
    const val XKCD = "xkcd"

    private val BY_NAME: Map<String, DrawingStyle> = mapOf(
        CRISP to CrispStyle,
        XKCD to XkcdStyle
    )

    val names: Set<String> get() = BY_NAME.keys

    fun forName(name: String): DrawingStyle {
        return BY_NAME[name] ?: throw IllegalArgumentException(
            "Unknown geom_style: '$name'.\nExpected one of: ${names.joinToString("|")}."
        )
    }
}
