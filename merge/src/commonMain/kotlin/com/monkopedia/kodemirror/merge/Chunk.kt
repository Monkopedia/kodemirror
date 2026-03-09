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
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.endPos

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
    val fromA: DocPos,
    val toA: DocPos,
    val fromB: DocPos,
    val toB: DocPos,
    val precise: Boolean = true
) {
    fun offset(offA: Int, offB: Int): Chunk {
        return if (offA == 0 && offB == 0) {
            this
        } else {
            Chunk(changes, fromA + offA, toA + offA, fromB + offB, toB + offB, precise)
        }
    }

    val endA: DocPos get() = maxOf(fromA, toA - 1)
    val endB: DocPos get() = maxOf(fromB, toB - 1)

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

private fun fromLine(fromA: DocPos, fromB: DocPos, a: Text, b: Text): Pair<DocPos, DocPos> {
    val lineA = a.lineAt(fromA)
    val lineB = b.lineAt(fromB)
    return if (lineA.to == fromA && lineB.to == fromB && fromA < a.endPos && fromB < b.endPos) {
        Pair(fromA + 1, fromB + 1)
    } else {
        Pair(lineA.from, lineB.from)
    }
}

private fun toLine(toA: DocPos, toB: DocPos, a: Text, b: Text): Pair<DocPos, DocPos> {
    val lineA = a.lineAt(toA)
    val lineB = b.lineAt(toB)
    return if (lineA.from == toA && lineB.from == toB) {
        Pair(toA, toB)
    } else {
        Pair(lineA.to + 1, lineB.to + 1)
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
        val fl = fromLine(DocPos(change.fromA + offA), DocPos(change.fromB + offB), a, b)
        var fromA = fl.first
        var fromB = fl.second
        var tl = toLine(DocPos(change.toA + offA), DocPos(change.toB + offB), a, b)
        var toA = tl.first
        var toB = tl.second
        val chunk = mutableListOf(
            change.offset(-(fromA.value) + offA, -(fromB.value) + offB)
        )
        while (i < changes.size - 1) {
            val next = changes[i + 1]
            val nfl = fromLine(DocPos(next.fromA + offA), DocPos(next.fromB + offB), a, b)
            val nextA = nfl.first
            val nextB = nfl.second
            if (nextA > toA + 1 && nextB > toB + 1) break
            chunk.add(next.offset(-(fromA.value) + offA, -(fromB.value) + offB))
            tl = toLine(DocPos(next.toA + offA), DocPos(next.toB + offB), a, b)
            toA = tl.first
            toB = tl.second
            i++
        }
        chunks.add(Chunk(chunk, fromA, maxOf(fromA, toA), fromB, maxOf(fromB, toB), precise))
        i++
    }
    return chunks
}

private const val UPDATE_MARGIN = 1000

private data class UpdateRange(
    val fromA: DocPos,
    val toA: DocPos,
    val fromB: DocPos,
    val toB: DocPos,
    val diffA: Int,
    val diffB: Int
)

private fun findPos(
    chunks: List<Chunk>,
    pos: DocPos,
    isA: Boolean,
    start: Boolean
): Pair<DocPos, DocPos> {
    var lo = 0
    var hi = chunks.size
    while (true) {
        if (lo == hi) {
            var refA = DocPos.ZERO
            var refB = DocPos.ZERO
            if (lo > 0) {
                refA = chunks[lo - 1].toA
                refB = chunks[lo - 1].toB
            }
            val off = pos - if (isA) refA else refB
            return Pair(refA + off, refB + off)
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
                Pair(chunk.fromA, chunk.fromB)
            } else {
                Pair(chunk.toA, chunk.toB)
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
            var fromA = DocPos.ZERO
            var toA = DocPos(if (isA) changes.length else otherLen)
            var fromB = DocPos.ZERO
            var toB = DocPos(if (isA) otherLen else changes.length)
            if (cFromA.value > UPDATE_MARGIN) {
                val fp = findPos(chunks, cFromA - UPDATE_MARGIN, isA, true)
                fromA = fp.first
                fromB = fp.second
            }
            if (cToA.value < changes.length - UPDATE_MARGIN) {
                val tp = findPos(chunks, cToA + UPDATE_MARGIN, isA, false)
                toA = tp.first
                toB = tp.second
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
        val fromA = if (range != null) range.fromA + offA else a.endPos
        val fromB = if (range != null) range.fromB + offB else b.endPos
        while (chunkI < chunks.size) {
            val next = chunks[chunkI]
            if (next.endA + offA > fromA || next.endB + offB > fromB) break
            result.add(next.offset(offA, offB))
            chunkI++
        }
        if (range == null) break
        val toA = range.toA + offA + range.diffA
        val toB = range.toB + offB + range.diffB
        val d = presentableDiff(
            a.sliceString(fromA, toA),
            b.sliceString(fromB, toB),
            conf
        )
        for (chunk in toChunks(d, a, b, fromA.value, fromB.value, lastDiffPrecise)) {
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
