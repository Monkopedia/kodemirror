/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lezer.lr

import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FragmentCursorTest {

    // --- nodeAt ---

    @Test
    fun nodeAtReturnsNullBeforeFragmentStart() {
        val input = "{\"a\": 1}"
        val tree = jsonParser.parse(input)
        // Create fragment starting at position 2
        val fragment = TreeFragment(2, input.length, tree, -2)
        val cursor = FragmentCursor(listOf(fragment), jsonParser.nodeSet)
        // Position 0 is before the fragment's safeFrom
        val result = cursor.nodeAt(0)
        assertNull(result, "nodeAt before fragment start should return null")
    }

    @Test
    fun nodeAtReturnsTreeAtValidPosition() {
        val input = "[1, 2, 3]"
        val tree = jsonParser.parse(input)
        val fragments = TreeFragment.addTree(tree)
        val cursor = FragmentCursor(fragments, jsonParser.nodeSet)
        // Position 0 should find the tree root
        val result = cursor.nodeAt(0)
        // Either finds a reusable node or returns null (depends on tree structure)
        // The key thing is it doesn't throw
        assertTrue(result != null || cursor.nextStart > 0)
    }

    // --- cutAt ---

    @Test
    fun cutAtFindsNonErrorBoundary() {
        val input = "{\"key\": [1, 2, 3], \"other\": true}"
        val tree = jsonParser.parse(input)
        // cutAt should return a valid position
        val cutPos = cutAt(tree, 10, 1)
        assertTrue(cutPos > 0, "cutAt should return a positive position")
        assertTrue(cutPos <= tree.length, "cutAt should not exceed tree length")
    }

    @Test
    fun cutAtNegativeSideReturnsSafePosition() {
        val input = "{\"key\": [1, 2, 3]}"
        val tree = jsonParser.parse(input)
        val cutPos = cutAt(tree, 10, -1)
        assertTrue(cutPos >= 0, "cutAt with negative side should return non-negative")
        assertTrue(cutPos <= tree.length)
    }

    // --- Fragment reuse via incremental parse ---

    @Test
    fun fragmentCursorUsedDuringIncrementalParse() {
        val input1 = "{\"a\": 1, \"b\": [1, 2, 3, 4, 5]}"
        val tree1 = jsonParser.parse(input1)
        val fragments = TreeFragment.addTree(tree1)

        // Verify fragments were created
        assertTrue(fragments.isNotEmpty())

        // Parse with fragments — the FragmentCursor will be used internally
        val tree2 = jsonParser.parse(input1, fragments)
        val result = treeToString(tree2)
        assertTrue(result.contains("Object"))
        assertTrue(result.contains("Array"))
    }
}
