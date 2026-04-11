/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.commons.intern.comic

/**
 * Configuration for comic-style rendering.
 *
 * @param roughness Overall wobble amplitude multiplier (default 3.0)
 * @param bowing How much lines bow/curve (default 1.0)
 * @param hatchAngle Angle of hatching lines in degrees (default -41.0)
 * @param hatchGap Gap between hatch lines in pixels (default 6.0)
 * @param hatchStrokeWidth Stroke width of hatch lines (default 2.0)
 * @param seed Random seed for deterministic output (default 42)
 * @param comicFont Font family name for comic-style text (default "Comic Neue")
 */
data class ComicConfig(
    val roughness: Double = DEF_ROUGHNESS,
    val bowing: Double = DEF_BOWING,
    val hatchAngle: Double = DEF_HATCH_ANGLE,
    val hatchGap: Double = DEF_HATCH_GAP,
    val hatchStrokeWidth: Double = DEF_HATCH_STROKE_WIDTH,
    val seed: Long = DEF_SEED,
    val comicFont: String = DEF_COMIC_FONT
) {
    companion object {
        private const val RENDERING_STYLE_KEY = "rendering_style"
        private const val NAME_KEY = "name"
        private const val DEF_ROUGHNESS = 3.0
        private const val DEF_BOWING = 1.0
        private const val DEF_HATCH_ANGLE = -41.0
        private const val DEF_HATCH_GAP = 6.0
        private const val DEF_HATCH_STROKE_WIDTH = 2.0
        private const val DEF_SEED = 42L
        private const val DEF_COMIC_FONT = "Comic Neue"

        /**
         * Parses a ComicConfig from a plot spec map.
         * Returns null if no `rendering_style` key is present or its name is not "comic".
         */
        fun fromSpec(spec: Map<String, Any>): ComicConfig? {
            val renderingStyle = spec[RENDERING_STYLE_KEY] as? Map<*, *> ?: return null
            val name = renderingStyle[NAME_KEY] as? String ?: return null
            if (name != "comic") return null

            return ComicConfig(
                roughness = (renderingStyle["roughness"] as? Number)?.toDouble() ?: DEF_ROUGHNESS,
                bowing = (renderingStyle["bowing"] as? Number)?.toDouble() ?: DEF_BOWING,
                hatchAngle = (renderingStyle["hatch_angle"] as? Number)?.toDouble() ?: DEF_HATCH_ANGLE,
                hatchGap = (renderingStyle["hatch_gap"] as? Number)?.toDouble() ?: DEF_HATCH_GAP,
                hatchStrokeWidth = (renderingStyle["hatch_stroke_width"] as? Number)?.toDouble() ?: DEF_HATCH_STROKE_WIDTH,
                seed = (renderingStyle["seed"] as? Number)?.toLong() ?: DEF_SEED,
                comicFont = renderingStyle["font"] as? String ?: DEF_COMIC_FONT
            )
        }
    }
}
