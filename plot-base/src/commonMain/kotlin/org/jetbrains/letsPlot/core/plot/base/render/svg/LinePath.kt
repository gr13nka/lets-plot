/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.render.svg

import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.intern.observable.property.WritableProperty
import org.jetbrains.letsPlot.commons.values.Color
import org.jetbrains.letsPlot.core.plot.base.render.linetype.LineType
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgColors
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathDataBuilder
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgPathElement

/**
 * Poly-line
 */
class LinePath(private val myPath: SvgPathElement) : SvgComponent() {

    private var myLineType: LineType? = null

    constructor(builder: SvgPathDataBuilder) : this(SvgPathElement(builder.build()))

    init {
        myPath.fill().set(SvgColors.NONE)
        val lineWidth = 1.0
        myPath.strokeWidth().set(lineWidth)

        add(myPath)
    }

    override fun buildComponent() {

    }

    fun color(): WritableProperty<Color?> {
        return myPath.strokeColor()
    }

    fun fill(): WritableProperty<Color?> {
        return myPath.fillColor()
    }

    fun width(): WritableProperty<Double> {
        return object : WritableProperty<Double> {
            override fun set(value: Double) {
                myPath.strokeWidth().set(value)
                updatePathDashArray()
            }
        }
    }

    fun lineType(): WritableProperty<LineType> {
        return object : WritableProperty<LineType> {
            override fun set(value: LineType) {
                myLineType = value
                updatePathDashArray()
            }
        }
    }

    private fun updatePathDashArray() {
        if (myLineType != null) {
            val width = myPath.strokeWidth().get() ?: 1.0
            StrokeDashArraySupport.apply(myPath, width, myLineType!!)
        }
    }

}
