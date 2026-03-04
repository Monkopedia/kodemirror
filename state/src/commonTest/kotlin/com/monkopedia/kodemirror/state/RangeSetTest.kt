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

import kotlin.math.ceil
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class Value(
    override val startSide: Int = 1,
    override val endSide: Int,
    override val point: Boolean,
    val name: String? = null,
    val pos: Int? = null
) : RangeValue() {
    override fun eq(other: RangeValue): Boolean {
        return other is Value && other.name == name
    }

    companion object {
        fun names(v: List<Value>): String {
            val result = mutableListOf<String>()
            for (value in v) {
                if (value.name != null || value.point) {
                    result.add(value.name ?: "POINT")
                }
            }
            return result.joinToString("/")
        }
    }
}

private fun cmp(a: Range<Value>, b: Range<Value>): Int = a.from - b.from

private fun mk(from: Int, to: Int, spec: Value): Range<Value> = spec.range(from, to) as Range<Value>

private fun mk(from: Int, to: Int, name: String): Range<Value> =
    mk(from, to, Value(endSide = if (from == to) 1 else -1, point = from == to, name = name))

private fun mk(from: Int, to: Int, spec: Map<String, Any?>): Range<Value> {
    val startSide = (spec["startSide"] as? Int) ?: 1
    val endSide = (spec["endSide"] as? Int) ?: (if (from == to) 1 else -1)
    val point = if (from == to) true else (spec["point"] as? Boolean) ?: false
    val name = spec["name"] as? String
    val pos = spec["pos"] as? Int
    return mk(
        from,
        to,
        Value(startSide = startSide, endSide = endSide, point = point, name = name, pos = pos)
    )
}

private fun mk(from: Int, to: Int): Range<Value> =
    mk(from, to, Value(endSide = if (from == to) 1 else -1, point = from == to))

private fun mk(from: Int): Range<Value> = mk(from, from)

private fun mk(from: Int, spec: String): Range<Value> = mk(from, from, spec)

private fun mk(from: Int, spec: Map<String, Any?>): Range<Value> = mk(from, from, spec)

private fun mkSet(ranges: List<Range<Value>>): RangeSet<Value> = RangeSet.of(ranges)

private fun changeSet(changes: List<Triple<Int, Int, Int>>): ChangeSet {
    val collect = mutableListOf<ChangeSpec>()
    for ((from, to, len) in changes) {
        if (len > 0) {
            collect.add(
                ChangeSpec.Single(
                    from = from,
                    to = to,
                    insert = InsertContent.StringContent("x".repeat(len))
                )
            )
        }
        if (from < to) collect.add(ChangeSpec.Single(from = from, to = to))
    }
    return ChangeSet.of(ChangeSpec.Multi(collect), 5100)
}

private val smallRanges: List<Range<Value>> = buildList {
    for (i in 0 until 5000) {
        add(mk(i, i + 1 + (i % 4), mapOf("pos" to i)))
    }
}

@Suppress("ktlint:standard:property-naming")
private var _set0: RangeSet<Value>? = null
private fun set0(): RangeSet<Value> {
    if (_set0 == null) _set0 = mkSet(smallRanges)
    return _set0!!
}

private fun checkSet(set: RangeSet<Value>) {
    var count = 0
    set.between(0, set.length) { from, _, value ->
        count++
        if (value.pos != null) assertEquals(value.pos, from)
        null
    }
    assertEquals(set.size, count)
}

class RangeSetTest {

    @Test
    fun dividesASetIntoChunksAndLayers() {
        val set = mkSet(
            (
                smallRanges + listOf(
                    mk(1000, 4000, mapOf("pos" to 1000)),
                    mk(2000, 3000, mapOf("pos" to 2000))
                )
                ).sortedWith(::cmp)
        )
        assertEquals(5002, set.size)
        assertTrue(set.chunk.size > 1)
        assertTrue(set.nextLayer.size > 0)
        checkSet(set)
    }

