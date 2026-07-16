/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.geom.util

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.intern.splitBy
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.*
import org.jetbrains.letsPlot.commons.intern.typedGeometry.algorithms.AdaptiveResampler.Companion.PIXEL_PRECISION
import org.jetbrains.letsPlot.commons.intern.util.VectorAdapter
import org.jetbrains.letsPlot.core.commons.geometry.PolylineSimplifier.Companion.DOUGLAS_PEUCKER_PIXEL_THRESHOLD
import org.jetbrains.letsPlot.core.commons.geometry.PolylineSimplifier.Companion.douglasPeucker
import org.jetbrains.letsPlot.core.plot.base.*
import org.jetbrains.letsPlot.core.plot.base.aes.AesScaling
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil.createPathDataFromRectangle
import org.jetbrains.letsPlot.core.plot.base.geom.util.GeomUtil.createPaths
import org.jetbrains.letsPlot.core.plot.base.render.SvgRoot

open class LinesHelper(
    pos: PositionAdjustment,
    coord: CoordinateSystem,
    ctx: GeomContext,
    flat: Boolean = false,
    private val counter: (Int) -> Unit = {} // todo: remove default counter
) : GeomHelper(pos, coord, ctx) {

    // Follow the coordinate system by default; the `flat` ctor flag (or withoutResampling()) is the opt-out.
    protected var myResamplingEnabled = !coord.isLinear && !flat
    protected var myResamplingPrecision = PIXEL_PRECISION

    // Polar coordinate system with discrete X scale.
    fun meetsRadarPlotReq(): Boolean {
        return coord.isPolar && ctx.plotContext.hasScale(Aes.X) && !ctx.plotContext.getScale(Aes.X).isContinuous
    }

    // for test only (force-enables resampling in linear coords; production relies on the ctor default)
    fun setResamplingEnabled(resample: Boolean) {
        this.myResamplingEnabled = resample
    }

    // Opt out of the follow-the-coord default: keep geometry straight even in bending coords.
    fun withoutResampling() {
        this.myResamplingEnabled = false
    }

    // for test only
    internal fun setResamplingPrecision(precision: Double) {
       this.myResamplingPrecision = precision
    }

    fun createPathData(
        dataPoints: Iterable<DataPointAesthetics>,
        locationTransform: (DataPointAesthetics) -> DoubleVector? = GeomUtil.TO_LOCATION_X_Y,
        closePath: Boolean = false,
    ): List<PathData> {
        val domainData = createPaths(
            dataPoints,
            locationTransform, sorted = true, closePath = closePath, nullsCounter = counter)
        return toClientPaths(domainData)
    }

    // Draw the paths as open lines onto `root`. When following a bending coord, the resampled
    // geometry is display-simplified first (Douglas-Peucker); straight geometry is drawn as-is.
    // The geom keeps its own target-collector call: geom_step collects pre-step geometry, so
    // folding it in here would change tooltip hit-testing.
    fun renderPaths(root: SvgRoot, paths: List<PathData>) {
        for (path in paths) {
            val visual = if (myResamplingEnabled) {
                douglasPeucker(path.coordinates, DOUGLAS_PEUCKER_PIXEL_THRESHOLD)
            } else {
                path.coordinates
            }
            root.add(ctx.renderer.path(visual, strokeFor(path.aes), closed = false, seed = path.aes.index()))
        }
    }

    // Client-space polygons (rings) for the Renderer, the polygon analog of createPathData.
    fun createPolygonData(
        dataPoints: Iterable<DataPointAesthetics>,
        locationTransform: (DataPointAesthetics) -> DoubleVector? = GeomUtil.TO_LOCATION_X_Y,
    ): List<PolygonData> {
        val domainPathData = createPaths(dataPoints, locationTransform, sorted = true, closePath = false, nullsCounter = counter)

        return toClientPolygons(domainPathData)
    }

    // The rect-polygon analog of createPolygonData: one polygon per data point, from a
    // multi-point transform
    fun createRectPolygonData(
        dataPoints: Iterable<DataPointAesthetics>,
        locationTransform: (DataPointAesthetics) -> List<DoubleVector>?,
    ): List<PolygonData> {
        val domainPathData = createPathDataFromRectangle(dataPoints, locationTransform)

        return toClientPolygons(domainPathData)
    }

    private fun toClientPolygons(domainPathData: Collection<PathData>): List<PolygonData> {
        // split in domain space! after resampling coordinates may repeat and splitRings will return wrong results
        val domainPolygonData = domainPathData
            .map { splitRings(it.points, PathPoint.LOC_EQ) }
            .mapNotNull { PolygonData.create(it) }

        return domainPolygonData.mapNotNull { polygon ->
            polygon.rings
                .map { if (myResamplingEnabled) resample(it) else toClient(it) }
                .let { PolygonData.create(it) }
        }
    }

    private fun resample(linestring: List<PathPoint>): List<PathPoint> {

        fun resampler(aes: DataPointAesthetics): AdaptiveResampler<PathPoint> {
            val adapter = object : VectorAdapter<PathPoint> {
                override fun x(p: PathPoint) = p.coord.x
                override fun y(p: PathPoint) = p.coord.y
                override fun create(x: Double, y: Double) = PathPoint(aes, DoubleVector(x, y))
            }
            return AdaptiveResampler.generic(myResamplingPrecision, adapter) { p: PathPoint ->
                toClient(p.coord, aes)?.let { PathPoint(aes, it) }
            }
        }
        val smoothed = mutableListOf<PathPoint>()

        linestring.windowed(size = 2).forEach { (p1, p2) ->
            // It is important to use the aes of the first element in each pair,
            // since aes may change depending on the group
            // see https://github.com/JetBrains/lets-plot/issues/1375
            val resampler = resampler(p1.aes)

            val resampledPoints = resampler.resample(p1, p2)

            // Return empty list if one of the points could not be transformed to client coordinates
            if (resampledPoints.isEmpty()) {
                return emptyList()
            }

            smoothed.addAll(resampledPoints.subList(0, resampledPoints.size - 1)) // Do not add the last point to avoid duplicates
        }

        // smoothed path doesn't contain PathPoint for the last point - append it
        val endPoint = linestring.last()
        val endCoord = toClient(endPoint.coord, endPoint.aes)
        if (endCoord != null) {
            smoothed.add(PathPoint(endPoint.aes, endCoord))
        }

        return smoothed
    }

    private fun toClient(linestring: List<PathPoint>): List<PathPoint> {
        return linestring.mapNotNull { p ->
            toClient(p.coord, p.aes)?.let { PathPoint(p.aes, it) }
        }
    }

    // Point-wise transform straight to client space (no toClientPaths resampling / style-split): the
    // paths stay straight even in bending coords. Contrast createPathData, which follows the coord.
    fun createStraightPathData(
        dataPoints: Iterable<DataPointAesthetics>,
        toLocation: (DataPointAesthetics) -> DoubleVector?
    ): List<PathData> {
        return createPaths(dataPoints, toClientLocation(toLocation), sorted = true, closePath = false, nullsCounter = counter)
    }

    // Client-space step paths (as PathData) for the Renderer, the step analog of createPathData.
    fun createStepsData(paths: Collection<PathData>, horizontalThenVertical: Boolean): List<PathData> {
        return paths.mapNotNull { subPath ->
            val points = subPath.points
            val newPoints = ArrayList<PathPoint>()
            var prev: PathPoint? = null
            for (point in points) {
                if (prev != null) {
                    val x = if (horizontalThenVertical) point.coord.x else prev.coord.x
                    val y = if (horizontalThenVertical) prev.coord.y else point.coord.y
                    newPoints.add(PathPoint(prev.aes, DoubleVector(x, y)))
                }
                newPoints.add(point)
                prev = point
            }
            PathData.create(newPoints)
        }
    }

    // Client-space band paths (upper points + reversed lower points, closed) for the Renderer,
    // the band analog of createPathData. `simplifyBorders` display-simplifies the client outline
    // (Douglas-Peucker) — for geoms fed by densely sampled data (area_ridges).
    fun createBandData(
        dataPoints: Iterable<DataPointAesthetics>,
        toLocationUpper: (DataPointAesthetics) -> DoubleVector?,
        toLocationLower: (DataPointAesthetics) -> DoubleVector?,
        closePath: Boolean = false,
        simplifyBorders: Boolean = false
    ): List<PathData> {
        val domainUpperPathData = createPaths(dataPoints, toLocationUpper, sorted = true, closePath, nullsCounter = counter)
        val domainLowerPathData = createPaths(dataPoints, toLocationLower, sorted = true, closePath, nullsCounter = counter)

        if (domainUpperPathData.isEmpty() || domainLowerPathData.isEmpty()) {
            return emptyList()
        }

        require(domainUpperPathData.size == domainLowerPathData.size) {
            "Upper and lower path data should contain the same number of paths"
        }

        val domainBandsPathData = domainUpperPathData
            .zip(domainLowerPathData)
            .mapNotNull { (upperPath, lowerPath) -> PathData.create(upperPath.points + lowerPath.points.reversed()) }

        val clientBandsPathData = toClientPaths(domainBandsPathData)
        if (!simplifyBorders) {
            return clientBandsPathData
        }
        // toClientPaths returns style-homogeneous sub-paths (splitByStyle), so rebuilding the
        // simplified points with the sub-path's decoration aes loses nothing the band fill uses.
        return clientBandsPathData.mapNotNull { path ->
            val simplified = douglasPeucker(path.coordinates, DOUGLAS_PEUCKER_PIXEL_THRESHOLD)
            PathData.create(simplified.map { PathPoint(path.aes, it) })
        }
    }

    fun toClientPaths(domainPathData: List<PathData>): List<PathData> {
        return when (myResamplingEnabled) {
            true -> {
                domainPathData
                    .map { path -> splitByStyle(path).let(::midPointsPathInterpolator) }
                    .flatMap { paths -> paths.mapNotNull { PathData.create(resample(it.points)) } }
            }

            false -> {
                val clientPathData = domainPathData.mapNotNull { segment ->
                    // Note that PathPoint have to be recreated with the point aes, not with a segment aes
                    val points = segment.points.mapNotNull { p ->
                        toClient(p.coord, p.aes)
                            ?.let { PathPoint(p.aes, coord = it) }
                    }
                    PathData.create(points)
                }

                clientPathData
                    .map { splitByStyle(it).let(::midPointsPathInterpolator) }
                    .flatten()
            }
        }
    }

    companion object {
        fun splitByStyle(pathData: PathData): List<PathData> {
            return pathData.points
                .splitBy(
                    compareBy(
                        { it.aes.size() },
                        { it.aes.color()?.red },
                        { it.aes.color()?.green },
                        { it.aes.color()?.blue },
                        { it.aes.color()?.alpha }
                    )
                )
                .mapNotNull { PathData.create(it)}
        }

        fun midPointsPathInterpolator(path: List<PathData>): List<PathData> {
            if (path.size == 1) {
                return path
            }

            val jointPoints = path
                .windowed(size = 2, step = 1)
                .map { (prevSubPath, nextSubPath) ->
                    val prevSubPathEnd = prevSubPath.coordinates.last()
                    val nextSubPathStart = nextSubPath.coordinates.first()
                    val midPoint = lerp(prevSubPathEnd, nextSubPathStart, 0.5)

                    midPoint
                }

            return path.mapIndexedNotNull { i, subPath ->
                when (i) {
                    0 -> {
                        val rightJointPoint = subPath.points.last().copy(coord = jointPoints[i])
                        PathData.create(subPath.points + rightJointPoint)
                    }

                    path.lastIndex -> {
                        val leftJointPoint = subPath.points.first().copy(coord = jointPoints[i - 1])
                        PathData.create(listOf(leftJointPoint) + subPath.points)
                    }

                    else -> {
                        val leftJointPoint = subPath.points.first().copy(coord = jointPoints[i - 1])
                        val rightJointPoint = subPath.points.last().copy(coord = jointPoints[i])
                        PathData.create(listOf(leftJointPoint) + subPath.points + rightJointPoint)
                    }
                }
            }
        }

        private fun lerp(p1: DoubleVector, p2: DoubleVector, progress: Double): DoubleVector {
            return p1.add(p2.subtract(p1).mul(progress))
        }
    }
}

