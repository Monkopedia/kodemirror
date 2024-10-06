package com.monkopedia.kodemirror.state

import com.monkopedia.kodemirror.state.Side.Companion.asSide
import com.monkopedia.kodemirror.state.SingleOrList.Companion.list
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min

//import {ChangeDesc, MapMode} from "./change"


/// Each range is associated with a value, which must inherit from
/// this class.
interface RangeValue {
    /// Compare this value with another value. Used when comparing
    /// rangesets. The default implementation compares by identity.
    /// Unless you are only creating a fixed Int of unique instances
    /// of your value type, it is a good idea to implement this
    /// properly.
    open fun eq(other: RangeValue): Boolean {
        return this == other
    }

    /// The bias value at the start of the range. Determines how the
    /// range is positioned relative to other ranges starting at this
    /// position. Defaults to 0.
    val startSide: Int
        get() = 0

    /// The bias value at the end of the range. Defaults to 0.
    val endSide: Int
        get() = 0

    /// The mode with which the location of the range should be mapped
    /// when its `from` and `to` are the same, to decide whether a
    /// change deletes the range. Defaults to `MapMode.TrackDel`.
    val mapMode: MapMode
        get() = MapMode.TrackDel

    /// Determines whether this value marks a point range. Regular
    /// ranges affect the part of the document they cover, and are
    /// meaningless when empty. Point ranges have a meaning on their
    /// own. When non-empty, a point range is treated as atomic and
    /// shadows any ranges contained in it.
    val point: Boolean
        get() = false

    companion object {
        /// Create a [range](#state.Range) with this value.
        fun <T : RangeValue> T.range(from: Int, to: Int = from): Range<T> {
            return Range.create(from, to, this)
        }
    }
}

/// A range associates a value with a range of positions.
class Range<T : RangeValue> private constructor(
    /// The range's start position.
    val from: Int,
    /// Its end position.
    val to: Int,
    /// The value associated with this range.
    val value: T
) {

    companion object {
        /// @internal
        internal fun <T : RangeValue> create(from: Int, to: Int, value: T): Range<T> {
            return Range<T>(from, to, value)
        }
    }
}

internal fun cmpRange(a: Range<*>, b: Range<*>): Boolean {
    return ((a.from - b.from).takeIf { it != 0 } ?: (a.value.startSide - b.value.startSide)) != 0
}

/// Collection of methods used when comparing range sets.
interface RangeComparator<T : RangeValue> {
    /// Notifies the comparator that a range (in positions in the new
    /// document) has the given sets of values associated with it, which
    /// are different in the old (A) and new (B) sets.
    fun compareRange(from: Int, to: Int, activeA: List<T>, activeB: List<T>): Unit

    /// Notification for a changed (or inserted, or deleted) point range.
    fun comparePoint(from: Int, to: Int, pointA: T?, pointB: T?): Unit
}

/// Methods used when iterating over the spans created by a set of
/// ranges. The entire iterated range will be covered with either
/// `span` or `point` calls.
interface SpanIterator<T : RangeValue> {
    /// Called for any ranges not covered by point decorations. `active`
    /// holds the values that the range is marked with (and may be
    /// empty). `openStart` indicates how many of those ranges are open
    /// (continued) at the start of the span.
    fun span(from: Int, to: Int, active: List<T>, openStart: Int): Unit

    /// Called when going over a point decoration. The active range
    /// decorations that cover the point and have a higher precedence
    /// are provided in `active`. The open count in `openStart` counts
    /// the Int of those ranges that started before the point and. If
    /// the point started before the iterated range, `openStart` will be
    /// `active.length + 1` to signal this.
    fun point(from: Int, to: Int, value: T, active: List<T>, openStart: Int, index: Int): Unit
}

object C {
    // The maximum amount of ranges to store in a single chunk
    const val ChunkSize = 250

    // A large (fixnum) value to use for max/min values.
    const val Far = Int.MAX_VALUE
}

