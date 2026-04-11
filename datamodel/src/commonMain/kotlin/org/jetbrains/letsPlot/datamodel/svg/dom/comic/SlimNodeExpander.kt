/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.datamodel.svg.dom.comic

import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCircleElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgLineElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement
import org.jetbrains.letsPlot.datamodel.svg.dom.slim.SvgSlimElements
import org.jetbrains.letsPlot.datamodel.svg.dom.slim.SvgSlimNode

/**
 * Converts SvgSlimNode subtrees (read-only, used for scatter-plot perf optimization)
 * into regular SvgElement nodes so they can be transformed by the comic transformer.
 *
 * Only triggered when comic mode is active.
 */
object SlimNodeExpander {

    /**
     * Recursively expand all SvgSlimNode children in the given element's subtree
     * into regular SvgElement nodes.
     */
    fun expandSlimNodes(root: SvgNode) {
        val children = root.children()
        var i = 0
        var expanded = false
        while (i < children.size) {
            val child = children[i]
            if (child is SvgSlimNode) {
                val expandedNode = expandSlimNode(child)
                children.removeAt(i)
                children.add(i, expandedNode)
                expanded = true
            } else {
                expandSlimNodes(child)
            }
            i++
        }
        // The subtree is no longer immutable — clear the prebuilt flag
        // so the raster mapper uses normal typed-property synchronization
        // instead of the string-based SvgNodeSubtreeGeneratingSynchronizer.
        if (expanded) {
            root.isPrebuiltSubtree = false
        }
    }

    private fun expandSlimNode(slim: SvgSlimNode): SvgElement {
        val element = when (slim.elementName) {
            SvgSlimElements.GROUP -> SvgGElement()
            SvgSlimElements.RECT -> SvgRectElement()
            SvgSlimElements.CIRCLE -> SvgCircleElement()
            SvgSlimElements.LINE -> SvgLineElement()
            SvgSlimElements.PATH -> SvgPathElement()
            else -> SvgGElement()
        }

        // Copy attributes
        for (attr in slim.attributes) {
            element.setAttribute(attr.key, attr.value)
        }

        // Recursively expand children
        for (slimChild in slim.slimChildren) {
            val childElement = expandSlimNode(slimChild)
            element.children().add(childElement)
        }

        return element
    }
}
