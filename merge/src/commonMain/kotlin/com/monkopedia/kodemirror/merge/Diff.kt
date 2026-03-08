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
package com.monkopedia.kodemirror.merge

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A single changed range between two documents, spanning [fromA]..[toA] in
 * document A and [fromB]..[toB] in document B.
 */
data class Change(
    val fromA: Int,
    val toA: Int,
    val fromB: Int,
    val toB: Int
) {
    /** Return a new [Change] with both ranges shifted by the given offsets. */
    fun offset(offA: Int, offB: Int = offA): Change =
        Change(fromA + offA, toA + offA, fromB + offB, toB + offB)
}

/**
 * Configuration for the diff algorithm.
 *
 * @param scanLimit Controls how many comparisons the algorithm makes before
 *   falling back to a crude (less precise) match. Default is effectively unlimited.
 * @param timeout Maximum time in milliseconds to spend diffing (0 = unlimited).
 * @param override Optional custom diff function that replaces the built-in algorithm.
 */
data class DiffConfig(
    val scanLimit: Int = 1_000_000_000,
    val timeout: Long = 0,
    val override: ((String, String) -> List<Change>)? = null
)

private var currentScanLimit = 1_000_000_000
private var currentTimeout = 0L
private var crude = false

// Reused across calls to avoid growing the vectors again and again
private val frontier1 = Frontier()
private val frontier2 = Frontier()

private class Frontier {
    var vec: IntArray = IntArray(0)
    var len: Int = 0
    var start: Int = 0
    var end: Int = 0

    fun reset(off: Int) {
        len = off shl 1
        if (vec.size < len + 2) vec = IntArray(len + 2)
        for (i in 0 until len) vec[i] = -1
        vec[off + 1] = 0
        start = 0
        end = 0
    }

    fun advance(
        depth: Int,
        lenX: Int,
        lenY: Int,
        vOff: Int,
        other: Frontier?,
        fromBack: Boolean,
        match: (Int, Int) -> Boolean
    ): IntArray? {
        var k = -depth + start
        while (k <= depth - end) {
            val off = vOff + k
            var x = if (k == -depth || (k != depth && vec[off - 1] < vec[off + 1])) {
                vec[off + 1]
            } else {
                vec[off - 1] + 1
            }
            var y = x - k
            while (x < lenX && y < lenY && match(x, y)) {
                x++
                y++
            }
            vec[off] = x
            if (x > lenX) {
                end += 2
            } else if (y > lenY) {
                start += 2
            } else if (other != null) {
                val offOther = vOff + (lenX - lenY) - k
                if (offOther >= 0 && offOther < len && other.vec[offOther] != -1) {
                    if (!fromBack) {
                        val xOther = lenX - other.vec[offOther]
                        if (x >= xOther) return intArrayOf(x, y)
                    } else {
                        val xOther = other.vec[offOther]
                        if (xOther >= lenX - x) return intArrayOf(xOther, vOff + xOther - offOther)
                    }
                }
            }
            k += 2
        }
        return null
    }
}

private fun findDiff(
    a: String,
    fromA: Int,
    toA: Int,
    b: String,
    fromB: Int,
    toB: Int
): MutableList<Change> {
    if (a.substring(fromA, toA) == b.substring(fromB, toB)) return mutableListOf()

    val prefix = commonPrefix(a, fromA, toA, b, fromB, toB)
    val suffix = commonSuffix(a, fromA + prefix, toA, b, fromB + prefix, toB)
    val fA = fromA + prefix
    val tA = toA - suffix
    val fB = fromB + prefix
    val tB = toB - suffix
    val lenA = tA - fA
    val lenB = tB - fB

    if (lenA == 0 || lenB == 0) return mutableListOf(Change(fA, tA, fB, tB))

    // Try to find one string in the other
    if (lenA > lenB) {
        val found = a.substring(fA, tA).indexOf(b.substring(fB, tB))
        if (found > -1) {
            return mutableListOf(
                Change(fA, fA + found, fB, fB),
                Change(fA + found + lenB, tA, tB, tB)
            )
        }
    } else if (lenB > lenA) {
        val found = b.substring(fB, tB).indexOf(a.substring(fA, tA))
        if (found > -1) {
            return mutableListOf(
                Change(fA, fA, fB, fB + found),
                Change(tA, tA, fB + found + lenA, tB)
            )
        }
    }

    if (lenA == 1 || lenB == 1) return mutableListOf(Change(fA, tA, fB, tB))

    val half = halfMatch(a, fA, tA, b, fB, tB)
    if (half != null) {
        val (sharedA, sharedB, sharedLen) = half
        return (
            findDiff(a, fA, sharedA, b, fB, sharedB) +
                findDiff(a, sharedA + sharedLen, tA, b, sharedB + sharedLen, tB)
            ).toMutableList()
    }

    return findSnake(a, fA, tA, b, fB, tB)
}

