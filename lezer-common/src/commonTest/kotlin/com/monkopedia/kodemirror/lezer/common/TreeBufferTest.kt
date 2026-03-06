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
package com.monkopedia.kodemirror.lezer.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TreeBufferTest {

    private val types = listOf(
        NodeType.define(NodeTypeSpec(name = "T", id = 0)),
        NodeType.define(NodeTypeSpec(name = "A", id = 1)),
        NodeType.define(NodeTypeSpec(name = "B", id = 2)),
        NodeType.define(NodeTypeSpec(name = "C", id = 3))
    )
    private val nodeSet = NodeSet(types)

    /**
     * Build a buffer with three sibling leaf nodes:
     * A(0..2), B(2..5), C(5..8)
     * Buffer format: [type, start, end, endIndex] where endIndex points past this node's children.
     * For leaves, endIndex = index + 4.
     */
    private fun threeLeaves(): TreeBuffer {
        val buf = intArrayOf(
            // A: type=1, start=0, end=2, endIndex=4 (leaf)
            1, 0, 2, 4,
            // B: type=2, start=2, end=5, endIndex=8 (leaf)
            2, 2, 5, 8,
            // C: type=3, start=5, end=8, endIndex=12 (leaf)
            3, 5, 8, 12
        )
        return TreeBuffer(buf, 8, nodeSet)
    }

    /**
     * Build a buffer with a parent and child:
     * A(0..8) containing B(1..4)
     */
    private fun parentChild(): TreeBuffer {
        val buf = intArrayOf(
            // A: type=1, start=0, end=8, endIndex=12 (contains B)
            1, 0, 8, 12,
            // B: type=2, start=1, end=4, endIndex=8 (leaf inside A)
            2, 1, 4, 8,
            // C: type=3, start=4, end=7, endIndex=12 (leaf inside A)
            3, 4, 7, 12
        )
        return TreeBuffer(buf, 8, nodeSet)
    }

    // --- findChild forward ---

    @Test
    fun findChildForwardReturnsFirstMatch() {
        val tb = threeLeaves()
        // Forward search (dir=1) for any node at position 0 with side=DONT_CARE
        val idx = tb.findChild(0, tb.buffer.size, 1, 0, Side.DONT_CARE)
        assertEquals(0, idx, "Should find node A at index 0")
    }

    @Test
    fun findChildForwardSkipsNonMatching() {
        val tb = threeLeaves()
        // Search for nodes around position 3 (inside B)
        val idx = tb.findChild(0, tb.buffer.size, 1, 3, Side.AROUND)
        assertEquals(4, idx, "Should find node B at index 4")
    }

    // --- findChild backward ---

    @Test
    fun findChildBackwardReturnsLastMatch() {
        val tb = threeLeaves()
        // Backward search (dir=-1) for any node — should find last matching
        val idx = tb.findChild(0, tb.buffer.size, -1, 6, Side.DONT_CARE)
        assertEquals(8, idx, "Should find node C at index 8")
    }

    // --- findChild with side constraints ---

    @Test
    fun findChildWithBeforeSide() {
        val tb = threeLeaves()
        // BEFORE means from < pos
        val idx = tb.findChild(0, tb.buffer.size, 1, 3, Side.BEFORE)
        assertEquals(0, idx, "BEFORE: A starts at 0 < 3")
    }

    @Test
    fun findChildWithAfterSide() {
        val tb = threeLeaves()
        // AFTER means to > pos
        val idx = tb.findChild(0, tb.buffer.size, 1, 1, Side.AFTER)
        assertEquals(0, idx, "AFTER: A ends at 2 > 1")
    }

    // --- findChild no match ---

    @Test
    fun findChildReturnsNeg1WhenNoMatch() {
        val tb = threeLeaves()
        // AROUND means from < pos AND to > pos — no node straddles position 0
        val idx = tb.findChild(0, tb.buffer.size, 1, 0, Side.AROUND)
        assertEquals(-1, idx, "Should return -1 when no node straddles position 0")
    }

    // --- slice ---

    @Test
    fun sliceCreatesAdjustedBuffer() {
        val tb = threeLeaves()
        // Slice out just the B node (index 4..8) with from=2
        val sliced = tb.slice(4, 8, 2)
        // The sliced buffer should have adjusted positions
        assertEquals(1, sliced.buffer.size / 4, "Sliced buffer should have 1 node")
        assertEquals(2, sliced.buffer[0], "Type should be B (id=2)")
        assertEquals(0, sliced.buffer[1], "Start should be adjusted: 2 - 2 = 0")
        assertEquals(3, sliced.buffer[2], "End should be adjusted: 5 - 2 = 3")
    }

    // --- toString ---

    @Test
    fun toStringFormatsLeafNodes() {
        val tb = threeLeaves()
        val str = tb.toString()
        assertEquals("A,B,C", str)
    }

    @Test
    fun toStringFormatsNestedNodes() {
        val tb = parentChild()
        val str = tb.toString()
        assertEquals("A(B,C)", str)
    }
}
