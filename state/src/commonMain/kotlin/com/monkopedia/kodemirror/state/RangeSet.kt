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

import kotlin.math.max
import kotlin.math.min

// The maximum amount of ranges to store in a single chunk
private const val CHUNK_SIZE = 250

// A large value to use for max/min values.
private const val FAR = 1_000_000_000

/**
 * Each range is associated with a value, which must inherit from
 * this class.
 */
abstract class RangeValue {
    override fun equals(other: Any?): Boolean = this === other

    /**
     * The bias value at the start of the range. Defaults to 0.
     */
    open val startSide: Int = 0

    /**
     * The bias value at the end of the range. Defaults to 0.
     */
    open val endSide: Int = 0

    /**
     * The mode with which the location of the range should be
     * mapped when its `from` and `to` are the same. Defaults to
     * [MapMode.TrackDel].
     */
    open val mapMode: MapMode = MapMode.TrackDel

    /**
     * Determines whether this value marks a point range.
     */
    open val point: Boolean = false

    /**
     * Create a [Range] with this value.
     */
    fun range(from: Int, to: Int = from): Range<Nothing> {
        @Suppress("UNCHECKED_CAST")
        return Range.create(from, to, this) as Range<Nothing>
    }
}

private fun cmpVal(a: RangeValue, b: RangeValue): Boolean = a == b

/**
 * A range associates a value with a range of positions.
 */
class Range<out T : RangeValue> private constructor(
    /** The range's start position. */
    val from: Int,
    /** Its end position. */
    val to: Int,
    /** The value associated with this range. */
    val value: @UnsafeVariance T
) {
    companion object {
        internal fun <T : RangeValue> create(from: Int, to: Int, value: T): Range<T> =
            Range(from, to, value)
    }
}

private fun <T : RangeValue> cmpRange(a: Range<T>, b: Range<T>): Int {
    val d = a.from - b.from
    return if (d != 0) {
        d
    } else {
        a.value.startSide - b.value.startSide
    }
}

/**
 * Collection of methods used when comparing range sets.
 */
interface RangeComparator<T : RangeValue> {
    fun compareRange(from: Int, to: Int, activeA: List<T>, activeB: List<T>)

    fun comparePoint(from: Int, to: Int, pointA: T?, pointB: T?)

    fun boundChange(pos: Int) {}
}

/**
 * Methods used when iterating over the spans created by a set
 * of ranges.
 */
interface SpanIterator<T : RangeValue> {
    fun span(from: Int, to: Int, active: List<T>, openStart: Int)

    fun point(from: Int, to: Int, value: T, active: List<T>, openStart: Int, index: Int)
}

internal class Chunk<T : RangeValue>(
    val from: List<Int>,
    val to: List<Int>,
    val value: List<T>,
    val maxPoint: Int
) {
    val length: Int get() = to[to.size - 1]

    fun findIndex(pos: Int, side: Int, end: Boolean, startAt: Int = 0): Int {
        val arr = if (end) to else from
        var lo = startAt
        var hi = arr.size
        while (true) {
            if (lo == hi) return lo
            val mid = (lo + hi) ushr 1
            val diff = (arr[mid] - pos).let { d ->
                if (d != 0) {
                    d
                } else {
                    (
                        if (end) {
                            value[mid].endSide
                        } else {
                            value[mid].startSide
                        }
                        ) - side
                }
            }
            if (mid == lo) return if (diff >= 0) lo else hi
            if (diff >= 0) {
                hi = mid
            } else {
                lo = mid + 1
            }
        }
    }

    fun between(offset: Int, from: Int, to: Int, f: (Int, Int, T) -> Boolean?): Boolean? {
        val start = findIndex(from, -FAR, true)
        val e = findIndex(to, FAR, false, start)
        for (i in start until e) {
            if (f(
                    this.from[i] + offset,
                    this.to[i] + offset,
                    value[i]
                ) == false
            ) {
                return false
            }
        }
        return null
    }

    data class MapResult<T : RangeValue>(
        val mapped: Chunk<T>?,
        val pos: Int
    )

    fun map(offset: Int, changes: ChangeDesc): MapResult<T> {
        val newValue = mutableListOf<T>()
        val newFrom = mutableListOf<Int>()
        val newTo = mutableListOf<Int>()
        var newPos = -1
        var maxPt = -1
        for (i in value.indices) {
            val v = value[i]
            val curFrom = from[i] + offset
            val curTo = to[i] + offset
            val mappedFrom: Int
            val mappedTo: Int
            if (curFrom == curTo) {
                val mapped = changes.mapPos(
                    curFrom, v.startSide, v.mapMode
                ) ?: continue
                mappedFrom = mapped
                mappedTo = if (v.startSide != v.endSide) {
                    val mt =
                        changes.mapPos(curFrom, v.endSide)
                    if (mt < mappedFrom) continue else mt
                } else {
                    mapped
                }
            } else {
                mappedFrom =
                    changes.mapPos(curFrom, v.startSide)
                mappedTo =
                    changes.mapPos(curTo, v.endSide)
                if (mappedFrom > mappedTo ||
                    (
                        mappedFrom == mappedTo &&
                            v.startSide > 0 &&
                            v.endSide <= 0
                        )
                ) {
                    continue
                }
            }
            if ((mappedTo - mappedFrom).let { d ->
                    if (d != 0) {
                        d
                    } else {
                        v.endSide - v.startSide
                    }
                } < 0
            ) {
                continue
            }
            if (newPos < 0) newPos = mappedFrom
            if (v.point) {
                maxPt = max(maxPt, mappedTo - mappedFrom)
            }
            newValue.add(v)
            newFrom.add(mappedFrom - newPos)
            newTo.add(mappedTo - newPos)
        }
        return MapResult(
            if (newValue.isNotEmpty()) {
                Chunk(newFrom, newTo, newValue, maxPt)
            } else {
                null
            },
            newPos
        )
    }
}

