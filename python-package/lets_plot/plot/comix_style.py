#  Copyright (c) 2026. JetBrains s.r.o.
#  Use of this source code is governed by the MIT license that can be found in the LICENSE file.

from .core import FeatureSpec

__all__ = ['comix_style']

def comix_style(*,
                roughness=None,
                hatching_stroke_width=None,
                hatching_angle=None,
                ):
    params = {}
    if roughness is not None:
        params['roughness'] = roughness
    if hatching_angle is not None:
        params['hatching_angle'] = hatching_angle
    if hatching_stroke_width is not None:
        params['hatching_stroke_width'] = hatching_stroke_width

    return FeatureSpec('comix_style', name=None, **params)