    @Test
    fun complainsAboutMisorderedRanges() {
        assertFailsWith<Exception> { mkSet(listOf(mk(8, 9), mk(7, 10))) }
        assertFailsWith<Exception> {
            mkSet(
                listOf(
                    mk(1, 1, mapOf("startSide" to 1)),
                    mk(1, 1, mapOf("startSide" to -1))
                )
            )
        }
    }

    // update

    @Test
    fun updateCanAddRanges() {
        val set = set0().update(RangeSetUpdate(add = listOf(mk(4000, mapOf("pos" to 4000)))))
        assertEquals(5001, set.size)
        assertSame(set0().chunk[0], set.chunk[0])
    }

    @Test
    fun updateCanAddALargeAmountOfRanges() {
        val ranges = mutableListOf<Range<Value>>()
        for (i in 0 until 4000 step 2) ranges.add(mk(i))
        val set = set0().update(RangeSetUpdate(add = ranges))
        assertEquals(2000 + set0().size, set.size)
        checkSet(set)
    }

    @Test
    fun updateCanFilterRanges() {
        val set = set0().update(RangeSetUpdate(filter = { from, _, _ -> from >= 2500 }))
        assertEquals(2500, set.size)
        assertTrue(set.chunk.size < set0().chunk.size)
        checkSet(set)
    }

    @Test
    fun updateCanFilterAllOver() {
        val set = set0().update(RangeSetUpdate(filter = { from, _, _ -> (from % 200) >= 100 }))
        assertEquals(2500, set.size)
        checkSet(set)
    }

    @Test
    fun updateCollapsesTheChunksWhenRemovingAlmostAllRanges() {
        val set = set0().update(
            RangeSetUpdate(filter = { from, _, _ -> from == 500 || from == 501 })
        )
        assertEquals(2, set.size)
        assertEquals(1, set.chunk.size)
    }

    @Test
    fun updateCallsFilterOnPreciselyThoseRangesTouchingTheFilterRange() {
        val ranges = mutableListOf<Range<Value>>()
        for (i in 0 until 1000) ranges.add(mk(i, i + 1, mapOf("pos" to i)))
        val set = mkSet(ranges)
        val called = mutableListOf<Pair<Int, Int>>()
        set.update(
            RangeSetUpdate(
                filter = { from, to, _ ->
                    called.add(Pair(from, to))
                    true
                },
                filterFrom = 400,
                filterTo = 600
            )
        )
        assertEquals(202, called.size)
        for (i in 399..600) {
            val j = i - 399
            assertEquals("$i,${i + 1}", "${called[j].first},${called[j].second}")
        }
    }

    @Test
    fun updateReturnsTheEmptySetWhenFilterRemovesEverything() {
        assertSame(
            RangeSet.empty<Value>(),
            set0().update(RangeSetUpdate(filter = { _, _, _ -> false }))
        )
    }

    // map

    private fun mapTest(
        positions: List<Range<Value>>,
        changes: List<Triple<Int, Int, Int>>,
        newPositions: List<Any>
    ) {
        val set = mkSet(positions)
        val mapped = set.map(changeSet(changes))
        val out = mutableListOf<String>()
        var iter = mapped.iter()
        while (iter.value != null) {
            out.add("${iter.from}-${iter.to}")
            iter.next()
        }
        val expected = newPositions.map { p ->
            if (p is List<*>) {
                "${(p[0] as Int)}-${(p[1] as Int)}"
            } else {
                "$p-$p"
            }
        }
        assertEquals(expected.toString(), out.toString())
    }

    @Test
    fun mapCanMapThroughChanges() {
        mapTest(
            listOf(mk(1), mk(4), mk(10)),
            listOf(Triple(0, 0, 1), Triple(2, 3, 0), Triple(8, 8, 20)),
            listOf(2, 4, 30)
        )
    }

