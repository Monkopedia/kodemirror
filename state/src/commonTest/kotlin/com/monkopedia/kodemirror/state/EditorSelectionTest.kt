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
                EditorSelection.range(DocPos(0), DocPos(1)),
                EditorSelection.range(DocPos(3), DocPos(2)),
                EditorSelection.range(DocPos(4), DocPos(5))
            ),
            1
        )
        assertEquals(DocPos(2), sel.main.from)
        assertEquals(DocPos(3), sel.main.to)
        assertEquals(DocPos(3), sel.main.anchor)
        assertEquals(DocPos(2), sel.main.head)
        assertEquals(
            "0/1,3/2,4/5",
            sel.ranges.joinToString(",") { "${it.anchor.value}/${it.head.value}" }
        )
    }

    @Test
    fun mergesAndSortsRangesWhenNormalizing() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(DocPos(10), DocPos(12)),
                EditorSelection.range(DocPos(6), DocPos(7)),
                EditorSelection.range(DocPos(4), DocPos(5)),
                EditorSelection.range(DocPos(3), DocPos(4)),
                EditorSelection.range(DocPos(0), DocPos(6)),
                EditorSelection.range(DocPos(7), DocPos(8)),
                EditorSelection.range(DocPos(9), DocPos(13)),
                EditorSelection.range(DocPos(13), DocPos(14))
            )
        )
        assertEquals(
            "0/6,6/7,7/8,9/13,13/14",
            sel.ranges.joinToString(",") { "${it.anchor.value}/${it.head.value}" }
        )
    }

    @Test
    fun mergesAdjacentPointRangesWhenNormalizing() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(DocPos(10), DocPos(12)),
                EditorSelection.range(DocPos(12), DocPos(12)),
                EditorSelection.range(DocPos(12), DocPos(12)),
                EditorSelection.range(DocPos(10), DocPos(10)),
                EditorSelection.range(DocPos(8), DocPos(10))
            )
        )
        assertEquals(
            "8/10,10/12",
            sel.ranges.joinToString(",") { "${it.anchor.value}/${it.head.value}" }
        )
    }

    @Test
    fun preservesDirectionOfLastRangeWhenMergingRanges() {
        val sel = EditorSelection.create(
            listOf(
                EditorSelection.range(DocPos(0), DocPos(2)),
                EditorSelection.range(DocPos(10), DocPos(1))
            )
        )
        assertEquals(
            "10/0",
            sel.ranges.joinToString(",") { "${it.anchor.value}/${it.head.value}" }
        )
    }
}