// One aes for a whole client-space polyline — the lean sibling of PathData (which carries per-point
// aes) for geometry produced one data point at a time (quantile lines, hexagons).
data class PolylineData(
    val aes: DataPointAesthetics,
    val coordinates: List<DoubleVector>
)

class PathData private constructor(
    val points: List<PathPoint>
) {
    companion object {
        fun create(points: List<PathPoint>): PathData? {
            if (points.isEmpty()) {
                return null
            }

            return PathData(points)
        }
    }

    init {
        require(points.isNotEmpty()) { "PathData should contain at least one point" }
    }

    val aes: DataPointAesthetics by lazy(points.first()::aes) // decoration aes (only for color, fill, size, stroke)
    val aesthetics by lazy { points.map(PathPoint::aes) }
    val coordinates by lazy { points.map(PathPoint::coord) } // may contain duplicates, don't work well for polygon
}

class PolygonData private constructor(
    val rings: List<List<PathPoint>>
) {
    companion object {
        fun create(rings: List<List<PathPoint>>): PolygonData? {
            // Force the invariants
            val processedRings = rings
                .filter { it.isNotEmpty() }
                .map { normalizeRing(it, PathPoint.LOC_EQ) }

            if (processedRings.isEmpty()) {
                return null
            }

            return PolygonData(processedRings)
        }
    }

    init {
        require(rings.isNotEmpty()) { "PolygonData should contain at least one ring" }
        require(rings.all { it.isClosed(PathPoint.LOC_EQ) }) { "PolygonData rings should be closed" }
        require(rings.all {
            isRingNormalized(
                it,
                PathPoint.LOC_EQ
            )
        }) { "PolygonData rings should be normalized" }
    }

    val aes: DataPointAesthetics by lazy( rings.first().first()::aes ) // decoration aes (only for color, fill, size, stroke)
    val aesthetics by lazy { rings.map { it.map(PathPoint::aes) } }
    val coordinates by lazy { rings.map { it.map(PathPoint::coord) } }
    val flattenCoordinates by lazy { rings.flatten().map(PathPoint::coord) }
}

data class PathPoint(
    val aes: DataPointAesthetics,
    val coord: DoubleVector
) {
    companion object {
        val LOC_EQ = { p1: PathPoint, p2: PathPoint -> p1.coord == p2.coord }
    }
}
