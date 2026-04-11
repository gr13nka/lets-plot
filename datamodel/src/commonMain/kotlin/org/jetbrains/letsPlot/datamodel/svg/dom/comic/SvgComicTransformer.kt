/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.datamodel.svg.dom.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.intern.comic.HatchureFill
import org.jetbrains.letsPlot.commons.intern.comic.ComicConfig
import org.jetbrains.letsPlot.commons.intern.comic.ComicRandom
import org.jetbrains.letsPlot.commons.intern.comic.WobblePath
import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.commons.values.Colors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCssResource
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgDefsElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgStyleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgSvgElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgTextContent
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgTextElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgTransform
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgTransformable
import org.jetbrains.letsPlot.datamodel.svg.dom.comic.SvgPathDataParser.PathCommand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Recursive SVG DOM tree walker that applies comic transformations:
 * - Wobbles straight line segments (L) to cubic Beziers (C)
 * - Converts SvgRectElement, SvgCircleElement, SvgLineElement to wobbled SvgPathElement
 * - Replaces solid fills with hatch-pattern groups
 * - Overrides text font-family to comic font
 *
 * All attribute setting on newly created elements uses typed property accessors
 * (fillColor().set(), strokeColor().set(), strokeWidth().set(), etc.)
 * following the pattern used by all geoms, axes, legends, and grid components.
 * Never use setAttribute("fill", string) for colors — the raster mapper
 * requires typed SvgColor/Color values in the attribute map.
 */
object SvgComicTransformer {
    // Minimum stroke width for comic-transformed elements.
    // Makes wobble visible on thin (1px) geom outlines and axes.
    private const val MIN_STROKE_WIDTH = 1.0

    // Geom content starts at depth >= 2: svg(0) → rootGroup(1) → contentGroup(2+).
    // Shapes at shallower depths are infrastructure (backgrounds, borders, panel fills).
    private const val GEOM_CONTENT_MIN_DEPTH = 2

    fun transform(svg: SvgSvgElement, config: ComicConfig) {
        val random = ComicRandom(config.seed)

        // Inject comic font CSS @import
        injectFontCss(svg, config)

        // Expand any slim nodes so they can be transformed
        SlimNodeExpander.expandSlimNodes(svg)

        // Walk and transform the tree
        transformNode(svg, config, random)
    }

    private fun injectFontCss(svg: SvgSvgElement, config: ComicConfig) {
        // Set a global font-family style for text elements.
        // Note: @import url(...) for web fonts is NOT used because Batik blocks
        // external resource loading and crashes. Instead, we rely on the per-element
        // font-family override in transformText() and local font availability.
        // Browsers that have Comic Neue installed will use it; others fall back to the default library font.
        val fontFamily = config.comicFont
        val fontCss = "text { font-family: '$fontFamily', sans-serif; }"
        val styleElement = SvgStyleElement(object : SvgCssResource {
            override fun css(): String = fontCss
        })
        svg.children().add(0, styleElement)
    }

    private fun transformNode(node: SvgNode, config: ComicConfig, random: ComicRandom, depth: Int = 0) {
        val isInfrastructure = depth < GEOM_CONTENT_MIN_DEPTH

        val children = node.children()
        var i = 0
        while (i < children.size) {
            val child = children[i]

            // Skip <defs> elements — they contain structural definitions (clip paths, gradients, etc.),
            // not visual content. Transforming the clip rect inside <clipPath> replaces it with a
            // fill="none" path, which empties the clip region and hides all clipped geom content.
            if (child is SvgDefsElement) {
                i++
                continue
            }

            val replacement = if (isInfrastructure) {
                if (child is SvgTextElement) { transformText(child, config); null } else null
            } else {
                when (child) {
                    is SvgRectElement -> transformRect(child, config, random)
                    is SvgCircleElement -> transformCircle(child, config, random)
                    is SvgLineElement -> transformLine(child, config, random)
                    is SvgPathElement -> transformPath(child, config, random)
                    is SvgTextElement -> {
                        transformText(child, config)
                        null
                    }
                    else -> null
                }
            }

            if (replacement != null) {
                children.removeAt(i)
                children.add(i, replacement)
            } else {
                transformNode(child, config, random, depth + 1)
            }
            i++
        }
    }

