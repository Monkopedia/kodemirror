package com.monkopedia.kodemirror.state

import com.monkopedia.kodemirror.state.RangeValue.Companion.range
import com.monkopedia.kodemirror.state.SingleOrList.Companion.list
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

object RangeSetTest {
//    import {ChangeSet, Range, RangeSet, RangeValue, RangeComparator, SpanIterator} from "@codemirror/state"
//    import ist from "ist"

    data class Spec(
        val startSide: Int? = null,
        val endSide: Int? = null,
        val point: Boolean? = null,
        val name: String? = null,
        val pos: Int? = null
    )

    class Value(
        override val startSide: Int,
        override val endSide: Int,
        override val point: Boolean,
        val name: String? = null,
        val pos: Int? = null
    ) : RangeValue {
        constructor(spec: Spec = Spec(), empty: Boolean = false) : this(
            startSide = spec.startSide ?: 1,
            endSide = spec.endSide ?: (if (empty) 1 else -1),
            point = empty || spec.point == true,
            name = spec.name,
            pos = spec.pos
        )

        override fun equals(other: Any?): Boolean =
            (other as? Value)?.let { it.name == name } ?: false

        override fun toString(): String = name ?: "POINT"

        companion object {
            fun names(v: List<Value>): String = v.filter { it.name != null || it.point }
                .joinToString("/") { it.name ?: "POINT" }
        }
    }

    operator fun Range<Value>.compareTo(other: Range<Value>): Int = from - other.from

    fun mk(from: Int, to: Any? = null, spec: Any? = null): Range<Value> {
        if (to !is Int) {
            return mk(
                from = from,
                to = from,
                spec = to
            )
        }
        if (spec is String) {
            return mk(from = from, to = to, spec = Spec(name = spec))
        }
        return Value(spec as? Spec ?: Spec(), from == to).range(from, to)
    }

    fun mkSet(ranges: List<Range<Value>>): RangeSet<Value> = RangeSet.of<Value>(ranges.list)

    fun changeSet(changes: List<List<Int>>): ChangeSet {
        val collect = changes.mapNotNull { (from, to, len) ->
            if (len != 0) {
                ChangeSpecData(from, to, insert = "x".repeat(len).asText)
            } else if (from < to) {
                ChangeSpecData(from, to)
            } else {
                null
            }
        }
        return ChangeSet.of(collect.asSpec, 5100).also {
            println("Changes: $it")
        }
    }

    val smallRanges = List(5000) { i ->
        mk(i, i + 1 + (i.mod(4)), Spec(pos = i))
    }
    val _set0: RangeSet<Value> by lazy {
        mkSet(smallRanges)
    }

    fun set0(): RangeSet<Value> = _set0

    fun checkSet(set: RangeSet<Value>) {
        var count = 0
        set.between(0, set.length) { from, _, value ->
            count++
            if (value.pos != null) assertEquals(from, value.pos)
            true
        }
        assertEquals(count, set.size)
    }

    class range_set {
        @Test
        fun divides_a_set_into_chunks_and_layers() {
            val set = mkSet(
                (
                    smallRanges +
                        listOf(
                            mk(1000, 4000, Spec(pos = 1000)),
                            mk(2000, 3000, Spec(pos = 2000))
                        )
                    ).sortedWith { a, b -> a.compareTo(b) }
            )
            assertEquals(smallRanges.size, 5000)
            assertEquals(set.size, 5002)
            assertTrue(set.chunk.size > 1)
            assertTrue(set.nextLayer.size != 0)
            checkSet(set)
        }

        @Test
        fun complains_about_misordered_ranges() {
            assertFails {
                mkSet(listOf(mk(8, 9), mk(7, 10)))
            } // /sorted/
            assertFails {
                mkSet(
                    listOf(
                        mk(1, 1, Spec(startSide = 1)),
                        mk(1, 1, Spec(startSide = -1))
                    )
                )
            } // /sorted/
        }
    }

    class update {
        @Test
        fun can_add_ranges() {
            val set = set0().update(
                RangeSetUpdate(add = listOf(mk(4000, Spec(pos = 4000))))
            )
            assertEquals(set.size, 5001)
            assertEquals(set.chunk.get(0), set0().chunk.get(0))
        }

