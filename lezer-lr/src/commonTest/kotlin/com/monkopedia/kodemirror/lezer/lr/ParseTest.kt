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

import com.monkopedia.kodemirror.lezer.common.ChangedRange
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParseTest {

    private fun parse(input: String): String = treeToString(jsonParser.parse(input))

    // --- Error recovery ---

    @Test
    fun recoversMissingClosingBrace() {
        val result = parse("{\"a\": 1")
        // Should still produce a tree with Object and Property
        assertTrue(result.contains("Object"), "Should recover an Object node")
        assertTrue(result.contains("Property"), "Should recover a Property node")
    }

    @Test
    fun recoversExtraComma() {
        val result = parse("[1, 2,, 3]")
        assertTrue(result.contains("Array"), "Should recover an Array node")
        assertTrue(result.contains("\u26A0"), "Should contain error marker")
    }

    @Test
    fun recoversGarbageInput() {
        val result = parse("@@@@")
        // Should not throw and should produce some tree
        assertTrue(result.isNotEmpty(), "Garbage input should produce a tree")
        assertTrue(result.contains("\u26A0"), "Garbage should produce error nodes")
    }

    @Test
    fun recoversPartialObject() {
        val result = parse("{\"key\":")
        assertTrue(result.contains("Object"), "Should recover an Object node")
        assertTrue(result.contains("PropertyName"), "Should recover a PropertyName")
    }

    // --- Incremental parsing ---

    @Test
    fun incrementalParseReusesFragments() {
        val input1 = "{\"a\": 1, \"b\": 2}"
        val tree1 = jsonParser.parse(input1)

        val fragments = TreeFragment.addTree(tree1)
        // Change "1" to "99" at position 6..7 -> 6..8 (insert one char)
        val changed = TreeFragment.applyChanges(
            fragments,
            listOf(ChangedRange(6, 7, 6, 8))
        )

        val input2 = "{\"a\": 99, \"b\": 2}"
        val tree2 = jsonParser.parse(input2, changed)

        assertEquals(
            "JsonText(Object(Property(PropertyName,Number),Property(PropertyName,Number)))",
            treeToString(tree2)
        )
    }

    @Test
    fun incrementalParseWithInsertion() {
        val input1 = "[1, 2]"
        val tree1 = jsonParser.parse(input1)

        val fragments = TreeFragment.addTree(tree1)
        // Insert ", 3" at position 4 (before "]")
        val changed = TreeFragment.applyChanges(
            fragments,
            listOf(ChangedRange(4, 4, 4, 7))
        )

        val input2 = "[1, 2, 3]"
        val tree2 = jsonParser.parse(input2, changed)
        assertEquals("JsonText(Array(Number,Number,Number))", treeToString(tree2))
    }

    @Test
    fun incrementalParseWithDeletion() {
        val input1 = "[1, 2, 3]"
        val tree1 = jsonParser.parse(input1)

        val fragments = TreeFragment.addTree(tree1)
        // Delete ", 3" (positions 4..7 shrink to 4..4)
        val changed = TreeFragment.applyChanges(
            fragments,
            listOf(ChangedRange(4, 7, 4, 4))
        )

        val input2 = "[1, 2]"
        val tree2 = jsonParser.parse(input2, changed)
        assertEquals("JsonText(Array(Number,Number))", treeToString(tree2))
    }

    @Test
    fun incrementalParseWithNoFragments() {
        // Empty fragment list should behave like a fresh parse
        val tree = jsonParser.parse("true", emptyList())
        assertEquals("JsonText(True)", treeToString(tree))
    }

    // --- Partial parse ---

    @Test
    fun partialParseStopsAtPosition() {
        val input = "{\"a\": 1, \"b\": 2, \"c\": 3}"
        val stopAt = 10
        val partial = jsonParser.startParse(input)
        partial.stopAt(stopAt)
        var tree = partial.advance()
        var iterations = 0
        while (tree == null && iterations < 1000) {
            tree = partial.advance()
            iterations++
        }
        val result = assertNotNull(tree, "Braked parse should finish with a tree")
        val detail = "stopAt=$stopAt doc=${input.length} " +
            "tree.length=${result.length} parsedPos=${partial.parsedPos} " +
            "tree=${treeToString(result)}"
        // The brake must actually cut the parse short: the tree may not span
        // the whole document. (@lezer/lr 1.4.10 yields length 12 here.)
        assertTrue(
            result.length < input.length,
            "stopAt must brake the parse before the end of the document; $detail"
        )
        assertTrue(
            partial.parsedPos < input.length,
            "Braked parse must not consume the whole document; $detail"
        )
        // ...but it must still parse up to the brake before stopping.
        assertTrue(
            result.length >= stopAt,
            "Braked parse should cover the input up to stopAt; $detail"
        )
    }

    @Test
    fun stopAtThrowsWhenMovedAhead() {
        val input = "{\"a\": 1, \"b\": 2}"
        val partial = jsonParser.startParse(input)
        partial.stopAt(10)
        // Trying to move the brake forward (larger pos) should throw
        assertFailsWith<IllegalArgumentException> {
            partial.stopAt(15)
        }
    }
}