    private fun transformRect(rect: SvgRectElement, config: ComicConfig, random: ComicRandom): SvgGElement {
        val x = safeGetDouble(rect, "x")
        val y = safeGetDouble(rect, "y")
        val w = safeGetDouble(rect, "width")
        val h = safeGetDouble(rect, "height")

        val corners = listOf(
            DoubleVector(x, y),
            DoubleVector(x + w, y),
            DoubleVector(x + w, y + h),
            DoubleVector(x, y + h),
            DoubleVector(x, y) // close
        )

        val group = SvgGElement()
        copyTransform(rect, group)

        val fillColor = safeGetColor(rect, "fill")
        val fillOpacity = safeGetDoubleOrNull(rect, "fill-opacity")
        val strokeColor = safeGetColor(rect, "stroke")
        val strokeOpacity = safeGetDoubleOrNull(rect, "stroke-opacity")
        val strokeWidth = safeGetDoubleOrNull(rect, "stroke-width")
        val hasStroke = strokeColor != null
        val hasFill = fillColor != null
        val isTransparentFill = fillOpacity != null && fillOpacity == 0.0

        val shouldHatch = isGeomBar(hasFill, hasStroke, isTransparentFill)
                || isColorBarSegment(hasFill, hasStroke, strokeWidth)

        if (shouldHatch) {
            val hatchLines = HatchureFill.hatchRect(x, y, w, h, config)
            addHatchLines(group, hatchLines, fillColor!!, fillOpacity, config, random)
        }

        // Add wobbled outline
        val outlinePath = buildWobbledClosedPath(corners, config, random)
        if (shouldHatch) {
            // Geom shape: hatched fill replaces solid fill
            outlinePath.fill().set(SvgColors.NONE)
            outlinePath.strokeColor().set(strokeColor)
        } else if (hasFill && !isTransparentFill) {
            // Background rect: keep solid fill, wobble the outline
            outlinePath.fillColor().set(fillColor)
            fillOpacity?.let { outlinePath.fillOpacity().set(it) }
            if (hasStroke) {
                outlinePath.strokeColor().set(strokeColor)
            } else {
                outlinePath.strokeColor().set(fillColor)
            }
        } else {
            // Transparent border or no-fill rect
            outlinePath.fill().set(SvgColors.NONE)
            if (hasStroke) outlinePath.strokeColor().set(strokeColor)
        }
        strokeOpacity?.let { outlinePath.strokeOpacity().set(it) }
        strokeWidth?.let { outlinePath.strokeWidth().set(it.coerceAtLeast(MIN_STROKE_WIDTH)) }
        group.children().add(outlinePath)

        return group
    }

    private fun transformCircle(circle: SvgCircleElement, config: ComicConfig, random: ComicRandom): SvgGElement? {
        val cx = safeGetDouble(circle, "cx")
        val cy = safeGetDouble(circle, "cy")
        val r = safeGetDouble(circle, "r")

        // Return null = leave original element unchanged (too small to wobble without destroying shape)
        if (r < 5.0) return null

        val segments = 32
        val vertices = (0..segments).map { i ->
            val theta = 2.0 * PI * i / segments
            DoubleVector(cx + r * cos(theta), cy + r * sin(theta))
        }

        val group = SvgGElement()
        copyTransform(circle, group)

        val fillColor = safeGetColor(circle, "fill")
        val fillOpacity = safeGetDoubleOrNull(circle, "fill-opacity")
        val strokeColor = safeGetColor(circle, "stroke")
        val strokeOpacity = safeGetDoubleOrNull(circle, "stroke-opacity")
        val strokeWidth = safeGetDoubleOrNull(circle, "stroke-width")

        if (fillColor != null) {
            val hatchLines = HatchureFill.hatchCircle(cx, cy, r, config)
            addHatchLines(group, hatchLines, fillColor, fillOpacity, config, random)
        }

        val outlinePath = buildWobbledClosedPath(vertices, config, random)
        outlinePath.fill().set(SvgColors.NONE)
        if (strokeColor != null) {
            outlinePath.strokeColor().set(strokeColor)
        } else if (fillColor != null) {
            outlinePath.strokeColor().set(fillColor)
        }
        strokeOpacity?.let { outlinePath.strokeOpacity().set(it) }
        strokeWidth?.let { outlinePath.strokeWidth().set(it.coerceAtLeast(MIN_STROKE_WIDTH)) }
        group.children().add(outlinePath)

        return group
    }