/**
 * A range cursor is an object that moves to the next range
 * every time you call [next] on it.
 */
interface RangeCursor<T> {
    fun next()
    fun goto(pos: Int, side: Int = -FAR): RangeCursor<T>
    var value: T?
    var from: Int
    var to: Int
    var rank: Int
}

/**
 * Specification for updating a range set.
 */
class RangeSetUpdate<T : RangeValue>(
    val add: List<Range<T>> = emptyList(),
    val sort: Boolean = false,
    val filter: (
        (from: Int, to: Int, value: T) -> Boolean
    )? = null,
    val filterFrom: Int = 0,
    val filterTo: Int = Int.MAX_VALUE
)

/**
 * A range set stores a collection of [Range]s in a way that
 * makes them efficient to [map] and [update]. This is an
 * immutable data structure.
 */
class RangeSet<T : RangeValue> internal constructor(
    internal val chunkPos: List<Int>,
    internal val chunk: List<Chunk<T>>,
    nextLayer: RangeSet<T>?,
    internal val maxPoint: Int
) {
    // Mutable backing allows the cyclic self-reference for
    // the empty singleton (nextLayer === this).
    internal var nextLayer: RangeSet<T> = nextLayer ?: this

    internal val length: Int
        get() {
            val last = chunk.size - 1
            return if (last < 0) {
                0
            } else {
                max(chunkEnd(last), nextLayer.length)
            }
        }

    /** The number of ranges in the set. */
    val size: Int
        get() {
            if (isEmpty) return 0
            var s = nextLayer.size
            for (c in chunk) s += c.value.size
            return s
        }

    internal fun chunkEnd(index: Int): Int = chunkPos[index] + chunk[index].length

    /**
     * Update the range set, optionally adding new ranges or
     * filtering out existing ones.
     */
    @Suppress("UNCHECKED_CAST")
    fun <U : T> update(updateSpec: RangeSetUpdate<U>): RangeSet<T> {
        var add: List<Range<U>> = updateSpec.add
        val filterFrom = updateSpec.filterFrom
        val filterTo =
            if (updateSpec.filterTo == Int.MAX_VALUE) {
                length
            } else {
                updateSpec.filterTo
            }
        val filter = updateSpec.filter as
            ((Int, Int, T) -> Boolean)?
        if (add.isEmpty() && filter == null) return this
        if (updateSpec.sort) {
            add = add.sortedWith(::cmpRange)
        }
        if (isEmpty) {
            return if (add.isNotEmpty()) {
                of(add) as RangeSet<T>
            } else {
                this
            }
        }

        val cur =
            LayerCursor(this, null, -1).goto(0)
        var i = 0
        val spill = mutableListOf<Range<T>>()
        val builder = RangeSetBuilder<T>()
        while (cur.value != null || i < add.size) {
            if (i < add.size &&
                (cur.from - add[i].from).let { d ->
                    if (d != 0) {
                        d
                    } else {
                        cur.startSide -
                            add[i].value.startSide
                    }
                } >= 0
            ) {
                val range = add[i++]
                if (!builder.addInner(
                        range.from,
                        range.to,
                        range.value
                    )
                ) {
                    spill.add(range as Range<T>)
                }
            } else if (
                cur.rangeIndex == 1 &&
                cur.chunkIndex < chunk.size &&
                (
                    i == add.size ||
                        chunkEnd(cur.chunkIndex) <
                        add[i].from
                    ) &&
                (
                    filter == null ||
                        filterFrom >
                        chunkEnd(cur.chunkIndex) ||
                        filterTo <
                        chunkPos[cur.chunkIndex]
                    ) &&
                builder.addChunk(
                    chunkPos[cur.chunkIndex],
                    chunk[cur.chunkIndex]
                )
            ) {
                cur.nextChunk()
            } else {
                if (filter == null ||
                    filterFrom > cur.to ||
                    filterTo < cur.from ||
                    filter(
                        cur.from, cur.to, cur.value!!
                    )
                ) {
                    if (!builder.addInner(
                            cur.from,
                            cur.to,
                            cur.value!!
                        )
                    ) {
                        spill.add(
                            Range.create(
                                cur.from,
                                cur.to,
                                cur.value!!
                            )
                        )
                    }
                }
                cur.next()
            }
        }

        val nextUpdate =
            if (nextLayer.isEmpty && spill.isEmpty()) {
                empty()
            } else {
                nextLayer.update(
                    RangeSetUpdate(
                        add = spill,
                        filter = filter,
                        filterFrom = filterFrom,
                        filterTo = filterTo
                    )
                )
            }
        return builder.finishInner(nextUpdate)
    }

    /** Map this range set through a set of changes. */
    fun map(changes: ChangeDesc): RangeSet<T> {
        if (changes.empty || isEmpty) return this

        val chunks = mutableListOf<Chunk<T>>()
        val positions = mutableListOf<Int>()
        var maxPt = -1
        for (i in chunk.indices) {
            val start = chunkPos[i]
            val c = chunk[i]
            val touch = changes.touchesRange(
                start,
                start + c.length
            )
            if (touch == TouchesResult.No) {
                maxPt = max(maxPt, c.maxPoint)
                chunks.add(c)
                positions.add(changes.mapPos(start))
            } else {
                val (mapped, pos) =
                    c.map(start, changes)
                if (mapped != null) {
                    maxPt = max(maxPt, mapped.maxPoint)
                    chunks.add(mapped)
                    positions.add(pos)
                }
            }
        }
        val next = nextLayer.map(changes)
        return if (chunks.isEmpty()) {
            next
        } else {
            RangeSet(positions, chunks, next, maxPt)
        }
    }

    /**
     * Iterate over the ranges that touch the region [from] to
     * [to], calling [f] for each. When the callback returns
     * `false`, iteration stops.
     */
    fun between(from: Int, to: Int, f: (from: Int, to: Int, value: T) -> Boolean?) {
        if (isEmpty) return
        for (i in chunk.indices) {
            val start = chunkPos[i]
            val c = chunk[i]
            if (to >= start &&
                from <= start + c.length &&
                c.between(
                    start, from - start,
                    to - start, f
                ) == false
            ) {
                return
            }
        }
        nextLayer.between(from, to, f)
    }

    /**
     * Iterate over the ranges in this set, in order,
     * including all ranges that end at or after [from].
     */
    fun iter(from: Int = 0): RangeCursor<T> = HeapCursor.from(listOf(this)).goto(from)

    /** True when this is the empty range set. */
    val isEmpty: Boolean get() = nextLayer === this

    companion object {
        private val EMPTY: RangeSet<RangeValue> =
            RangeSet(emptyList(), emptyList(), null, -1)

        /** The empty set of ranges. */
        @Suppress("UNCHECKED_CAST")
        fun <T : RangeValue> empty(): RangeSet<T> = EMPTY as RangeSet<T>

        /**
         * Iterate over the ranges in a collection of sets,
         * in order, starting from [from].
         */
        fun <T : RangeValue> iter(sets: List<RangeSet<T>>, from: Int = 0): RangeCursor<T> =
            HeapCursor.from(sets).goto(from)

        /**
         * Iterate over two groups of sets, calling methods on
         * [comparator] to notify it of possible differences.
         */
        fun <T : RangeValue> compare(
            oldSets: List<RangeSet<T>>,
            newSets: List<RangeSet<T>>,
            textDiff: ChangeDesc,
            comparator: RangeComparator<T>,
            minPointSize: Int = -1
        ) {
            val a = oldSets.filter { set ->
                set.maxPoint > 0 ||
                    !set.isEmpty &&
                    set.maxPoint >= minPointSize
            }
            val b = newSets.filter { set ->
                set.maxPoint > 0 ||
                    !set.isEmpty &&
                    set.maxPoint >= minPointSize
            }
            val shared =
                findSharedChunks(a, b, textDiff)
            val sideA =
                SpanCursor(a, shared, minPointSize)
            val sideB =
                SpanCursor(b, shared, minPointSize)

            textDiff.iterGaps { fromA, fromB, length ->
                compareSpans(
                    sideA,
                    fromA,
                    sideB,
                    fromB,
                    length,
                    comparator
                )
            }
            if (textDiff.empty &&
                textDiff.length == 0
            ) {
                compareSpans(
                    sideA,
                    0,
                    sideB,
                    0,
                    0,
                    comparator
                )
            }
        }

        /**
         * Compare the contents of two groups of range sets,
         * returning true if they are equivalent in the given
         * range.
         */
        fun <T : RangeValue> eq(
            oldSets: List<RangeSet<T>>,
            newSets: List<RangeSet<T>>,
            from: Int = 0,
            to: Int = FAR - 1
        ): Boolean {
            val a = oldSets.filter { set ->
                !set.isEmpty && set !in newSets
            }
            val b = newSets.filter { set ->
                !set.isEmpty && set !in oldSets
            }
            if (a.size != b.size) return false
            if (a.isEmpty()) return true
            val shared = findSharedChunks(a, b)
            val sideA =
                SpanCursor(a, shared, 0).goto(from)
            val sideB =
                SpanCursor(b, shared, 0).goto(from)
            while (true) {
                if (sideA.to != sideB.to ||
                    !sameValues(
                        sideA.active, sideB.active
                    ) ||
                    (
                        sideA.point != null &&
                            (
                                sideB.point == null ||
                                    !cmpVal(
                                        sideA.point!!,
                                        sideB.point!!
                                    )
                                )
                        )
                ) {
                    return false
                }
                if (sideA.to > to) return true
                sideA.next()
                sideB.next()
            }
        }

        /**
         * Iterate over a group of range sets at the same time,
         * notifying the iterator about the ranges covering
         * every given piece of content.
         */
        fun <T : RangeValue> spans(
            sets: List<RangeSet<T>>,
            from: Int,
            to: Int,
            iterator: SpanIterator<T>,
            minPointSize: Int = -1
        ): Int {
            val cursor =
                SpanCursor(sets, null, minPointSize)
                    .goto(from)
            var pos = from
            var openRanges = cursor.openStart
            while (true) {
                val curTo = min(cursor.to, to)
                if (cursor.point != null) {
                    val active =
                        cursor.activeForPoint(cursor.to)
                    val openCount = when {
                        cursor.pointFrom < from ->
                            active.size + 1
                        cursor.point!!.startSide < 0 ->
                            active.size
                        else ->
                            min(active.size, openRanges)
                    }
                    iterator.point(
                        pos,
                        curTo,
                        cursor.point!!,
                        active,
                        openCount,
                        cursor.pointRank
                    )
                    openRanges = min(
                        cursor.openEnd(curTo),
                        active.size
                    )
                } else if (curTo > pos) {
                    iterator.span(
                        pos,
                        curTo,
                        cursor.active,
                        openRanges
                    )
                    openRanges = cursor.openEnd(curTo)
                }
                if (cursor.to > to) {
                    return openRanges + if (
                        cursor.point != null &&
                        cursor.to > to
                    ) {
                        1
                    } else {
                        0
                    }
                }
                pos = cursor.to
                cursor.next()
            }
        }

        /**
         * Create a range set for the given range or array of
         * ranges.
         */
        fun <T : RangeValue> of(ranges: List<Range<T>>, sort: Boolean = false): RangeSet<T> {
            val build = RangeSetBuilder<T>()
            val sorted =
                if (sort) lazySort(ranges) else ranges
            for (range in sorted) {
                build.add(
                    range.from,
                    range.to,
                    range.value
                )
            }
            return build.finish()
        }

        /** Create a range set from a single range. */
        fun <T : RangeValue> of(range: Range<T>, sort: Boolean = false): RangeSet<T> =
            of(listOf(range), sort)

        /**
         * Join an array of range sets into a single set.
         */
        fun <T : RangeValue> join(sets: List<RangeSet<T>>): RangeSet<T> {
            if (sets.isEmpty()) return empty()
            var result = sets[sets.size - 1]
            for (i in sets.size - 2 downTo 0) {
                var layer = sets[i]
                while (!layer.isEmpty) {
                    result = RangeSet(
                        layer.chunkPos, layer.chunk,
                        result,
                        max(
                            layer.maxPoint,
                            result.maxPoint
                        )
                    )
                    layer = layer.nextLayer
                }
            }
            return result
        }

        internal fun <T : RangeValue> create(
            chunkPos: List<Int>,
            chunk: List<Chunk<T>>,
            nextLayer: RangeSet<T>,
            maxPoint: Int
        ): RangeSet<T> = RangeSet(
            chunkPos,
            chunk,
            nextLayer,
            maxPoint
        )
    }
}

