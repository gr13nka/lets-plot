/*
 * Copyright (c) 2024. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.livemap.api

import org.jetbrains.letsPlot.commons.intern.spatial.LonLat
import org.jetbrains.letsPlot.commons.intern.typedGeometry.Scalar
import org.jetbrains.letsPlot.commons.intern.typedGeometry.Vec
import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.livemap.chart.ChartElementComponent
import org.jetbrains.letsPlot.livemap.chart.IndexComponent
import org.jetbrains.letsPlot.livemap.chart.Locator
import org.jetbrains.letsPlot.livemap.chart.LocatorComponent
import org.jetbrains.letsPlot.livemap.chart.PointComponent
import org.jetbrains.letsPlot.livemap.chart.SmileyComponent
import org.jetbrains.letsPlot.livemap.chart.point.PointLocator
import org.jetbrains.letsPlot.livemap.chart.smiley.SmileyRenderer
import org.jetbrains.letsPlot.livemap.core.ecs.EcsEntity
import org.jetbrains.letsPlot.livemap.core.ecs.addComponents
import org.jetbrains.letsPlot.livemap.core.layers.LayerKind
import org.jetbrains.letsPlot.livemap.mapengine.LayerEntitiesComponent
import org.jetbrains.letsPlot.livemap.mapengine.MapProjection
import org.jetbrains.letsPlot.livemap.mapengine.RenderableComponent
import org.jetbrains.letsPlot.livemap.mapengine.placement.ScreenDimensionComponent
import org.jetbrains.letsPlot.livemap.mapengine.placement.WorldOriginComponent

@LiveMapDsl
class SmileyLayerBuilder(
    val factory: FeatureEntityFactory,
    val mapProjection: MapProjection
)

fun FeatureLayerBuilder.smileys(block: SmileyLayerBuilder.() -> Unit) {
    val layerEntity = myComponentManager
        .createEntity("map_layer_smiley")
        .addComponents {
            + layerManager.addLayer("geom_smiley", LayerKind.FEATURES)
            + LayerEntitiesComponent()
        }

    SmileyLayerBuilder(
        FeatureEntityFactory(layerEntity, panningPointsMaxCount = 200),
        mapProjection
    ).apply(block)
}

fun SmileyLayerBuilder.smiley(block: SmileyEntityBuilder.() -> Unit) {
    SmileyEntityBuilder(factory)
        .apply(block)
        .build()
}

private object SmileyLocator : Locator by PointLocator

//Creates entities with [SmileyComponent], [SmileyRenderer], and [SmileyLocator].

@LiveMapDsl
class SmileyEntityBuilder(
    private val myFactory: FeatureEntityFactory
) {
    var sizeScalingRange: ClosedRange<Int>? = null
    var alphaScalingEnabled: Boolean = false
    var layerIndex: Int? = null
    var index: Int? = null
    var point: Vec<LonLat> = LonLat.ZERO_VEC
    var radius: Double = 4.0
    var happiness: Double = 0.5
    var fillColor: Color = Color.WHITE
    var strokeColor: Color = Color.BLACK
    var strokeWidth: Double = 1.0

    fun build(): EcsEntity {
        return myFactory.createStaticFeatureWithLocation("map_ent_s_smiley", point)
            .run {
                myFactory.incrementLayerPointsTotalCount(1)
                setInitializer { worldPoint ->
                    if (layerIndex != null && index != null) {
                        +IndexComponent(layerIndex!!, index!!)
                    }
                    +RenderableComponent().apply {
                        renderer = SmileyRenderer()
                    }
                    +ChartElementComponent().apply {
                        sizeScalingRange = this@SmileyEntityBuilder.sizeScalingRange
                        alphaScalingEnabled = this@SmileyEntityBuilder.alphaScalingEnabled
                        fillColor = this@SmileyEntityBuilder.fillColor
                        strokeColor = this@SmileyEntityBuilder.strokeColor
                        strokeWidth = this@SmileyEntityBuilder.strokeWidth
                    }
                    +PointComponent().apply {
                        size = Scalar(radius * 2.0)
                    }
                    +SmileyComponent(happiness)
                    +WorldOriginComponent(worldPoint)
                    +ScreenDimensionComponent()
                    +LocatorComponent(SmileyLocator)
                }
            }
    }
}
