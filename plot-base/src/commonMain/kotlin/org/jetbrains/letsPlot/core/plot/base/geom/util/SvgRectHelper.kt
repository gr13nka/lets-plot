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
//   `dataRect`   - a data-space rectangle that follows the coordinate system: an axis-aligned
//                  client box in a linear coord, a resampled polygon that curves with the coord
//                  otherwise (the isLinear check lives here, not in the geom),
//   `clientRect` - a factory that already returns client coordinates (no transform, no resampling)
//
// then emit:
//   drawTo(root)       - draws the rectangles as renderer nodes, in data-point order
//   buildNodes()       - builds the same nodes and returns them, for a geom that must control
//                        paint order itself
//   drawSlimTo(root)   - a lightweight SvgSlimGroup for large-N geoms like geom_tile (no xkcd)
// Pass a RectangleTooltipHelper to any of the above to register tooltip targets alongside the draw.

internal class SvgRectHelper private constructor(
    private val myAesthetics: Aesthetics,
    pos: PositionAdjustment,
    coord: CoordinateSystem,
    ctx: GeomContext,
    private val geometryFactory: (DataPointAesthetics) -> DoubleRectangle?,
    private val clientSpace: Boolean
) : GeomHelper(pos, coord, ctx) {

    // The client-space shape of a rectangle:
    //      axis-aligned Bounding Box (never rotated or skewed)
    //      polygon (for a data-space rect in non-linear coords) that follows the coordinate curve.
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
            tooltips?.let { addTooltipTarget(it, p, clientShape) }
            nodes.add(
                when (clientShape) {
                    is RectShape.Aabb -> ctx.renderer.rect(clientShape.rect, outlineStrokeFor(p), fillFor(p), seed = p.index())
                    is RectShape.Polygon -> ctx.renderer.path(clientShape.points, outlineStrokeFor(p), fillFor(p), seed = p.index())
                }
            )
        }
        return nodes
    }

    // Draws the rectangles as renderer nodes, placing them on `root` in data-point order.
    fun drawTo(root: SvgRoot, tooltips: RectangleTooltipHelper? = null) {
        buildNodes(tooltips).forEach(root::add)
    }

    // Lightweight slim group for large-N geoms; bypasses the renderer, so no xkcd styling
    // (a by-design perf exemption: this path emits SvgSlim* directly and never holds a Renderer).
    fun drawSlimTo(root: SvgRoot, tooltips: RectangleTooltipHelper? = null) {
        val group = SvgSlimElements.g(myAesthetics.dataPointCount())
        forEachClientShape { p, clientShape ->
            val slimShape = when (clientShape) {
                is RectShape.Aabb -> {
                    tooltips?.let { addTooltipTarget(it, p, clientShape) }
                    val r = clientShape.rect
                    SvgSlimElements.rect(r.left, r.top, r.width, r.height)
                }
                is RectShape.Polygon -> {
                    // Resampling of a tiny rectangle can still produce a very small polygon - simplify it.
                    val simplified = simplifySubPixelJitter(clientShape.points)
                    tooltips?.let { addTooltipTarget(it, p, RectShape.Polygon(simplified)) }
                    SvgSlimElements.path(SvgPathDataBuilder().lineString(simplified).build())
                }
            }
            decorateSlimShape(slimShape, p)
            slimShape.appendTo(group)
        }
        root.add(GeomBase.wrap(group))
    }

    private fun addTooltipTarget(tooltips: RectangleTooltipHelper, p: DataPointAesthetics, clientShape: RectShape) {
        when (clientShape) {
            is RectShape.Aabb -> tooltips.addRectangleTarget(p, clientShape.rect)
            is RectShape.Polygon -> tooltips.addPolygonTarget(p, clientShape.points)
        }
    }

    // The single place the coordinate space is resolved: per data point produce the client-space shape.
    private fun forEachClientShape(consume: (DataPointAesthetics, RectShape) -> Unit) {
        myAesthetics.dataPoints().forEach { p ->
            val rect = geometryFactory(p) ?: return@forEach
            val clientShape: RectShape? = when {
                clientSpace -> RectShape.Aabb(rect)
                coord.isLinear -> toClient(rect, p)?.let { RectShape.Aabb(it) }
                else -> RectShape.Polygon(resampleRectToClient(rect, p))
            }
            clientShape?.let { consume(p, it) }
        }
    }

    // Resample the rectangle outline to a client-space polygon (closed: the last point repeats the first).
    private fun resampleRectToClient(rect: DoubleRectangle, p: DataPointAesthetics): List<DoubleVector> = resampleToClient(
        listOf(
            DoubleVector(rect.left, rect.top),
            DoubleVector(rect.right, rect.top),
            DoubleVector(rect.right, rect.bottom),
            DoubleVector(rect.left, rect.bottom),
            DoubleVector(rect.left, rect.top)
        ), p
    )

    companion object {
        // Data-space rectangle that follows the coordinate system: an axis-aligned client box in a
        // linear coord, a resampled polygon that curves with the coord otherwise (the isLinear check
        // lives here, not in the geom).
        fun dataRect(
            aesthetics: Aesthetics, pos: PositionAdjustment, coord: CoordinateSystem, ctx: GeomContext,
            geometryFactory: (DataPointAesthetics) -> DoubleRectangle?
        ) = SvgRectHelper(aesthetics, pos, coord, ctx, geometryFactory, clientSpace = false)

        // Factory already returns client coordinates: no transform, no resampling.
        fun clientRect(
            aesthetics: Aesthetics, pos: PositionAdjustment, coord: CoordinateSystem, ctx: GeomContext,
            geometryFactory: (DataPointAesthetics) -> DoubleRectangle?
        ) = SvgRectHelper(aesthetics, pos, coord, ctx, geometryFactory, clientSpace = true)
    }
}
