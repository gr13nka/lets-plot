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
import org.jetbrains.letsPlot.core.plot.base.geom.GeomBase
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot
import org.jetbrains.letsPlot.core.plot.base.render.svg.lineString
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.slim.SvgSlimElements

// Construct by intent:
// `area` (a data-space region that follows the coordinate system, so it resamples
//          to a polygon in non-linear coords),
// `box` (a data-space axis-aligned box that never curves)
// `clientBox` (a factory that already returns client coordinates)
//
// then emit:
//   drawTo(root)       - draws the rectangles as renderer nodes, in data-point order
//   buildNodes()       - builds the same nodes and returns them, for a geom that must place them in
//                        its own order (only BarGeom; see the reverse there)
//   drawSlimTo(root)   - a lightweight SvgSlimGroup for large-N geoms like geom_tile (no xkcd)
//   collectTooltips(t) - registers tooltip targets only, draws nothing
// Pass a RectangleTooltipHelper to register tooltip targets alongside any draw.

internal class SvgRectHelper private constructor(
    private val myAesthetics: Aesthetics,
    pos: PositionAdjustment,
    coord: CoordinateSystem,
    ctx: GeomContext,
    private val geometryFactory: (DataPointAesthetics) -> DoubleRectangle?,
    private val shape: Shape
) : GeomHelper(pos, coord, ctx) {

    private enum class Shape { AREA, BOX, CLIENT_BOX }

    // The client-space shape of a rectangle:
    //      axis-aligned Bounding Box (never rotated or skewed)
    //      polygon (for an area in non-linear coords) that follows the coordinate curve.
    private sealed interface RectShape {
        class Aabb(val rect: DoubleRectangle) : RectShape
        class Polygon(val points: List<DoubleVector>) : RectShape
    }

    // Builds the rectangles as renderer nodes (registering tooltip targets when `tooltips` is given)
    // and returns them without placing. Use this only when the geom must control paint order itself;
    // otherwise call drawTo.
    fun buildNodes(tooltips: RectangleTooltipHelper? = null): List<SvgNode> {
        val nodes = ArrayList<SvgNode>()
        forEachClientShape { p, clientShape ->
            tooltips?.let { report(it, p, clientShape) }
            nodes.add(
                when (clientShape) {
                    is RectShape.Aabb -> ctx.renderer.rect(clientShape.rect, strokeFor(p, applyAlpha = false), fillFor(p))
                    is RectShape.Polygon -> ctx.renderer.path(clientShape.points, strokeFor(p, applyAlpha = false), fillFor(p))
                }
            )
        }
        return nodes
    }

    // Draws the rectangles as renderer nodes, placing them on `root` in data-point order.
    fun drawTo(root: SvgRoot, tooltips: RectangleTooltipHelper? = null) {
        buildNodes(tooltips).forEach(root::add)
    }

    // Lightweight slim group for large-N geoms; bypasses the renderer, so no xkcd styling.
    fun drawSlimTo(root: SvgRoot, tooltips: RectangleTooltipHelper? = null) {
        val group = SvgSlimElements.g(myAesthetics.dataPointCount())
        forEachClientShape { p, clientShape ->
            val slimShape = when (clientShape) {
                is RectShape.Aabb -> {
                    tooltips?.let { report(it, p, clientShape) }
                    val r = clientShape.rect
                    SvgSlimElements.rect(r.left, r.top, r.width, r.height)
                }
                is RectShape.Polygon -> {
                    // Resampling of a tiny rectangle can still produce a very small polygon - simplify it.
                    val simplified = PolylineSimplifier.douglasPeucker(clientShape.points)
                        .setWeightLimit(PolylineSimplifier.DOUGLAS_PEUCKER_PIXEL_THRESHOLD).points.let {
                            if (it.size != 1) {
                                println("SvgRectHelper: expected a single path, but got ${it.size}")
                            }
                            it.firstOrNull() ?: emptyList()
                        }
                    tooltips?.let { report(it, p, RectShape.Polygon(simplified)) }
                    SvgSlimElements.path(SvgPathDataBuilder().lineString(simplified).build())
                }
            }
            decorateSlimShape(slimShape, p)
            slimShape.appendTo(group)
        }
        root.add(GeomBase.wrap(group))
    }

    // Registers tooltip targets only - draws nothing.
    fun collectTooltips(tooltips: RectangleTooltipHelper) {
        forEachClientShape { p, clientShape -> report(tooltips, p, clientShape) }
    }

    // Feeds a client-space shape to the tooltip collector, dispatching rect vs polygon internally.
    private fun report(tooltips: RectangleTooltipHelper, p: DataPointAesthetics, clientShape: RectShape) {
        when (clientShape) {
            is RectShape.Aabb -> tooltips.addTarget(p, clientShape.rect)
            is RectShape.Polygon -> tooltips.addTarget(p, clientShape.points)
        }
    }

    // The single place the coordinate space is resolved: per data point produce the client-space shape.
    private fun forEachClientShape(consume: (DataPointAesthetics, RectShape) -> Unit) {
        myAesthetics.dataPoints().forEach { p ->
            val rect = geometryFactory(p) ?: return@forEach
            val clientShape: RectShape? = when (shape) {
                Shape.AREA ->
                    if (coord.isLinear) toClient(rect, p)?.let { RectShape.Aabb(it) }
                    else RectShape.Polygon(resampleRectToClient(rect, p))
                Shape.BOX -> toClient(rect, p)?.let { RectShape.Aabb(it) }
                Shape.CLIENT_BOX -> RectShape.Aabb(rect)
            }
            clientShape?.let { consume(p, it) }
        }
    }

    // Resample the rectangle outline to a client-space polygon (closed: the last point repeats the first).
    private fun resampleRectToClient(rect: DoubleRectangle, p: DataPointAesthetics): List<DoubleVector> = resample(
        precision = AdaptiveResampler.PIXEL_PRECISION,
        points = listOf(
            DoubleVector(rect.left, rect.top),
            DoubleVector(rect.right, rect.top),
            DoubleVector(rect.right, rect.bottom),
            DoubleVector(rect.left, rect.bottom),
            DoubleVector(rect.left, rect.top)
        )
    ) { toClient(it, p) }

    companion object {
        // Data-space region that follows the coordinate system (resamples to a polygon in non-linear coords).
        fun area(
            aesthetics: Aesthetics, pos: PositionAdjustment, coord: CoordinateSystem, ctx: GeomContext,
            geometryFactory: (DataPointAesthetics) -> DoubleRectangle?
        ) = SvgRectHelper(aesthetics, pos, coord, ctx, geometryFactory, Shape.AREA)

        // Data-space axis-aligned box that never curves (toClient corners only).
        fun box(
            aesthetics: Aesthetics, pos: PositionAdjustment, coord: CoordinateSystem, ctx: GeomContext,
            geometryFactory: (DataPointAesthetics) -> DoubleRectangle?
        ) = SvgRectHelper(aesthetics, pos, coord, ctx, geometryFactory, Shape.BOX)

        // Factory already returns client coordinates: no transform, no resampling.
        fun clientBox(
            aesthetics: Aesthetics, pos: PositionAdjustment, coord: CoordinateSystem, ctx: GeomContext,
            geometryFactory: (DataPointAesthetics) -> DoubleRectangle?
        ) = SvgRectHelper(aesthetics, pos, coord, ctx, geometryFactory, Shape.CLIENT_BOX)
    }
}