        @Test
        fun can_add_a_large_amount_of_ranges() {
            val ranges = List(2000) {
                mk(it * 2)
            }
            val set = set0().update(RangeSetUpdate(add = ranges))
            assertEquals(set.size, 2000 + set0().size)
            checkSet(set)
        }

        @Test
        fun can_filter_ranges() {
            val set = set0().update(RangeSetUpdate<Value>(filter = { from, _, _ -> from >= 2500 }))
            assertEquals(set.size, 2500)
            assertTrue(set.chunk.size < set0().chunk.size)
            checkSet(set)
        }

        @Test
        fun can_filter_all_over() {
            val set =
                set0().update(RangeSetUpdate<Value>(filter = { from, _, _ -> (from % 200) >= 100 }))
            assertEquals(set.size, 2500)
            checkSet(set)
        }

        @Test
        fun collapses_the_chunks_when_removing_almost_all_ranges() {
            val set =
                set0().update(
                    RangeSetUpdate<Value>(filter = { from, _, _ ->
                        println("Filter $from")
                        from == 500 ||
                            from == 501
                    })
                )
            assertEquals(set.size, 2)
            assertEquals(set.chunk.size, 1)
        }

        @Test
        fun calls_filter_on_precisely_those_ranges_touching_the_filter_range() {
            val ranges = List(1000) { i ->
                mk(i, i + 1, Spec(pos = i))
            }
            val set = mkSet(ranges)
            val called = mutableListOf<List<Int>>()
            set.update(
                RangeSetUpdate<Value>(
                    filter = { from, to, _ ->
                        called.add(listOf(from, to))
                        true
                    },
                    filterFrom = 400,
                    filterTo = 600
                )
            )
            assertEquals(called.size, 202)
            for (i in 399..600) {
                val j = i - 399
                assertEquals(called.get(j).joinToString(","), "$i,${i + 1}")
            }
        }

        @Test
        fun returns_the_empty_set_when_filter_removes_everything() {
            assertEquals(
                set0().update(RangeSetUpdate<Value>(filter = { _, _, _ -> false })),
                RangeSet.empty()
            )
        }
    }

    class map {
        private fun test(
            positions: List<Range<Value>>,
            changes: List<List<Int>>,
            newPositions: List<Any>
        ) {
            val set = mkSet(positions)
            val mapped = set.map(changeSet(changes))
            val out = mapped.describe()
            assertEquals(
                newPositions.map { p ->
                    if (p is List<*>) {
                        "${p[0]}-${p[1]}"
                    } else {
                        "$p-$p"
                    }
                },
                out
            )
        }

        private fun RangeSet<Value>.describe(): List<String> = buildList {
            val iter = iter()
            while (iter.value != null) {
                add(iter.from.toString() + "-" + iter.to)
                iter.next()
            }
        }

        @Test
        fun can_map_through_changes() {
            test(
                listOf(mk(1), mk(4), mk(10)),
                listOf(listOf(0, 0, 1), listOf(2, 3, 0), listOf(8, 8, 20)),
                listOf(2, 4, 30)
            )
        }

        @Test
        fun takes_inclusivity_into_account() {
            test(
                listOf(mk(1, 2, Spec(startSide = -1, endSide = 1))),
                listOf(listOf(1, 1, 2), listOf(2, 2, 2)),
                listOf(listOf(1, 6))
            )
        }

        @Test
        fun uses_side_to_determine_mapping_of_points() {
            test(
                listOf(
                    mk(1, 1, Spec(startSide = -1, endSide = -1)),
                    mk(1, 1, Spec(startSide = 1, endSide = 1))
                ),
                listOf(
                    listOf(1, 1, 2)
                ),
                listOf(1, 3)

            )
        }

        @Test
        fun defaults_to_exclusive_on_both_sides() {
            test(listOf(mk(1, 2)), listOf(listOf(1, 1, 2), listOf(4, 4, 2)), listOf(listOf(3, 4)))
        }

        @Test
        fun drops_point_ranges() {
            test(listOf(mk(1, 2)), listOf(listOf(1, 2, 0), listOf(1, 1, 1)), listOf())
        }

        @Test
        fun drops_ranges_in_deleted_regions() {
            test(listOf(mk(1, 2), mk(3)), listOf(listOf(0, 4, 0)), listOf())
        }