class Chunk<T : RangeValue> constructor(
    val from: List<Int>,
    val to: List<Int>,
    val value: List<T>,
    // Chunks are marked with the largest point that occurs
    // in them (or -1 for no points), so that scans that are
    // only interested in points (such as the
    // heightmap-related logic) can skip range-only chunks.
    val maxPoint: Int
) {

    val length: Int
        get() {
            return this.to[this.to.size - 1]
        }

    // Find the index of the given position and side. Use the ranges'
    // `from` pos when `end == false`, `to` when `end == true`.
    fun findIndex(pos: Int, side: Int, end: Boolean, startAt: Int = 0): Int {
        val arr = if (end) this.to else this.from
        var lo = startAt
        var hi = arr.size
        while (true) {
            if (lo == hi) return lo
            val mid = (lo + hi) shr 1
            val midDiff = pos.takeIf { it != 0 }
                ?: (if (end) this.value[mid].endSide else this.value[mid].startSide)
            val diff = arr[mid] - midDiff - side
            if (mid == lo) return if (diff >= 0) lo else hi
            if (diff >= 0) hi = mid
            else lo = mid + 1
        }
    }

    fun between(
        offset: Int,
        from: Int,
        to: Int,
        f: (from: Int, to: Int, value: T) -> Boolean
    ): Boolean {
        var i = this.findIndex(from, -C.Far, true)
        var e = this.findIndex(to, C.Far, false, i)
        while (i < e) {
            if (f(this.from[i] + offset, this.to[i] + offset, this.value[i]) === false) return false
            i++
        }
        return true
    }

    fun map(offset: Int, changes: ChangeDesc): MappedChunk<T> {
        val value = mutableListOf<T>()
        val from = mutableListOf<Int>()
        val to = mutableListOf<Int>()
        var newPos = -1
        var maxPoint = -1
        for (i in value.indices) {
            val v = this.value[i]
            val curFrom = this.from[i] + offset
            val curTo = this.to[i] + offset
            var newFrom: Int
            var newTo: Int
            if (curFrom == curTo) {
                val mapped = changes.mapPos(curFrom, v.startSide.asSide, v.mapMode)
                    ?: continue
                newFrom = mapped
                newTo = mapped
                if (
                    v.startSide !=
                    v.endSide
                ) {
                    newTo = changes.mapPos(curFrom, v.endSide.asSide) ?: 0
                    if (newTo < newFrom) continue
                }
            } else {
                newFrom = changes.mapPos(curFrom, v.startSide.asSide) ?: 0
                newTo = changes.mapPos(curTo, v.endSide.asSide) ?: 0
                if (newFrom > newTo || newFrom == newTo &&
                    v.startSide > 0 &&
                    v.endSide <= 0
                ) continue
            }
            if (((newTo - newFrom).takeIf { it != 0 } ?: (v.endSide - v.startSide)) < 0) continue
            if (newPos < 0) newPos = newFrom
            if (v.point) maxPoint = max(maxPoint, newTo - newFrom)
            value.add(v)
            from.add(newFrom - newPos)
            to.add(newTo - newPos)
        }
        return MappedChunk(
            mapped = if (value.size != 0) Chunk(from, to, value, maxPoint) else null,
            pos = newPos
        )
    }

    data class MappedChunk<T : RangeValue>(
        val mapped: Chunk<T>?,
        val pos: Int
    )
}

/// A range cursor is an object that moves to the next range every
/// time you call `next` on it. Note that, unlike ES6 iterators, these
/// start out pointing at the first element, so you should call `next`
/// only after reading the first range (if any).
interface RangeCursor<T> {
    /// Move the iterator forward.
    fun next()

    /// The next range's value. Holds `null` when the cursor has reached
    /// its end.
    val value: T?

    /// The next range's start position.
    val from: Int

    /// The next end position.
    val to: Int
}

data class RangeSetUpdate<T : RangeValue>(
    /// An array of ranges to add. If given, this should be sorted by
    /// `from` position and `startSide` unless
    /// [`sort`](#state.RangeSet.update^updateSpec.sort) is given as
    /// `true`.
    val add: List<Range<out T>>? = null,

    /// Indicates whether the library should sort the ranges in `add`.
    /// Defaults to `false`.
    val sort: Boolean = false,

    /// Filter the ranges already in the set. Only those for which this
    /// fun returns `true` are kept.
    val filter: ((from: Int, to: Int, value: T) -> Boolean)? = null,

    /// Can be used to limit the range on which the filter is
    /// applied. Filtering only a small range, as opposed to the entire
    /// set, can make updates cheaper.
    val filterFrom: Int? = null,

    /// The end position to apply the filter to.
    val filterTo: Int? = null
)

