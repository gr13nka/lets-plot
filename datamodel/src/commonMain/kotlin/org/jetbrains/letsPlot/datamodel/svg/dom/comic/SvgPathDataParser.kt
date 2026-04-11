/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.datamodel.svg.dom.comic

import org.jetbrains.letsPlot.commons.geometry.DoubleVector

/**
 * Parses an SVG path `d` string into a list of [PathCommand] with absolute coordinates.
 * Supports M, L, H, V, C, S, Q, T, A, Z commands (both absolute and relative).
 */
object SvgPathDataParser {

    sealed class PathCommand {
        data class MoveTo(val to: DoubleVector) : PathCommand()
        data class LineTo(val to: DoubleVector) : PathCommand()
        data class CurveTo(val cp1: DoubleVector, val cp2: DoubleVector, val to: DoubleVector) : PathCommand()
        data class SmoothCurveTo(val cp2: DoubleVector, val to: DoubleVector) : PathCommand()
        data class QuadTo(val cp: DoubleVector, val to: DoubleVector) : PathCommand()
        data class SmoothQuadTo(val to: DoubleVector) : PathCommand()
        data class ArcTo(
            val rx: Double, val ry: Double, val xRotation: Double,
            val largeArc: Boolean, val sweep: Boolean, val to: DoubleVector
        ) : PathCommand()

        data object ClosePath : PathCommand()
    }

