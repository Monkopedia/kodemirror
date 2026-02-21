/*
 * Copyright 2026 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT.
 * See NOTICE file for details.
 */
package com.monkopedia.kodemirror.state

/**
 * Returns a next grapheme cluster break _after_ (not equal to)
 * [pos], if [forward] is true, or before otherwise. Returns [pos]
 * itself if no further cluster break is available in the string.
 * Moves across surrogate pairs, extending characters (when
 * [includeExtending] is true), characters joined with zero-width
 * joiners, and flag emoji.
 */
fun findClusterBreak(
    str: String,
    pos: Int,
    forward: Boolean = true,
    includeExtending: Boolean = true
): Int {
    var position = pos
    if (forward) {
        if (position >= str.length) return position

        // Move past the current character (handle surrogate pairs)
        if (position < str.length && isSurrogateHigh(str[position].code)) {
            position++
        }
        position++

        // Continue moving while we're in the same cluster
        while (position < str.length) {
            val code = codePointAt(str, position)

            // Check for extending characters
            if (includeExtending && isExtendingChar(code)) {
                position += codePointSize(code)
                continue
            }

            // Check for zero-width joiner (U+200D)
            if (code == 0x200D) {
                position++
                // Skip the next character after ZWJ
                if (position < str.length) {
                    val nextCode = codePointAt(str, position)
                    position += codePointSize(nextCode)
                }
                continue
            }

            // Check for regional indicator symbols (flags)
            // Regional indicators are in range U+1F1E6..U+1F1FF
            if (position >= 2 && code in 0x1F1E6..0x1F1FF) {
                val prevPos = position - codePointSize(code)
                val prevCode = if (prevPos > 0) codePointAt(str, prevPos - 2) else 0
                if (prevCode in 0x1F1E6..0x1F1FF) {
                    // We're the second regional indicator in a pair
                    position += codePointSize(code)
                    continue
                }
            }

            break
        }
    } else {
        if (position <= 0) return position

        // Move before the current character
        position--
        if (position > 0 && isSurrogateLow(str[position].code)) {
            position--
        }

        // Continue moving while we're in the same cluster
        while (position > 0) {
            val prevPos = position - 1
            if (prevPos > 0 && isSurrogateLow(str[position - 1].code)) {
                if (isSurrogateHigh(str[prevPos - 1].code)) {
                    val code = codePointAt(str, prevPos - 1)

                    // Check for extending characters
                    if (includeExtending && isExtendingChar(code)) {
                        position -= 2
                        continue
                    }

                    // Check for regional indicators
                    if (code in 0x1F1E6..0x1F1FF) {
                        val nextCode = codePointAt(str, position)
                        if (nextCode in 0x1F1E6..0x1F1FF) {
                            position -= 2
                            continue
                        }
                    }

                    break
                }
            }

            val code = codePointAt(str, prevPos)

            // Check for extending characters
            if (includeExtending && isExtendingChar(code)) {
                position--
                continue
            }

            // Check for ZWJ
            if (prevPos > 0 && str[prevPos].code == 0x200D) {
                position--
                // Move past the character before ZWJ
                if (position > 0) {
                    position--
                    if (position > 0 && isSurrogateLow(str[position].code)) {
                        position--
                    }
                }
                continue
            }

            break
        }
    }

    return position
}

/**
 * Query whether the given character has a Grapheme_Cluster_Break
 * value of Extend in Unicode.
 */
internal fun isExtendingChar(code: Int): Boolean {
    // Combining marks (Mn, Mc, Me)
    if (code in 0x0300..0x036F) return true // Combining Diacritical Marks
    if (code in 0x1AB0..0x1AFF) return true // Combining Diacritical Marks Extended
    if (code in 0x1DC0..0x1DFF) return true // Combining Diacritical Marks Supplement
    if (code in 0x20D0..0x20FF) return true // Combining Diacritical Marks for Symbols
    if (code in 0xFE20..0xFE2F) return true // Combining Half Marks

    // Variation selectors
    if (code in 0xFE00..0xFE0F) return true
    if (code in 0xE0100..0xE01EF) return true

    // Common extending characters
    if (code in 0x0903..0x0903) return true // Devanagari
    if (code in 0x093E..0x094F) return true
    if (code in 0x0951..0x0957) return true
    if (code in 0x0962..0x0963) return true

    // Additional combining marks
    if (code in 0x0981..0x0983) return true // Bengali
    if (code in 0x09BE..0x09C4) return true
    if (code in 0x09C7..0x09C8) return true
    if (code in 0x09CB..0x09CD) return true
    if (code in 0x09D7..0x09D7) return true

    // Arabic combining marks
    if (code in 0x064B..0x0655) return true
    if (code in 0x0670..0x0670) return true

    // More ranges for other scripts...
    // This is a simplified version; full Unicode support would need
    // comprehensive tables

    return false
}

internal fun isSurrogateLow(ch: Int): Boolean = ch in 0xDC00..0xDFFF

internal fun isSurrogateHigh(ch: Int): Boolean = ch in 0xD800..0xDBFF

/**
 * Find the code point at the given position in a string (like the
 * [codePointAt](https://developer.mozilla.org/en-US/docs/Web/JavaScript/
 * Reference/Global_Objects/String/codePointAt)
 * string method).
 */
fun codePointAt(str: String, pos: Int): Int {
    val code0 = str[pos].code
    if (!isSurrogateHigh(code0) || pos + 1 == str.length) return code0
    val code1 = str[pos + 1].code
    if (!isSurrogateLow(code1)) return code0
    return ((code0 - 0xD800) shl 10) + (code1 - 0xDC00) + 0x10000
}

/**
 * Given a Unicode codepoint, return the Kotlin string that
 * represents it (like
 * [String.fromCodePoint](https://developer.mozilla.org/en-US/docs/Web/JavaScript/
 * Reference/Global_Objects/String/fromCodePoint)).
 */
fun fromCodePoint(code: Int): String {
    if (code <= 0xFFFF) return code.toChar().toString()
    val adjusted = code - 0x10000
    return buildString {
        append(((adjusted shr 10) + 0xD800).toChar())
        append(((adjusted and 1023) + 0xDC00).toChar())
    }
}

/**
 * The amount of positions a character takes up in a Kotlin string.
 */
fun codePointSize(code: Int): Int = if (code < 0x10000) 1 else 2
