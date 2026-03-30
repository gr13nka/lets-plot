/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.livemap.chart.point

import org.jetbrains.letsPlot.commons.intern.math.toRadians
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
import kotlin.math.sqrt

class PointRenderer(
    private val shape: Int,
    degreeAngle: Double
) : Renderer {
    private val angle = toRadians(degreeAngle)

    override fun render(entity: EcsEntity, ctx: Context2d, renderHelper: RenderHelper) {
        val chartElement = entity.get<ChartElementComponent>()
        val pointData = entity.get<PointComponent>()
        val radius = pointData.scaledRadius(chartElement.scalingSizeFactor).value
        val strokeWidth = chartElement.scaledStrokeWidth()

        ctx.translate(renderHelper.dimToScreen(entity.get<WorldOriginComponent>().origin))

        entity.tryGet<SmileyComponent>()?.let {
            drawSmiley(
                ctx = ctx,
                radius = radius,
                strokeWidth = strokeWidth,
                happiness = it.happiness,
                chartElement = chartElement
            )
            return
        }

        ctx.beginPath()
        drawMarker(
            ctx = ctx,
            radius = radius,
            stroke = strokeWidth,
            shape = shape,
            angle = angle
        )
        if (chartElement.fillColor != null) {
            ctx.setFillStyle(chartElement.scaledFillColor())
            ctx.fill()
        }
        if (chartElement.strokeColor != null && chartElement.scaledStrokeWidth() > 0.0) {
            ctx.setStrokeStyle(chartElement.scaledStrokeColor())
            ctx.setLineWidth(chartElement.scaledStrokeWidth())
            ctx.stroke()
        }
    }

    private fun drawSmiley(
        ctx: Context2d,
        radius: Double,
        strokeWidth: Double,
        happiness: Double,
        chartElement: ChartElementComponent
    ) {
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

        val eyeRadius = 0.08 * faceRadius + 0.15 * strokeWidth
        ctx.beginPath()
        circle(ctx, -0.25 * faceRadius, -0.2 * faceRadius, eyeRadius)
        circle(ctx, 0.25 * faceRadius, -0.2 * faceRadius, eyeRadius)
        ctx.setFillStyle(chartElement.scaledStrokeColor())
        ctx.fill()

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

    private fun drawMarker(ctx: Context2d, radius: Double, stroke: Double, shape: Int, angle: Double) {
        val needToRotate = shape !in listOf(1, 10, 16, 19, 20, 21) && angle != 0.0
        if (needToRotate) {
            ctx.rotate(angle)
        }

        when (shape) {
            0 -> square(ctx, radius)
            1 -> circle(ctx, radius)
            2 -> triangle(ctx, radius, stroke)
            3 -> plus(ctx, radius)
            4 -> cross(ctx, radius / sqrt(2.0))
            5 -> diamond(ctx, radius)
            6 -> triangle(ctx, radius, stroke, pointingUp = false)
            7 -> {
                square(ctx, radius)
                cross(ctx, radius)
            }
            8 -> {
                plus(ctx, radius)
                cross(ctx, radius / sqrt(2.0))
            }
            9 -> {
                diamond(ctx, radius)
                plus(ctx, radius)
            }
            10 -> {
                circle(ctx, radius)
                plus(ctx, radius)
            }
            11 -> {
                triangle(ctx, radius, stroke, pointingUp = true)
                triangle(ctx, radius, stroke, pointingUp = false)
            }
            12 -> {
                square(ctx, radius)
                plus(ctx, radius)
            }
            13 -> {
                circle(ctx, radius)
                cross(ctx, radius / sqrt(2.0))
            }
            14 -> squareTriangle(ctx, radius, stroke)
            15 -> square(ctx, radius)
            16 -> circle(ctx, radius)
            17 -> triangle(ctx, radius, 1.0)
            18 -> diamond(ctx, radius)
            19 -> circle(ctx, radius)
            20 -> circle(ctx, radius)
            21 -> circle(ctx, radius)
            22 -> square(ctx, radius)
            23 -> diamond(ctx, radius)
            24 -> triangle(ctx, radius, stroke)
            25 -> triangle(ctx, radius, stroke, pointingUp = false)
            else -> throw IllegalStateException("Unknown point shape")
        }

        if (needToRotate) {
            ctx.rotate(-angle)
        }
    }

    private fun circle(ctx: Context2d, x: Double, y: Double, r: Double) {
        ctx.arc(x, y, r, 0.0, 2 * PI)
    }

    private fun circle(ctx: Context2d, r: Double) {
        circle(ctx, 0.0, 0.0, r)
    }

    private fun square(ctx: Context2d, r: Double) {
        ctx.moveTo(-r, -r)
        ctx.lineTo(r, -r)
        ctx.lineTo(r, r)
        ctx.lineTo(-r, r)
        ctx.closePath()
    }

    private fun squareTriangle(ctx: Context2d, r: Double, stroke: Double) {
        val outerSize = 2 * r + stroke
        val triangleHeight = outerSize - stroke / 2 - sqrt(5.0) * stroke / 2
        ctx.moveTo(-triangleHeight / 2, r)
        ctx.lineTo(0.0, r - triangleHeight)
        ctx.lineTo(triangleHeight / 2, r)
        ctx.lineTo(-r, r)
        ctx.lineTo(-r, -r)
        ctx.lineTo(r, -r)
        ctx.lineTo(r, r)
        ctx.closePath()
    }

    private fun triangle(ctx: Context2d, r: Double, stroke: Double, pointingUp: Boolean = true) {
        val outerHeight = 2 * r + stroke
        val height = outerHeight - 3.0 * stroke / 2.0
        val side = 2.0 * height / sqrt(3.0)
        val distanceToBase = (outerHeight - stroke) / 2.0
        val distanceToPeak = height - distanceToBase
        val pointingCoeff = if (pointingUp)
            1.0
        else
            -1.0
        val centroidOffset = height / 6.0 + stroke / 4.0

        ctx.moveTo(0.0, -pointingCoeff * (distanceToPeak + centroidOffset))
        ctx.lineTo(side / 2.0, pointingCoeff * (distanceToBase - centroidOffset))
        ctx.lineTo(-side / 2.0, pointingCoeff * (distanceToBase - centroidOffset))
        ctx.lineTo(0.0, -pointingCoeff * (distanceToPeak + centroidOffset))
        ctx.closePath()
    }

    internal fun plus(ctx: Context2d, r: Double) {
        ctx.moveTo(0.0, -r)
        ctx.lineTo(0.0, r)
        ctx.moveTo(-r, 0.0)
        ctx.lineTo(r, 0.0)
    }

    private fun cross(ctx: Context2d, r: Double) {
        ctx.moveTo(-r, -r)
        ctx.lineTo(r, r)
        ctx.moveTo(-r, r)
        ctx.lineTo(r, -r)
    }

    private fun diamond(ctx: Context2d, r: Double) {
        ctx.moveTo(0.0, -r)
        ctx.lineTo(r, 0.0)
        ctx.lineTo(0.0, r)
        ctx.lineTo(-r, 0.0)
        ctx.closePath()
    }

}