    @Test
    fun mapTakesInclusivityIntoAccount() {
        mapTest(
            listOf(mk(1, 2, mapOf("startSide" to -1, "endSide" to 1))),
            listOf(Triple(1, 1, 2), Triple(2, 2, 2)),
            listOf(listOf(1, 6))
        )
    }

    @Test
    fun mapUsesSideToDetermineMappingOfPoints() {
        mapTest(
            listOf(
                mk(1, 1, mapOf("startSide" to -1, "endSide" to -1)),
                mk(1, 1, mapOf("startSide" to 1, "endSide" to 1))
            ),
            listOf(Triple(1, 1, 2)),
            listOf(1, 3)
        )
    }

    @Test
    fun mapDefaultsToExclusiveOnBothSides() {
        mapTest(
            listOf(mk(1, 2)),
            listOf(Triple(1, 1, 2), Triple(4, 4, 2)),
            listOf(listOf(3, 4))
        )
    }

    @Test
    fun mapDropsPointRanges() {
        mapTest(
            listOf(mk(1, 2)),
            listOf(Triple(1, 2, 0), Triple(1, 1, 1)),
            emptyList()
        )
    }

    @Test
    fun mapDropsRangesInDeletedRegions() {
        mapTest(
            listOf(mk(1, 2), mk(3)),
            listOf(Triple(0, 4, 0)),
            emptyList()
        )
    }

    @Test
    fun mapShrinksRanges() {
        mapTest(
            listOf(mk(2, 4), mk(2, 8), mk(6, 8)),
            listOf(Triple(3, 7, 0)),
            listOf(listOf(2, 3), listOf(2, 4), listOf(3, 4))
        )
    }

    @Test
    fun mapLeavesPointRangesOnChangeBoundaries() {
        mapTest(
            listOf(mk(2), mk(4)),
            listOf(Triple(2, 4, 6)),
            listOf(2, 8)
        )
    }

    @Test
    fun mapCanCollapseChunks() {
        val smaller = set0().map(changeSet(listOf(Triple(30, 4500, 0))))
        assertTrue(smaller.chunk.size < set0().chunk.size)
        val empty = smaller.map(changeSet(listOf(Triple(0, 1000, 0))))
        assertSame(RangeSet.empty<Value>(), empty)
    }

    // compare

    private class TestComparator : RangeComparator<Value> {
        val ranges = mutableListOf<Int>()
        private fun addRange(from: Int, to: Int) {
            if (ranges.isNotEmpty() && ranges.last() == from) {
                ranges[ranges.size - 1] = to
            } else {
                ranges.add(from)
                ranges.add(to)
            }
        }

        override fun compareRange(from: Int, to: Int, activeA: List<Value>, activeB: List<Value>) =
            addRange(from, to)

        override fun comparePoint(from: Int, to: Int, pointA: Value?, pointB: Value?) =
            addRange(from, to)
    }

    private fun compareTest(
        ranges: Any,
        changes: List<Triple<Int, Int, Int>>? = null,
        filter: ((Int, Int, Value) -> Boolean)? = null,
        add: List<Range<Value>>? = null,
        expected: List<Int>
    ) {
        val set = if (ranges is RangeSet<*>) {
            @Suppress("UNCHECKED_CAST")
            ranges as RangeSet<Value>
        } else {
            @Suppress("UNCHECKED_CAST")
            mkSet(ranges as List<Range<Value>>)
        }
        var newSet: RangeSet<Value> = set
        val docChanges = changeSet(changes ?: emptyList())
        if (changes != null) newSet = newSet.map(docChanges)
        if (filter != null || add != null) {
            newSet = newSet.update(
                RangeSetUpdate(
                    add = add ?: emptyList(),
                    filter = filter
                )
            )
        }
        val comp = TestComparator()
        RangeSet.compare(listOf(set), listOf(newSet), docChanges, comp)
        assertEquals(expected.toString(), comp.ranges.toString())
    }

