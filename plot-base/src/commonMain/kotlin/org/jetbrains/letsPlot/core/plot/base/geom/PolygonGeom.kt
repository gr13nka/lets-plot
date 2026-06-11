/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil
import org.jetbrains.letsPlot.core.plot.base.geom.util.LinesHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.TargetCollectorHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.fillFor
import org.jetbrains.letsPlot.core.plot.base.geom.util.strokeFor
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot

open class PolygonGeom : GeomBase() {

    override fun prepareDataPoints(dataPoints: Iterable<DataPointAesthetics>): Iterable<DataPointAesthetics> {
        return GeomUtil.with_X_Y(dataPoints)
    }

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val dataPoints = dataPoints(aesthetics)
        val linesHelper = LinesHelper(pos, coord, ctx)
        linesHelper.setResamplingEnabled(coord.isPolar)

        val targetCollectorHelper = TargetCollectorHelper(GeomKind.POLYGON, ctx)

        val renderer = ctx.rendererFactory(root)
        linesHelper.createPolygonData(dataPoints, GeomUtil.TO_LOCATION_X_Y).forEach { polygon ->
            targetCollectorHelper.addPolygons(polygon)
            renderer.drawPolygon(polygon.coordinates, strokeFor(polygon.aes, applyAlpha = false), fillFor(polygon.aes))
        }
    }

    companion object {
        const val HANDLES_GROUPS = true
    }
}
