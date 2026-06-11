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
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.render.svg.lineString
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.slim.SvgSlimElements
import org.jetbrains.letsPlot.datamodel.svg.dom.slim.SvgSlimGroup

class RectanglesHelper(
    private val myAesthetics: Aesthetics,
    pos: PositionAdjustment,
    coord: CoordinateSystem,
    ctx: GeomContext,
    private val geometryFactory: (DataPointAesthetics) -> DoubleRectangle?
) : GeomHelper(pos, coord, ctx) {
    // TODO: Replace with SvgRectHelper
    fun createNonLinearRectangles(handler: (DataPointAesthetics, List<DoubleVector>) -> Unit) {
        myAesthetics.dataPoints().forEach { p ->
            geometryFactory(p)?.let { rect ->
                val polyRect = resample(
                    precision = AdaptiveResampler.PIXEL_PRECISION,
                    points = listOf(
                        DoubleVector(rect.left, rect.top),
                        DoubleVector(rect.right, rect.top),
                        DoubleVector(rect.right, rect.bottom),
                        DoubleVector(rect.left, rect.bottom),
                        DoubleVector(rect.left, rect.top)
                    )
                ) { toClient(it, p) }

                handler(p, polyRect)
            }
        }
    }

    fun createRectangles(handler: (DataPointAesthetics, DoubleRectangle) -> Unit) {
        myAesthetics.dataPoints().forEach { p ->
            geometryFactory(p)?.let { rect ->
                val clientRect = toClient(rect, p) ?: return@let
                handler(p, clientRect)
            }
        }
    }

    fun createRectangles(): MutableList<SvgNode> {
        val result = ArrayList<SvgNode>()

        for (index in 0 until myAesthetics.dataPointCount()) {
            val p = myAesthetics.dataPointAt(index)
            val clientRect = geometryFactory(p) ?: continue

            val svgRect = SvgRectElement(clientRect)
            decorate(svgRect, p)

            result.add(svgRect)
        }

        return result
    }

    fun createSvgRectHelper(): SvgRectHelper {
        return SvgRectHelper()
    }

    inner class SvgRectHelper {
        private var onGeometry: (DataPointAesthetics, DoubleRectangle?, List<DoubleVector>?) -> Unit = { _, _, _ -> }
        private var myResamplingEnabled = false
        private var myResamplingPrecision = AdaptiveResampler.PIXEL_PRECISION

        fun setResamplingEnabled(b: Boolean) {
            myResamplingEnabled = b
        }

        fun setResamplingPrecision(precision: Double) {
            myResamplingPrecision = precision
        }

        fun onGeometry(handler: (DataPointAesthetics, DoubleRectangle?, List<DoubleVector>?) -> Unit) {
            onGeometry = handler
        }

        fun drawRectangles(renderer: Renderer) {
            val pointCount = myAesthetics.dataPointCount()
            for (index in 0 until pointCount) {
                val p = myAesthetics.dataPointAt(index)
                val rect = geometryFactory(p) ?: continue

                if (myResamplingEnabled) {
                    val polyRect = resample(
                        precision = myResamplingPrecision,
                        points = listOf(
                            DoubleVector(rect.left, rect.top),
                            DoubleVector(rect.right, rect.top),
                            DoubleVector(rect.right, rect.bottom),
                            DoubleVector(rect.left, rect.bottom),
                            DoubleVector(rect.left, rect.top)
                        )
                    ) { toClient(it, p) }

                    // Resampling of a tiny rectangle still can produce a very small polygon - simplify it.
                    val simplified = PolylineSimplifier.douglasPeucker(polyRect).setWeightLimit(PolylineSimplifier.DOUGLAS_PEUCKER_PIXEL_THRESHOLD).points.let {
                        if (it.size != 1) {
                            println("RectanglesHelper: expected a single path, but got ${it.size}")
                        }

                        it.firstOrNull() ?: emptyList()
                    }

                    onGeometry(p, null, simplified)
                    renderer.drawPath(simplified, strokeFor(p, applyAlpha = false), fillFor(p), closed = true)
                } else {
                    val clientRect = toClient(rect, p) ?: continue

                    onGeometry(p, clientRect, null)
                    renderer.drawRect(clientRect, strokeFor(p, applyAlpha = false), fillFor(p))
                }
            }
        }

        fun createSlimRectangles(): SvgSlimGroup {
            val pointCount = myAesthetics.dataPointCount()
            val group = SvgSlimElements.g(pointCount)

            for (index in 0 until pointCount) {
                val p = myAesthetics.dataPointAt(index)
                val rect = geometryFactory(p) ?: continue

                if (myResamplingEnabled) {
                    val polyRect = resample(
                        precision = myResamplingPrecision,
                        points = listOf(
                            DoubleVector(rect.left, rect.top),
                            DoubleVector(rect.right, rect.top),
                            DoubleVector(rect.right, rect.bottom),
                            DoubleVector(rect.left, rect.bottom),
                            DoubleVector(rect.left, rect.top)
                        )
                    ) { toClient(it, p) }

                    // Resampling of a tiny rectangle still can produce a very small polygon - simplify it.
                    val simplified = PolylineSimplifier.douglasPeucker(polyRect).setWeightLimit(PolylineSimplifier.DOUGLAS_PEUCKER_PIXEL_THRESHOLD).points.let {
                        if (it.size != 1) {
                            println("RectanglesHelper: expected a single path, but got ${it.size}")
                        }

                        it.firstOrNull() ?: emptyList()
                    }

                    onGeometry(p, null, simplified)

                    val slimShape = SvgSlimElements.path(SvgPathDataBuilder().lineString(simplified).build())
                    decorateSlimShape(slimShape, p)
                    slimShape.appendTo(group)
                } else {
                    val clientRect = toClient(rect, p) ?: continue

                    onGeometry(p, clientRect, null)

                    val slimShape = SvgSlimElements.rect(clientRect.left, clientRect.top, clientRect.width, clientRect.height)
                    decorateSlimShape(slimShape, p)
                    slimShape.appendTo(group)
                }

            }
            return group
        }
    }
}