    /**
     * Parse an SVG path data string into absolute-coordinate commands.
     */
    fun parse(pathData: String): List<PathCommand> {
        val commands = mutableListOf<PathCommand>()
        val tokens = tokenize(pathData)
        var i = 0
        var currentX = 0.0
        var currentY = 0.0
        var startX = 0.0
        var startY = 0.0

        while (i < tokens.size) {
            val token = tokens[i]
            if (token.length == 1 && token[0].isLetter()) {
                val cmd = token[0]
                val isRelative = cmd.isLowerCase()
                i++

                when (cmd.uppercaseChar()) {
                    'M' -> {
                        // MoveTo: may have implicit LineTo params after the first pair
                        var first = true
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val x = tokens[i++].toDouble()
                            val y = tokens[i++].toDouble()
                            val absX = if (isRelative) currentX + x else x
                            val absY = if (isRelative) currentY + y else y
                            if (first) {
                                commands.add(PathCommand.MoveTo(DoubleVector(absX, absY)))
                                startX = absX
                                startY = absY
                                first = false
                            } else {
                                commands.add(PathCommand.LineTo(DoubleVector(absX, absY)))
                            }
                            currentX = absX
                            currentY = absY
                        }
                    }

                    'L' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val x = tokens[i++].toDouble()
                            val y = tokens[i++].toDouble()
                            val absX = if (isRelative) currentX + x else x
                            val absY = if (isRelative) currentY + y else y
                            commands.add(PathCommand.LineTo(DoubleVector(absX, absY)))
                            currentX = absX
                            currentY = absY
                        }
                    }

                    'H' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val x = tokens[i++].toDouble()
                            val absX = if (isRelative) currentX + x else x
                            commands.add(PathCommand.LineTo(DoubleVector(absX, currentY)))
                            currentX = absX
                        }
                    }

                    'V' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val y = tokens[i++].toDouble()
                            val absY = if (isRelative) currentY + y else y
                            commands.add(PathCommand.LineTo(DoubleVector(currentX, absY)))
                            currentY = absY
                        }
                    }

                    'C' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val x1 = tokens[i++].toDouble()
                            val y1 = tokens[i++].toDouble()
                            val x2 = tokens[i++].toDouble()
                            val y2 = tokens[i++].toDouble()
                            val x = tokens[i++].toDouble()
                            val y = tokens[i++].toDouble()
                            val ox = if (isRelative) currentX else 0.0
                            val oy = if (isRelative) currentY else 0.0
                            commands.add(
                                PathCommand.CurveTo(
                                    DoubleVector(ox + x1, oy + y1),
                                    DoubleVector(ox + x2, oy + y2),
                                    DoubleVector(ox + x, oy + y)
                                )
                            )
                            currentX = ox + x
                            currentY = oy + y
                        }
                    }

                    'S' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val x2 = tokens[i++].toDouble()
                            val y2 = tokens[i++].toDouble()
                            val x = tokens[i++].toDouble()
                            val y = tokens[i++].toDouble()
                            val ox = if (isRelative) currentX else 0.0
                            val oy = if (isRelative) currentY else 0.0
                            commands.add(
                                PathCommand.SmoothCurveTo(
                                    DoubleVector(ox + x2, oy + y2),
                                    DoubleVector(ox + x, oy + y)
                                )
                            )
                            currentX = ox + x
                            currentY = oy + y
                        }
                    }

                    'Q' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val x1 = tokens[i++].toDouble()
                            val y1 = tokens[i++].toDouble()
                            val x = tokens[i++].toDouble()
                            val y = tokens[i++].toDouble()
                            val ox = if (isRelative) currentX else 0.0
                            val oy = if (isRelative) currentY else 0.0
                            commands.add(
                                PathCommand.QuadTo(
                                    DoubleVector(ox + x1, oy + y1),
                                    DoubleVector(ox + x, oy + y)
                                )
                            )
                            currentX = ox + x
                            currentY = oy + y
                        }
                    }

                    'T' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val x = tokens[i++].toDouble()
                            val y = tokens[i++].toDouble()
                            val absX = if (isRelative) currentX + x else x
                            val absY = if (isRelative) currentY + y else y
                            commands.add(PathCommand.SmoothQuadTo(DoubleVector(absX, absY)))
                            currentX = absX
                            currentY = absY
                        }
                    }

                    'A' -> {
                        while (i < tokens.size && isNumber(tokens[i])) {
                            val rx = tokens[i++].toDouble()
                            val ry = tokens[i++].toDouble()
                            val xRot = tokens[i++].toDouble()
                            val largeArc = tokens[i++].toDouble() != 0.0
                            val sweep = tokens[i++].toDouble() != 0.0
                            val x = tokens[i++].toDouble()
                            val y = tokens[i++].toDouble()
                            val absX = if (isRelative) currentX + x else x
                            val absY = if (isRelative) currentY + y else y
                            commands.add(PathCommand.ArcTo(rx, ry, xRot, largeArc, sweep, DoubleVector(absX, absY)))
                            currentX = absX
                            currentY = absY
                        }
                    }

                    'Z' -> {
                        commands.add(PathCommand.ClosePath)
                        currentX = startX
                        currentY = startY
                    }
                }
            } else {
                // Skip unexpected token
                i++
            }
        }
        return commands
    }

    private fun isNumber(s: String): Boolean {
        val c = s[0]
        return c.isDigit() || c == '-' || c == '.'
    }

    /**
     * Tokenize an SVG path data string into command letters and number strings.
     *
     * Handles SVG number grammar:
     * - Scientific notation: 1e-4, 3.5E+2
     * - Implicit dot separation: "1.2.3" → "1.2", ".3"
     * - Minus as separator: "10-5" → "10", "-5"
     */
    private fun tokenize(pathData: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0

        while (i < pathData.length) {
            val c = pathData[i]
            when {
                c.isLetter() && c != 'e' && c != 'E' -> {
                    tokens.add(c.toString())
                    i++
                }

                c.isDigit() || c == '.' || c == '-' || c == '+' -> {
                    i = readNumber(pathData, i, tokens)
                }

                else -> {
                    // Whitespace, comma — skip
                    i++
                }
            }
        }

        return tokens
    }

    /**
     * Read a single SVG number starting at [start], add it to [tokens], return next index.
     * Handles optional sign, integer/fractional parts, and scientific notation (e/E).
     * Splits implicit sequences like "1.2.3" into "1.2" and ".3".
     */
    private fun readNumber(pathData: String, start: Int, tokens: MutableList<String>): Int {
        val sb = StringBuilder()
        var i = start
        var hasDot = false

        // Optional leading sign
        if (i < pathData.length && (pathData[i] == '-' || pathData[i] == '+')) {
            sb.append(pathData[i])
            i++
        }

        // Integer and fractional parts
        while (i < pathData.length) {
            val ch = pathData[i]
            when {
                ch.isDigit() -> {
                    sb.append(ch)
                    i++
                }
                ch == '.' && !hasDot -> {
                    hasDot = true
                    sb.append(ch)
                    i++
                }
                ch == '.' && hasDot -> {
                    // Second dot starts a new number (e.g., "1.2.3" → "1.2", ".3")
                    break
                }
                else -> break
            }
        }

        // Scientific notation: e/E followed by optional sign and digits
        if (i < pathData.length && (pathData[i] == 'e' || pathData[i] == 'E')) {
            sb.append(pathData[i])
            i++
            if (i < pathData.length && (pathData[i] == '+' || pathData[i] == '-')) {
                sb.append(pathData[i])
                i++
            }
            while (i < pathData.length && pathData[i].isDigit()) {
                sb.append(pathData[i])
                i++
            }
        }

        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }

        return i
    }
}