/// A range set stores a collection of [ranges](#state.Range) in a
/// way that makes them efficient to [map](#state.RangeSet.map) and
/// [update](#state.RangeSet.update). This is an immutable data
/// structure.
class RangeSet<T : RangeValue>
private constructor(
    /// @internal
    internal val chunkPos: List<Int>,
    /// @internal
    internal val chunk: List<Chunk<T>>,
    nextLayer: RangeSet<T>? = null,
    /// @internal
    internal val maxPoint: Int
) {
    /// @internal
    internal val nextLayer: RangeSet<T> = nextLayer ?: this

    /// @internal
    val length: Int
        get() {
            val last = this.chunk.size - 1
            return if (last < 0) 0 else max(this.chunkEnd(last), this.nextLayer.length)
        }

    /// The Int of ranges in the set.
    val size: Int
        get() {
            if (this.isEmpty) return 0
            return chunk.sumOf { it.value.size } + this.nextLayer.size
        }

    /// @internal
    fun chunkEnd(index: Int): Int {
        return this.chunkPos[index] + this.chunk[index].length
    }

    /// Update the range set, optionally adding new ranges or filtering
    /// out existing ones.
    ///
    /// (Note: The type parameter is just there as a kludge to work
    /// around TypeScript variance issues that prevented `RangeSet<X>`
    /// from being a subtype of `RangeSet<Y>` when `X` is a subtype of
    /// `Y`.)
    fun update(updateSpec: RangeSetUpdate<T>): RangeSet<T> {
        var add: List<Range<out T>> = updateSpec.add ?: mutableListOf()
        val sort = updateSpec.sort ?: false
        val filterFrom = updateSpec.filterFrom ?: 0
        val filterTo = updateSpec.filterTo ?: this.length

        @Suppress("UNCHECKED_CAST")
        val filter = updateSpec.filter as? (from: Int, to: Int, value: T) -> Boolean
        if (add.size == 0 && filter == null) return this
        if (sort) add =
            add.toList().sortedWith(Comparator { a, b -> if (cmpRange(a, b)) 1 else -1 })
        if (this.isEmpty) return if (add.size != 0) RangeSet.of(add.list) else this

        val cur = LayerCursor(this, null, -1).goto(0)
        var i = 0
        val spill = mutableListOf<Range<out T>>()
        val builder = RangeSetBuilder<T>()
        while (cur.value != null || i < add.size) {
            if (i < add.size && ((cur.from - add[i].from) or (cur.startSide - add[i].value.startSide)) >= 0) {
                val range = add[i++]
                if (!builder.addInner(range.from, range.to, range.value)) spill.add(range)
            } else if (cur.rangeIndex == 1 && cur.chunkIndex < this.chunk.size &&
                (i == add.size || this.chunkEnd(cur.chunkIndex) < add[i].from) &&
                (filter == null || filterFrom > this.chunkEnd(cur.chunkIndex) || filterTo < this.chunkPos[cur.chunkIndex]) &&
                builder.addChunk(
                    this.chunkPos[cur.chunkIndex],
                    this.chunk[cur.chunkIndex]
                )
            ) {
                cur.nextChunk()
            } else {
                if (filter == null || filterFrom > cur.to || filterTo < cur.from ||
                    filter(cur.from, cur.to, cur.value!!)
                ) {
                    if (!builder.addInner(cur.from, cur.to, cur.value!!)) {
                        spill.add(Range.create(cur.from, cur.to, cur.value!!))
                    }
                }
                cur.next()
            }
        }

        val set: RangeSet<T> =
            if (this.nextLayer.isEmpty && spill.isEmpty()) RangeSet.empty()
            else this.nextLayer.update(
                RangeSetUpdate<T>(
                    add = spill,
                    filter = filter,
                    filterFrom = filterFrom,
                    filterTo = filterTo
                )
            )
        return builder.finishInner(set)
    }

    /// Map this range set through a set of changes, return the new set.
    fun map(changes: ChangeDesc): RangeSet<T> {
        if (changes.isEmpty || this.isEmpty) return this

        val chunks = mutableListOf<Chunk<T>>()
        val chunkPos = mutableListOf<Int>()
        var maxPoint = -1
        for (i in this.chunk.indices) {
            val start = this.chunkPos[i]
            val chunk = this.chunk[i]
            val touch = changes.touchesRange(start, start + chunk.length)
            if (touch == false) {
                maxPoint = max(maxPoint, chunk.maxPoint)
                chunks.add(chunk)
                chunkPos.add(changes.mapPos(start)!!)
            } else if (touch == true) {
                val (mapped, pos) = chunk.map(start, changes)
                if (mapped != null) {
                    maxPoint = max(maxPoint, mapped.maxPoint)
                    chunks.add(mapped)
                    chunkPos.add(pos)
                }
            }
        }
        val next = this.nextLayer.map(changes)
        return if (chunks.size == 0) next
        else {
            val set: RangeSet<T> = next ?: RangeSet.empty()
            RangeSet(chunkPos, chunks, set, maxPoint)
        }
    }

    /// Iterate over the ranges that touch the region `from` to `to`,
    /// calling `f` for each. There is no guarantee that the ranges will
    /// be reported in any specific order. When the callback returns
    /// `false`, iteration stops.
    fun between(from: Int, to: Int, f: (from: Int, to: Int, value: T) -> Boolean): Unit {
        if (this.isEmpty) return
        for (i in chunk.indices) {
            val start = this.chunkPos[i]
            val chunk = this.chunk[i]
            if (to >= start && from <= start + chunk.length &&
                !chunk.between(start, from - start, to - start, f)
            ) {
                return
            }
        }
        this.nextLayer.between(from, to, f)
    }

    /// Iterate over the ranges in this set, in order, including all
    /// ranges that end at or after `from`.
    fun iter(from: Int = 0): Cursor<T> {
        return HeapCursor.from(listOf(this)).goto(from)
    }

    /// @internal
    val isEmpty: Boolean
        get() {
            return this.nextLayer == this
        }

    companion object {
        /// Iterate over the ranges in a collection of sets, in order,
        /// starting from `from`.
        fun <T : RangeValue> iter(sets: List<RangeSet<T>>, from: Int = 0): Cursor<T> {
            return HeapCursor.from(sets).goto(from)
        }

        /// @internal
        fun <T : RangeValue> create(
            chunkPos: List<Int>,
            chunk: List<Chunk<T>>,
            nextLayer: RangeSet<T>,
            maxPoint: Int
        ): RangeSet<T> {
            return RangeSet<T>(chunkPos, chunk, nextLayer, maxPoint)
        }

        /// Iterate over two groups of sets, calling methods on `comparator`
        /// to notify it of possible differences.
        fun <T : RangeValue> compare(
            oldSets: List<RangeSet<T>>, newSets: List<RangeSet<T>>,
            /// This indicates how the underlying data changed between these
            /// ranges, and is needed to synchronize the iteration.
            textDiff: ChangeDesc,
            comparator: RangeComparator<T>,
            /// Can be used to ignore all non-point ranges, and points below
            /// the given size. When -1, all ranges are compared.
            minPointSize: Int = -1
        ) {
            val a = oldSets.filter { set ->
                set.maxPoint > 0 || !set.isEmpty && set.maxPoint >= minPointSize
            }
            val b = newSets.filter { set ->
                set.maxPoint > 0 || !set.isEmpty && set.maxPoint >= minPointSize
            }
            val sharedChunks = findSharedChunks(a, b, textDiff)

            val sideA = SpanCursor(a, sharedChunks, minPointSize)
            val sideB = SpanCursor(b, sharedChunks, minPointSize)

            textDiff.iterGaps({ fromA, fromB, length ->
                compare(sideA, fromA, sideB, fromB, length, comparator)
            })
            if (textDiff.isEmpty && textDiff.length == 0) compare(
                sideA,
                0,
                sideB,
                0,
                0,
                comparator
            )
        }

        /// Compare the contents of two groups of range sets, returning true
        /// if they are equivalent in the given range.
        fun <T : RangeValue> eq(
            oldSets: List<RangeSet<T>>, newSets: List<RangeSet<T>>,
            from: Int = 0, to: Int? = null
        ): Boolean {
            val to = to ?: (C.Far - 1)
            val a = oldSets.filter { set -> !set.isEmpty && newSets.indexOf(set) < 0 }
            val b = newSets.filter { set -> !set.isEmpty && oldSets.indexOf(set) < 0 }
            if (a.size != b.size) return false
            if (a.isEmpty()) return true
            val sharedChunks = findSharedChunks(a, b)
            val sideA = SpanCursor(a, sharedChunks, 0).goto(from)
            val sideB = SpanCursor(b, sharedChunks, 0).goto(from)
            while (true) {
                if (sideA.to != sideB.to ||
                    !sameValues(sideA.active, sideB.active) ||
                    sideA.point == sideB.point
                )
                    return false
                if (sideA.to > to) return true
                sideA.next(); sideB.next()
            }
        }

        /// Iterate over a group of range sets at the same time, notifying
        /// the iterator about the ranges covering every given piece of
        /// content. Returns the open count (see
        /// [`SpanIterator.span`](#state.SpanIterator.span)) at the end
        /// of the iteration.
        fun <T : RangeValue> spans(
            sets: List<RangeSet<T>>, from: Int, to: Int,
            iterator: SpanIterator<T>,
            /// When given and greater than -1, only points of at least this
            /// size are taken into account.
            minPointSize: Int = -1
        ): Int {
            val cursor = SpanCursor(sets, null, minPointSize).goto(from)
            var pos = from
            var openRanges = cursor.openStart
            while (true) {
                val curTo = min(cursor.to, to)
                val point = cursor.point
                if (point != null) {
                    val active = cursor.activeForPoint(cursor.to)
                    val openCount = if (cursor.pointFrom < from) active.size + 1
                    else if (point.startSide < 0) active.size
                    else min(active.size, openRanges)
                    iterator.point(
                        pos,
                        curTo,
                        point,
                        active,
                        openCount,
                        cursor.pointRank
                    )
                    openRanges = min(cursor.openEnd(curTo), active.size)
                } else if (curTo > pos) {
                    iterator.span(pos, curTo, cursor.active, openRanges)
                    openRanges = cursor.openEnd(curTo)
                }
                if (cursor.to > to) {
                    return openRanges + if (point != null && cursor.to > to) 1 else 0
                }
                pos = cursor.to
                cursor.next()
            }
        }

        /// Create a range set for the given range or array of ranges. By
        /// default, this expects the ranges to be _sorted_ (by start
        /// position and, if two start at the same position,
        /// `value.startSide`). You can pass `true` as second argument to
        /// cause the method to sort them.
        fun <T : RangeValue> of(
            ranges: SingleOrList<Range<out T>>,
            sort: Boolean = false
        ): RangeSet<T> {
            val build = RangeSetBuilder<T>()
            ranges.list.let(::lazySort).forEach { range ->
                build.add(range.from, range.to, range.value)
            }
            return build.finish()
        }

        /// Join an array of range sets into a single set.
        fun <T : RangeValue> join(sets: List<RangeSet<T>>): RangeSet<T> {
            if (sets.size == 0) return RangeSet.empty()
            var result = sets[sets.size - 1]
            for (i in sets.indices.reversed().drop(1)) {
                var layer = sets[i]
                while (layer != empty<T>()) {
                    result = RangeSet(
                        layer.chunkPos,
                        layer.chunk,
                        result,
                        max(layer.maxPoint, result.maxPoint)
                    )
                    layer = layer.nextLayer
                }
            }
            return result
        }

        private val singleEmpty = RangeSet<Nothing>(emptyList(), emptyList(), null, -1)

        /// The empty set of ranges.
        @Suppress("UNCHECKED_CAST")
        fun <T : RangeValue> empty(): RangeSet<T> = singleEmpty as RangeSet<T>
    }
}