private fun <T : RangeValue> lazySort(ranges: List<Range<T>>): List<Range<T>> {
    if (ranges.size > 1) {
        var prev = ranges[0]
        for (i in 1 until ranges.size) {
            val cur = ranges[i]
            if (cmpRange(prev, cur) > 0) {
                return ranges.sortedWith(::cmpRange)
            }
            prev = cur
        }
    }
    return ranges
}

/**
 * A range set builder is a data structure that helps build up
 * a [RangeSet] directly, without first allocating an array of
 * [Range] objects.
 */
class RangeSetBuilder<T : RangeValue> {
    private var chunks = mutableListOf<Chunk<T>>()
    private var chunkPos = mutableListOf<Int>()
    private var chunkStart = -1
    private var last: T? = null
    private var lastFrom = -FAR
    private var lastTo = -FAR
    private var from = mutableListOf<Int>()
    private var to = mutableListOf<Int>()
    private var value = mutableListOf<T>()
    private var maxPoint = -1
    private var setMaxPoint = -1
    private var nextLayer: RangeSetBuilder<T>? = null

    private fun finishChunk(newArrays: Boolean) {
        chunks.add(Chunk(from, to, value, maxPoint))
        chunkPos.add(chunkStart)
        chunkStart = -1
        setMaxPoint = max(setMaxPoint, maxPoint)
        maxPoint = -1
        if (newArrays) {
            from = mutableListOf()
            to = mutableListOf()
            value = mutableListOf()
        }
    }

