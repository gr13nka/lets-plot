/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.plot.common.model.plotConfig

import demoAndTestShared.parsePlotSpec

class ComicBarPlot {
    fun plotSpecList(): List<MutableMap<String, Any>> {
        return listOf(
            comicGrid()
        )
    }

    companion object {

        private fun comicGrid(): MutableMap<String, Any> {
            val spec = """
                {
                    'kind': 'subplots',
                    'layout': { 'name': 'grid', 'ncol': 3, 'nrow': 3, 'hspace': 0.4, 'vspace': 0.4 },
                    'figures': [
                        ${barPlot()},
                        ${stackedBarPlot()},
                        ${scatterLinePlot()},
                        ${areaPlot()},
                        ${histogramPlot()},
                        ${boxPlot()},
                        ${smileyPlot()},
                        ${piePlot()},
                        ${facetPlot()}
                    ],
                    'rendering_style': { 'name': 'comic' }
                }
            """.trimIndent()
            return parsePlotSpec(spec)
        }

        private fun barPlot() = """
            {
                'kind': 'plot',
                'data': {
                    'x': ['A', 'B', 'C', 'D', 'E'],
                    'y': [3, 7, 2, 5, 4]
                },
                'mapping': { 'x': 'x', 'y': 'y', 'fill': 'x' },
                'layers': [
                    { 'geom': 'bar', 'stat': 'identity' }
                ],
                'ggtitle': { 'text': 'Bar Plot' }
            }
        """

        private fun stackedBarPlot() = """
            {
                'kind': 'plot',
                'data': {
                    'cat': ['A', 'A', 'B', 'B', 'C', 'C'],
                    'grp': ['X', 'Y', 'X', 'Y', 'X', 'Y'],
                    'val': [3, 5, 7, 2, 4, 6]
                },
                'mapping': { 'x': 'cat', 'y': 'val', 'fill': 'grp' },
                'layers': [
                    { 'geom': 'bar', 'stat': 'identity', 'position': 'stack' }
                ],
                'ggtitle': { 'text': 'Stacked Bars' }
            }
        """

        private fun scatterLinePlot() = """
            {
                'kind': 'plot',
                'data': {
                    'x': [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
                    'y': [2, 5, 3, 8, 4, 7, 1, 6, 9, 3]
                },
                'mapping': { 'x': 'x', 'y': 'y' },
                'layers': [
                    { 'geom': 'line' },
                    { 'geom': 'point', 'size': 5 }
                ],
                'ggtitle': { 'text': 'Scatter + Line' }
            }
        """

        private fun areaPlot() = """
            {
                'kind': 'plot',
                'data': {
                    'x': [1, 2, 3, 4, 5, 6, 7, 8],
                    'y': [1, 4, 2, 7, 5, 3, 6, 4]
                },
                'mapping': { 'x': 'x', 'y': 'y' },
                'layers': [
                    { 'geom': 'area', 'fill': '#4488cc', 'color': '#2266aa' }
                ],
                'ggtitle': { 'text': 'Area Chart' }
            }
        """

        private fun histogramPlot() = """
            {
                'kind': 'plot',
                'data': {
                    'x': [1.2, 2.3, 2.1, 3.5, 3.2, 3.8, 4.1, 4.5, 4.2, 5.0, 2.8, 3.1, 3.9, 4.7, 2.5, 3.3, 4.0, 1.8, 5.2, 3.6]
                },
                'mapping': { 'x': 'x' },
                'layers': [
                    { 'geom': 'histogram', 'bins': 8, 'fill': '#cc6644', 'color': '#993322' }
                ],
                'ggtitle': { 'text': 'Histogram' }
            }
        """

        private fun boxPlot() = """
            {
                'kind': 'plot',
                'data': {
                    'cat': ['A','A','A','A','A','A','A','A','B','B','B','B','B','B','B','B','C','C','C','C','C','C','C','C'],
                    'val': [2,3,5,7,1,4,6,8, 4,6,8,3,5,7,9,2, 1,2,3,5,4,6,7,8]
                },
                'mapping': { 'x': 'cat', 'y': 'val', 'fill': 'cat' },
                'layers': [
                    { 'geom': 'boxplot' }
                ],
                'ggtitle': { 'text': 'Box Plot' }
            }
        """

        private fun smileyPlot() = """
            {
                'kind': 'plot',
                'data': {
                    'x': [1, 2, 3, 4, 5],
                    'y': [2, 4, 3, 5, 1],
                    'h': [-0.8, -0.3, 0.0, 0.5, 1.0],
                    's': [8, 10, 12, 10, 8]
                },
                'mapping': { 'x': 'x', 'y': 'y', 'happiness': 'h', 'size': 's', 'fill': 'h' },
                'layers': [
                    { 'geom': 'smiley' }
                ],
                'scales': [
                    { 'aesthetic': 'fill', 'low': '#cc4444', 'high': '#44cc44', 'scale_mapper_kind': 'color_gradient' }
                ],
                'ggtitle': { 'text': 'Smiley Plot' }
            }
        """

        private fun piePlot() = """
            {
                'kind': 'plot',
                'data': {
                    'cat': ['Apple', 'Banana', 'Cherry', 'Date'],
                    'val': [30, 20, 35, 15],
                    'x': [1, 1, 1, 1]
                },
                'mapping': { 'x': 'x', 'y': 'val', 'fill': 'cat' },
                'layers': [
                    { 'geom': 'bar', 'stat': 'identity', 'width': 1.0 }
                ],
                'coord': { 'name': 'polar', 'theta': 'y' },
                'ggtitle': { 'text': 'Pie Chart' }
            }
        """

        private fun facetPlot() = """
            {
                'kind': 'plot',
                'data': {
                    'x': ['A','B','C','A','B','C','A','B','C','A','B','C'],
                    'y': [3,5,2,7,4,6,1,8,3,5,2,7],
                    'grp': ['G1','G1','G1','G1','G1','G1','G2','G2','G2','G2','G2','G2']
                },
                'mapping': { 'x': 'x', 'y': 'y', 'fill': 'x' },
                'layers': [
                    { 'geom': 'bar', 'stat': 'identity' }
                ],
                'facet': { 'name': 'wrap', 'facets': 'grp', 'ncol': 2 },
                'ggtitle': { 'text': 'Faceted Bars' }
            }
        """
    }
}
