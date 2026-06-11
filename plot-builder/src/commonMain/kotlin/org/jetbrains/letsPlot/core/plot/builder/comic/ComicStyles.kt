/*
 * Copyright (c) 2026. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.comic

// Single place where the active comic style is chosen. Keyed on the comic-enabled flag for now,
// becomes a style-id selector once a second style is added.
object ComicStyles {
    fun resolve(comicEnabled: Boolean): ComicStylize? = if (comicEnabled) XkcdWobble() else null
}