        @Test
        fun shrinks_ranges() {
            test(
                listOf(mk(2, 4), mk(2, 8), mk(6, 8)),
                listOf(listOf(3, 7, 0)),
                listOf(listOf(2, 3), listOf(2, 4), listOf(3, 4))
            )
        }

        @Test
        fun leaves_point_ranges_on_change_boundaries() {
            test(listOf(mk(2), mk(4)), listOf(listOf(2, 4, 6)), listOf(2, 8))
        }

        @Test
        fun can_collapse_chunks() {
            val smaller = set0().map(changeSet(listOf(listOf(30, 4500, 0))))
            assertTrue(smaller.chunk.size < set0().chunk.size)
            val empty = smaller.map(changeSet(listOf(listOf(0, 1000, 0))))
            assertEquals(empty, RangeSet.empty())
        }
    }

    class Comparator : RangeComparator<Value> {
        val ranges = mutableListOf<Int>()

        private fun addRange(from: Int, to: Int) {
            if (this.ranges.isNotEmpty() && this.ranges.last() == from) {
                this.ranges[this.ranges.size - 1] = to
            } else {
                this.ranges.add(from)
                this.ranges.add(to)
            }
        }

        override fun compareRange(from: Int, to: Int, activeA: List<Value>, activeB: List<Value>) {
            this.addRange(from, to)
        }

        override fun comparePoint(from: Int, to: Int, pointA: Value?, pointB: Value?) {
            println("Point: $from $to")
            this.addRange(from, to)
        }
    }

    class compare {
        data class U(
            val changes: List<List<Int>>? = null,
            val prepare: ((RangeSet<Value>) -> RangeSet<Value>)? = null,
            val add: List<Range<out Value>>? = null,
            val sort: Boolean = false,
            val filter: ((from: Int, to: Int, value: Value) -> Boolean)? = null,
            val filterFrom: Int? = null,
            val filterTo: Int? = null
        ) {
            val asUpdate: RangeSetUpdate<Value>
                get() = RangeSetUpdate(add, sort, filter, filterFrom, filterTo)
        }

        private fun test(ranges: Any, update: U, changes: List<Int>) {
            @Suppress("UNCHECKED_CAST")
            val set = if (ranges is List<*>) {
                mkSet(ranges as List<Range<Value>>)
            } else {
                ranges as RangeSet<Value>
            }
            var newSet = set
            val docChanges = changeSet(update.changes ?: listOf())
            if (update.changes != null) newSet = newSet.map(docChanges)
            if (update.filter != null || update.add != null) newSet = newSet.update(update.asUpdate)
            newSet = update.prepare?.invoke(newSet) ?: newSet
            val comp = Comparator()
            RangeSet.compare(listOf(set), listOf(newSet), docChanges, comp)
            assertEquals(changes, comp.ranges)
        }

        @Test
        fun notices_added_ranges() {
            test(
                listOf(mk(2, 4, "a"), mk(8, 11, "a")),
                U(
                    add = listOf(mk(3, 9, "b"), mk(106, 107, "b"))
                ),
                listOf(3, 9, 106, 107)
            )
        }

        @Test
        fun notices_deleted_ranges() {
            test(
                listOf(mk(4, 6, "a"), mk(5, 7, "b"), mk(6, 8, "c"), mk(20, 30, "d")),
                U(
                    filter = { from, _, _ -> from != 5 && from != 20 }
                ),
                listOf(5, 7, 20, 30)
            )
        }

        @Test
        fun recognizes_identical_ranges() {
            test(
                listOf(mk(0, 50, "a")),
                U(
                    add = listOf(mk(10, 40, "a")),
                    filter = { _, _, _ -> false }
                ),
                listOf(0, 10, 40, 50)
            )
        }

        @Test
        fun skips_changes() {
            test(
                listOf(mk(0, 20, "a")),
                U(
                    changes = listOf(listOf(5, 15, 20)),
                    filter = { _, _, _ -> false }
                ),
                listOf(0, 5, 25, 30)
            )
        }

        @Test
        fun ignores_identical_sub_nodes() {
            val ranges = List(1000) { i ->
                mk(i, i + 1, "a")
            }
            test(
                ranges,
                U(
                    changes = listOf(listOf(900, 1000, 0)),
                    add = listOf(mk(850, 860, "b")),
                    prepare = { set: RangeSet<Value> ->
                        set.chunk[0].clearForTest()
                        set
                    }
                ),
                listOf(850, 860)
            )
        }