// Myers O(ND) algorithm
private fun findSnake(
    a: String,
    fromA: Int,
    toA: Int,
    b: String,
    fromB: Int,
    toB: Int
): MutableList<Change> {
    val lenA = toA - fromA
    val lenB = toB - fromB
    if (currentScanLimit < 1_000_000_000 && min(lenA, lenB) > currentScanLimit.toLong() * 16 ||
        currentTimeout > 0 && currentTimeMillis() > currentTimeout
    ) {
        if (min(lenA, lenB) > currentScanLimit.toLong() * 64) {
            return mutableListOf(Change(fromA, toA, fromB, toB))
        }
        return crudeMatch(a, fromA, toA, b, fromB, toB)
    }
    val off = ceil((lenA + lenB) / 2.0).toInt()
    frontier1.reset(off)
    frontier2.reset(off)
    val match1 = { x: Int, y: Int -> a[fromA + x] == b[fromB + y] }
    val match2 = { x: Int, y: Int -> a[toA - x - 1] == b[toB - y - 1] }
    val test1 = if ((lenA - lenB) % 2 != 0) frontier2 else null
    val test2 = if (test1 != null) null else frontier1
    for (depth in 0 until off) {
        if (depth > currentScanLimit ||
            currentTimeout > 0 && (depth and 63) == 0 && currentTimeMillis() > currentTimeout
        ) {
            return crudeMatch(a, fromA, toA, b, fromB, toB)
        }
        val done = frontier1.advance(depth, lenA, lenB, off, test1, false, match1)
            ?: frontier2.advance(depth, lenA, lenB, off, test2, true, match2)
        if (done != null) {
            return bisect(a, fromA, toA, fromA + done[0], b, fromB, toB, fromB + done[1])
        }
    }
    return mutableListOf(Change(fromA, toA, fromB, toB))
}

private fun bisect(
    a: String,
    fromA: Int,
    toA: Int,
    splitA: Int,
    b: String,
    fromB: Int,
    toB: Int,
    splitB: Int
): MutableList<Change> {
    var sA = splitA
    var sB = splitB
    var stop = false
    if (!validIndex(a, sA) && ++sA == toA) stop = true
    if (!validIndex(b, sB) && ++sB == toB) stop = true
    if (stop) return mutableListOf(Change(fromA, toA, fromB, toB))
    return (findDiff(a, fromA, sA, b, fromB, sB) + findDiff(a, sA, toA, b, sB, toB))
        .toMutableList()
}

private fun chunkSize(lenA: Int, lenB: Int): Int {
    var size = 1
    val max = min(lenA, lenB)
    while (size < max) size = size shl 1
    return size
}

private fun commonPrefix(a: String, fromA: Int, toA: Int, b: String, fromB: Int, toB: Int): Int {
    if (fromA == toA || fromB == toB || a[fromA] != b[fromB]) return 0
    var chunk = chunkSize(toA - fromA, toB - fromB)
    var pA = fromA
    var pB = fromB
    while (true) {
        val endA = pA + chunk
        val endB = pB + chunk
        if (endA > toA || endB > toB || a.substring(pA, endA) != b.substring(pB, endB)) {
            if (chunk == 1) return pA - fromA - if (validIndex(a, pA)) 0 else 1
            chunk = chunk shr 1
        } else if (endA == toA || endB == toB) {
            return endA - fromA
        } else {
            pA = endA
            pB = endB
        }
    }
}

private fun commonSuffix(a: String, fromA: Int, toA: Int, b: String, fromB: Int, toB: Int): Int {
    if (fromA == toA || fromB == toB || a[toA - 1] != b[toB - 1]) return 0
    var chunk = chunkSize(toA - fromA, toB - fromB)
    var pA = toA
    var pB = toB
    while (true) {
        val sA = pA - chunk
        val sB = pB - chunk
        if (sA < fromA || sB < fromB || a.substring(sA, pA) != b.substring(sB, pB)) {
            if (chunk == 1) return toA - pA - if (validIndex(a, pA)) 0 else 1
            chunk = chunk shr 1
        } else if (sA == fromA || sB == fromB) {
            return toA - sA
        } else {
            pA = sA
            pB = sB
        }
    }
}

