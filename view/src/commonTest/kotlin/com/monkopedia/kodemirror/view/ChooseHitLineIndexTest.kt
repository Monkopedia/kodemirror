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
package com.monkopedia.kodemirror.view

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [chooseHitLineIndex] is the never-null line-selection at the heart of the
 * #165 fix: click/drag/hover resolve their line from the LIVE LazyColumn layout
 * and must NEVER fall back to the stale-after-scroll absolute-position cache.
 * So for any y with at least one visible line, this must return a line (the
 * containing one, or the nearest), and null only when there are no lines.
 */
class ChooseHitLineIndexTest {

    // Three lines 20px tall at tops 100, 120, 140 (no gaps).
    private val tops = floatArrayOf(100f, 120f, 140f)
    private val sizes = floatArrayOf(20f, 20f, 20f)

    @Test
    fun yInsideALineReturnsThatLine() {
        assertEquals(0, chooseHitLineIndex(tops, sizes, 100f)) // top edge of line 0
        assertEquals(0, chooseHitLineIndex(tops, sizes, 110f))
        assertEquals(1, chooseHitLineIndex(tops, sizes, 120f)) // top edge of line 1
        assertEquals(1, chooseHitLineIndex(tops, sizes, 139.9f))
        assertEquals(2, chooseHitLineIndex(tops, sizes, 150f))
    }

    @Test
    fun yInAGapResolvesToTheLineBelow() {
        // Tops 100, 125, 150 with 20px lines leaves 5px gaps at [120,125) etc.
        val gapTops = floatArrayOf(100f, 125f, 150f)
        val gapSizes = floatArrayOf(20f, 20f, 20f)
        // y=122 is in the gap between line 0 (ends 120) and line 1 (starts 125):
        // prefer the line below (line 1).
        assertEquals(1, chooseHitLineIndex(gapTops, gapSizes, 122f))
        assertEquals(2, chooseHitLineIndex(gapTops, gapSizes, 147f))
    }

    @Test
    fun yAboveAllLinesResolvesToTheFirst() {
        // e.g. a click in the top padding above the first rendered line.
        assertEquals(0, chooseHitLineIndex(tops, sizes, 50f))
    }

    @Test
    fun yBelowAllLinesResolvesToTheLast() {
        // a click below the last rendered line (e.g. empty space under content).
        assertEquals(2, chooseHitLineIndex(tops, sizes, 500f))
    }

    @Test
    fun noVisibleLinesReturnsNull() {
        assertNull(chooseHitLineIndex(floatArrayOf(), floatArrayOf(), 100f))
    }

    @Test
    fun singleLineAlwaysResolvesToIt() {
        val t = floatArrayOf(80f)
        val s = floatArrayOf(20f)
        assertEquals(0, chooseHitLineIndex(t, s, 0f)) // above
        assertEquals(0, chooseHitLineIndex(t, s, 90f)) // inside
        assertEquals(0, chooseHitLineIndex(t, s, 999f)) // below
    }
}
