/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.commons.intern.comic

/**
 * Seeded LCG PRNG for deterministic comic rendering.
 * Produces the same wobble pattern for the same seed, ensuring
 * consistent output across renders.
 */
class ComicRandom(seed: Long) {
    private var state: Long = seed

    /**
     * Returns a pseudo-random double in [0, 1).
     */
    fun nextDouble(): Double {
        state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return (state.toDouble()) / 0x100000000L
    }

    /**
     * Returns a pseudo-random double in [-1, 1).
     */
    fun nextBipolar(): Double {
        return nextDouble() * 2.0 - 1.0
    }
}
