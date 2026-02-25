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
package com.monkopedia.kodemirror.view

import kotlin.math.min

/** Text direction. */
enum class Direction { LTR, RTL }

/**
 * A bidi span records a range of text with a uniform bidi level.
 * Even levels are LTR, odd levels are RTL.
 */
class BidiSpan(val from: Int, val to: Int, val level: Int) {
    val dir: Direction get() = if ((level % 2) == 0) Direction.LTR else Direction.RTL

    override fun toString(): String = "BidiSpan($from-$to:$level)"
}

/** An open bidi isolate. */
data class Isolate(val from: Int, val to: Int, val direction: Direction)

// Bidi character types (simplified UAX#9 subset)
private const val T_L = 0 // Left-to-right
private const val T_R = 1 // Right-to-left strong
private const val T_AL = 2 // Arabic letter (strong RTL)
private const val T_EN = 3 // European number
private const val T_AN = 4 // Arabic number
private const val T_ET = 5 // European terminator (+/- % $)
private const val T_CS = 6 // Common number separator (, . / :)
private const val T_WS = 7 // Whitespace
private const val T_S = 8 // Segment separator (tab, etc.)
private const val T_ON = 9 // Other neutral
private const val T_BN = 10 // Boundary neutral

// Classify a Unicode code point into a simplified bidi type
private fun charType(code: Int): Int = when {
    // C0 controls
    code in 0..8 || code in 14..27 || code == 127 -> T_BN
    code == 9 || code in 28..31 -> T_S
    code == 10 || code == 13 -> T_S
    code == 12 -> T_WS

    // Printable ASCII
    code == 32 -> T_WS
    code in 33..34 -> T_ON
    code == 35 -> T_ET // #
    code == 36 -> T_ET // $
    code == 37 -> T_ET // %
    code in 38..39 -> T_ON
    code in 40..42 -> T_ON
    code == 43 -> T_ET // +
    code == 44 -> T_CS // ,
    code == 45 -> T_ET // -
    code == 46 -> T_CS // .
    code == 47 -> T_CS // /
    code in 48..57 -> T_EN // 0-9
    code == 58 -> T_CS // :
    code in 59..64 -> T_ON
    code in 65..90 -> T_L // A-Z
    code in 91..96 -> T_ON
    code in 97..122 -> T_L // a-z
    code in 123..126 -> T_ON

    // Latin-1 Supplement
    code in 0xC0..0xFF -> T_L

    // Latin Extended
    code in 0x100..0x2FF -> T_L

    // Hebrew: U+0590..U+05FF
    code in 0x0591..0x05BD -> T_R // Hebrew combining marks (treat as R for simplicity)
    code == 0x05BE -> T_R
    code == 0x05BF -> T_R
    code in 0x05C0..0x05FF -> T_R
    code in 0x0590..0x05FF -> T_R

    // Arabic: U+0600..U+06FF
    code in 0x0600..0x0605 -> T_AN
    code in 0x0606..0x060B -> T_AL
    code == 0x060C -> T_CS
    code in 0x060D..0x061A -> T_AL
    code in 0x061B..0x064A -> T_AL
    code in 0x064B..0x065F -> T_AL // Arabic combining marks
    code in 0x0660..0x0669 -> T_AN // Arabic-Indic digits
    code in 0x066A..0x066D -> T_AL
    code == 0x066E -> T_AL
    code == 0x066F -> T_AL
    code in 0x0670..0x06D5 -> T_AL
    code in 0x06D6..0x06DC -> T_AL
    code in 0x06DD..0x06DE -> T_AL
    code in 0x06DF..0x06E4 -> T_AL
    code in 0x06E5..0x06E6 -> T_AL
    code in 0x06E7..0x06E8 -> T_AL
    code in 0x06E9..0x06EA -> T_AL
    code in 0x06EB..0x06EF -> T_AL
    code in 0x06F0..0x06F9 -> T_AN // Extended Arabic-Indic digits
    code in 0x06FA..0x06FF -> T_AL

    // Syriac, Thaana: U+0700..U+07BF
    code in 0x0700..0x07BF -> T_R

    // NKo: U+07C0..U+07FF
    code in 0x07C0..0x07FF -> T_R

    // Arabic Extended: U+0800..U+08FF
    code in 0x0800..0x08FF -> T_AL

    // Right-to-left marks: U+200F
    code == 0x200F -> T_R
    code == 0x200E -> T_L

    // Arabic Presentation Forms A: U+FB50..U+FDFF
    code in 0xFB50..0xFDFF -> T_AL

    // Hebrew Presentation Forms: U+FB1D..U+FB4F
    code in 0xFB1D..0xFB4F -> T_R

    // Arabic Presentation Forms B: U+FE70..U+FEFF
    code in 0xFE70..0xFEFF -> T_AL

    // Default: left-to-right
    else -> T_L
}

// Helper: is type a "strong" RTL type?
private fun isRTLStrong(t: Int): Boolean = t == T_R || t == T_AL || t == T_AN

// Helper: is type neutral?
private fun isNeutral(t: Int): Boolean = t == T_WS || t == T_S || t == T_ON || t == T_BN

