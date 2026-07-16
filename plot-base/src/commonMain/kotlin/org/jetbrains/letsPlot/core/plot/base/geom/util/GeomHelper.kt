/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler.Companion.resample
import org.jetbrains.letsPlot.core.commons.geometry.PolylineSimplifier
import org.jetbrains.letsPlot.commons.intern.util.curve
import org.jetbrains.letsPlot.commons.intern.util.padLineString
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.aes.AestheticsUtil.DEFAULT_APPLY_ALPHA_TO_ALL
import org.jetbrains.letsPlot.core.plot.base.geom.DimensionUnit
import org.jetbrains.letsPlot.core.plot.base.geom.DimensionUnit.*
import org.jetbrains.letsPlot.core.plot.base.render.primitive.applyStyles
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape
import org.jetbrains.letsPlot.datamodel.svg.dom.slim.SvgSlimShape
import kotlin.math.cos
import kotlin.math.sin

open class GeomHelper(
    protected val pos: PositionAdjustment,
    protected val coord: CoordinateSystem,
    internal val ctx: GeomContext
) {
    fun toClient(location: DoubleVector, p: DataPointAesthetics): DoubleVector? {
        return coord.toClient(adjust(location, p, pos, ctx))
    }

    fun toClient(x: Double, y: Double, p: DataPointAesthetics): DoubleVector? {
        val location = DoubleVector(x, y)
        return coord.toClient(adjust(location, p, pos, ctx))
    }

    fun toClient(r: DoubleRectangle, p: DataPointAesthetics): DoubleRectangle? {
        var clientRect = coord.toClient(adjust(r, p, pos, ctx))
        if (clientRect == null) return null

        // do not allow zero height or width (shape becomes invisible)
        if (clientRect.width == 0.0) {
            clientRect = DoubleRectangle(clientRect.origin.x, clientRect.origin.y, 0.1, clientRect.height)
        }
        if (clientRect.height == 0.0) {
            clientRect = DoubleRectangle(clientRect.origin.x, clientRect.origin.y, clientRect.width, 0.1)
        }
        return clientRect
    }

    private fun adjust(
        location: DoubleVector,
        p: DataPointAesthetics,
        pos: PositionAdjustment,
        ctx: GeomContext
    ): DoubleVector {
        return pos.translate(location, p, ctx)
    }

    private fun adjust(
        r: DoubleRectangle,
        p: DataPointAesthetics,
        pos: PositionAdjustment,
        ctx: GeomContext
    ): DoubleRectangle {
        val leftTop = pos.translate(r.origin, p, ctx)
        val rightBottom = pos.translate(r.origin.add(r.dimension), p, ctx)
        return DoubleRectangle.span(leftTop, rightBottom)
    }


    internal fun toClientLocation(aesMapper: (DataPointAesthetics) -> DoubleVector?): (DataPointAesthetics) -> DoubleVector? {
        return { aes ->
            aesMapper(aes)?.let { location -> toClient(location, aes) }
        }
    }

    fun createLineGeometryHelper(): LineGeometryHelper {
        return LineGeometryHelper(::toClient, coord)
    }

    // Pure client-space geometry factory: data-space endpoints in, client-space polylines out
    class LineGeometryHelper(
        private val toClient: (DoubleVector, DataPointAesthetics) -> DoubleVector? = { v, _ -> v },
        private val coord: CoordinateSystem? = null
    ) {
        // Follow the coordinate system by default (no coord = standalone client-space helper = straight).
        private var myResamplingEnabled = coord?.isLinear == false
        private val myResamplingPrecision = AdaptiveResampler.PIXEL_PRECISION

        // Opt out of the follow-the-coord default: keep geometry straight even in bending coords.
        fun withoutResampling() = apply { myResamplingEnabled = false }

        // Raw client-space curve, no padding — buildLineWithArrow / livemap own the arrow + padding.
        fun createCurveGeometry(
            start: DoubleVector,
            end: DoubleVector,
            curvature: Double,
            angle: Double,
            ncp: Int,
            p: DataPointAesthetics
        ): List<DoubleVector>? {
            if (start == end) return null
            val clientStart = toClient(start, p) ?: return null
            val clientEnd = toClient(end, p) ?: return null
            return curve(clientStart, clientEnd, curvature, angle, ncp)
        }

        // Padded client-space line: the polyline pulled back by the target start/end spacing.
        fun createPaddedLineGeometry(
            start: DoubleVector,
            end: DoubleVector,
            p: DataPointAesthetics,
        ): List<DoubleVector>? {
            val lineString = createLineGeometry(start, end, p) ?: return null
            if (lineString.size < 2) return null
            return padLineString(lineString, AesScaling.targetStartSize(p), AesScaling.targetEndSize(p))
        }

        // Padded client-space spoke polyline
        fun createSpokeGeometry(
            base: DoubleVector,
            angle: Double,
            radius: Double,
            pivot: Double,
            p: DataPointAesthetics,
        ): List<DoubleVector>? {
            val (start, end) = spokeEndpoints(base, angle, radius, pivot)
            return createPaddedLineGeometry(start, end, p)
        }

        internal fun createLineGeometry(
            start: DoubleVector,
            end: DoubleVector,
            aes: DataPointAesthetics,
        ): List<DoubleVector>? {
            return createLineGeometry(listOf(start, end), aes)
        }

        internal fun createLineGeometry(
            points: List<DoubleVector>,
            aes: DataPointAesthetics,
        ): List<DoubleVector>? {
            if (myResamplingEnabled) {
                return resample(points, myResamplingPrecision) { toClient(it, aes) }
            } else {
                return points.map { toClient(it, aes) ?: return null }
            }
        }

        companion object {
            // Endpoints of a spoke of length `radius` at `angle`, anchored at `base`; `pivot` in [0, 1]
            // slides the anchor from the spoke's tail (0) to its tip (1). Data-space; no coordinate transform.
            fun spokeEndpoints(
                base: DoubleVector,
                angle: Double,
                radius: Double,
                pivot: Double
            ): Pair<DoubleVector, DoubleVector> {
                val spoke = DoubleVector(radius * cos(angle), radius * sin(angle))
                return base.subtract(spoke.mul(pivot)) to base.add(spoke.mul(1 - pivot))
            }
        }
    }

    fun getUnitResolution(
        unit: DimensionUnit,
        axisAes: Aes<Double>
    ): Double {
        val unitSize = when (axisAes) {
            Aes.X -> coord.unitSize(DoubleVector(1.0, 0.0)).x
            Aes.Y -> coord.unitSize(DoubleVector(0.0, 1.0)).y
            else -> error("Unsupported axis aes: $axisAes")
        }
        return when (unit) {
            RESOLUTION -> ctx.getResolution(axisAes) // i.e. the minimum distance between data points
            IDENTITY -> 1.0 // distance from 0 to 1 on the axis
            SIZE -> {
                // diameter of a point of size 1 (in standard point size units)
                AesScaling.POINT_UNIT_SIZE / unitSize
            }
            PIXEL -> {
                1.0 / unitSize
            }
        }
    }

    // The single resample step for area-like geoms drawn as polygons: resample the data-space
    // outline to a client-space polyline that follows the coordinate curve. Owned here so rect/hex
    // don't each re-encode it. Callers pair it with simplifySubPixelJitter (below) to Douglas-Peucker
    // away the sub-pixel jitter resampling can leave.
    protected fun resampleToClient(points: List<DoubleVector>, p: DataPointAesthetics): List<DoubleVector> =
        resample(precision = AdaptiveResampler.PIXEL_PRECISION, points = points) { toClient(it, p) }

    // Unconditional Douglas-Peucker pass: removes the sub-pixel jitter
    // resampling can leave on any polygon.
    protected fun simplifySubPixelJitter(points: List<DoubleVector>): List<DoubleVector> =
        PolylineSimplifier.douglasPeucker(points)
            .setWeightLimit(PolylineSimplifier.DOUGLAS_PEUCKER_PIXEL_THRESHOLD).points
            .firstOrNull() ?: emptyList()

    companion object {
        fun decorate(
            shape: SvgShape,
            p: DataPointAesthetics,
            applyAlphaToAll: Boolean = DEFAULT_APPLY_ALPHA_TO_ALL,
            strokeScaler: (DataPointAesthetics) -> Double = AesScaling::strokeWidth,
            filled: Boolean = true
        ) {
            // One aes-to-paint owner (AesStyling) drives the Renderer path, this legacy path, and the slim path.
            shape.applyStyles(
                stroke = strokeFor(p, applyAlpha = applyAlphaToAll, strokeScaler = strokeScaler),
                fill = if (filled) fillFor(p) else null
            )
        }

        internal fun decorateSlimShape(
            shape: SvgSlimShape,
            p: DataPointAesthetics,
            applyAlphaToAll: Boolean = DEFAULT_APPLY_ALPHA_TO_ALL
        ) {
            // Route through AesStyling too, so the slim path can't drift from decorate / the Renderer.
            shape.applyStyles(
                stroke = strokeFor(p, applyAlpha = applyAlphaToAll),
                fill = fillFor(p)
            )
        }
    }
}