/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

internal class Lcg(seed: Int) {
    private var state: Int = seed
    fun nextDouble(): Double {
        state = 1664525 * state + 1013904223
        return (state.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}