/**
 * Compute the bidi ordering for a single line of text.
 *
 * Returns a list of [BidiSpan] objects. For pure LTR text this is a single
 * span at level 0; for text containing RTL characters the spans reflect the
 * resolved embedding levels.
 */
fun computeOrder(
    line: String,
    direction: Direction,
    isolates: List<Isolate> = emptyList()
): List<BidiSpan> {
    val len = line.length
    if (len == 0) return listOf(BidiSpan(0, 0, direction.ordinal))

    // P2-P3: Paragraph embedding level
    val paraLevel = if (direction == Direction.RTL) 1 else 0

    // Step 1: Classify each character
    val types = IntArray(len) { charType(line[it].code) }

    // Step 2: Handle AL → R (W3)
    for (i in 0 until len) {
        if (types[i] == T_AL) types[i] = T_R
    }

    // W2: EN after AL-context becomes AN
    var lastStrong = paraLevel
    for (i in 0 until len) {
        when (types[i]) {
            T_R -> lastStrong = T_R
            T_L -> lastStrong = T_L
            T_EN -> if (lastStrong == T_R) types[i] = T_AN
        }
    }

    // W4: Single ES/CS between matching number types
    for (i in 1 until len - 1) {
        if (types[i] == T_CS || types[i] == T_ET) {
            val prevType = types[i - 1]
            val nextType = types[i + 1]
            if (types[i] == T_CS) {
                if (prevType == T_EN && nextType == T_EN) {
                    types[i] = T_EN
                } else if (prevType == T_AN && nextType == T_AN) types[i] = T_AN
            } else { // T_ET
                if (prevType == T_EN && nextType == T_EN) types[i] = T_EN
            }
        }
    }

    // W5-W6: ET sequences adjacent to EN → EN; remaining ET/CS → ON
    var i = 0
    while (i < len) {
        if (types[i] == T_ET) {
            val start = i
            while (i < len && types[i] == T_ET) i++
            // Check if adjacent to EN
            val prevEN = start > 0 && types[start - 1] == T_EN
            val nextEN = i < len && types[i] == T_EN
            val newType = if (prevEN || nextEN) T_EN else T_ON
            for (j in start until i) types[j] = newType
        } else {
            i++
        }
    }
    // Remaining CS → ON
    for (j in 0 until len) {
        if (types[j] == T_CS) types[j] = T_ON
    }

    // W7: EN in L context → L
    lastStrong = paraLevel
    for (j in 0 until len) {
        when (types[j]) {
            T_L -> lastStrong = T_L
            T_R -> lastStrong = T_R
            T_EN -> if (lastStrong == T_L) types[j] = T_L
        }
    }

    // N1-N2: Resolve neutrals
    var pos = 0
    while (pos < len) {
        if (isNeutral(types[pos])) {
            val start = pos
            while (pos < len && isNeutral(types[pos])) pos++
            // Find bordering strong types
            val prevStrong = findPrevStrong(types, start, paraLevel)
            val nextStrong = findNextStrong(types, pos, paraLevel)
            val resolvedType = when {
                prevStrong == T_L && nextStrong == T_L -> T_L
                prevStrong != T_L && nextStrong != T_L -> T_R
                else -> if (paraLevel == 1) T_R else T_L
            }
            for (k in start until pos) types[k] = resolvedType
        } else {
            pos++
        }
    }

    // I1-I2: Assign levels
    val levels = IntArray(len) { paraLevel }
    for (j in 0 until len) {
        if (paraLevel == 0) {
            when (types[j]) {
                T_R -> levels[j] = 1
                T_AN, T_EN -> levels[j] = 2
            }
        } else {
            when (types[j]) {
                T_L, T_EN, T_AN -> levels[j] = 2
            }
        }
    }

    // Build BidiSpan list from contiguous same-level runs
    val spans = mutableListOf<BidiSpan>()
    var spanStart = 0
    var spanLevel = levels[0]
    for (k in 1..len) {
        val curLevel = if (k < len) levels[k] else -1
        if (curLevel != spanLevel) {
            spans.add(BidiSpan(spanStart, k, spanLevel))
            spanStart = k
            if (k < len) spanLevel = levels[k]
        }
    }

    return spans
}

private fun findPrevStrong(types: IntArray, from: Int, paraLevel: Int): Int {
    for (i in from - 1 downTo 0) {
        val t = types[i]
        if (t == T_L || t == T_R) return t
    }
    return if (paraLevel == 1) T_R else T_L
}

private fun findNextStrong(types: IntArray, from: Int, paraLevel: Int): Int {
    for (i in from until types.size) {
        val t = types[i]
        if (t == T_L || t == T_R) return t
    }
    return if (paraLevel == 1) T_R else T_L
}

/**
 * Detect the dominant direction in the given range of text.
 * Looks for the first strong bidi character (L or R/AL).
 */
fun autoDirection(text: String, from: Int, to: Int): Direction {
    val end = min(to, text.length)
    for (i in from until end) {
        val type = charType(text[i].code)
        if (type == T_R || type == T_AL) return Direction.RTL
        if (type == T_L) return Direction.LTR
    }
    return Direction.LTR
}