    /**
     * Add a range. Ranges should be added in sorted (by `from`
     * and `value.startSide`) order.
     */
    fun add(from: Int, to: Int, value: T) {
        if (!addInner(from, to, value)) {
            (
                nextLayer ?: RangeSetBuilder<T>().also {
                    nextLayer = it
                }
                ).add(from, to, value)
        }
    }

    internal fun addInner(from: Int, to: Int, value: T): Boolean {
        val diff = (from - lastTo).let { d ->
            if (d != 0) {
                d
            } else {
                value.startSide - (last?.endSide ?: 0)
            }
        }
        if (diff <= 0 && (from - lastFrom).let { d ->
                if (d != 0) {
                    d
                } else {
                    value.startSide -
                        (last?.startSide ?: 0)
                }
            } < 0
        ) {
            error(
                "Ranges must be added sorted by `from`" +
                    " position and `startSide`"
            )
        }
        if (diff < 0) return false
        if (this.from.size == CHUNK_SIZE) {
            finishChunk(true)
        }
        if (chunkStart < 0) chunkStart = from
        this.from.add(from - chunkStart)
        this.to.add(to - chunkStart)
        last = value
        lastFrom = from
        lastTo = to
        this.value.add(value)
        if (value.point) {
            maxPoint = max(maxPoint, to - from)
        }
        return true
    }

