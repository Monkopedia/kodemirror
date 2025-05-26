package com.monkopedia.kodemirror.state

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorSelectionTest {

    @Test
    fun stores_ranges_with_a_primary_range() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(0, 1),
                EditorSelection.range(3, 2),
                EditorSelection.range(4, 5)
            ),
            1
        )
        assertEquals(2, sel.main.from)
        assertEquals(3, sel.main.to)
        assertEquals(3, sel.main.anchor)
        assertEquals(2, sel.main.head)
        assertEquals("0/1,3/2,4/5", sel.ranges.joinToString(",") { r -> "${r.anchor}/${r.head}" })
    }

    @Test
    fun merges_and_sorts_ranges_when_normalizing() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(10, 12),
                EditorSelection.range(6, 7),
                EditorSelection.range(4, 5),
                EditorSelection.range(3, 4),
                EditorSelection.range(0, 6),
                EditorSelection.range(7, 8),
                EditorSelection.range(9, 13),
                EditorSelection.range(13, 14)
            )
        )
        assertEquals(
            "0/6,6/7,7/8,9/13,13/14",
            sel.ranges.joinToString(",") { r -> "${r.anchor}/${r.head}" })
    }

    @Test
    fun merges_adjacent_point_ranges_when_normalizing() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(10, 12),
                EditorSelection.range(12, 12),
                EditorSelection.range(12, 12),
                EditorSelection.range(10, 10),
                EditorSelection.range(8, 10)
            )
        )
        assertEquals("8/10,10/12", sel.ranges.joinToString(",") { r -> "${r.anchor}/${r.head}" })
    }

    @Test
    fun preserves_the_direction_of_the_last_range_when_merging_ranges() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(0, 2),
                EditorSelection.range(10, 1)
            )
        )
        assertEquals("10/0", sel.ranges.joinToString(",") { r -> "${r.anchor}/${r.head}" })
    }
}
