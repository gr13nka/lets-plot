/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.core.plot.base.DataPointAesthetics
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle

internal fun strokeFor(p: DataPointAesthetics): StrokeStyle =
    StrokeStyle(
        color = p.color(),
        alpha = p.alpha(),
        width = AesScaling.strokeWidth(p),
        lineType = p.lineType()
    )
