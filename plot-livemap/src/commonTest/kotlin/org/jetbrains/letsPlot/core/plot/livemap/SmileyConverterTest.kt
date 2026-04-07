/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.livemap

import org.jetbrains.letsPlot.core.plot.base.aes.AestheticsBuilder.Companion.constant
import org.jetbrains.letsPlot.core.plot.base.aes.AestheticsBuilder.Companion.list
import org.jetbrains.letsPlot.core.plot.base.render.point.NamedShape
import org.jetbrains.letsPlot.core.plot.livemap.ConverterDataHelper.AestheticsDataHelper
import org.jetbrains.letsPlot.core.plot.livemap.ConverterDataHelper.GENERIC_POINTS
import org.jetbrains.letsPlot.core.plot.livemap.MapObjectMatcher.Companion.eq
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SmileyConverterTest {
    private val aesData: AestheticsDataHelper = AestheticsDataHelper.create()
    private val matcher: MapObjectMatcher = ConverterDataHelper.createDefaultMatcher()

    @BeforeTest
    fun setUp() {
        aesData.addGroup(GENERIC_POINTS)
        aesData.builder()
            .size(constant(10.0))
            .linewidth(constant(2.0))
    }

    @Test
    fun smileyShouldConvertToPointLikeLiveMapObject() {
        matcher
            .shape(eq(NamedShape.FILLED_CIRCLE.code))
            .radius(eq(11.88))
            .strokeWidth(eq(1.76))
            .point(eq(GENERIC_POINTS[0]))

        val mapObjectList = aesData.buildConverter().toSmiley()
        assertEquals(2, mapObjectList.size)
        matcher.match(mapObjectList[0])
        assertEquals(0.5, mapObjectList[0].smileyHappiness)
    }

    @Test
    fun mappedHappinessShouldBeReadFromAestheticAndClamped() {
        aesData.builder().happiness(list(listOf(2.0, -0.25)))

        val mapObjectList = aesData.buildConverter().toSmiley()

        assertEquals(2, mapObjectList.size)
        assertEquals(1.0, mapObjectList[0].smileyHappiness)
        assertEquals(-0.25, mapObjectList[1].smileyHappiness)
    }

    @Test
    fun constantHappinessShouldBeReadFromAestheticAndClamped() {
        aesData.builder().happiness(constant(-2.0))

        val mapObjectList = aesData.buildConverter().toSmiley()

        assertEquals(2, mapObjectList.size)
        assertEquals(-1.0, mapObjectList[0].smileyHappiness)
        assertEquals(-1.0, mapObjectList[1].smileyHappiness)
    }
}
