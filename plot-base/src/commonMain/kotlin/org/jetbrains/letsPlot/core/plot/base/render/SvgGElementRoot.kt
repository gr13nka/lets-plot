/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render

import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode

// Adapts an SvgGElement to SvgRoot so a value-returning caller (legend key factories, chrome
// components) can drive a Renderer and hand back the populated group.
class SvgGElementRoot(private val g: SvgGElement) : SvgRoot {
    override fun add(node: SvgNode) {
        g.children().add(node)
    }
}