internal fun <T : RangeValue> lazySort(ranges: List<Range<out T>>): List<Range<out T>> {
    if (ranges.size > 1) {
        var prev = ranges.first()
        if (ranges.zipWithNext().any { (a, b) -> cmpRange(a, b) }) {
            return ranges.sortedWith { a, b -> if (cmpRange(a, b)) 1 else -1 }
        }
    }
    return ranges
}

// Awkward patch-up to create a cyclic structure.
//;(RangeSet.empty as any).nextLayer = RangeSet.empty

/// A range set builder is a data structure that helps build up a
/// [range set](#state.RangeSet) directly, without first allocating
/// an array of [`Range`](#state.Range) objects.
class RangeSetBuilder<T : RangeValue> {
    private val chunks = mutableListOf<Chunk<T>>()
    private val chunkPos = mutableListOf<Int>()
    private var chunkStart = -1
    private var last: T? = null
    private var lastFrom = -C.Far
    private var lastTo = -C.Far
    private var from = mutableListOf<Int>()
    private var to = mutableListOf<Int>()
    private var value = mutableListOf<T>()
    private var maxPoint = -1
    private var setMaxPoint = -1
    private var nextLayer: RangeSetBuilder<T>? = null

    private fun finishChunk(newArrays: Boolean) {
        this.chunks.add(Chunk(this.from, this.to, this.value, this.maxPoint))
        this.chunkPos.add(this.chunkStart)
        this.chunkStart = -1
        this.setMaxPoint = max(this.setMaxPoint, this.maxPoint)
        this.maxPoint = -1
        if (newArrays) {
            this.from.clear()
            this.to.clear()
            this.value.clear()
        }
    }