    private fun transformLine(line: SvgLineElement, config: ComicConfig, random: ComicRandom): SvgPathElement {
        val x1 = safeGetDouble(line, "x1")
        val y1 = safeGetDouble(line, "y1")
        val x2 = safeGetDouble(line, "x2")
        val y2 = safeGetDouble(line, "y2")

        val from = DoubleVector(x1, y1)
        val to = DoubleVector(x2, y2)
        val seg = WobblePath.wobbleSegment(from, to, config, random)

        val builder = SvgPathDataBuilder()
        builder.moveTo(from)
        builder.curveTo(seg.cp1, seg.cp2, seg.to)
        val path = SvgPathElement(builder.build())

        path.fill().set(SvgColors.NONE)
        copyStrokeAttrs(line, path)
        copyTransformFromElement(line, path)

        return path
    }

    private fun transformPath(pathElement: SvgPathElement, config: ComicConfig, random: ComicRandom): SvgNode? {
        val pathData = safeGetAttr(pathElement, "d") ?: return null
        if (pathData.isBlank()) return null

        val commands = SvgPathDataParser.parse(pathData)
        if (commands.isEmpty()) return null

        val fillColor = safeGetColor(pathElement, "fill")
        val fillOpacity = safeGetDoubleOrNull(pathElement, "fill-opacity")
        val strokeColor = safeGetColor(pathElement, "stroke")
        val isTransparentFill = fillOpacity != null && fillOpacity == 0.0
        val hasFill = fillColor != null && !isTransparentFill
        val hasStroke = strokeColor != null

        val fillRule = safeGetAttr(pathElement, "fill-rule")

        if (hasFill && !isBackgroundPath(fillRule, hasStroke)) {
            // Geom shape (bar, area band, etc.): hatch + wobbled outline
            val group = SvgGElement()
            copyTransformFromElement(pathElement, group)

            val vertices = extractVertices(commands)
            if (vertices.size >= 3) {
                val hatchLines = HatchureFill.hatchPolygon(vertices, config)
                addHatchLines(group, hatchLines, fillColor!!, fillOpacity, config, random)
            }

            val wobbledPath = wobbleParsedPath(commands, config, random)
            wobbledPath.fill().set(SvgColors.NONE)
            if (hasStroke) {
                copyStrokeAttrs(pathElement, wobbledPath)
            } else {
                // Fill-only geom (area band): use fill color as outline
                wobbledPath.strokeColor().set(fillColor)
            }
            group.children().add(wobbledPath)

            return group
        } else if (hasFill) {
            // Background path: keep solid fill, wobble the outline
            val wobbledPath = wobbleParsedPath(commands, config, random)
            wobbledPath.fillColor().set(fillColor)
            fillOpacity?.let { wobbledPath.fillOpacity().set(it) }
            // Copy fill-rule using typed accessor
            val rawFillRule = pathElement.getAttribute(SvgPathElement.FILL_RULE).get()
            if (rawFillRule != null) {
                wobbledPath.fillRule().set(rawFillRule)
            }
            wobbledPath.strokeColor().set(fillColor)
            copyTransformFromElement(pathElement, wobbledPath)
            return wobbledPath
        } else {
            // Stroke-only or no-fill path: just wobble
            val wobbledPath = wobbleParsedPath(commands, config, random)
            wobbledPath.fill().set(SvgColors.NONE)
            copyStrokeAttrs(pathElement, wobbledPath)
            copyTransformFromElement(pathElement, wobbledPath)
            return wobbledPath
        }
    }

    private fun transformText(text: SvgTextElement, config: ComicConfig) {
        val comicFont = "${config.comicFont}, sans-serif"
        // Set SVG presentation attribute (works when no inline style is present)
        text.setAttribute(SvgTextContent.FONT_FAMILY, comicFont)

        // Also override font-family in inline style attribute if present.
        // Label.updateStyleAttribute() sets style="...font-family:sans-serif;..."
        // which has higher CSS specificity than both SVG attributes and <style> rules.
        val style = text.getAttribute("style").get()?.toString()
        if (style != null) {
            val updated = if (style.contains("font-family:")) {
                style.replace(Regex("font-family:[^;]+;?"), "font-family:$comicFont;")
            } else {
                "${style}font-family:$comicFont;"
            }
            text.setAttribute("style", updated)
        }
    }

