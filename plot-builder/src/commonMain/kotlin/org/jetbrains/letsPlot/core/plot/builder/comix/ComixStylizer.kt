/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comix

import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgEllipseElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGraphicsElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgShape
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgSvgElement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class ComixStylizer(
    private val roughness: Double,
    private val hatchingAngle: Double,
    private val hatchingStrokeWidth: Double,
) {
    private val rng = Random(0)

    fun stylize(svg: SvgSvgElement) = walk(svg)

    fun stylizeTooltip(tooltipRoot: SvgGElement) = walk(tooltipRoot)

    private fun walk(node: SvgNode) {
        when (node) {
            is SvgLineElement -> processLine(node)
            is SvgPathElement -> processPath(node)
            is SvgRectElement -> processRect(node)
            is SvgCircleElement -> processCircle(node)
            is SvgEllipseElement -> processEllipse(node)
        }
        // Snapshot: process* may replace the element in its parent.
        node.children().toList().forEach { walk(it) }
    }

    private fun processLine(el: SvgLineElement) {
        val x1 = el.x1().get() ?: return
        val y1 = el.y1().get() ?: return
        val x2 = el.x2().get() ?: return
        val y2 = el.y2().get() ?: return

        val d = SvgPathDataBuilder().apply {
            moveTo(x1, y1)
            wigglyLineTo(this, x1, y1, x2, y2)
        }.build()

        val newPath = SvgPathElement(d)
        copyStroke(el, newPath)
        replaceInParent(el, newPath)
    }

    private fun processPath(el: SvgPathElement) {
        // Too tedious
    }

    private fun processRect(el: SvgRectElement) {
        val x = el.x().get() ?: return
        val y = el.y().get() ?: return
        val w = el.width().get() ?: return
        val h = el.height().get() ?: return

        val d = SvgPathDataBuilder().apply {
            moveTo(x, y)
            wigglyLineTo(this, x, y, x + w, y)
            wigglyLineTo(this, x + w, y, x + w, y + h)
            wigglyLineTo(this, x + w, y + h, x, y + h)
            wigglyLineTo(this, x, y + h, x, y)
            closePath()
        }.build()

        val newPath = SvgPathElement(d)
        copyFillAndStroke(el, newPath)
        replaceInParent(el, newPath)
    }

    private fun processCircle(el: SvgCircleElement) {
        val cx = el.cx().get() ?: return
        val cy = el.cy().get() ?: return
        val r = el.r().get() ?: return
        replaceWithWigglyEllipse(el, cx, cy, r, r)
    }

    private fun processEllipse(el: SvgEllipseElement) {
        val cx = el.cx().get() ?: return
        val cy = el.cy().get() ?: return
        val rx = el.rx().get() ?: return
        val ry = el.ry().get() ?: return
        replaceWithWigglyEllipse(el, cx, cy, rx, ry)
    }

    private fun <T> replaceWithWigglyEllipse(
        el: T,
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
    ) where T : SvgGraphicsElement, T : SvgShape {
        val steps = 36
        val d = SvgPathDataBuilder().apply {
            for (i in 0..steps) {
                val t = 2 * PI * i / steps
                val jitter = rng.nextDouble(-roughness, roughness)
                val px = cx + (rx + jitter) * cos(t)
                val py = cy + (ry + jitter) * sin(t)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            closePath()
        }.build()

        val newPath = SvgPathElement(d)
        copyFillAndStroke(el, newPath)
        replaceInParent(el, newPath)
    }

    private fun wigglyLineTo(
        b: SvgPathDataBuilder,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ) {
        val len = hypot(x2 - x1, y2 - y1)
        if (len == 0.0) {
            b.lineTo(x2, y2)
            return
        }
        val steps = maxOf(2, (len / 8.0).toInt())
        // Unit perpendicular.
        val nx = -(y2 - y1) / len
        val ny = (x2 - x1) / len
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            val baseX = x1 + (x2 - x1) * t
            val baseY = y1 + (y2 - y1) * t
            val offset = if (i == steps) 0.0 else rng.nextDouble(-roughness, roughness)
            b.lineTo(baseX + nx * offset, baseY + ny * offset)
        }
    }

    private fun copyStroke(src: SvgShape, dst: SvgShape) {
        src.stroke().get()?.let { dst.stroke().set(it) }
        src.strokeOpacity().get()?.let { dst.strokeOpacity().set(it) }
        src.strokeWidth().get()?.let { dst.strokeWidth().set(it) }
        src.strokeDashArray().get()?.let { dst.strokeDashArray().set(it) }
        src.strokeDashOffset().get()?.let { dst.strokeDashOffset().set(it) }
    }

    private fun copyFillAndStroke(src: SvgShape, dst: SvgShape) {
        src.fill().get()?.let { dst.fill().set(it) }
        src.fillOpacity().get()?.let { dst.fillOpacity().set(it) }
        copyStroke(src, dst)
    }

    private fun replaceInParent(old: SvgNode, new: SvgNode) {
        val parent = old.parent().get() ?: return
        val siblings = parent.children()
        val idx = siblings.indexOf(old)
        if (idx >= 0) {
            siblings[idx] = new
        }
    }
}