    /// Add a range. Ranges should be added in sorted (by `from` and
    /// `value.startSide`) order.
    fun add(from: Int, to: Int, value: T) {
        if (!this.addInner(from, to, value)) {
            nextLayer = (nextLayer ?: RangeSetBuilder()).also { it.add(from, to, value) }
        }
    }

    /// @internal
    internal fun addInner(from: Int, to: Int, value: T): Boolean {
        val diff = (from - this.lastTo) or (value.startSide - this.last!!.endSide)
        if (diff <= 0 && ((from - this.lastFrom) or (value.startSide - this.last!!.startSide)) < 0) {
            throw Error("Ranges must be added sorted by `from` position and `startSide`")
        }
        if (diff < 0) return false
        if (this.from.size == C.ChunkSize) this.finishChunk(true)
        if (this.chunkStart < 0) this.chunkStart = from
        this.from.add(from - this.chunkStart)
        this.to.add(to - this.chunkStart)
        this.last = value
        this.lastFrom = from
        this.lastTo = to
        this.value.add(value)
        if (value.point) this.maxPoint = max(this.maxPoint, to - from)
        return true
    }

    /// @internal
    internal fun addChunk(from: Int, chunk: Chunk<T>): Boolean {
        if (((from - this.lastTo) or (chunk.value[0].startSide - this.last!!.endSide)) < 0) {
            return false
        }
        if (this.from.size != 0) this.finishChunk(true)
        this.setMaxPoint = max(this.setMaxPoint, chunk.maxPoint)
        this.chunks.add(chunk)
        this.chunkPos.add(from)
        val last = chunk.value.size - 1
        this.last = chunk.value[last]
        this.lastFrom = chunk.from[last] + from
        this.lastTo = chunk.to[last] + from
        return true
    }