private fun findMatch(
    a: String,
    fromA: Int,
    toA: Int,
    b: String,
    fromB: Int,
    toB: Int,
    size: Int,
    divideTo: Int
): Triple<Int, Int, Int>? {
    val rangeB = b.substring(fromB, toB)
    var best: Triple<Int, Int, Int>? = null
    var currentSize = size
    while (true) {
        if (best != null || currentSize < divideTo) return best
        var start = fromA + currentSize
        while (true) {
            if (!validIndex(a, start)) start++
            var end = start + currentSize
            if (!validIndex(a, end)) end += if (end == start + 1) 1 else -1
            if (end >= toA) break
            val seed = a.substring(start, end)
            var found = -1
            while (true) {
                found = rangeB.indexOf(seed, found + 1)
                if (found == -1) break
                val prefixAfter =
                    commonPrefix(a, end, toA, b, fromB + found + seed.length, toB)
                val suffixBefore =
                    commonSuffix(a, fromA, start, b, fromB, fromB + found)
                val length = seed.length + prefixAfter + suffixBefore
                if (best == null || best.third < length) {
                    best = Triple(start - suffixBefore, fromB + found - suffixBefore, length)
                }
            }
            start = end
        }
        if (divideTo < 0) return best
        currentSize = currentSize shr 1
    }
}

private fun halfMatch(
    a: String,
    fromA: Int,
    toA: Int,
    b: String,
    fromB: Int,
    toB: Int
): Triple<Int, Int, Int>? {
    val lenA = toA - fromA
    val lenB = toB - fromB
    if (lenA < lenB) {
        val result = halfMatch(b, fromB, toB, a, fromA, toA)
        return result?.let { Triple(it.second, it.first, it.third) }
    }
    if (lenA < 4 || lenB * 2 < lenA) return null
    return findMatch(a, fromA, toA, b, fromB, toB, floor(lenA / 4.0).toInt(), -1)
}

private fun crudeMatch(
    a: String,
    fromA: Int,
    toA: Int,
    b: String,
    fromB: Int,
    toB: Int
): MutableList<Change> {
    crude = true
    val lenA = toA - fromA
    val lenB = toB - fromB
    val result: Triple<Int, Int, Int>?
    if (lenA < lenB) {
        val inv = findMatch(b, fromB, toB, a, fromA, toA, floor(lenA / 6.0).toInt(), 50)
        result = inv?.let { Triple(it.second, it.first, it.third) }
    } else {
        result = findMatch(a, fromA, toA, b, fromB, toB, floor(lenB / 6.0).toInt(), 50)
    }
    if (result == null) return mutableListOf(Change(fromA, toA, fromB, toB))
    val (sharedA, sharedB, sharedLen) = result
    return (
        findDiff(a, fromA, sharedA, b, fromB, sharedB) +
            findDiff(a, sharedA + sharedLen, toA, b, sharedB + sharedLen, toB)
        ).toMutableList()
}

private fun mergeAdjacent(changes: MutableList<Change>, minGap: Int) {
    var i = 1
    while (i < changes.size) {
        val prev = changes[i - 1]
        val cur = changes[i]
        if (prev.toA > cur.fromA - minGap && prev.toB > cur.fromB - minGap) {
            changes[i - 1] = Change(prev.fromA, cur.toA, prev.fromB, cur.toB)
            changes.removeAt(i)
        } else {
            i++
        }
    }
}

private fun normalize(a: String, b: String, changes: MutableList<Change>): MutableList<Change> {
    while (true) {
        mergeAdjacent(changes, 1)
        var moved = false
        for (i in changes.indices) {
            var ch = changes[i]
            val pre = commonPrefix(a, ch.fromA, ch.toA, b, ch.fromB, ch.toB)
            if (pre > 0) {
                ch = Change(ch.fromA + pre, ch.toA, ch.fromB + pre, ch.toB)
                changes[i] = ch
            }
            val post = commonSuffix(a, ch.fromA, ch.toA, b, ch.fromB, ch.toB)
            if (post > 0) {
                ch = Change(ch.fromA, ch.toA - post, ch.fromB, ch.toB - post)
                changes[i] = ch
            }
            val lenA = ch.toA - ch.fromA
            val lenB = ch.toB - ch.fromB
            if (lenA != 0 && lenB != 0) continue
            val beforeLen = ch.fromA - if (i > 0) changes[i - 1].toA else 0
            val afterLen = (if (i < changes.size - 1) changes[i + 1].fromA else a.length) - ch.toA
            if (beforeLen == 0 || afterLen == 0) continue
            val text = if (lenA != 0) {
                a.substring(
                    ch.fromA,
                    ch.toA
                )
            } else {
                b.substring(ch.fromB, ch.toB)
            }
            if (beforeLen <= text.length &&
                a.substring(ch.fromA - beforeLen, ch.fromA) ==
                text.substring(text.length - beforeLen)
            ) {
                changes[i] = Change(
                    ch.fromA - beforeLen,
                    ch.toA - beforeLen,
                    ch.fromB - beforeLen,
                    ch.toB - beforeLen
                )
                moved = true
            } else if (afterLen <= text.length &&
                a.substring(ch.toA, ch.toA + afterLen) == text.substring(0, afterLen)
            ) {
                changes[i] = Change(
                    ch.fromA + afterLen,
                    ch.toA + afterLen,
                    ch.fromB + afterLen,
                    ch.toB + afterLen
                )
                moved = true
            }
        }
        if (!moved) break
    }
    return changes
}

