/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.spec.config

import org.jetbrains.letsPlot.core.spec.Option
import org.jetbrains.letsPlot.core.plot.builder.comix.ComixStylizer

class ComixStyleConfig private constructor(options: Map<String, Any>) : OptionsAccessor(options) {

    val roughness: Double = getDouble(ROUGHNESS) ?: DEF_ROUGHNESS
    val hatchingAngle: Double = getDouble(HATCHING_ANGLE) ?: DEF_HATCHING_ANGLE
    val hatchingStrokeWidth: Double = getDouble(HATCHING_STROKE_WIDTH) ?: DEF_HATCHING_STROKE_WIDTH

    companion object {
        const val DEF_ROUGHNESS = 3.0
        const val DEF_HATCHING_ANGLE = -40.0
        const val DEF_HATCHING_STROKE_WIDTH = 2.0

        private const val ROUGHNESS = "roughness"
        private const val HATCHING_ANGLE = "hatching_angle"
        private const val HATCHING_STROKE_WIDTH = "hatching_stroke_width"

        fun fromPlotSpec(plotSpec: Map<String, Any>): ComixStyleConfig? {
            @Suppress("UNCHECKED_CAST")
            val options = plotSpec[Option.Plot.COMIX_STYLE] as? Map<String, Any> ?: return null
            return ComixStyleConfig(options)
        }

        fun stylizerFromPlotSpec(plotSpec: Map<String, Any>): ComixStylizer? {
            val config = fromPlotSpec(plotSpec) ?: return null
            return ComixStylizer(
                roughness = config.roughness,
                hatchingAngle = config.hatchingAngle,
                hatchingStrokeWidth = config.hatchingStrokeWidth,
            )
        }
    }
}