    /// Finish the range set. Returns the new set. The builder can't be
    /// used anymore after this has been called.
    fun finish(): RangeSet<T> {
        return this.finishInner(RangeSet.empty())
    }

    /// @internal
    internal fun finishInner(next: RangeSet<T>): RangeSet<T> {
        if (this.from.size != 0) this.finishChunk(false)
        if (this.chunks.size == 0) return next
        val result = RangeSet.create(
            this.chunkPos,
            this.chunks,
            this.nextLayer?.finishInner(next) ?: next, this.setMaxPoint
        )
        // TODO: Add this sealing somehow
//        this.from = null as any // Make sure further `add` calls produce errors
        return result
    }
}

internal fun <T : RangeValue> findSharedChunks(
    a: List<RangeSet<T>>,
    b: List<RangeSet<T>>,
    textDiff: ChangeDesc? = null
): Set<Chunk<T>> {
    val inA = mutableMapOf<Chunk<*>, Int>()
    a.forEach { set ->
        set.chunk.indices.forEach { i ->
            inA[set.chunk[i]] = set.chunkPos[i]
        }
    }
    val shared = mutableSetOf<Chunk<T>>()
    b.forEach { set ->
        set.chunk.indices.forEach { i ->
            val known = inA[set.chunk[i]] ?: return@forEach
            if ((textDiff?.mapPos(known) ?: known) == set.chunkPos[i] &&
                textDiff?.touchesRange(known, known + set.chunk[i].length) != true
            ) {
                shared.add(set.chunk[i])
            }
        }
    }
    return shared
}

sealed interface Cursor<T> {
    var from: Int
    var to: Int
    var value: T?

    val startSide: Int
    val rank: Int?
        get() = null
    fun goto(pos: Int, side: Int = -C.Far): Cursor<T>
    fun forward(pos: Int, side: Int)
    fun next()
}

class LayerCursor<T : RangeValue>
constructor(
    val layer: RangeSet<T>,
    val skip: Set<Chunk<T>>?,
    val minPoint: Int,
    override val rank: Int = 0
) : Cursor<T> {
    override var from: Int = 0
    override var to: Int = 0
    override var value: T? = null

    var chunkIndex: Int = 0
    @get:JvmName("getRangeIndexVal")
    @set:JvmName("setRangeIndexVal")
    var rangeIndex: Int = 0

    override val startSide: Int
        get() {
            return this.value?.startSide ?: 0
        }
    val endSide: Int
        get() {
            return this.value?.endSide ?: 0
        }

    override fun goto(pos: Int, side: Int): LayerCursor<T> {
        this.chunkIndex = 0
        this.rangeIndex = 0
        this.gotoInner(pos, side, false)
        return this
    }

    fun gotoInner(pos: Int, side: Int, forward: Boolean) {
        var forward = forward
        while (this.chunkIndex < this.layer.chunk.size) {
            val next = this.layer.chunk[this.chunkIndex]
            if (!(this.skip?.contains(next) == true ||
                    this.layer.chunkEnd(this.chunkIndex) < pos ||
                    next.maxPoint < this.minPoint)
            ) break
            this.chunkIndex++
            forward = false
        }
        if (this.chunkIndex < this.layer.chunk.size) {
            val rangeIndex = this.layer.chunk[this.chunkIndex].findIndex(
                pos - this.layer.chunkPos[this.chunkIndex],
                side,
                true
            )
            if (!forward || this.rangeIndex < rangeIndex) this.setRangeIndex(rangeIndex)
        }
        this.next()
    }

    override fun forward(pos: Int, side: Int) {
        if (((this.to - pos) or (this.endSide - side)) < 0)
            this.gotoInner(pos, side, true)
    }

    override fun next() {
        while (true) {
            if (this.chunkIndex == this.layer.chunk.size) {
                this.from = C.Far
                this.to = C.Far
                this.value = null
                break
            } else {
                val chunkPos = this.layer.chunkPos[this.chunkIndex]
                val chunk = this.layer.chunk[this.chunkIndex]
                val from = chunkPos + chunk.from[this.rangeIndex]
                this.from = from
                this.to = chunkPos + chunk.to[this.rangeIndex]
                this.value = chunk.value[this.rangeIndex]
                this.setRangeIndex(this.rangeIndex + 1)
                if (this.minPoint < 0 || this.value!!.point && this.to - this.from >= this.minPoint) break
            }
        }
    }

    fun setRangeIndex(index: Int) {
        if (index == this.layer.chunk[this.chunkIndex].value.size) {
            this.chunkIndex++
            if (this.skip != null) {
                while (this.chunkIndex < this.layer.chunk.size && this.skip!!.contains(this.layer.chunk[this.chunkIndex]))
                    this.chunkIndex++
            }
            this.rangeIndex = 0
        } else {
            this.rangeIndex = index
        }
    }

    fun nextChunk() {
        this.chunkIndex++
        this.rangeIndex = 0
        this.next()
    }

    fun compare(other: LayerCursor<T>): Int {
        return (this.from - other.from) or (this.startSide - other.startSide) or (this.rank - other.rank) or
            (this.to - other.to) or (this.endSide - other.endSide)
    }
}