private const val MAX_SCAN = 8

private fun asciiWordChar(code: Int): Boolean =
    (code in 49..57) || (code in 65..90) || (code in 97..122)

private fun wordCharAfter(s: String, pos: Int): Int {
    if (pos == s.length) return 0
    val ch = s[pos]
    val code = ch.code
    if (code < 192) return if (asciiWordChar(code)) 1 else 0
    if (ch.isHighSurrogate() && pos < s.length - 1) {
        return if (ch.isLetter() || s[pos + 1].isLetter()) 2 else 0
    }
    return if (ch.isLetterOrDigit()) 1 else 0
}

private fun wordCharBefore(s: String, pos: Int): Int {
    if (pos == 0) return 0
    val ch = s[pos - 1]
    val code = ch.code
    if (code < 192) return if (asciiWordChar(code)) 1 else 0
    if (ch.isLowSurrogate() && pos >= 2) {
        return if (ch.isLetter() || s[pos - 2].isLetter()) 2 else 0
    }
    return if (ch.isLetterOrDigit()) 1 else 0
}

private fun findWordBoundaryAfter(s: String, pos: Int, max: Int): Int {
    if (pos == s.length || wordCharBefore(s, pos) == 0) return pos
    var cur = pos
    val end = pos + max
    for (i in 0 until MAX_SCAN) {
        val size = wordCharAfter(s, cur)
        if (size == 0 || cur + size > end) return cur
        cur += size
    }
    return pos
}

private fun findWordBoundaryBefore(s: String, pos: Int, max: Int): Int {
    if (pos == 0 || wordCharAfter(s, pos) == 0) return pos
    var cur = pos
    val end = pos - max
    for (i in 0 until MAX_SCAN) {
        val size = wordCharBefore(s, cur)
        if (size == 0 || cur - size < end) return cur
        cur -= size
    }
    return pos
}

private fun findLineBreakBefore(s: String, pos: Int, stop: Int): Int {
    var p = pos
    while (p != stop) {
        if (s[p - 1].code == 10) return p
        p--
    }
    return -1
}

private fun findLineBreakAfter(s: String, pos: Int, stop: Int): Int {
    var p = pos
    while (p != stop) {
        if (s[p].code == 10) return p
        p++
    }
    return -1
}

private fun isSurrogate1(code: Int) = code in 0xD800..0xDBFF
private fun isSurrogate2(code: Int) = code in 0xDC00..0xDFFF

private fun validIndex(s: String, index: Int): Boolean {
    if (index <= 0 || index >= s.length) return true
    return !isSurrogate1(s[index - 1].code) || !isSurrogate2(s[index].code)
}

