/*
 * Copyright (c) 2024. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.livemap.chart.smiley

import org.jetbrains.letsPlot.core.canvas.Context2d
import org.jetbrains.letsPlot.core.canvas.LineCap
import org.jetbrains.letsPlot.livemap.chart.ChartElementComponent
import org.jetbrains.letsPlot.livemap.chart.PointComponent
import org.jetbrains.letsPlot.livemap.chart.SmileyComponent
import org.jetbrains.letsPlot.livemap.core.ecs.EcsEntity
import org.jetbrains.letsPlot.livemap.mapengine.RenderHelper
import org.jetbrains.letsPlot.livemap.mapengine.Renderer
import org.jetbrains.letsPlot.livemap.mapengine.placement.WorldOriginComponent
import org.jetbrains.letsPlot.livemap.mapengine.translate
import kotlin.math.PI


// Reads [SmileyComponent.happiness] to control mouth curvature (-1.0 to 1.0).

class SmileyRenderer : Renderer {
    override fun render(entity: EcsEntity, ctx: Context2d, renderHelper: RenderHelper) {
        val chartElement = entity.get<ChartElementComponent>()
        val pointData = entity.get<PointComponent>()
        val smiley = entity.get<SmileyComponent>()
        val radius = pointData.scaledRadius(chartElement.scalingSizeFactor).value
        val strokeWidth = chartElement.scaledStrokeWidth()

        ctx.translate(renderHelper.dimToScreen(entity.get<WorldOriginComponent>().origin))

        // Face circle
        val faceRadius = (radius - strokeWidth / 2.0).coerceAtLeast(0.0)
        ctx.beginPath()
        circle(ctx, 0.0, 0.0, faceRadius)
        if (chartElement.fillColor != null) {
            ctx.setFillStyle(chartElement.scaledFillColor())
            ctx.fill()
        }
        if (chartElement.strokeColor != null && strokeWidth > 0.0) {
            ctx.setStrokeStyle(chartElement.scaledStrokeColor())
            ctx.setLineWidth(strokeWidth)
            ctx.stroke()
        }

        if (chartElement.strokeColor == null) {
            return
        }

        // Eyes
        val eyeRadius = 0.08 * faceRadius + 0.15 * strokeWidth
        ctx.beginPath()
        circle(ctx, -0.25 * faceRadius, -0.2 * faceRadius, eyeRadius)
        circle(ctx, 0.25 * faceRadius, -0.2 * faceRadius, eyeRadius)
        ctx.setFillStyle(chartElement.scaledStrokeColor())
        ctx.fill()

        // Mouth (bezier curve controlled by happiness)
        val happiness = smiley.happiness
        val mouthLeftX = -0.4 * faceRadius
        val mouthLeftY = 0.3 * faceRadius
        val mouthRightX = 0.4 * faceRadius
        val mouthRightY = 0.3 * faceRadius
        val qcpX = 0.0
        val qcpY = 0.3 * faceRadius + happiness * 0.4 * faceRadius
        val cp1X = mouthLeftX + 2.0 / 3.0 * (qcpX - mouthLeftX)
        val cp1Y = mouthLeftY + 2.0 / 3.0 * (qcpY - mouthLeftY)
        val cp2X = mouthRightX + 2.0 / 3.0 * (qcpX - mouthRightX)
        val cp2Y = mouthRightY + 2.0 / 3.0 * (qcpY - mouthRightY)

        ctx.beginPath()
        ctx.moveTo(mouthLeftX, mouthLeftY)
        ctx.bezierCurveTo(cp1X, cp1Y, cp2X, cp2Y, mouthRightX, mouthRightY)
        ctx.setStrokeStyle(chartElement.scaledStrokeColor())
        ctx.setLineWidth(strokeWidth)
        ctx.setLineCap(LineCap.ROUND)
        ctx.stroke()
        ctx.setLineCap(LineCap.BUTT)
    }

    private fun circle(ctx: Context2d, x: Double, y: Double, r: Double) {
        ctx.arc(x, y, r, 0.0, 2 * PI)
    }
}