    @Test
    fun compareNoticesAddedRanges() {
        compareTest(
            ranges = listOf(mk(2, 4, "a"), mk(8, 11, "a")),
            add = listOf(mk(3, 9, "b"), mk(106, 107, "b")),
            expected = listOf(3, 9, 106, 107)
        )
    }

    @Test
    fun compareNoticesDeletedRanges() {
        compareTest(
            ranges = listOf(mk(4, 6, "a"), mk(5, 7, "b"), mk(6, 8, "c"), mk(20, 30, "d")),
            filter = { from, _, _ -> from != 5 && from != 20 },
            expected = listOf(5, 7, 20, 30)
        )
    }

    @Test
    fun compareRecognizesIdenticalRanges() {
        compareTest(
            ranges = listOf(mk(0, 50, "a")),
            add = listOf(mk(10, 40, "a")),
            filter = { _, _, _ -> false },
            expected = listOf(0, 10, 40, 50)
        )
    }

    @Test
    fun compareSkipsChanges() {
        compareTest(
            ranges = listOf(mk(0, 20, "a")),
            changes = listOf(Triple(5, 15, 20)),
            filter = { _, _, _ -> false },
            expected = listOf(0, 5, 25, 30)
        )
    }

    @Test
    fun compareIgnoresIdenticalSubNodes() {
        // The original TS test uses Object.defineProperty to make accessing
        // chunk values throw. This is a JS-specific trick that can't be
        // replicated in Kotlin. We skip the "prepare" step but still validate
        // that the comparison reports the correct ranges.
        val ranges = mutableListOf<Range<Value>>()
        for (i in 0 until 1000) ranges.add(mk(i, i + 1, "a"))
        val set = mkSet(ranges)
        var newSet = set.map(changeSet(listOf(Triple(900, 1000, 0))))
        newSet = newSet.update(RangeSetUpdate(add = listOf(mk(850, 860, "b"))))
        val comp = TestComparator()
        RangeSet.compare(listOf(set), listOf(newSet), changeSet(listOf(Triple(900, 1000, 0))), comp)
        assertEquals(listOf(850, 860).toString(), comp.ranges.toString())
    }

    @Test
    fun compareIgnoresChangesInPoints() {
        val ranges = mutableListOf<Range<Value>>()
        ranges.add(mk(3, 997, mapOf("point" to true)))
        for (i in 0 until 1000 step 2) ranges.add(mk(i, i + 1, "a"))
        val set = mkSet(ranges.sortedWith(::cmp))
        compareTest(
            ranges = set,
            changes = listOf(Triple(300, 500, 100)),
            expected = emptyList()
        )
    }

    @Test
    fun compareNoticesAddingAPoint() {
        compareTest(
            ranges = listOf(mk(3, 50, mapOf("point" to true))),
            add = listOf(mk(40, 80, mapOf("point" to true))),
            expected = listOf(50, 80)
        )
    }

    @Test
    fun compareNoticesRemovingAPoint() {
        compareTest(
            ranges = listOf(mk(3, 50, mapOf("point" to true))),
            filter = { _, _, _ -> false },
            expected = listOf(3, 50)
        )
    }

    @Test
    fun compareCanHandleMultipleChanges() {
        val ranges = mutableListOf<Range<Value>>()
        for (i in 0 until 200 step 2) {
            val end = i + 1 + ceil(i / 50.0).toInt()
            ranges.add(mk(i, end, "$i-$end"))
        }
        compareTest(
            ranges = ranges,
            changes = listOf(Triple(0, 0, 50), Triple(50, 100, 0), Triple(150, 200, 0)),
            filter = { from, _, _ -> from % 50 > 0 },
            expected = listOf(50, 51, 100, 103, 148, 153)
        )
    }