class HeapCursor<T : RangeValue>(val heap: MutableList<LayerCursor<T>>) : Cursor<T> {

    override var from: Int = 0
    override var to: Int = 0
    override var value: T? = null
    override var rank: Int = 0

    override val startSide: Int
        get() {
            return this.value?.startSide ?: 0
        }

    override fun goto(pos: Int, side: Int): HeapCursor<T> {
        this.heap.forEach { it.goto(pos, side) }
        for (i in heap.size / 2 downTo 0) heapBubble(this.heap, i)
        this.next()
        return this
    }

    override fun forward(pos: Int, side: Int) {
        this.heap.forEach { cur -> cur.forward(pos, side) }
        for (i in heap.size / 2 downTo 0)
            heapBubble(this.heap, i)
        if (((this.to - pos) or (this.value!!.endSide - side)) < 0) this.next()
    }

    override fun next() {
        if (this.heap.isEmpty()) {
            this.from = C.Far
            this.to = C.Far
            this.value = null
            this.rank = -1
        } else {
            val top = this.heap[0]
            this.from = top.from
            this.to = top.to
            this.value = top.value
            this.rank = top.rank
            if (top.value != null) top.next()
            heapBubble(this.heap, 0)
        }
    }

    companion object {

        fun <T : RangeValue> from(
            sets: List<RangeSet<T>>,
            skip: Set<Chunk<T>>? = null,
            minPoint: Int = -1
        ): Cursor<T> {
            val heap = mutableListOf<LayerCursor<T>>()
            for (i in sets.indices) {
                var cur = sets[i]
                while (!cur.isEmpty) {
                    if (cur.maxPoint >= minPoint)
                        heap.add(LayerCursor(cur, skip, minPoint, i))
                    cur = cur.nextLayer
                }
            }
            return heap.singleOrNull() ?: HeapCursor(heap)
        }
    }

}

internal fun <T : RangeValue> heapBubble(heap: MutableList<LayerCursor<T>>, index: Int) {
    var cur = heap[index]
    var index = index
    while (true) {
        var childIndex = (index shl 1) + 1
        if (childIndex >= heap.size) break
        var child = heap[childIndex]
        if (childIndex + 1 < heap.size && child.compare(heap[childIndex + 1]) >= 0) {
            child = heap[childIndex + 1]
            childIndex++
        }
        if (cur.compare(child) < 0) break
        heap[childIndex] = cur
        heap[index] = child
        index = childIndex
    }
}

