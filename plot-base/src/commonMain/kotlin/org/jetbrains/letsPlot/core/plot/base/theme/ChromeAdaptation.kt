/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.theme

// Chrome-geometry behaviors a renderer style can request (Theme.chromeAdaptations). Each value is
// honored by exactly one chrome component (named below); add a value here only together with the
// chrome code that honors it.
enum class ChromeAdaptation {
    // AxisComponent: draw the axis line as one segment per tick gap so a wobbling renderer stays
    // anchored at every tick base. Without it the line is a single element (dash-phase-safe).
    SPLIT_AXIS_LINE_AT_TICKS,

    // TooltipBox: build the box+pointer outline as a wobbled closed path instead of the crisp
    // bezier-cornered path.
    HAND_DRAWN_TOOLTIP_OUTLINE,

    // SquareFrameOfReference.doDrawPanelBorder: draw the panel border as four independent wobbled spine
    // lines (with a small corner overshoot) instead of one crisp rectangle.
    HAND_DRAWN_PANEL_BORDER,
}