    @Test
    fun compareReportsPointDecorationsWithDifferentCover() {
        compareTest(
            ranges = listOf(
                mk(0, 4, mapOf("startSide" to 1, "endSide" to -1)),
                mk(1, 3, mapOf("point" to true, "startSide" to -1, "endSide" to 1))
            ),
            changes = listOf(Triple(2, 4, 0)),
            expected = listOf(1, 2)
        )
    }

    // spans

    private class TestBuilder : SpanIterator<Value> {
        val spans = mutableListOf<String>()
        override fun span(from: Int, to: Int, active: List<Value>, openStart: Int) {
            val name = Value.names(active)
            spans.add("${to - from}${if (name.isNotEmpty()) "=$name" else ""}")
        }

        override fun point(
            from: Int,
            to: Int,
            value: Value,
            active: List<Value>,
            openStart: Int,
            index: Int
        ) {
            spans.add(
                "${if (to > from) "${to - from}=" else ""}" +
                    "${if (value.name != null) "[${value.name}]" else "ø"}"
            )
        }
    }

    private fun spansTest(set: Any, start: Int, end: Int, expected: String) {
        val builder = TestBuilder()
        val sets = if (set is List<*>) {
            @Suppress("UNCHECKED_CAST")
            set as List<RangeSet<Value>>
        } else {
            listOf(set as RangeSet<Value>)
        }
        RangeSet.spans(sets, start, end, builder)
        assertEquals(expected, builder.spans.joinToString(" "))
    }

    @Test
    fun spansSeparatesTheRangeInCoveringSpans() {
        spansTest(
            mkSet(listOf(mk(3, 8, "one"), mk(5, 8, "two"), mk(10, 12, "three"))),
            0,
            15,
            "3 2=one 3=two/one 2 2=three 3"
        )
    }

    @Test
    fun spansCanRetrieveALimitedRange() {
        val decos = mutableListOf(mk(0, 200, "wide"))
        for (i in 0 until 100) decos.add(mk(i * 2, i * 2 + 2, "span$i"))
        val set = mkSet(decos)
        val start = 20
        val end = start + 6
        val parts = mutableListOf<String>()
        var pos = start
        while (pos < end) {
            val step = if (pos % 2 != 0) 1 else 2
            val segEnd = min(end, pos + step)
            parts.add("${segEnd - pos}=span${pos / 2}/wide")
            pos = segEnd
        }
        val expected = parts.joinToString(" ")
        spansTest(set, start, end, expected)
    }

    @Test
    fun spansReadsFromMultipleSetsAtOnce() {
        val one = mkSet(listOf(mk(2, 3, "x"), mk(5, 10, "y"), mk(10, 12, "z")))
        val two = mkSet(listOf(mk(0, 6, "a"), mk(10, 12, "b")))
        spansTest(listOf(one, two), 0, 12, "2=a 1=x/a 2=a 1=y/a 4=y 2=z/b")
    }

    @Test
    fun spansOrdersActiveRangesByOriginSet() {
        val one = mkSet(listOf(mk(2, 10, "a"), mk(20, 30, "a")))
        val two = mkSet(listOf(mk(3, 4, "b"), mk(8, 12, "b"), mk(18, 22, "b")))
        val three = mkSet(listOf(mk(0, 25, "c")))
        spansTest(
            listOf(one, two, three),
            0,
            25,
            "2=c 1=a/c 1=a/b/c 4=a/c 2=a/b/c 2=b/c 6=c 2=b/c 2=a/b/c 3=a/c"
        )
    }

    @Test
    fun spansDoesNotGetConfusedBySamePlacePoints() {
        spansTest(
            mkSet(listOf(mk(1, "a"), mk(1, "b"), mk(1, "c"))),
            0,
            2,
            "1 [a] [b] [c] 1"
        )
    }

    @Test
    fun spansProperlyResyncsActiveRangesAfterPoints() {
        spansTest(
            mkSet(
                listOf(
                    mk(0, 20, "r1"),
                    mk(1, 10, "r2"),
                    mk(3, 12, mapOf("name" to "p", "point" to true)),
                    mk(4, 8, "r3"),
                    mk(5, 20, "r4")
                )
            ),
            0,
            20,
            "1=r1 2=r2/r1 9=[p] 8=r4/r1"
        )
    }