    internal fun addChunk(from: Int, chunk: Chunk<T>): Boolean {
        if ((from - lastTo).let { d ->
                if (d != 0) {
                    d
                } else {
                    chunk.value[0].startSide -
                        (last?.endSide ?: 0)
                }
            } < 0
        ) {
            return false
        }
        if (this.from.isNotEmpty()) finishChunk(true)
        setMaxPoint = max(setMaxPoint, chunk.maxPoint)
        chunks.add(chunk)
        chunkPos.add(from)
        val lastIdx = chunk.value.size - 1
        last = chunk.value[lastIdx]
        lastFrom = chunk.from[lastIdx] + from
        lastTo = chunk.to[lastIdx] + from
        return true
    }

    /**
     * Finish the range set. Returns the new set. The builder
     * can't be used anymore after this has been called.
     */
    fun finish(): RangeSet<T> = finishInner(RangeSet.empty())

    internal fun finishInner(next: RangeSet<T>): RangeSet<T> {
        if (from.isNotEmpty()) finishChunk(false)
        if (chunks.isEmpty()) return next
        return RangeSet.create(
            chunkPos,
            chunks,
            nextLayer?.finishInner(next) ?: next,
            setMaxPoint
        )
    }
}

private fun <T : RangeValue> findSharedChunks(
    a: List<RangeSet<T>>,
    b: List<RangeSet<T>>,
    textDiff: ChangeDesc? = null
): Set<Chunk<T>> {
    val inA = mutableMapOf<Chunk<T>, Int>()
    for (set in a) {
        for (i in set.chunk.indices) {
            if (set.chunk[i].maxPoint <= 0) {
                inA[set.chunk[i]] = set.chunkPos[i]
            }
        }
    }
    val shared = mutableSetOf<Chunk<T>>()
    for (set in b) {
        for (i in set.chunk.indices) {
            val known = inA[set.chunk[i]] ?: continue
            val mappedPos = if (textDiff != null) {
                textDiff.mapPos(known)
            } else {
                known
            }
            if (mappedPos == set.chunkPos[i] &&
                (
                    textDiff == null ||
                        textDiff.touchesRange(
                            known,
                            known + set.chunk[i].length
                        ) == TouchesResult.No
                    )
            ) {
                shared.add(set.chunk[i])
            }
        }
    }
    return shared
}

