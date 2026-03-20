/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package demo.plot.common.model.plotConfig

import demoAndTestShared.parsePlotSpec

class Smiley {
    fun plotSpecList(): List<MutableMap<String, Any>> {
        return listOf(
            basic(),
            sizeUnit(),
            happinessRange(),
            aesthetics(),
            withOtherLayers(),
            faceted(),
        )
    }

    private fun basic(): MutableMap<String, Any> {
        val spec = """
            {
              'kind': 'plot',
              'data': {
                'x': [1, 2, 3],
                'y': [1, 2, 1]
              },
              'mapping': {
                'x': 'x',
                'y': 'y'
              },
              'ggtitle': {
                'text': 'Default aesthetics'
              },
              'layers': [
                {
                  'geom': 'smiley'
                }
              ]
            }
        """.trimIndent()

        return HashMap(parsePlotSpec(spec))
    }

    private fun sizeUnit(): MutableMap<String, Any> {
        val spec = """
            {
              'kind': 'plot',
              'ggtitle': {
                'text': 'size_unit=x'
              },
              'layers': [
                {
                  'geom': 'smiley',
                  'x': 0,
                  'size': 1,
                  'size_unit': 'x'
                }
              ],
              'coord': {"name": "fixed", "ratio": 1.0, "flip": "False"},
              'scales': [
                {'aesthetic': 'x', 'limits': [-2, 2]},
                {'aesthetic': 'y', 'limits': [-2, 2]}
              ]
            }
        """.trimIndent()

        return HashMap(parsePlotSpec(spec))
    }

    private fun happinessRange(): MutableMap<String, Any> {
        val spec = """
            {
              'kind': 'plot',
              'ggtitle': {
                'text': 'Different happiness per smiley (separate layers)'
              },
              'layers': [
                {
                  'geom': 'smiley',
                  'data': {
                    'x': [1],
                    'y': [1]
                  },
                  'mapping': {
                    'x': 'x',
                    'y': 'y'
                  },
                  'happiness': 1.0,
                  'size': 10,
                  'fill': '#6BCB77',
                  'color': 'black'
                },
                {
                  'geom': 'smiley',
                  'data': {
                    'x': [2],
                    'y': [2]
                  },
                  'mapping': {
                    'x': 'x',
                    'y': 'y'
                  },
                  'happiness': 0.0,
                  'size': 10,
                  'fill': '#FFD93D',
                  'color': 'black'
                },
                {
                  'geom': 'smiley',
                  'data': {
                    'x': [3],
                    'y': [1]
                  },
                  'mapping': {
                    'x': 'x',
                    'y': 'y'
                  },
                  'happiness': -1.0,
                  'size': 10,
                  'fill': '#FF6B6B',
                  'color': 'black'
                }
              ]
            }
        """.trimIndent()

        return HashMap(parsePlotSpec(spec))
    }

    private fun aesthetics(): MutableMap<String, Any> {
        val spec = """
            {
              'kind': 'plot',
              'data': {
                'x': [1, 2, 3, 4],
                'y': [1, 2, 3, 2]
              },
              'mapping': {
                'x': 'x',
                'y': 'y'
              },
              'ggtitle': {
                'text': 'All fixed: color, fill, size, alpha, happiness'
              },
              'layers': [
                {
                  'geom': 'smiley',
                  'color': 'red',
                  'fill': 'blue',
                  'size': 12,
                  'alpha': 0.2,
                  'happiness': -1
                }
              ]
            }
        """.trimIndent()

        return HashMap(parsePlotSpec(spec))
    }

    private fun withOtherLayers(): MutableMap<String, Any> {
        val spec = """
            {
              'kind': 'plot',
              'data': {
                'x': [1, 2, 3, 4, 5],
                'y': [2, 3, 1, 4, 2]
              },
              'mapping': {
                'x': 'x',
                'y': 'y'
              },
              'ggtitle': {
                'text': 'geom_line behind geom_smiley'
              },
              'layers': [
                {
                  'geom': 'line',
                  'color': 'steelblue',
                  'size': 2
                },
                {
                  'geom': 'smiley',
                  'size': 8,
                  'fill': 'blue',
                  'color': 'white',
                  'happiness': 0.6
                }
              ]
            }
        """.trimIndent()

        return HashMap(parsePlotSpec(spec))
    }

    private fun faceted(): MutableMap<String, Any> {
        val spec = """
            {
              'kind': 'plot',
              'data': {
                'x': [1, 2, 3, 1, 2, 3],
                'y': [1, 2, 1, 2, 1, 2],
                'cat': ['A', 'A', 'A', 'B', 'B', 'B']
              },
              'mapping': {
                'x': 'x',
                'y': 'y'
              },
              'ggtitle': {
                'text': 'facet_wrap by category'
              },
              'facet': {
                'name': 'wrap',
                'facets': 'cat'
              },
              'layers': [
                {
                  'geom': 'smiley'
                }
              ]
            }
        """.trimIndent()

        return HashMap(parsePlotSpec(spec))
    }
}
