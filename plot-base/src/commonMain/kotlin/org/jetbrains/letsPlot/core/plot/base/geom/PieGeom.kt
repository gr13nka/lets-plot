/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.intern.math.toRadians
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler.Companion.resample
import org.jetbrains.letsPlot.commons.interval.DoubleSpan
import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.geom.annotation.PieAnnotation
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomHelper
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil
import org.jetbrains.letsPlot.core.plot.base.geom.util.HintColorUtil
import org.jetbrains.letsPlot.core.plot.base.geom.util.fillFor
import org.jetbrains.letsPlot.core.plot.base.render.LegendKeyElementFactory
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.render.linetype.NamedLineType
import org.jetbrains.letsPlot.core.plot.base.render.primitive.FillStyle
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.StrokeStyle
import org.jetbrains.letsPlot.core.plot.base.tooltip.GeomTargetCollector
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PieGeom : GeomBase(), WithWidth, WithHeight {
    var holeSize: Double = 0.0
    var spacerWidth: Double = 0.75
    var spacerColor: Color = Color.WHITE
    var strokeSide: StrokeSide = StrokeSide.BOTH
    var sizeUnit: String? = null
    var start: Double? = null
    var clockwise: Boolean = true

    enum class StrokeSide {
        OUTER, INNER, BOTH;

        val hasOuter: Boolean
            get() = this == OUTER || this == BOTH

        val hasInner: Boolean
            get() = this == INNER || this == BOTH
    }

    override val legendKeyElementFactory: LegendKeyElementFactory
        get() = PieLegendKeyElementFactory()

    override fun buildIntern(
        root: SvgRoot,
        aesthetics: Aesthetics,
        pos: PositionAdjustment,
        coord: CoordinateSystem,
        ctx: GeomContext
    ) {
        val geomHelper = GeomHelper(pos, coord, ctx)
        val renderer = ctx.rendererFactory(root)
        GeomUtil.withDefined(aesthetics.dataPoints(), Aes.X, Aes.Y, Aes.SLICE)
            .groupBy { p -> DoubleVector(p.x()!!, p.y()!!) }
            .forEach { (point, dataPoints) ->
                val sizeUnitRatio = AesScaling.sizeUnitRatio(point, coord, sizeUnit, AesScaling.PIE_UNIT_SIZE)

                val toLocation = { p: DataPointAesthetics -> geomHelper.toClient(point, p) }
                val pieSectors = computeSectors(dataPoints, toLocation, sizeUnitRatio)

                // Fills first, then arc strokes on top, preserving the original two-pass z-order.
                pieSectors.forEach { sector ->
                    renderer.drawSector(
                        sector.position, sector.holeRadius, sector.radius,
                        sector.startAngle, sector.endAngle,
                        fill = fillStyleFor(sector)
                    )
                }
                pieSectors.forEach { sector -> drawArcStrokes(renderer, sector) }
                if (spacerWidth > 0) {
                    drawSpacerLines(renderer, pieSectors, width = spacerWidth, color = spacerColor)
                }

                pieSectors.forEach { buildHint(it, ctx.targetCollector) }

                ctx.annotation?.let { PieAnnotation.build(root, pieSectors, ctx) }
            }
    }

    // Fill convention: keep the color, carry opacity in the alpha channel (fillFor).
    private fun fillStyleFor(sector: Sector): FillStyle = fillFor(sector.p)

    private fun drawArcStrokes(renderer: Renderer, sector: Sector) {
        if (sector.strokeWidth <= 0.0) return
        val stroke = StrokeStyle(
            color = sector.p.color(),
            alpha = null,
            width = sector.strokeWidth,
            lineType = NamedLineType.SOLID
        )
        if (strokeSide.hasOuter) {
            renderer.drawArc(sector.position, sector.radius, sector.startAngle, sector.endAngle, stroke)
        }
        // A zero-radius inner "arc" is invisible in SVG but would collapse to a blob once wobbled.
        if (strokeSide.hasInner && sector.holeRadius > 0.0) {
            renderer.drawArc(sector.position, sector.holeRadius, sector.startAngle, sector.endAngle, stroke)
        }
    }

    private fun drawSpacerLines(renderer: Renderer, pieSectors: List<Sector>, width: Double, color: Color) {
        val stroke = StrokeStyle(color = color, alpha = null, width = width, lineType = NamedLineType.SOLID)

        // Do not draw spacer lines for exploded sectors and their neighbors

        val explodedSectors = pieSectors.mapIndexedNotNull { index, sector ->
            index.takeIf { sector.position != sector.pieCenter }
        }

        fun needAddAtStart(index: Int) = when (index) {
            in explodedSectors -> false
            0 -> pieSectors.lastIndex !in explodedSectors
            else -> index - 1 !in explodedSectors
        }

        fun needAddAtEnd(index: Int) = when (index) {
            in explodedSectors -> false
            pieSectors.lastIndex -> 0 !in explodedSectors
            else -> index + 1 !in explodedSectors
        }

        pieSectors.forEachIndexed { index, sector ->
            if (needAddAtStart(index)) {
                renderer.drawLine(sector.innerStrokeStartPoint, sector.outerStrokeStartPoint, stroke)
            }
            if (needAddAtEnd(index)) {
                renderer.drawLine(sector.innerStrokeEndPoint, sector.outerStrokeEndPoint, stroke)
            }
        }
    }

    private fun buildHint(sector: Sector, targetCollector: GeomTargetCollector) {
        fun resampleArc(outerArc: Boolean): List<DoubleVector> {
            val arcPoint = when (outerArc) {
                true -> { angle: Double -> sector.outerArcPointWithStroke(angle) }
                false -> { angle: Double -> sector.innerArcPointWithStroke(angle) }
            }

            val startPoint = when (outerArc) {
                true -> sector.outerStrokeStartPoint
                false -> sector.innerStrokeStartPoint
            }

            val endPoint = when (outerArc) {
                true -> sector.outerStrokeEndPoint
                false -> sector.innerStrokeEndPoint
            }

            val segmentLength = startPoint.subtract(endPoint).length()

            return resample(startPoint, endPoint, AdaptiveResampler.PIXEL_PRECISION) { p: DoubleVector ->
                val ratio = p.subtract(startPoint).length() / segmentLength
                if (ratio.isFinite()) {
                    arcPoint(sector.startAngle + sector.angle * ratio)
                } else {
                    p
                }
            }
        }

        targetCollector.addPolygon(
            points = resampleArc(outerArc = true) + resampleArc(outerArc = false).reversed(),
            index = sector.p.index(),
            GeomTargetCollector.TooltipParams(
                markerColors = listOf(
                    HintColorUtil.applyAlpha(sector.p.fill()!!, sector.p.alpha()!!)
                )
            )
        )
    }

    private fun computeSectors(
        dataPoints: List<DataPointAesthetics>,
        toLocation: (DataPointAesthetics) -> DoubleVector?,
        sizeUnitRatio: Double
    ): List<Sector> {
        val sum = dataPoints.sumOf { abs(it.slice()!!) }

        fun angle(p: DataPointAesthetics) = when (sum) {
            0.0 -> 1.0 / dataPoints.size
            else -> abs(p.slice()!!) / sum
        }.let { PI * 2.0 * it }

        val startAngle = if (start != null) {
            toRadians(start!!)
        } else {
            // the first slice goes to the left of 12 o'clock and others go clockwise
            angle(dataPoints.first()) * (if (clockwise) -1 else 1)
        }

        // Starts at 12 o'clock
        var currentAngle = -PI / 2.0 + startAngle

        return (dataPoints.takeIf { clockwise } ?: dataPoints.reversed()).mapNotNull { p ->
            val pieCenter = toLocation(p) ?: return@mapNotNull null
            Sector(
                p = p,
                pieCenter = pieCenter,
                startAngle = currentAngle,
                endAngle = currentAngle + angle(p),
                sizeUnitRatio = sizeUnitRatio
            ).also { sector -> currentAngle = sector.endAngle }
        }
    }

    inner class Sector(
        val pieCenter: DoubleVector,
        val p: DataPointAesthetics,
        val startAngle: Double,
        val endAngle: Double,
        sizeUnitRatio: Double
    ) {
        val angle = endAngle - startAngle
        val strokeWidth = p.stroke()?.takeIf { p.color()?.alpha != 0 } ?: 0.0
        private val hasVisibleStroke = strokeWidth > 0.0
        val radius: Double = sizeUnitRatio * AesScaling.pieDiameter(p) / 2
        val holeRadius = radius * holeSize
        val direction = startAngle + angle / 2
        private val explode = p.explode()?.let { radius * it } ?: 0.0
        val position = pieCenter.add(DoubleVector(explode * cos(direction), explode * sin(direction)))
        private val fullCircleDrawingFix = if (angle % (2 * PI) == 0.0) 0.0001 else 0.0

        val outerArcStart = arcPoint(radius, startAngle)
        val outerArcEnd = arcPoint(radius, endAngle - fullCircleDrawingFix)

        val innerArcStart = arcPoint(holeRadius, startAngle)
        val innerArcEnd = arcPoint(holeRadius, endAngle - fullCircleDrawingFix)

        val outerStrokeStartPoint = outerArcPointWithStroke(startAngle)
        val outerStrokeEndPoint = outerArcPointWithStroke(endAngle - fullCircleDrawingFix)

        val innerStrokeStartPoint = innerArcPointWithStroke(startAngle)
        val innerStrokeEndPoint = innerArcPointWithStroke(endAngle - fullCircleDrawingFix)

        fun outerArcPointWithStroke(angle: Double) = arcPoint(
            radius = when (strokeSide.hasOuter && hasVisibleStroke) {
                true -> radius + strokeWidth / 2
                false -> radius
            },
            angle = angle
        )

        fun innerArcPointWithStroke(angle: Double) = arcPoint(
            radius = when (strokeSide.hasInner && hasVisibleStroke && holeSize > 0) {
                true -> holeRadius - strokeWidth / 2
                false -> holeRadius
            },
            angle = angle
        )

        private fun arcPoint(radius: Double, angle: Double): DoubleVector {
            return position.add(DoubleVector(radius * cos(angle), radius * sin(angle)))
        }

        val sectorCenter: DoubleVector // center of the pie slice geometry
            get() {
                val offset = holeRadius + 0.5 * (radius - holeRadius)
                return arcPoint(offset, direction)
            }
    }

    private inner class PieLegendKeyElementFactory : LegendKeyElementFactory {
        override fun createKeyElement(p: DataPointAesthetics, size: DoubleVector): SvgGElement {
            return SvgGElement().apply {
                children().add(
                    SvgCircleElement(
                        size.x / 2,
                        size.y / 2,
                        shapeSize(p) / 2
                    ).apply {
                        fillColor().set(p.fill())
                        strokeColor().set(p.color())
                        strokeWidth().set(p.stroke())
                    }
                )
            }
        }

        override fun minimumKeySize(p: DataPointAesthetics): DoubleVector {
            val shapeSize = shapeSize(p)
            val size = shapeSize + 4.0
            return DoubleVector(size, size)
        }

        private fun shapeSize(p: DataPointAesthetics) = AesScaling.pieDiameter(p)
    }

    companion object {
        const val HANDLES_GROUPS = false
    }

    override fun widthSpan(
        p: DataPointAesthetics,
        coordAes: Aes<Double>,
        resolution: Double,
        isDiscrete: Boolean
    ): DoubleSpan? {
        if (!isDiscrete) return null
        return DimensionsUtil.dimensionSpan(p, coordAes, Aes.SIZE, 1.0, DimensionUnit.RESOLUTION)
    }

    override fun heightSpan(
        p: DataPointAesthetics,
        coordAes: Aes<Double>,
        resolution: Double,
        isDiscrete: Boolean
    ): DoubleSpan? {
        if (!isDiscrete) return null
        return DimensionsUtil.dimensionSpan(p, coordAes, Aes.SIZE, 1.0, DimensionUnit.RESOLUTION)
    }
}
