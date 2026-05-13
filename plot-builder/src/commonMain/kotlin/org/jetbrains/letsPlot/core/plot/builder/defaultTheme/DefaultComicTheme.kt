/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.defaultTheme

import org.jetbrains.letsPlot.core.plot.base.theme.ComicTheme
import org.jetbrains.letsPlot.core.plot.base.theme.FontFamilyRegistry
import org.jetbrains.letsPlot.core.plot.builder.defaultTheme.values.ThemeOption.COMIC

internal class DefaultComicTheme(
    options: Map<String, Any>,
    fontFamilyRegistry: FontFamilyRegistry
) : ThemeValuesAccess(options, fontFamilyRegistry), ComicTheme {

    private val comicKey = listOf(COMIC)

    override fun enabled(): Boolean = getBoolean(comicKey)
}
