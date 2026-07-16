/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.defaultTheme

import org.jetbrains.letsPlot.core.plot.base.render.primitive.CrispRenderer
import org.jetbrains.letsPlot.core.plot.base.render.primitive.Renderer
import org.jetbrains.letsPlot.core.plot.base.theme.ChromeAdaptation
import org.jetbrains.letsPlot.core.plot.builder.defaultTheme.ThemeFlavor.Companion.SymbolicColor
import org.jetbrains.letsPlot.core.plot.builder.defaultTheme.values.ThemeOption
import org.jetbrains.letsPlot.core.plot.builder.xkcd.XkcdRenderer

// One registration entry is the single owner of everything a renderer name means: which node factory
// draws the primitives, which chrome-geometry adaptations the choice asks for, and which theme-value
// defaults it implies. The registry map below is the single source of the valid renderer-name set.
internal class RendererStyle(
    val name: String,
    val createRenderer: () -> Renderer,
    val chrome: Set<ChromeAdaptation>,
    val themeOverlay: Map<String, Any>,
)

// Public so cross-module tooling (the render-snapshot harness in platf-awt) can auto-discover the
// registered style names; `forName`/`RendererStyle` stay internal so the registry entry type is not
// exposed as API.
object RendererStyles {
    private val styles: Map<String, RendererStyle> = listOf(
        RendererStyle(
            name = ThemeOption.Renderer.CRISP,
            createRenderer = { CrispRenderer },
            chrome = emptySet(),
            themeOverlay = emptyMap(),
        ),
        RendererStyle(
            name = ThemeOption.Renderer.XKCD,
            createRenderer = { XkcdRenderer() },
            chrome = setOf(
                ChromeAdaptation.SPLIT_AXIS_LINE_AT_TICKS,
                ChromeAdaptation.HAND_DRAWN_TOOLTIP_OUTLINE,
                ChromeAdaptation.HAND_DRAWN_PANEL_BORDER,
            ),
            // Theme-value defaults the xkcd look implies (chrome font, legend border, no grid, hand-drawn
            // frame instead of axis lines). Merged UNDER user options in ThemeUtil.getThemeValues, so
            // explicit user settings win over the look.
            themeOverlay = mapOf(
                ThemeOption.TEXT to mapOf(ThemeOption.Elem.FONT_FAMILY to XkcdRenderer.FONT_FAMILY),
                ThemeOption.LEGEND_BKGR_RECT to mapOf(ThemeOption.Elem.SIZE to 1.0),
                // Real xkcd has no grid: hide it by default. A user who sets panel_grid gets it back,
                // drawn crisp (GridComponent never distorts the grid).
                ThemeOption.PANEL_GRID to ThemeOption.ELEMENT_BLANK,
                // The matplotlib-xkcd frame: a wobbled border on all four sides (drawn as four spines by
                // HAND_DRAWN_PANEL_BORDER). It replaces the axis lines, which are blanked so the frame is
                // the single boundary (no doubled baseline).
                ThemeOption.PANEL_BORDER_RECT to mapOf(
                    // BLANK=false explicitly: the theme merge is deep, so the baseline's blank flag would
                    // otherwise survive and keep the border hidden.
                    ThemeOption.Elem.BLANK to false,
                    ThemeOption.Elem.SIZE to 1.0,
                    ThemeOption.Elem.COLOR to SymbolicColor.BLACK,
                ),
                ThemeOption.AXIS_LINE to ThemeOption.ELEMENT_BLANK,
            ),
        ),
    ).associateBy { it.name }

    val names: Set<String> = styles.keys

    internal fun forName(name: String?): RendererStyle =
        if (name == null) {
            styles.getValue(ThemeOption.Renderer.CRISP)
        } else {
            styles[name] ?: throw IllegalArgumentException(
                "Unsupported renderer: '$name'. Expected one of: ${names.joinToString(", ")}."
            )
        }
}
