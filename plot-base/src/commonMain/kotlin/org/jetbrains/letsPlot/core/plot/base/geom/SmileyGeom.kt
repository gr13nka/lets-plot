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
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.tooltip.GeomTargetCollector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape

class SmileyGeom : GeomBase() {
    override val geomName: String = "smiley"
    var happiness: Double = DEF_HAPPINESS
    var sizeUnit: String? = null

    override val legendKeyElementFactory: LegendKeyElementFactory
        get() = SmileyLegendKeyElementFactory()

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
        var goodPointsCount = 0

        for (p in aesthetics.dataPoints()) {
            val location = p.toLocation(Aes.X, Aes.Y) ?: continue
            val client = helper.toClient(location, p) ?: continue
            val sizeScale = AesScaling.sizeUnitRatio(location, coord, sizeUnit, AesScaling.POINT_UNIT_SIZE)
            val faceRadius = AesScaling.circleDiameter(p) * sizeScale / 2.0
            val happiness = effectiveHappiness(p)

            root.add(createFace(p, client.x, client.y, faceRadius, happiness))
            targetCollector.addPoint(
                p.index(),
                client,
                faceRadius + effectiveLineWidth(p) / 2.0,
                GeomTargetCollector.TooltipParams(
                    markerColors = colorsByDataPoint(p)
                )
            )
            goodPointsCount += 1
        }
        addNulls(aesthetics.dataPointCount() - goodPointsCount)
    }

    private fun effectiveHappiness(point: DataPointAesthetics): Double {
        return (point.happiness() ?: happiness).coerceIn(-1.0, 1.0)
    }

    private fun createFace(
        point: DataPointAesthetics,
        cx: Double,
        cy: Double,
        faceRadius: Double,
        happiness: Double
    ): SvgGElement {
        val group = SvgGElement()
        group.children().add(createFaceCircle(point, cx, cy, faceRadius))
        group.children().add(createEye(point, cx - 0.25 * faceRadius, cy - 0.2 * faceRadius, faceRadius))
        group.children().add(createEye(point, cx + 0.25 * faceRadius, cy - 0.2 * faceRadius, faceRadius))
        group.children().add(createMouth(point, cx, cy, faceRadius, happiness))
        return group
    }

    private fun createFaceCircle(
        point: DataPointAesthetics,
        cx: Double,
        cy: Double,
        r: Double
    ): SvgCircleElement {
        val face = SvgCircleElement(cx, cy, r)
        GeomHelper.decorate(face, point, applyAlphaToAll = false, strokeScaler= ::effectiveLineWidth)
        return face
    }

    private fun createEye(
        point: DataPointAesthetics,
        ex: Double,
        ey: Double,
        faceRadius: Double
    ): SvgCircleElement {
        val eye = SvgCircleElement(ex, ey, eyeRadius(point, faceRadius))
        eye.fillColor().set(point.color() ?: Color.BLACK)
        eye.strokeWidth().set(0.0)
        return eye
    }

    private fun createMouth(
        point: DataPointAesthetics,
        cx: Double,
        cy: Double,
        r: Double,
        happiness: Double
    ): SvgPathElement {
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

        val strokeWidth = effectiveLineWidth(point)
        val mouth = SvgPathElement(pathData)
        mouth.strokeColor().set(point.color())
        mouth.strokeWidth().set(strokeWidth)
        mouth.fill().set(SvgColors.NONE)
        mouth.setAttribute(SvgShape.STROKE_LINECAP, SvgShape.StrokeLineCap.ROUND)
        return mouth
    }

    private fun effectiveLineWidth(point: DataPointAesthetics): Double {
        return AesScaling.lineWidth(point) / 2.5
    }

    private fun eyeRadius(point: DataPointAesthetics, faceRadius: Double): Double {
        return 0.08 * faceRadius + 0.15 * effectiveLineWidth(point)
    }

    private inner class SmileyLegendKeyElementFactory : LegendKeyElementFactory {
        override fun createKeyElement(p: DataPointAesthetics, size: DoubleVector): SvgGElement {
            val strokeWidth = effectiveLineWidth(p)
            val faceRadius = (minOf(size.x, size.y) - strokeWidth) / 2.0
            return createFace(
                p,
                size.x / 2.0,
                size.y / 2.0,
                faceRadius,
                effectiveHappiness(p)
            )
        }

        override fun minimumKeySize(p: DataPointAesthetics): DoubleVector {
            val size = AesScaling.circleDiameter(p) + effectiveLineWidth(p) + 2.0
            return DoubleVector(size, size)
        }
    }

    companion object {
        const val DEF_HAPPINESS = 0.5
        const val HANDLES_GROUPS = PointGeom.HANDLES_GROUPS
    }
}