    // --- Hatch decision predicates ---

    /** Geom bars: filled shape with visible stroke (from GeomHelper.decorate) */
    private fun isGeomBar(hasFill: Boolean, hasStroke: Boolean, isTransparentFill: Boolean): Boolean {
        return hasFill && hasStroke && !isTransparentFill
    }

    /** Legend color-bar segments: filled, strokeWidth explicitly set to 0, no strokeColor */
    private fun isColorBarSegment(hasFill: Boolean, hasStroke: Boolean, strokeWidth: Double?): Boolean {
        return hasFill && !hasStroke && strokeWidth != null && strokeWidth == 0.0
    }

    /**
     * Livemap background path uses fillRule=evenodd for window holes.
     * Only fill-only evenodd paths are treated as backgrounds; stroked evenodd paths are geom content.
     */
    private fun isBackgroundPath(fillRule: String?, hasStroke: Boolean): Boolean {
        return fillRule == "evenodd" && !hasStroke
    }

    // --- Helper methods ---

    private fun buildWobbledClosedPath(
        vertices: List<DoubleVector>,
        config: ComicConfig,
        random: ComicRandom
    ): SvgPathElement {
        val builder = SvgPathDataBuilder()
        if (vertices.isEmpty()) return SvgPathElement(builder.build())

        builder.moveTo(vertices[0])
        val segments = WobblePath.wobblePolyline(vertices, config, random)
        for (seg in segments) {
            builder.curveTo(seg.cp1, seg.cp2, seg.to)
        }
        builder.closePath()

        return SvgPathElement(builder.build())
    }

