/*
 * Copyright (c) 2024. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil.toLocation
import org.jetbrains.letsPlot.core.plot.base.geom.util.HintColorUtil
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.tooltip.GeomTargetCollector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement

class SmileyGeom : GeomBase() {
    override val geomName: String = "smiley"

    var happiness: Double = DEF_HAPPINESS
        set(value) {
            field = value.coerceIn(-1.0, 1.0)
        }

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val helper = GeomHelper(pos, coord, ctx)
        val targetCollector = getGeomTargetCollector(ctx)
        val colorsByDataPoint = HintColorUtil.createColorMarkerMapper(GeomKind.SMILEY, ctx)

        val smileys = mutableListOf<Smiley>()
        for (p in aesthetics.dataPoints()) {
            val location = p.toLocation(Aes.X, Aes.Y) ?: continue
            val h = happiness
            smileys.add(Smiley(p, location, h))
        }
        addNulls(aesthetics.dataPointCount() - smileys.size)

        for (smiley in smileys) {
            root.add(smiley.createFace(helper))
            buildHint(smiley, helper, targetCollector, colorsByDataPoint)
        }
    }

    private fun buildHint(
        smiley: Smiley,
        helper: GeomHelper,
        targetCollector: GeomTargetCollector,
        colorsByDataPoint: (DataPointAesthetics) -> List<Color>
    ) {
        targetCollector.addPoint(
            smiley.point.index(),
            helper.toClient(smiley.location, smiley.point)!!,
            smiley.faceRadius,
            GeomTargetCollector.TooltipParams(
                markerColors = colorsByDataPoint(smiley.point)
            )
        )
    }

    private inner class Smiley(
        val point: DataPointAesthetics,
        val location: DoubleVector,
        val happiness: Double
    ) {
        val faceRadius: Double
            get() = AesScaling.circleDiameter(point) / 2.0

        fun createFace(helper: GeomHelper): SvgGElement {
            val client = helper.toClient(location, point)!!
            val cx = client.x
            val cy = client.y
            val r = faceRadius

            val group = SvgGElement()
            group.children().add(createFaceCircle(cx, cy, r))
            group.children().add(createEye(cx - 0.25 * r, cy - 0.2 * r, r))
            group.children().add(createEye(cx + 0.25 * r, cy - 0.2 * r, r))
            group.children().add(createMouth(cx, cy, r))
            return group
        }

        private fun createFaceCircle(cx: Double, cy: Double, r: Double): SvgCircleElement {
            val face = SvgCircleElement(cx, cy, r)
            GeomHelper.decorate(face, point, applyAlphaToAll = false, strokeScaler = AesScaling::lineWidth)
            return face
        }

        private fun createEye(ex: Double, ey: Double, faceRadius: Double): SvgCircleElement {
            val eye = SvgCircleElement(ex, ey, 0.08 * faceRadius)
            eye.fillColor().set(point.color() ?: Color.BLACK)
            eye.strokeWidth().set(0.0)
            return eye
        }

        private fun createMouth(cx: Double, cy: Double, r: Double): SvgPathElement {
            val mouthLeft = DoubleVector(cx - 0.4 * r, cy + 0.3 * r)
            val mouthRight = DoubleVector(cx + 0.4 * r, cy + 0.3 * r)
            val qcp = DoubleVector(cx, cy + 0.3 * r + happiness * 0.4 * r)

            // Convert quadratic Bézier control point to cubic:
            // CP1 = start + 2/3 * (qcp - start), CP2 = end + 2/3 * (qcp - end)
            val cp1x = mouthLeft.x + 2.0 / 3.0 * (qcp.x - mouthLeft.x)
            val cp1y = mouthLeft.y + 2.0 / 3.0 * (qcp.y - mouthLeft.y)
            val cp2x = mouthRight.x + 2.0 / 3.0 * (qcp.x - mouthRight.x)
            val cp2y = mouthRight.y + 2.0 / 3.0 * (qcp.y - mouthRight.y)

            val pathData = SvgPathDataBuilder().apply {
                moveTo(mouthLeft.x, mouthLeft.y)
                curveTo(cp1x, cp1y, cp2x, cp2y, mouthRight.x, mouthRight.y)
            }.build()

            val mouth = SvgPathElement(pathData)
            mouth.strokeColor().set(point.color())
            mouth.strokeWidth().set(AesScaling.lineWidth(point))
            mouth.fill().set(SvgColors.NONE)
            return mouth
        }
    }

    companion object {
        const val DEF_HAPPINESS = 0.5
        const val HANDLES_GROUPS = PointGeom.HANDLES_GROUPS
    }
}
