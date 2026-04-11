/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.datamodel.svg.dom.comic

import org.jetbrains.letsPlot.commons.intern.comic.ComicConfig
import org.jetbrains.letsPlot.datamodel.svg.dom.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvgComicTransformerTest {

    private val config = ComicConfig()

    /**
     * Helper: wrap elements at depth >= 2 to simulate real geom content.
     * Tree: svg(0) → rootGroup(1) → contentGroup(2) → elements
     * The transformer skips shapes at depth < 2 (backgrounds/borders).
     */
    private fun svgWithContent(vararg elements: SvgNode): Pair<SvgSvgElement, SvgGElement> {
        val svg = SvgSvgElement(200.0, 200.0)
        val rootGroup = SvgGElement()
        val contentGroup = SvgGElement()
        for (el in elements) contentGroup.children().add(el)
        rootGroup.children().add(contentGroup)
        svg.children().add(rootGroup)
        return svg to contentGroup
    }

    @Test
    fun rectIsReplacedWithGroup() {
        val rect = SvgRectElement(10.0, 10.0, 80.0, 60.0)
        rect.fill().set(SvgColors.BLUE)
        rect.stroke().set(SvgColors.BLACK)
        rect.strokeWidth().set(1.0)
        val (svg, group) = svgWithContent(rect)

        SvgComicTransformer.transform(svg, config)

        // Rect with fill + strokeWidth should be hatched (geom shape).
        assertTrue(group.children().any { it is SvgGElement }, "Rect should be replaced with SvgGElement")

        val replacementGroup = group.children().filterIsInstance<SvgGElement>().first()
        assertTrue(replacementGroup.children().size > 1, "Group should contain hatch lines and outline")

        for (child in replacementGroup.children()) {
            assertTrue(child is SvgPathElement, "Group children should be SvgPathElement, got ${child::class}")
        }
    }

    @Test
    fun lineIsReplacedWithWobblyPath() {
        val line = SvgLineElement(10.0, 10.0, 190.0, 10.0)
        line.stroke().set(SvgColors.BLACK)
        val (svg, group) = svgWithContent(line)

        SvgComicTransformer.transform(svg, config)

        val paths = group.children().filterIsInstance<SvgPathElement>()
        assertTrue(paths.isNotEmpty(), "Line should be replaced with a path")

        val pathData = paths.first().d().get().toString()
        assertTrue(pathData.contains("C"), "Wobbled line should contain cubic Bezier (C) commands")
    }

    @Test
    fun pathWithLinesGetsWobbled() {
        val pathData = SvgPathDataBuilder()
            .moveTo(10.0, 10.0)
            .lineTo(100.0, 10.0)
            .lineTo(100.0, 100.0)
            .build()
        val path = SvgPathElement(pathData)
        path.stroke().set(SvgColors.BLACK)
        val (svg, group) = svgWithContent(path)

        SvgComicTransformer.transform(svg, config)

        val paths = group.children().filterIsInstance<SvgPathElement>()
        assertTrue(paths.isNotEmpty(), "Path should be replaced with wobbled version")

        val newPathData = paths.first().d().get().toString()
        assertTrue(newPathData.contains("C"), "Wobbled path should use cubic Bezier (C) commands")
    }

    @Test
    fun textGetsComicFont() {
        val text = SvgTextElement("Hello")
        val (svg, _) = svgWithContent(text)

        SvgComicTransformer.transform(svg, config)

        val fontFamily = text.getAttribute(SvgTextContent.FONT_FAMILY).get()
        assertTrue(fontFamily != null && fontFamily.contains("Comic Neue"),
            "Text should have comic font, got: $fontFamily")
    }

    @Test
    fun rootLevelTextAlsoGetsComicFont() {
        val svg = SvgSvgElement(200.0, 200.0)
        val text = SvgTextElement("Title")
        svg.children().add(text)

        SvgComicTransformer.transform(svg, config)

        val fontFamily = text.getAttribute(SvgTextContent.FONT_FAMILY).get()
        assertTrue(fontFamily != null && fontFamily.contains("Comic Neue"),
            "Root-level text should also get comic font, got: $fontFamily")
    }

    @Test
    fun fontCssStyleIsInjected() {
        val svg = SvgSvgElement(200.0, 200.0)

        SvgComicTransformer.transform(svg, config)

        val styleElements = svg.children().filterIsInstance<SvgStyleElement>()
        assertTrue(styleElements.isNotEmpty(), "Should inject a style element")
    }

    @Test
    fun circleIsReplacedWithGroup() {
        val circle = SvgCircleElement(100.0, 100.0, 40.0)
        circle.fill().set(SvgColors.RED)
        circle.stroke().set(SvgColors.BLACK)
        val (svg, group) = svgWithContent(circle)

        SvgComicTransformer.transform(svg, config)

        val groups = group.children().filterIsInstance<SvgGElement>()
        assertTrue(groups.isNotEmpty(), "Circle should be replaced with SvgGElement")
    }

    @Test
    fun nestedGroupsAreTransformed() {
        val innerGroup = SvgGElement()
        val rect = SvgRectElement(0.0, 0.0, 50.0, 30.0)
        rect.fill().set(SvgColors.GREEN)
        rect.strokeWidth().set(1.0)
        innerGroup.children().add(rect)
        val (svg, _) = svgWithContent(innerGroup)

        SvgComicTransformer.transform(svg, config)

        val replacements = innerGroup.children().filterIsInstance<SvgGElement>()
        assertTrue(replacements.isNotEmpty(), "Rect inside nested group should be replaced with a group")
    }

    @Test
    fun shallowRectsAreNotTransformed() {
        // Simulate real tree: svg > rootGroup > backgroundRect
        // Background rects at depth < 2 should not be transformed
        val svg = SvgSvgElement(200.0, 200.0)
        val rootGroup = SvgGElement()
        val backgroundRect = SvgRectElement(0.0, 0.0, 200.0, 200.0)
        backgroundRect.fill().set(SvgColors.WHITE)
        backgroundRect.stroke().set(SvgColors.BLACK)
        backgroundRect.strokeWidth().set(1.0)
        rootGroup.children().add(backgroundRect)
        svg.children().add(rootGroup)

        SvgComicTransformer.transform(svg, config)

        // Rect at depth 1 (inside root group) should NOT be replaced — it's a background
        assertTrue(rootGroup.children().any { it is SvgRectElement },
            "Background rect at depth 1 should remain untransformed")
    }

    @Test
    fun deterministicOutputWithSameSeed() {
        fun buildAndTransform(seed: Long): String {
            val rect = SvgRectElement(10.0, 10.0, 80.0, 60.0)
            rect.fill().set(SvgColors.BLUE)
            rect.stroke().set(SvgColors.BLACK)
            rect.strokeWidth().set(1.0)
            val (svg, group) = svgWithContent(rect)
            SvgComicTransformer.transform(svg, ComicConfig(seed = seed))
            val replacementGroup = group.children().filterIsInstance<SvgGElement>().first()
            val paths = replacementGroup.children().filterIsInstance<SvgPathElement>()
            return paths.joinToString("|") { it.d().get().toString() }
        }

        val result1 = buildAndTransform(42)
        val result2 = buildAndTransform(42)
        assertEquals(result1, result2, "Same seed should produce identical output")

        val result3 = buildAndTransform(99)
        assertTrue(result1 != result3, "Different seeds should produce different output")
    }
}