class SpanCursor<T : RangeValue>(
    val sets: List<RangeSet<T>>,
    val skip: Set<Chunk<T>>?,
    val minPoint: Int
) {
    val cursor: Cursor<T> = HeapCursor.from(sets, skip, minPoint)

    val active = mutableListOf<T>()
    val activeTo = mutableListOf<Int>()
    val activeRank = mutableListOf<Int>()
    var minActive = -1

    // A currently active point range, if any
    var point: T? = null
    var pointFrom = 0
    var pointRank = 0

    var to = -C.Far
    var endSide = 0

    // The amount of open active ranges at the start of the iterator.
    // Not including points.
    var openStart = -1

    fun goto(pos: Int, side: Int = -C.Far): SpanCursor<T> {
        this.cursor.goto(pos, side)
        this.active.clear()
        this.activeTo.clear()
        this.activeRank.clear()
        this.minActive = -1
        this.to = pos
        this.endSide = side
        this.openStart = -1
        this.next()
        return this
    }

    fun forward(pos: Int, side: Int) {
        while (this.minActive > -1 && ((this.activeTo[this.minActive] - pos) or (this.active[this.minActive].endSide - side)) < 0) {
            this.removeActive(this.minActive)
        }
        this.cursor.forward(pos, side)
    }

    fun removeActive(index: Int) {
        remove(this.active, index)
        remove(this.activeTo, index)
        remove(this.activeRank, index)
        this.minActive = findMinIndex(this.active, this.activeTo)
    }

    fun addActive(trackOpen: MutableList<Int>?) {
        var i = 0
        val value = this.cursor.value!!
        val to = this.cursor.to
        val rank = this.cursor.rank!!
        // Organize active marks by rank first, then by size
        while (i < this.activeRank.size && ((rank - this.activeRank[i]) or (to - this.activeTo[i])) > 0) i++
        insert(this.active, i, value)
        insert(this.activeTo, i, to)
        insert(this.activeRank, i, rank)
        trackOpen?.add(i, this.cursor.from)
        this.minActive = findMinIndex(this.active, this.activeTo)
    }

    // After calling this, if `this.point` != null, the next range is a
    // point. Otherwise, it's a regular range, covered by `this.active`.
    fun next() {
        val from = this.to
        val wasPoint = this.point
        this.point = null
        val trackOpen = if (this.openStart < 0) mutableListOf<Int>() else null
        while (true) {
            val a = this.minActive
            if (a > -1 && ((this.activeTo[a] - this.cursor.from) or (this.active[a].endSide - this.cursor.startSide)) < 0) {
                if (this.activeTo[a] > from) {
                    this.to = this.activeTo[a]
                    this.endSide = this.active[a].endSide
                    break
                }
                this.removeActive(a)
                trackOpen?.removeAt(a)
            } else if (this.cursor.value == null) {
                this.to = C.Far
                this.endSide = C.Far
                break
            } else if (this.cursor.from > from) {
                this.to = this.cursor.from
                this.endSide = this.cursor.startSide
                break
            } else {
                val nextVal = this.cursor.value
                if (!nextVal!!.point) { // Opening a range
                    this.addActive(trackOpen)
                    this.cursor.next()
                } else if (wasPoint != null && this.cursor.to == this.to && this.cursor.from < this.cursor.to) {
                    // Ignore any non-empty points that end precisely at the end of the prev point
                    this.cursor.next()
                } else { // New point
                    this.point = nextVal
                    this.pointFrom = this.cursor.from
                    this.pointRank = this.cursor.rank!!
                    this.to = this.cursor.to
                    this.endSide = nextVal.endSide
                    this.cursor.next()
                    this.forward(this.to, this.endSide)
                    break
                }
            }
        }
        trackOpen?.reversed()?.takeWhile { it < from }?.size?.let {
            this.openStart = it
        }
    }

    fun activeForPoint(to: Int): List<T> {
        if (this.active.size == 0) return this.active
        val active = mutableListOf<T>()
        for (i in active.indices.reversed()) {
            if (this.activeRank[i] < this.pointRank) break
            if (this.activeTo[i] > to || this.activeTo[i] == to && this.active[i].endSide >= this.point!!.endSide) {
                active.add(this.active[i])
            }
        }
        return active.reversed()
    }

    fun openEnd(to: Int): Int {
//        var open = 0
//        for (i in activeTo.indices.reversed()) {
//            if (activeTo[i] < to) break
//            open++
//        }
//        return open
        return activeTo.reversed().takeWhile { it < to }.size
    }
}

internal fun <T : RangeValue> compare(
    a: SpanCursor<T>, startA: Int,
    b: SpanCursor<T>, startB: Int,
    length: Int,
    comparator: RangeComparator<T>
) {
    a.goto(startA)
    b.goto(startB)
    var endB = startB + length
    var pos = startB
    var dPos = startB - startA
    while (true) {
        val diff = ((a.to + dPos) - b.to) or (a.endSide - b.endSide)
        val end = if (diff < 0) a.to + dPos else b.to
        val clipEnd = min(end, endB)
        if (a.point != null || b.point != null) {
            if (!(a.point != null && b.point != null && (a.point === b.point || a.point!!.eq(b.point!!)) &&
                    sameValues(a.activeForPoint(a.to), b.activeForPoint(b.to)))
            )
                comparator.comparePoint(pos, clipEnd, a.point, b.point)
        } else {
            if (clipEnd > pos && !sameValues(a.active, b.active)) comparator.compareRange(
                pos,
                clipEnd,
                a.active,
                b.active
            )
        }
        if (end > endB) break
        pos = end
        if (diff <= 0) a.next()
        if (diff >= 0) b.next()
    }
}

internal fun <T : RangeValue> sameValues(a: List<T>, b: List<T>): Boolean {
    return a == b
}

internal fun <T> remove(array: MutableList<T>, index: Int) {
    array.removeAt(index)
}

internal fun <T> insert(array: MutableList<T>, index: Int, value: T) {
    array.add(index, value)
}

internal fun findMinIndex(value: List<RangeValue>, array: List<Int>): Int {
    var found = -1
    var foundPos = C.Far
    for (i in array.indices) {
        if (((array[i] - foundPos) or (value[i].endSide - value[found].endSide)) < 0) {
            found = i
            foundPos = array[i]
        }
    }
    return found
}

internal infix fun Int?.or(other: Int): Int {
    return this?.takeIf { it != 0 } ?: other
}
