/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.livemap.chart.smiley

import org.jetbrains.letsPlot.commons.intern.typedGeometry.Vec
import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.core.canvas.Context2d
import org.jetbrains.letsPlot.core.canvas.Context2dDelegate
import org.jetbrains.letsPlot.core.canvas.LineCap
import org.jetbrains.letsPlot.livemap.Client.Companion.px
import org.jetbrains.letsPlot.livemap.chart.ChartElementComponent
import org.jetbrains.letsPlot.livemap.chart.PointComponent
import org.jetbrains.letsPlot.livemap.chart.SmileyComponent
import org.jetbrains.letsPlot.livemap.core.ecs.EcsComponentManager
import org.jetbrains.letsPlot.livemap.core.ecs.addComponents
import org.jetbrains.letsPlot.livemap.mapengine.RenderHelper
import org.jetbrains.letsPlot.livemap.mapengine.placement.WorldOriginComponent
import org.jetbrains.letsPlot.livemap.mapengine.viewport.Viewport
import org.jetbrains.letsPlot.livemap.mapengine.viewport.ViewportHelper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmileyRendererTest {
    private val viewport = Viewport(
        ViewportHelper(org.jetbrains.letsPlot.livemap.World.DOMAIN, true, myLoopY = false),
        org.jetbrains.letsPlot.livemap.ClientPoint(256, 256),
        1,
        15
    )
    private val renderHelper = RenderHelper(viewport)
    private val manager = EcsComponentManager()

    @Test
    fun smileyRendererShouldDrawFaceEyesAndMouth() {
        val context = RecordingContext2d()

        SmileyRenderer().render(
            entity(happiness = 1.0),
            context,
            renderHelper
        )

        assertEquals(3, context.arcs.size)
        assertArcEquals(ArcCall(0.0, 0.0, 11.0, 0.0, 2 * PI, false), context.arcs[0])
        assertArcEquals(ArcCall(-2.75, -2.2, 1.144, 0.0, 2 * PI, false), context.arcs[1])
        assertArcEquals(ArcCall(2.75, -2.2, 1.144, 0.0, 2 * PI, false), context.arcs[2])
        assertEquals(listOf(MoveCall(-4.4, 3.3)), context.moves)
        assertEquals(1, context.beziers.size)
        assertBezierEquals(
            BezierCall(
                cp1x = -1.4666666666666668,
                cp1y = 6.233333333333333,
                cp2x = 1.4666666666666668,
                cp2y = 6.233333333333333,
                x = 4.4,
                y = 3.3
            ),
            context.beziers.single()
        )
        assertEquals(listOf(LineCap.ROUND, LineCap.BUTT), context.lineCaps)
    }

    @Test
    fun smileyRendererShouldResetLineCapAfterDrawing() {
        val context = RecordingContext2d()

        SmileyRenderer().render(
            entity(happiness = 0.5),
            context,
            renderHelper
        )

        assertEquals(LineCap.BUTT, context.lineCaps.last())
    }

    @Test
    fun smileyRendererShouldChangeMouthWithHappiness() {
        val happyContext = RecordingContext2d()
        val sadContext = RecordingContext2d()
        val renderer = SmileyRenderer()

        renderer.render(entity(happiness = 1.0), happyContext, renderHelper)
        renderer.render(entity(happiness = -1.0), sadContext, renderHelper)

        assertDoubleEquals(6.233333333333333, happyContext.beziers.single().cp1y)
        assertDoubleEquals(0.3666666666666669, sadContext.beziers.single().cp1y)
        assertDoubleEquals(6.233333333333333, happyContext.beziers.single().cp2y)
        assertDoubleEquals(0.3666666666666669, sadContext.beziers.single().cp2y)
    }

    private fun entity(happiness: Double) = manager.createEntity("smiley")
        .addComponents {
            +PointComponent().apply { size = 23.76.px }
            +ChartElementComponent().apply {
                fillColor = Color.YELLOW
                strokeColor = Color.BLACK
                strokeWidth = 1.76
                scalingSizeFactor = 1.0
            }
            +SmileyComponent(happiness)
            +WorldOriginComponent(Vec(5.0, 5.0))
        }

    private data class ArcCall(
        val x: Double,
        val y: Double,
        val radius: Double,
        val startAngle: Double,
        val endAngle: Double,
        val anticlockwise: Boolean,
    )

    private data class MoveCall(
        val x: Double,
        val y: Double,
    )

    private data class BezierCall(
        val cp1x: Double,
        val cp1y: Double,
        val cp2x: Double,
        val cp2y: Double,
        val x: Double,
        val y: Double,
    )

    private class RecordingContext2d : Context2d by Context2dDelegate() {
        val arcs = mutableListOf<ArcCall>()
        val moves = mutableListOf<MoveCall>()
        val beziers = mutableListOf<BezierCall>()
        val lineCaps = mutableListOf<LineCap>()

        override fun arc(
            x: Double,
            y: Double,
            radius: Double,
            startAngle: Double,
            endAngle: Double,
            anticlockwise: Boolean
        ) {
            arcs.add(ArcCall(x, y, radius, startAngle, endAngle, anticlockwise))
        }

        override fun moveTo(x: Double, y: Double) {
            moves.add(MoveCall(x, y))
        }

        override fun bezierCurveTo(
            cp1x: Double,
            cp1y: Double,
            cp2x: Double,
            cp2y: Double,
            x: Double,
            y: Double
        ) {
            beziers.add(BezierCall(cp1x, cp1y, cp2x, cp2y, x, y))
        }

        override fun setLineCap(lineCap: LineCap) {
            lineCaps.add(lineCap)
        }
    }

    private fun assertArcEquals(expected: ArcCall, actual: ArcCall) {
        assertDoubleEquals(expected.x, actual.x)
        assertDoubleEquals(expected.y, actual.y)
        assertDoubleEquals(expected.radius, actual.radius)
        assertDoubleEquals(expected.startAngle, actual.startAngle)
        assertDoubleEquals(expected.endAngle, actual.endAngle)
        assertEquals(expected.anticlockwise, actual.anticlockwise)
    }

    private fun assertBezierEquals(expected: BezierCall, actual: BezierCall) {
        assertDoubleEquals(expected.cp1x, actual.cp1x)
        assertDoubleEquals(expected.cp1y, actual.cp1y)
        assertDoubleEquals(expected.cp2x, actual.cp2x)
        assertDoubleEquals(expected.cp2y, actual.cp2y)
        assertDoubleEquals(expected.x, actual.x)
        assertDoubleEquals(expected.y, actual.y)
    }

    private fun assertDoubleEquals(expected: Double, actual: Double, eps: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= eps, "expected <$expected> but was <$actual>")
    }
}
