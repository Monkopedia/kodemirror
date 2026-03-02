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

import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.Text
import kotlin.math.max

val defaultDiffConfig = DiffConfig(scanLimit = 500)

/**
 * A chunk describes a range of lines which have changed content in
 * them. Either side (a/b) may either be empty (when its `to` is
 * equal to its `from`), or points at a range starting at the start
 * of the first changed line, to 1 past the end of the last changed
 * line.
 */
class Chunk(
    val changes: List<Change>,
    val fromA: Int,
    val toA: Int,
    val fromB: Int,
    val toB: Int,
    val precise: Boolean = true
) {
    fun offset(offA: Int, offB: Int): Chunk {
        return if (offA == 0 && offB == 0) {
            this
        } else {
            Chunk(changes, fromA + offA, toA + offA, fromB + offB, toB + offB, precise)
        }
    }

    val endA: Int get() = max(fromA, toA - 1)
    val endB: Int get() = max(fromB, toB - 1)

    companion object {
        /**
         * Build a set of changed chunks for the given documents.
         */
        fun build(a: Text, b: Text, conf: DiffConfig = defaultDiffConfig): List<Chunk> {
            val d = presentableDiff(a.toString(), b.toString(), conf)
            return toChunks(d, a, b, 0, 0, lastDiffPrecise)
        }

        /**
         * Update a set of chunks for changes in document A.
         * [a] should hold the updated document A.
         */
        fun updateA(
            chunks: List<Chunk>,
            a: Text,
            b: Text,
            changes: ChangeDesc,
            conf: DiffConfig = defaultDiffConfig
        ): List<Chunk> {
            return updateChunks(
                findRangesForChange(chunks, changes, isA = true, otherLen = b.length),
                chunks,
                a,
                b,
                conf
            )
        }

        /**
         * Update a set of chunks for changes in document B.
         */
        fun updateB(
            chunks: List<Chunk>,
            a: Text,
            b: Text,
            changes: ChangeDesc,
            conf: DiffConfig = defaultDiffConfig
        ): List<Chunk> {
            return updateChunks(
                findRangesForChange(chunks, changes, isA = false, otherLen = a.length),
                chunks,
                a,
                b,
                conf
            )
        }
    }
}

private fun fromLine(fromA: Int, fromB: Int, a: Text, b: Text): IntArray {
    val lineA = a.lineAt(fromA)
    val lineB = b.lineAt(fromB)
    return if (lineA.to == fromA && lineB.to == fromB && fromA < a.length && fromB < b.length) {
        intArrayOf(fromA + 1, fromB + 1)
    } else {
        intArrayOf(lineA.from, lineB.from)
    }
}

private fun toLine(toA: Int, toB: Int, a: Text, b: Text): IntArray {
    val lineA = a.lineAt(toA)
    val lineB = b.lineAt(toB)
    return if (lineA.from == toA && lineB.from == toB) {
        intArrayOf(toA, toB)
    } else {
        intArrayOf(lineA.to + 1, lineB.to + 1)
    }
}

private fun toChunks(
    changes: List<Change>,
    a: Text,
    b: Text,
    offA: Int,
    offB: Int,
    precise: Boolean
): List<Chunk> {
    val chunks = mutableListOf<Chunk>()
    var i = 0
    while (i < changes.size) {
        val change = changes[i]
        val fl = fromLine(change.fromA + offA, change.fromB + offB, a, b)
        var fromA = fl[0]
        var fromB = fl[1]
        var tl = toLine(change.toA + offA, change.toB + offB, a, b)
        var toA = tl[0]
        var toB = tl[1]
        val chunk = mutableListOf(change.offset(-fromA + offA, -fromB + offB))
        while (i < changes.size - 1) {
            val next = changes[i + 1]
            val nfl = fromLine(next.fromA + offA, next.fromB + offB, a, b)
            val nextA = nfl[0]
            val nextB = nfl[1]
            if (nextA > toA + 1 && nextB > toB + 1) break
            chunk.add(next.offset(-fromA + offA, -fromB + offB))
            tl = toLine(next.toA + offA, next.toB + offB, a, b)
            toA = tl[0]
            toB = tl[1]
            i++
        }
        chunks.add(Chunk(chunk, fromA, max(fromA, toA), fromB, max(fromB, toB), precise))
        i++
    }
    return chunks
}