        @Test
        fun ignores_changes_in_points() {
            val ranges = mutableListOf(mk(3, 997, Spec(point = true)))
            for (i in 0 until 1000 step 2) {
                ranges.add(mk(i, i + 1, "a"))
            }
            val set = mkSet(ranges.sortedWith { a, b -> a.compareTo(b) })
            test(
                set,
                U(
                    changes = listOf(listOf(300, 500, 100))
                ),
                listOf()
            )
        }

        @Test
        fun notices_adding_a_point() {
            test(
                listOf(mk(3, 50, Spec(point = true))),
                U(
                    add = listOf(mk(40, 80, Spec(point = true)))
                ),
                listOf(50, 80)
            )
        }

        @Test
        fun notices_removing_a_point() {
            test(
                listOf(mk(3, 50, Spec(point = true))),
                U(
                    filter = { _, _, _ -> false }
                ),
                listOf(3, 50)
            )
        }

        @Test
        fun can_handle_multiple_changes() {
            val ranges = List(100) {
                val i = it * 2
                val end = i + 1 + ceil(i / 50.0).roundToInt()
                mk(i, end, "$i-$end")
            }
            test(
                ranges,
                U(
                    changes = listOf(listOf(0, 0, 50), listOf(50, 100, 0), listOf(150, 200, 0)),
                    filter = { from, _, _ -> from.mod(50) > 0 }
                ),
                listOf(50, 51, 100, 103, 148, 153)
            )
        }

        @Test
        fun reports_point_decorations_with_different_cover() {
            test(
                listOf(
                    mk(0, 4, Spec(startSide = 1, endSide = -1)),
                    mk(1, 3, Spec(startSide = -1, endSide = 1, point = true))
                ),
                U(
                    changes = listOf(listOf(2, 4, 0))
                ),
                listOf(1, 2)
            )
        }
    }

    class spans {
        class Builder : SpanIterator<Value> {
            val spans = mutableListOf<String>()
            override fun span(from: Int, to: Int, active: List<Value>, openStart: Int) {
                val name = Value.names(active)
                this.spans.add(
                    "${(to - from)}${name.takeIf { it.isNotEmpty() }?.let("="::plus) ?: ""}"
                )
            }

            override fun point(
                from: Int,
                to: Int,
                value: Value,
                active: List<Value>,
                openStart: Int,
                index: Int
            ) {
                this.spans.add(
                    (if (to > from) (to - from).toString() + "=" else "") +
                        (if (value.name != null) "[" + value.name + "]" else "ø")
                )
            }
        }

        fun test(set: Any, start: Int, end: Int, expected: String) {
            val builder = Builder()
            val setList =
                if (set is List<*>) set as List<RangeSet<Value>> else listOf(set as RangeSet<Value>)
            RangeSet.spans(setList, start, end, builder)
            assertEquals(expected, builder.spans.joinToString(" "))
        }

        @Test
        fun separates_the_range_in_covering_spans() {
            test(
                mkSet(listOf(mk(3, 8, "one"), mk(5, 8, "two"), mk(10, 12, "three"))),
                0,
                15,
                "3 2=one 3=two/one 2 2=three 3"
            )
        }

        @Test
        fun can_retrieve_a_limited_range() {
            val decos = listOf(mk(0, 200, "wide")) + List(100) { i ->
                mk(i * 2, i * 2 + 2, "span" + i)
            }
            val set = mkSet(decos)
            val start = 20
            val end = start + 6
            var expected = ""
            var pos = start
            while (pos < end) {
                expected += (if (expected.isNotBlank()) " " else "") + (
                    min(
                        end,
                        pos + (if (pos.mod(2) != 0) 1 else 2)
                    ) - pos
                    ) + "=span" + (pos / 2) + "/wide"
                pos += (if (pos.mod(2) != 0) 1 else 2)
            }
            test(set, start, end, expected)
        }

        @Test
        fun reads_from_multiple_sets_at_once() {
            val one = mkSet(listOf(mk(2, 3, "x"), mk(5, 10, "y"), mk(10, 12, "z")))
            val two = mkSet(listOf(mk(0, 6, "a"), mk(10, 12, "b")))
            test(listOf(one, two), 0, 12, "2=a 1=x/a 2=a 1=y/a 4=y 2=z/b")
        }

