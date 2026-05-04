/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.base.theme

import org.jetbrains.letsPlot.commons.values.FontFamily

class ComicFontFamilyRegistry : FontFamilyRegistry {
    private val comic = FontFamily(COMIC_FONT_FAMILY, monospaced = false)
    private val delegate = DefaultFontFamilyRegistry()
    override fun get(name: String): FontFamily =
        if (name == FontFamily.DEF_FAMILY_NAME) comic else delegate.get(name)

    companion object {
        const val COMIC_FONT_FAMILY = "Comic Sans MS"
    }
}
