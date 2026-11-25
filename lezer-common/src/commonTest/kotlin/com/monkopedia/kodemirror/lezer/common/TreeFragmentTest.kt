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
package com.monkopedia.kodemirror.lezer.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [TreeFragment.addTree] and [TreeFragment.applyChanges] against
 * `@lezer/common` 1.5.1. Each expectation is the fragment list upstream
 * returns for the same input, written `from-to@offset`, with `<` for
 * `openStart` and `>` for `openEnd`.
 */
class TreeFragmentTest {

    private fun tree(length: Int) = Tree(NodeType.none, emptyList(), emptyList(), length)

    private fun show(fragments: List<TreeFragment>): String = fragments.joinToString(" ") {
        "${it.from}-${it.to}@${it.offset}" + (if (it.openStart) "<" else "") +
            (if (it.openEnd) ">" else "")
    }

    private fun apply(length: Int, vararg changes: ChangedRange): String =
        show(TreeFragment.applyChanges(TreeFragment.addTree(tree(length)), changes.toList()))

    @Test
    fun aChangeSplitsTheFragmentAndShiftsTheTail() =
        assertEquals("0-500@0> 505-1003@-3<", apply(1000, ChangedRange(500, 502, 500, 505)))

    @Test
    fun anUnchangedStretchShorterThanMinGapIsDropped() =
        // 0-50 is too short to keep before the change; only the tail survives.
        assertEquals("55-103@-3<", apply(100, ChangedRange(50, 52, 50, 55)))

    @Test
    fun aDeletionNearTheStartKeepsOnlyTheTail() =
        assertEquals("10-990@10<", apply(1000, ChangedRange(10, 20, 10, 10)))

    @Test
    fun anInsertionAtTheEndKeepsTheWholeTree() =
        assertEquals("0-1000@0>", apply(1000, ChangedRange(1000, 1000, 1000, 1003)))

    @Test
    fun aGapShorterThanMinGapBetweenChangesIsDropped() = assertEquals(
        "0-300@0> 401-1000@0<",
        apply(1000, ChangedRange(300, 301, 300, 301), ChangedRange(400, 401, 400, 401))
    )

    @Test
    fun aGapOfAtLeastMinGapBetweenChangesIsKeptOpenAtBothEnds() = assertEquals(
        "0-300@0> 302-501@-1<> 502-1001@-1<",
        apply(1000, ChangedRange(300, 301, 300, 302), ChangedRange(500, 501, 501, 502))
    )

    @Test
    fun existingFragmentsAreCutAroundAChange() {
        val fragments = listOf(
            TreeFragment(0, 400, tree(400), 0, openStart = false, openEnd = true),
            TreeFragment(600, 1000, tree(1000), 0, openStart = true, openEnd = false)
        )
        assertEquals(
            "0-400@0> 600-800@0> 800-990@10<",
            show(TreeFragment.applyChanges(fragments, listOf(ChangedRange(800, 810, 800, 800))))
        )
    }

    @Test
    fun aGapOfExactlyMinGapIsKept() = assertEquals(
        "0-299@0> 300-428@0<> 429-1000@0<",
        apply(1000, ChangedRange(299, 300, 299, 300), ChangedRange(428, 429, 428, 429))
    )

    @Test
    fun aGapOneUnderMinGapIsDropped() = assertEquals(
        "0-299@0> 428-1000@0<",
        apply(1000, ChangedRange(299, 300, 299, 300), ChangedRange(427, 428, 427, 428))
    )

    @Test
    fun aFragmentWhollyAfterAChangeIsShiftedByIt() {
        val fragments = listOf(
            TreeFragment(0, 400, tree(400), 0, openStart = false, openEnd = true),
            TreeFragment(600, 1000, tree(1000), 0, openStart = true, openEnd = false)
        )
        assertEquals(
            "0-400@0> 590-990@10<",
            show(TreeFragment.applyChanges(fragments, listOf(ChangedRange(500, 510, 500, 500))))
        )
    }

    @Test
    fun aFragmentLeftEmptyByAChangeIsDropped() {
        val fragments = listOf(
            TreeFragment(0, 500, tree(500), 0, openStart = false, openEnd = true),
            TreeFragment(500, 600, tree(600), 0, openStart = true, openEnd = false)
        )
        assertEquals(
            "0-500@0> 500-550@0>",
            show(TreeFragment.applyChanges(fragments, listOf(ChangedRange(550, 600, 550, 550))))
        )
    }

    @Test
    fun addTreeOfAPartialParseIsOpenAtTheEnd() =
        assertEquals("0-500@0>", show(TreeFragment.addTree(tree(500), partial = true)))

    @Test
    fun addTreeKeepsOnlyOldFragmentsReachingPastTheNewTree() {
        val old = listOf(
            TreeFragment(0, 300, tree(300), 0),
            TreeFragment(400, 900, tree(900), 0, openStart = true, openEnd = false)
        )
        assertEquals("0-500@0 400-900@0<", show(TreeFragment.addTree(tree(500), old)))
        assertEquals(
            "0-500@0> 400-900@0<",
            show(TreeFragment.addTree(tree(500), old, partial = true))
        )
    }

    @Test
    fun addTreeDropsAnOldFragmentEndingWhereTheNewTreeEnds() {
        val old = listOf(TreeFragment(400, 500, tree(500), 0, openStart = true, openEnd = false))
        assertEquals("0-500@0", show(TreeFragment.addTree(tree(500), old)))
    }
}
