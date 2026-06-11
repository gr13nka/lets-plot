/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector

interface ComicStylize {
    fun apply(points: List<DoubleVector>, seed: Int? = null): List<DoubleVector>

    // Font family used by text in comic mode.
    val fontFamily: String

    companion object {
        // Single name, not a CSS stack: the AWT-canvas frontend can't parse a comma-separated
        // font-family. Resolved by name at render time (falls back to default if not installed).
        const val FONT_FAMILY = "Comic Sans MS"
    }
}