internal class LayerCursor<T : RangeValue>(
    val layer: RangeSet<T>,
    val skip: Set<Chunk<T>>?,
    val minPoint: Int,
    override var rank: Int = 0
) : RangeCursor<T> {
    override var from: Int = 0
    override var to: Int = 0
    override var value: T? = null

    var chunkIndex: Int = 0
    var rangeIndex: Int = 0

    val startSide: Int
        get() = value?.startSide ?: 0

    val endSide: Int
        get() = value?.endSide ?: 0

    override fun goto(pos: Int, side: Int): LayerCursor<T> {
        chunkIndex = 0
        rangeIndex = 0
        gotoInner(pos, side, false)
        return this
    }

    fun goto(pos: Int): LayerCursor<T> = goto(pos, -FAR)

    private fun gotoInner(pos: Int, side: Int, forward: Boolean) {
        var fwd = forward
        while (chunkIndex < layer.chunk.size) {
            val next = layer.chunk[chunkIndex]
            if (!(
                    skip != null && next in skip ||
                        layer.chunkEnd(chunkIndex) < pos ||
                        next.maxPoint < minPoint
                    )
            ) {
                break
            }
            chunkIndex++
            fwd = false
        }
        if (chunkIndex < layer.chunk.size) {
            val ri =
                layer.chunk[chunkIndex].findIndex(
                    pos - layer.chunkPos[chunkIndex],
                    side,
                    true
                )
            if (!fwd || rangeIndex < ri) {
                advanceRangeIndex(ri)
            }
        }
        next()
    }

    fun forward(pos: Int, side: Int) {
        if ((to - pos).let { d ->
                if (d != 0) d else endSide - side
            } < 0
        ) {
            gotoInner(pos, side, true)
        }
    }

    override fun next() {
        while (true) {
            if (chunkIndex == layer.chunk.size) {
                from = FAR
                to = FAR
                value = null
                break
            } else {
                val cp = layer.chunkPos[chunkIndex]
                val chunk = layer.chunk[chunkIndex]
                val f = cp + chunk.from[rangeIndex]
                from = f
                to = cp + chunk.to[rangeIndex]
                value = chunk.value[rangeIndex]
                advanceRangeIndex(rangeIndex + 1)
                if (minPoint < 0 ||
                    (
                        value!!.point &&
                            to - from >= minPoint
                        )
                ) {
                    break
                }
            }
        }
    }

    private fun advanceRangeIndex(index: Int) {
        if (index ==
            layer.chunk[chunkIndex].value.size
        ) {
            chunkIndex++
            if (skip != null) {
                while (
                    chunkIndex < layer.chunk.size &&
                    layer.chunk[chunkIndex] in skip
                    ) chunkIndex++
            }
            rangeIndex = 0
        } else {
            rangeIndex = index
        }
    }

    fun nextChunk() {
        chunkIndex++
        rangeIndex = 0
        next()
    }

    fun compare(other: LayerCursor<T>): Int {
        return (from - other.from).let { d ->
            if (d != 0) {
                d
            } else {
                (startSide - other.startSide).let { d2 ->
                    if (d2 != 0) {
                        d2
                    } else {
                        (rank - other.rank).let { d3 ->
                            if (d3 != 0) {
                                d3
                            } else {
                                (to - other.to).let { d4 ->
                                    if (d4 != 0) {
                                        d4
                                    } else {
                                        endSide - other.endSide
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class HeapCursor<T : RangeValue>(
    val heap: MutableList<LayerCursor<T>>
) : RangeCursor<T> {
    override var from: Int = 0
    override var to: Int = 0
    override var value: T? = null
    override var rank: Int = 0

    val startSide: Int
        get() = value?.startSide ?: 0

    override fun goto(pos: Int, side: Int): HeapCursor<T> {
        for (cur in heap) cur.goto(pos, side)
        var i = heap.size shr 1
        while (i >= 0) {
            heapBubble(heap, i)
            i--
        }
        next()
        return this
    }

    fun forward(pos: Int, side: Int) {
        for (cur in heap) cur.forward(pos, side)
        var i = heap.size shr 1
        while (i >= 0) {
            heapBubble(heap, i)
            i--
        }
        if ((to - pos).let { d ->
                if (d != 0) {
                    d
                } else {
                    (value?.endSide ?: 0) - side
                }
            } < 0
        ) {
            next()
        }
    }

    override fun next() {
        if (heap.isEmpty()) {
            from = FAR
            to = FAR
            value = null
            rank = -1
        } else {
            val top = heap[0]
            from = top.from
            to = top.to
            value = top.value
            rank = top.rank
            if (top.value != null) top.next()
            heapBubble(heap, 0)
        }
    }

    companion object {
        fun <T : RangeValue> from(
            sets: List<RangeSet<T>>,
            skip: Set<Chunk<T>>? = null,
            minPoint: Int = -1
        ): RangeCursor<T> {
            val heap =
                mutableListOf<LayerCursor<T>>()
            for (i in sets.indices) {
                var cur: RangeSet<T> = sets[i]
                while (!cur.isEmpty) {
                    if (cur.maxPoint >= minPoint) {
                        heap.add(
                            LayerCursor(
                                cur,
                                skip,
                                minPoint,
                                i
                            )
                        )
                    }
                    cur = cur.nextLayer
                }
            }
            return if (heap.size == 1) {
                heap[0]
            } else {
                HeapCursor(heap)
            }
        }
    }
}

private fun <T : RangeValue> heapBubble(heap: MutableList<LayerCursor<T>>, index: Int) {
    if (index >= heap.size) return
    val cur = heap[index]
    var idx = index
    while (true) {
        var childIndex = (idx shl 1) + 1
        if (childIndex >= heap.size) break
        var child = heap[childIndex]
        if (childIndex + 1 < heap.size &&
            child.compare(heap[childIndex + 1]) >= 0
        ) {
            child = heap[childIndex + 1]
            childIndex++
        }
        if (cur.compare(child) < 0) break
        heap[childIndex] = cur
        heap[idx] = child
        idx = childIndex
    }
}

internal class SpanCursor<T : RangeValue>(
    sets: List<RangeSet<T>>,
    skip: Set<Chunk<T>>?,
    val minPoint: Int
) {
    val cursor: RangeCursor<T> =
        HeapCursor.from(sets, skip, minPoint)

    val active = mutableListOf<T>()
    val activeTo = mutableListOf<Int>()
    val activeRank = mutableListOf<Int>()
    var minActive = -1

    var point: T? = null
    var pointFrom = 0
    var pointRank = 0

    var to = -FAR
    var endSide = 0
    var openStart = -1

    private val cursorAsLayer: LayerCursor<T>?
        get() = cursor as? LayerCursor<T>

    private val cursorAsHeap: HeapCursor<T>?
        get() = cursor as? HeapCursor<T>

    private val cursorStartSide: Int
        get() = cursorAsLayer?.startSide
            ?: cursorAsHeap?.startSide
            ?: (cursor.value?.startSide ?: 0)

    fun goto(pos: Int, side: Int = -FAR): SpanCursor<T> {
        cursorAsLayer?.goto(pos, side)
            ?: cursorAsHeap?.goto(pos, side)
            ?: cursor.goto(pos, side)
        active.clear()
        activeTo.clear()
        activeRank.clear()
        minActive = -1
        to = pos
        endSide = side
        openStart = -1
        next()
        return this
    }

    fun forward(pos: Int, side: Int) {
        while (minActive > -1 &&
            (activeTo[minActive] - pos).let { d ->
                if (d != 0) {
                    d
                } else {
                    active[minActive].endSide - side
                }
            } < 0
            ) removeActive(minActive)
        cursorAsLayer?.forward(pos, side)
            ?: cursorAsHeap?.forward(pos, side)
    }

    private fun removeActive(index: Int) {
        removeAt(active, index)
        removeAt(activeTo, index)
        removeAt(activeRank, index)
        minActive = findMinIndex(active, activeTo)
    }

    private fun addActive(trackOpen: MutableList<Int>?) {
        var i = 0
        val v = cursor.value!!
        val curTo = cursor.to
        val r = cursor.rank
        while (i < activeRank.size &&
            (r - activeRank[i]).let { d ->
                if (d != 0) {
                    d
                } else {
                    curTo - activeTo[i]
                }
            } > 0
            ) i++
        insertAt(active, i, v)
        insertAt(activeTo, i, curTo)
        insertAt(activeRank, i, r)
        if (trackOpen != null) {
            insertAt(trackOpen, i, cursor.from)
        }
        minActive = findMinIndex(active, activeTo)
    }

    fun next() {
        val from = to
        val wasPoint = point
        point = null
        val trackOpen: MutableList<Int>? =
            if (openStart < 0) mutableListOf() else null
        while (true) {
            val a = minActive
            if (a > -1 &&
                (activeTo[a] - cursor.from).let { d ->
                    if (d != 0) {
                        d
                    } else {
                        active[a].endSide -
                            cursorStartSide
                    }
                } < 0
            ) {
                if (activeTo[a] > from) {
                    to = activeTo[a]
                    endSide = active[a].endSide
                    break
                }
                removeActive(a)
                if (trackOpen != null) {
                    removeAt(trackOpen, a)
                }
            } else if (cursor.value == null) {
                to = FAR
                endSide = FAR
                break
            } else if (cursor.from > from) {
                to = cursor.from
                endSide = cursorStartSide
                break
            } else {
                val nextVal = cursor.value!!
                if (!nextVal.point) {
                    addActive(trackOpen)
                    cursor.next()
                } else if (
                    wasPoint != null &&
                    cursor.to == to &&
                    cursor.from < cursor.to
                ) {
                    cursor.next()
                } else {
                    point = nextVal
                    pointFrom = cursor.from
                    pointRank = cursor.rank
                    to = cursor.to
                    endSide = nextVal.endSide
                    cursor.next()
                    forward(to, endSide)
                    break
                }
            }
        }
        if (trackOpen != null) {
            openStart = 0
            for (i in trackOpen.size - 1 downTo 0) {
                if (trackOpen[i] < from) {
                    openStart++
                } else {
                    break
                }
            }
        }
    }

    fun activeForPoint(to: Int): List<T> {
        if (active.isEmpty()) return active
        val result = mutableListOf<T>()
        for (i in active.size - 1 downTo 0) {
            if (activeRank[i] < pointRank) break
            if (activeTo[i] > to ||
                (
                    activeTo[i] == to &&
                        active[i].endSide >=
                        point!!.endSide
                    )
            ) {
                result.add(active[i])
            }
        }
        return result.reversed()
    }

    fun openEnd(to: Int): Int {
        var open = 0
        for (i in activeTo.size - 1 downTo 0) {
            if (activeTo[i] > to) open++ else break
        }
        return open
    }
}

private fun <T : RangeValue> compareSpans(
    a: SpanCursor<T>,
    startA: Int,
    b: SpanCursor<T>,
    startB: Int,
    length: Int,
    comparator: RangeComparator<T>
) {
    a.goto(startA)
    b.goto(startB)
    val endB = startB + length
    var pos = startB
    val dPos = startB - startA
    var boundChange = false
    while (true) {
        val dEnd = (a.to + dPos) - b.to
        val diff = if (dEnd != 0) {
            dEnd
        } else {
            a.endSide - b.endSide
        }
        val end = if (diff < 0) a.to + dPos else b.to
        val clipEnd = min(end, endB)
        val pt = a.point ?: b.point
        if (pt != null) {
            if (!(
                    a.point != null &&
                        b.point != null &&
                        cmpVal(a.point!!, b.point!!) &&
                        sameValues(
                            a.activeForPoint(a.to),
                            b.activeForPoint(b.to)
                        )
                    )
            ) {
                comparator.comparePoint(
                    pos,
                    clipEnd,
                    a.point,
                    b.point
                )
            }
            boundChange = false
        } else {
            if (boundChange) {
                comparator.boundChange(pos)
            }
            if (clipEnd > pos &&
                !sameValues(a.active, b.active)
            ) {
                comparator.compareRange(
                    pos,
                    clipEnd,
                    a.active,
                    b.active
                )
            }
            if (clipEnd < endB &&
                (
                    dEnd != 0 ||
                        a.openEnd(end) != b.openEnd(end)
                    )
            ) {
                boundChange = true
            }
        }
        if (end > endB) break
        pos = end
        if (diff <= 0) a.next()
        if (diff >= 0) b.next()
    }
}

private fun <T : RangeValue> sameValues(a: List<T>, b: List<T>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] !== b[i] && !cmpVal(a[i], b[i])) {
            return false
        }
    }
    return true
}

private fun <T> removeAt(array: MutableList<T>, index: Int) {
    for (i in index until array.size - 1) {
        array[i] = array[i + 1]
    }
    array.removeAt(array.size - 1)
}

private fun <T> insertAt(array: MutableList<T>, index: Int, value: T) {
    array.add(value) // add a slot at the end
    for (i in array.size - 1 downTo index + 1) {
        array[i] = array[i - 1]
    }
    array[index] = value
}

private fun findMinIndex(value: List<RangeValue>, array: List<Int>): Int {
    var found = -1
    var foundPos = FAR
    for (i in array.indices) {
        if ((array[i] - foundPos).let { d ->
                if (d != 0) {
                    d
                } else if (found >= 0) {
                    value[i].endSide -
                        value[found].endSide
                } else {
                    -1
                }
            } < 0
        ) {
            found = i
            foundPos = array[i]
        }
    }
    return found
}