private fun makePresentable(
    changes: MutableList<Change>,
    a: String,
    b: String
): MutableList<Change> {
    var posA = 0
    for (i in changes.indices) {
        var change = changes[i]
        val lenA = change.toA - change.fromA
        val lenB = change.toB - change.fromB
        if ((lenA != 0 && lenB != 0) || lenA > 3 || lenB > 3) {
            val nextChangeA = if (i == changes.size - 1) a.length else changes[i + 1].fromA
            val maxScanBefore = change.fromA - posA
            val maxScanAfter = nextChangeA - change.toA
            var boundBefore = findWordBoundaryBefore(a, change.fromA, maxScanBefore)
            var boundAfter = findWordBoundaryAfter(a, change.toA, maxScanAfter)
            var lenBefore = change.fromA - boundBefore
            var lenAfter = boundAfter - change.toA

            if ((lenA == 0 || lenB == 0) && lenBefore != 0 && lenAfter != 0) {
                val changeLen = max(lenA, lenB)
                val changeText: String
                val changeFrom: Int
                val changeTo: Int
                if (lenA != 0) {
                    changeText = a
                    changeFrom = change.fromA
                    changeTo = change.toA
                } else {
                    changeText = b
                    changeFrom = change.fromB
                    changeTo = change.toB
                }
                if (changeLen > lenBefore &&
                    a.substring(boundBefore, change.fromA) ==
                    changeText.substring(changeTo - lenBefore, changeTo)
                ) {
                    change = Change(
                        boundBefore,
                        boundBefore + lenA,
                        change.fromB - lenBefore,
                        change.toB - lenBefore
                    )
                    changes[i] = change
                    boundBefore = change.fromA
                    boundAfter =
                        findWordBoundaryAfter(a, change.toA, nextChangeA - change.toA)
                } else if (changeLen > lenAfter &&
                    a.substring(change.toA, boundAfter) ==
                    changeText.substring(changeFrom, changeFrom + lenAfter)
                ) {
                    change = Change(
                        boundAfter - lenA,
                        boundAfter,
                        change.fromB + lenAfter,
                        change.toB + lenAfter
                    )
                    changes[i] = change
                    boundAfter = change.toA
                    boundBefore =
                        findWordBoundaryBefore(a, change.fromA, change.fromA - posA)
                }
                lenBefore = change.fromA - boundBefore
                lenAfter = boundAfter - change.toA
            }
            if (lenBefore != 0 || lenAfter != 0) {
                change = Change(
                    change.fromA - lenBefore,
                    change.toA + lenAfter,
                    change.fromB - lenBefore,
                    change.toB + lenAfter
                )
                changes[i] = change
            } else if (lenA == 0) {
                // Align insertion to line boundary
                val first = findLineBreakAfter(b, change.fromB, change.toB)
                val last =
                    if (first < 0) -1 else findLineBreakBefore(b, change.toB, change.fromB)
                if (first > -1) {
                    val len = first - change.fromB
                    if (len <= maxScanAfter &&
                        b.substring(change.fromB, first) ==
                        b.substring(change.toB, change.toB + len)
                    ) {
                        change = change.offset(len)
                        changes[i] = change
                    } else if (last > -1) {
                        val len2 = change.toB - last
                        if (len2 <= maxScanBefore &&
                            b.substring(change.fromB - len2, change.fromB) ==
                            b.substring(last, change.toB)
                        ) {
                            change = change.offset(-len2)
                            changes[i] = change
                        }
                    }
                }
            } else if (lenB == 0) {
                // Align deletion to line boundary
                val first = findLineBreakAfter(a, change.fromA, change.toA)
                val last =
                    if (first < 0) -1 else findLineBreakBefore(a, change.toA, change.fromA)
                if (first > -1) {
                    val len = first - change.fromA
                    if (len <= maxScanAfter &&
                        a.substring(change.fromA, first) ==
                        a.substring(change.toA, change.toA + len)
                    ) {
                        change = change.offset(len)
                        changes[i] = change
                    } else if (last > -1) {
                        val len2 = change.toA - last
                        if (len2 <= maxScanBefore &&
                            a.substring(change.fromA - len2, change.fromA) ==
                            a.substring(last, change.toA)
                        ) {
                            change = change.offset(-len2)
                            changes[i] = change
                        }
                    }
                }
            }
        }
        posA = change.toA
    }
    mergeAdjacent(changes, 3)
    return changes
}

internal fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

internal var lastDiffPrecise: Boolean = true
    private set

/**
 * Compute the difference between two strings.
 */
fun diff(a: String, b: String, config: DiffConfig = DiffConfig()): List<Change> {
    val override = config.override
    if (override != null) return override(a, b)
    currentScanLimit = (config.scanLimit) shr 1
    currentTimeout = if (config.timeout > 0) currentTimeMillis() + config.timeout else 0
    crude = false
    val result = normalize(a, b, findDiff(a, 0, a.length, b, 0, b.length))
    lastDiffPrecise = !crude
    return result
}

/**
 * Compute the difference between the given strings, and clean up the
 * resulting diff for presentation to users by dropping short
 * unchanged ranges, and aligning changes to word boundaries when
 * appropriate.
 */
fun presentableDiff(a: String, b: String, config: DiffConfig = DiffConfig()): List<Change> {
    val changes = diff(a, b, config).toMutableList()
    return makePresentable(changes, a, b)
}