    @Test
    fun spansDoesNotSplitSpansOnIgnoredRanges() {
        val ranges = mutableListOf<Int>()
        RangeSet.spans(
            listOf(
                mkSet(listOf(mk(0, 10, "a"), mk(20, 30, mapOf("name" to "b", "point" to true))))
            ),
            0,
            30,
            object : SpanIterator<Value> {
                override fun span(from: Int, to: Int, active: List<Value>, openStart: Int) {
                    ranges.add(from)
                    ranges.add(to)
                }

                override fun point(
                    from: Int,
                    to: Int,
                    value: Value,
                    active: List<Value>,
                    openStart: Int,
                    index: Int
                ) {
                    ranges.add(from)
                    ranges.add(to)
                }
            },
            0
        )
        assertEquals("0,20,20,30", ranges.joinToString(","))
    }

    @Test
    fun spansOmitsPointsThatAreCoveredByThePreviousPoint() {
        var points = 0
        RangeSet.spans(
            listOf(
                mkSet(
                    listOf(
                        mk(0, 4, mapOf("point" to true)),
                        mk(1, 5, "a"),
                        mk(2, 4, mapOf("point" to true))
                    )
                )
            ),
            0,
            10,
            object : SpanIterator<Value> {
                override fun span(from: Int, to: Int, active: List<Value>, openStart: Int) {}
                override fun point(
                    from: Int,
                    to: Int,
                    value: Value,
                    active: List<Value>,
                    openStart: Int,
                    index: Int
                ) {
                    points++
                }
            }
        )
        assertEquals(1, points)
    }

    @Test
    fun spansPutsSmallerSpansInsideBiggerOnesWithTheSameRank() {
        spansTest(
            mkSet(
                listOf(
                    mk(0, 3, mapOf("name" to "x")),
                    mk(0, 1, mapOf("name" to "a")),
                    mk(1, 2, mapOf("name" to "b")),
                    mk(2, 3, mapOf("name" to "c"))
                )
            ),
            0,
            3,
            "1=a/x 1=b/x 1=c/x"
        )
    }

    // iter

    @Test
    fun iterIteratesOverRanges() {
        val set = mkSet(
            (
                smallRanges + listOf(
                    mk(1000, 4000, mapOf("pos" to 1000)),
                    mk(2000, 3000, mapOf("pos" to 2000))
                )
                ).sortedWith(::cmp)
        )
        var count = 0
        val iter = set.iter()
        while (iter.value != null) {
            val expectedFrom = when {
                count > 2001 -> count - 2
                count > 1000 -> count - 1
                else -> count
            }
            assertEquals(expectedFrom, iter.from)
            iter.next()
            count++
        }
        assertEquals(5002, count)
    }

    @Test
    fun iterCanIterateOverASubset() {
        var count = 0
        val iter = set0().iter(1000)
        while (iter.value != null) {
            if (iter.from > 2000) break
            assertEquals(iter.from + 1 + (iter.from % 4), iter.to)
            iter.next()
            count++
        }
        assertEquals(1003, count)
    }

    // between

    @Test
    fun betweenIteratesOverRanges() {
        var found = 0
        set0().between(100, 200) { from, to, _ ->
            assertEquals(from + 1 + (from % 4), to)
            assertTrue(to >= 100)
            assertTrue(from <= 200)
            found++
            null
        }
        assertEquals(103, found)
    }

    @Test
    fun betweenReturnsRangesInAZeroLengthSet() {
        val set = RangeSet.of(mk(0, 0))
        val found = mutableListOf<Int>()
        set.between(0, 0) { from, to, _ ->
            found.add(from)
            found.add(to)
            null
        }
        assertEquals("0,0", found.joinToString(","))
    }
}
