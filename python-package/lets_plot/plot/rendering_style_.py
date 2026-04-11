#
# Copyright (c) 2025. JetBrains s.r.o.
# Use of this source code is governed by the MIT license that can be found in the LICENSE file.
#

from .core import FeatureSpec

__all__ = ['rendering_style_comic']


def rendering_style_comic(
        *,
        roughness=None,
        bowing=None,
        hatch_angle=None,
        hatch_gap=None,
        hatch_stroke_width=None,
        seed=None,
        font=None
):
    """
    Apply a comic rendering style to a plot, making it look hand-drawn.

    Lines become wobbly, solid fills become cross-hatched, and text uses a comic font.
    This is orthogonal to themes: themes control WHAT (colors, layout, fonts),
    rendering styles control HOW (wobbly lines, hatching). Combine freely with any theme.

    Parameters
    ----------
    roughness : float, default=3.0
        Overall wobble amplitude multiplier. Higher values produce more distortion.
    bowing : float, default=1.0
        How much straight lines bow/curve. Higher values create more curved lines.
    hatch_angle : float, default=-41.0
        Angle of hatching lines in degrees.
    hatch_gap : float, default=6.0
        Gap between hatch lines in pixels.
    hatch_stroke_width : float, default=2.0
        Stroke width of individual hatch lines.
    seed : int, default=42
        Random seed for deterministic output. Same seed produces identical rendering.
    font : str, default='Comic Neue'
        Font family name for comic-style text rendering.

    Returns
    -------
    `FeatureSpec`
        Rendering style specification.

    Examples
    --------
    .. jupyter-execute::
        :linenos:
        :emphasize-lines: 7

        from lets_plot import *
        LetsPlot.setup_html()
        data = {'x': list('abcde'), 'y': [3, 7, 2, 5, 4]}
        p = ggplot(data, aes(x='x', y='y')) + geom_bar(stat='identity')
        p + rendering_style_comic()
    """
    params = {}
    if roughness is not None:
        params['roughness'] = roughness
    if bowing is not None:
        params['bowing'] = bowing
    if hatch_angle is not None:
        params['hatch_angle'] = hatch_angle
    if hatch_gap is not None:
        params['hatch_gap'] = hatch_gap
    if hatch_stroke_width is not None:
        params['hatch_stroke_width'] = hatch_stroke_width
    if seed is not None:
        params['seed'] = seed
    if font is not None:
        params['font'] = font

    return FeatureSpec('rendering_style', name='comic', **params)