        @Test
        fun orders_active_ranges_by_origin_set() {
            val one = mkSet(listOf(mk(2, 10, "a"), mk(20, 30, "a")))
            val two = mkSet(listOf(mk(3, 4, "b"), mk(8, 12, "b"), mk(18, 22, "b")))
            val three = mkSet(listOf(mk(0, 25, "c")))
            test(
                listOf(one, two, three),
                0,
                25,
                "2=c 1=a/c 1=a/b/c 4=a/c 2=a/b/c 2=b/c 6=c 2=b/c 2=a/b/c 3=a/c"
            )
        }

        @Test
        fun doesn_t_get_confused_by_same_place_points() {
            test(
                mkSet(listOf(mk(1, "a"), mk(1, "b"), mk(1, "c"))),
                0,
                2,
                "1 [a] [b] [c] 1"
            )
        }

        @Test
        fun properly_resyncs_active_ranges_after_points() {
            test(
                mkSet(
                    listOf(
                        mk(0, 20, "r1"),
                        mk(1, 10, "r2"),
                        mk(3, 12, Spec(name = "p", point = true)),
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
        fun doesn_t_split_spans_on_ignored_ranges() {
            val ranges = mutableListOf<Int>()
            RangeSet.spans(
                listOf(mkSet(listOf(mk(0, 10, "a"), mk(20, 30, Spec(name = "b", point = true))))),
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
            assertEquals(ranges.joinToString(","), "0,20,20,30")
        }

        @Test
        fun omits_points_that_are_covered_by_the_previous_point() {
            var points = 0
            RangeSet.spans(
                listOf(
                    mkSet(
                        listOf(
                            mk(0, 4, Spec(point = true)),
                            mk(1, 5, Spec(name = "a")),
                            mk(
                                2,
                                4,
                                Spec(point = true)
                            )
                        )
                    )
                ),
                0,
                10,
                object : SpanIterator<Value> {
                    override fun span(from: Int, to: Int, active: List<Value>, openStart: Int) =
                        Unit

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
            assertEquals(points, 1)
        }

        @Test
        fun puts_smaller_spans_inside_bigger_ones_with_the_same_rank() {
            test(
                mkSet(
                    listOf(
                        mk(0, 3, Spec(name = "x")),
                        mk(0, 1, Spec(name = "a")),
                        mk(1, 2, Spec(name = "b")),
                        mk(2, 3, Spec(name = "c"))
                    )
                ),
                0,
                3,
                "1=a/x 1=b/x 1=c/x"
            )
        }
    }

    class iter {
        @Test
        fun iterates_over_ranges() {
            val set = mkSet(
                (
                    smallRanges + listOf(
                        mk(1000, 4000, Spec(pos = 1000)),
                        mk(2000, 3000, Spec(pos = 2000))
                    )
                    ).sortedWith { a, b -> a.from.compareTo(b.from) }
            )
            var count = 0
            val iter = set.iter()
            while (iter.value != null) {
                assertEquals(
                    if (count > 2001) {
                        count - 2
                    } else if (count > 1000) {
                        count - 1
                    } else {
                        count
                    },
                    iter.from
                )
                iter.next()
                count++
            }
            assertEquals(5002, count)
        }

        @Test
        fun can_iterate_over_a_subset() {
            var count = 0
            val iter = set0().iter(1000)
            while (iter.value != null) {
                if (iter.from > 2000) break
                assertEquals(iter.to, iter.from + 1 + (iter.from % 4))
                iter.next()
                count++
            }
            assertEquals(count, 1003)
        }
    }

    class between {
        @Test
        fun iterates_over_ranges() {
            var found = 0
            set0().between(100, 200) { from, to, _ ->
                assertEquals(to, from + 1 + (from % 4))
                assertTrue(to >= 100, "$to >= 100")
                assertTrue(from <= 200, "$from <= 200")
                found++
                true
            }
            assertEquals(found, 103)
        }

        @Test
        fun returns_ranges_in_a_zero_length_set() {
            val set = RangeSet.of(listOf(mk(0, 0)).list)
            val found = mutableListOf<Int>()
            set.between(0, 0) { from, to, _ ->
                found.add(from)
                found.add(to)
            }
            assertEquals(found.toString(), "[0, 0]")
        }
    }
}
