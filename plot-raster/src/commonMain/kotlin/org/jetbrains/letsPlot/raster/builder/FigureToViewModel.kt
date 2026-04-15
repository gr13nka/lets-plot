/*
 * Copyright (c) 2023 JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.raster.builder

import org.jetbrains.letsPlot.commons.registration.Registration
import org.jetbrains.letsPlot.core.plot.builder.PlotContainer
import org.jetbrains.letsPlot.core.plot.builder.PlotSvgRoot
import org.jetbrains.letsPlot.core.plot.builder.buildinfo.FigureBuildInfo
import org.jetbrains.letsPlot.core.plot.builder.comix.ComixStylizer
import org.jetbrains.letsPlot.core.plot.builder.subPlots.CompositeFigureSvgRoot

internal object FigureToViewModel {
    fun eval(buildInfo: FigureBuildInfo, comixStylizer: ComixStylizer? = null): ViewModel {
        @Suppress("NAME_SHADOWING")
        val buildInfo = buildInfo.layoutedByOuterSize()
        return when (val svgRoot = buildInfo.createSvgRoot()) {
            is PlotSvgRoot -> processPlotFigure(svgRoot, comixStylizer)
            is CompositeFigureSvgRoot -> processCompositeFigure(svgRoot, comixStylizer).also {
                it.assembleAsRoot()
            }

            else -> error("Unsupported figure: ${svgRoot::class.simpleName}")
        }
    }

    private fun processCompositeFigure(
        svgRoot: CompositeFigureSvgRoot,
        comixStylizer: ComixStylizer?,
    ): CompositeFigureModel {
        svgRoot.ensureContentBuilt()
        comixStylizer?.stylize(svgRoot.svg)

        val compositeModel = CompositeFigureModel(svgRoot.svg)

        for (childSvg in svgRoot.elements) {
            val childBounds = childSvg.bounds.add(svgRoot.bounds.origin)

            childSvg.svg.x().set(childBounds.left)
            childSvg.svg.y().set(childBounds.top)

            val childModel = when (childSvg) {
                is CompositeFigureSvgRoot -> processCompositeFigure(childSvg, comixStylizer)
                is PlotSvgRoot -> processPlotFigure(childSvg, comixStylizer)
                else -> error("Unsupported figure: ${svgRoot::class.simpleName}")
            }

            // Use childSvg.bounds (local to this composite) for mouse event dispatch,
            // not childBounds (which includes the parent's origin offset).
            // Events arriving here are already translated to local coordinates
            // by the parent's ChildMouseEventSource.
            compositeModel.addChild(childModel, childSvg.bounds)
        }
        return compositeModel
    }

    private fun processPlotFigure(svgRoot: PlotSvgRoot, comixStylizer: ComixStylizer?): SinglePlotModel {
        val plotContainer = PlotContainer(svgRoot, comixStylizer)

        val plotModel = SinglePlotModel(
            svg = svgRoot.svg,
            toolEventDispatcher = plotContainer.toolEventDispatcher,
            registration = Registration.from(plotContainer)
        )
        plotContainer.mouseEventPeer.addEventSource(plotModel.mouseEventPeer)

        return plotModel
    }
}