    private fun wobbleParsedPath(
        commands: List<PathCommand>,
        config: ComicConfig,
        random: ComicRandom
    ): SvgPathElement {
        val builder = SvgPathDataBuilder()
        var currentPos = DoubleVector.ZERO

        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> {
                    builder.moveTo(cmd.to)
                    currentPos = cmd.to
                }

                is PathCommand.LineTo -> {
                    val seg = WobblePath.wobbleSegment(currentPos, cmd.to, config, random)
                    builder.curveTo(seg.cp1, seg.cp2, seg.to)
                    currentPos = cmd.to
                }

                is PathCommand.CurveTo -> {
                    val jitter = config.roughness * 0.5
                    val cp1 = DoubleVector(
                        cmd.cp1.x + jitter * random.nextBipolar(),
                        cmd.cp1.y + jitter * random.nextBipolar()
                    )
                    val cp2 = DoubleVector(
                        cmd.cp2.x + jitter * random.nextBipolar(),
                        cmd.cp2.y + jitter * random.nextBipolar()
                    )
                    builder.curveTo(cp1, cp2, cmd.to)
                    currentPos = cmd.to
                }

                is PathCommand.SmoothCurveTo -> {
                    val jitter = config.roughness * 0.5
                    val cp2 = DoubleVector(
                        cmd.cp2.x + jitter * random.nextBipolar(),
                        cmd.cp2.y + jitter * random.nextBipolar()
                    )
                    builder.smoothCurveTo(cp2, cmd.to)
                    currentPos = cmd.to
                }

                is PathCommand.QuadTo -> {
                    builder.quadraticBezierCurveTo(cmd.cp, cmd.to)
                    currentPos = cmd.to
                }

                is PathCommand.SmoothQuadTo -> {
                    builder.smoothQuadraticBezierCurveTo(cmd.to)
                    currentPos = cmd.to
                }

                is PathCommand.ArcTo -> {
                    builder.ellipticalArc(
                        cmd.rx, cmd.ry, cmd.xRotation,
                        cmd.largeArc, cmd.sweep, cmd.to
                    )
                    currentPos = cmd.to
                }

                is PathCommand.ClosePath -> {
                    builder.closePath()
                }
            }
        }

        return SvgPathElement(builder.build())
    }

    private fun addHatchLines(
        group: SvgGElement,
        hatchLines: List<HatchureFill.HatchLine>,
        fillColor: Color,
        fillOpacity: Double?,
        config: ComicConfig,
        random: ComicRandom
    ) {
        for (hatch in hatchLines) {
            val seg = WobblePath.wobbleSegment(hatch.from, hatch.to, config, random)
            val builder = SvgPathDataBuilder()
            builder.moveTo(hatch.from)
            builder.curveTo(seg.cp1, seg.cp2, seg.to)
            val hatchPath = SvgPathElement(builder.build())
            hatchPath.fill().set(SvgColors.NONE)
            hatchPath.strokeColor().set(fillColor)
            fillOpacity?.let { hatchPath.strokeOpacity().set(it) }
            hatchPath.strokeWidth().set(config.hatchStrokeWidth)
            group.children().add(hatchPath)
        }
    }

    private fun extractVertices(commands: List<PathCommand>): List<DoubleVector> {
        val vertices = mutableListOf<DoubleVector>()
        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> vertices.add(cmd.to)
                is PathCommand.LineTo -> vertices.add(cmd.to)
                is PathCommand.CurveTo -> vertices.add(cmd.to)
                is PathCommand.SmoothCurveTo -> vertices.add(cmd.to)
                is PathCommand.QuadTo -> vertices.add(cmd.to)
                is PathCommand.SmoothQuadTo -> vertices.add(cmd.to)
                is PathCommand.ArcTo -> vertices.add(cmd.to)
                is PathCommand.ClosePath -> {}
            }
        }
        return vertices
    }

    // --- Attribute reading (safe for source elements that may have string values) ---

    /**
     * Safely read a color attribute. Returns null for "none" or missing values.
     * Handles both typed SvgColor values and string color values.
     */
    private fun safeGetColor(element: SvgElement, attrName: String): Color? {
        val raw = element.getAttribute(attrName).get() ?: return null
        return when (raw) {
            is SvgColors -> when (raw) {
                SvgColors.NONE, SvgColors.TRANSPARENT, SvgColors.CURRENT_COLOR -> null
                else -> runCatching { Colors.parseColor(raw.toString()) }.getOrNull()
            }
            is Color -> raw
            is String -> if (raw == "none") null else runCatching { Colors.parseColor(raw) }.getOrNull()
            else -> runCatching { Colors.parseColor(raw.toString()) }.getOrNull()
        }
    }

    private fun safeGetAttr(element: SvgElement, attrName: String): String? {
        return element.getAttribute(attrName).get()?.toString()
    }

    private fun safeGetDouble(element: SvgElement, attrName: String, default: Double = 0.0): Double {
        val raw = element.getAttribute(attrName).get() ?: return default
        return when (raw) {
            is Double -> raw
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: default
            else -> default
        }
    }

    private fun safeGetDoubleOrNull(element: SvgElement, attrName: String): Double? {
        val raw = element.getAttribute(attrName).get() ?: return null
        return when (raw) {
            is Double -> raw
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
    }

    // --- Attribute writing (typed, following codebase conventions) ---

    private fun copyTransform(from: SvgShape, to: SvgGElement) {
        if (from is SvgTransformable) {
            from.transform().get()?.let { to.transform().set(it) }
        }
    }

    private fun copyTransformFromElement(from: SvgElement, to: SvgElement) {
        val raw = from.getAttribute("transform").get() ?: return
        if (to is SvgTransformable) {
            val transform = if (raw is SvgTransform) raw else SvgTransform(raw.toString())
            to.transform().set(transform)
        }
    }

    /**
     * Copy stroke attributes from source element to target shape.
     * Reads via untyped access (safe for source elements with string values),
     * writes via typed accessors (safe for all rendering paths).
     */
    private fun copyStrokeAttrs(from: SvgElement, to: SvgShape) {
        safeGetColor(from, "stroke")?.let { to.strokeColor().set(it) }
        safeGetDoubleOrNull(from, "stroke-opacity")?.let { to.strokeOpacity().set(it) }
        safeGetDoubleOrNull(from, "stroke-width")?.let { to.strokeWidth().set(it.coerceAtLeast(MIN_STROKE_WIDTH)) }
        safeGetAttr(from, "stroke-dasharray")?.let { to.strokeDashArray().set(it) }
        safeGetDoubleOrNull(from, "stroke-dashoffset")?.let { to.strokeDashOffset().set(it) }
    }
}
