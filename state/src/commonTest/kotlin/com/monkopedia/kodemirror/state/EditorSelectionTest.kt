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

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorSelectionTest {

    @Test
    fun storesRangesWithPrimaryRange() {
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
        assertEquals(
            "0/1,3/2,4/5",
            sel.ranges.joinToString(",") { "${it.anchor}/${it.head}" }
        )
    }

    @Test
    fun mergesAndSortsRangesWhenNormalizing() {
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
            sel.ranges.joinToString(",") { "${it.anchor}/${it.head}" }
        )
    }

    @Test
    fun mergesAdjacentPointRangesWhenNormalizing() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(10, 12),
                EditorSelection.range(12, 12),
                EditorSelection.range(12, 12),
                EditorSelection.range(10, 10),
                EditorSelection.range(8, 10)
            )
        )
        assertEquals(
            "8/10,10/12",
            sel.ranges.joinToString(",") { "${it.anchor}/${it.head}" }
        )
    }

    @Test
    fun preservesDirectionOfLastRangeWhenMergingRanges() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(0, 2),
                EditorSelection.range(10, 1)
            )
        )
        assertEquals(
            "10/0",
            sel.ranges.joinToString(",") { "${it.anchor}/${it.head}" }
        )
    }
}