private const val UPDATE_MARGIN = 1000

private data class UpdateRange(
    val fromA: Int,
    val toA: Int,
    val fromB: Int,
    val toB: Int,
    val diffA: Int,
    val diffB: Int
)

private fun findPos(chunks: List<Chunk>, pos: Int, isA: Boolean, start: Boolean): IntArray {
    var lo = 0
    var hi = chunks.size
    while (true) {
        if (lo == hi) {
            var refA = 0
            var refB = 0
            if (lo > 0) {
                refA = chunks[lo - 1].toA
                refB = chunks[lo - 1].toB
            }
            val off = pos - if (isA) refA else refB
            return intArrayOf(refA + off, refB + off)
        }
        val mid = (lo + hi) shr 1
        val chunk = chunks[mid]
        val from = if (isA) chunk.fromA else chunk.fromB
        val to = if (isA) chunk.toA else chunk.toB
        if (from > pos) {
            hi = mid
        } else if (to <= pos) {
            lo = mid + 1
        } else {
            return if (start) {
                intArrayOf(chunk.fromA, chunk.fromB)
            } else {
                intArrayOf(chunk.toA, chunk.toB)
            }
        }
    }
}

private fun findRangesForChange(
    chunks: List<Chunk>,
    changes: ChangeDesc,
    isA: Boolean,
    otherLen: Int
): List<UpdateRange> {
    val ranges = mutableListOf<UpdateRange>()
    changes.iterChangedRanges(
        f = { cFromA, cToA, cFromB, cToB ->
            var fromA = 0
            var toA = if (isA) changes.length else otherLen
            var fromB = 0
            var toB = if (isA) otherLen else changes.length
            if (cFromA > UPDATE_MARGIN) {
                val fp = findPos(chunks, cFromA - UPDATE_MARGIN, isA, true)
                fromA = fp[0]
                fromB = fp[1]
            }
            if (cToA < changes.length - UPDATE_MARGIN) {
                val tp = findPos(chunks, cToA + UPDATE_MARGIN, isA, false)
                toA = tp[0]
                toB = tp[1]
            }
            val lenDiff = (cToB - cFromB) - (cToA - cFromA)
            val diffA = if (isA) lenDiff else 0
            val diffB = if (isA) 0 else lenDiff
            if (ranges.isNotEmpty() && ranges.last().toA >= fromA) {
                val last = ranges.last()
                ranges[ranges.size - 1] = UpdateRange(
                    last.fromA, last.fromB, toA, toB,
                    last.diffA + diffA, last.diffB + diffB
                )
            } else {
                ranges.add(UpdateRange(fromA, toA, fromB, toB, diffA, diffB))
            }
        }
    )
    return ranges
}

private fun updateChunks(
    ranges: List<UpdateRange>,
    chunks: List<Chunk>,
    a: Text,
    b: Text,
    conf: DiffConfig
): List<Chunk> {
    if (ranges.isEmpty()) return chunks
    val result = mutableListOf<Chunk>()
    var offA = 0
    var offB = 0
    var chunkI = 0
    for (i in 0..ranges.size) {
        val range = if (i == ranges.size) null else ranges[i]
        val fromA = if (range != null) range.fromA + offA else a.length
        val fromB = if (range != null) range.fromB + offB else b.length
        while (chunkI < chunks.size) {
            val next = chunks[chunkI]
            if (next.endA + offA > fromA || next.endB + offB > fromB) break
            result.add(next.offset(offA, offB))
            chunkI++
        }
        if (range == null) break
        val toA = range.toA + offA + range.diffA
        val toB = range.toB + offB + range.diffB
        val d = presentableDiff(a.sliceString(fromA, toA), b.sliceString(fromB, toB), conf)
        for (chunk in toChunks(d, a, b, fromA, fromB, lastDiffPrecise)) {
            result.add(chunk)
        }
        offA += range.diffA
        offB += range.diffB
        while (chunkI < chunks.size) {
            val next = chunks[chunkI]
            if (next.fromA + offA > toA && next.fromB + offB > toB) break
            chunkI++
        }
    }
    return result
}
