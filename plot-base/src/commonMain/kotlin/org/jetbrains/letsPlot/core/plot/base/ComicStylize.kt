/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base

import org.jetbrains.letsPlot.commons.geometry.DoubleVector

fun interface ComicStylize {
    fun apply(points: List<DoubleVector>): List<DoubleVector>

    companion object {
        val IDENTITY: ComicStylize = ComicStylize { it }
    }
}
