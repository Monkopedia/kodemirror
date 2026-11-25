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
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `SyntaxNode.cursor()` must start on the node itself, as upstream's
 * `new TreeCursor(this, mode)` does, for both tree-backed and buffer-backed nodes (#448).
 */
class NodeCursorTest {
    private val types = listOf("T", "a", "b", "c", "Pa", "Br").mapIndexed { id, name ->
        NodeType.define(NodeTypeSpec(name = name, id = id))
    }
    private val nodeSet = NodeSet(types)

    private fun leaf(type: Int) = Tree(types[type], emptyList(), emptyList(), 1)

    /**
     * T[0,20] { a[0,1] Pa[2,20] { b[2,3] <buffer> c[14,15] } }, where the buffer at 4 holds
     * Br[4,12] { c[5,6] Br[7,11] { c[8,9] } } a[12,13].
     */
    private fun tree(): Tree {
        val buffer = TreeBuffer(
            intArrayOf(
                5, 0, 8, 16,
                3, 1, 2, 8,
                5, 3, 7, 16,
                3, 4, 5, 16,
                1, 8, 9, 20
            ),
            9,
            nodeSet
        )
        val pa = Tree(types[4], listOf(leaf(2), buffer, leaf(3)), listOf(0, 2, 12), 18)
        return Tree(types[0], listOf(leaf(1), pa), listOf(0, 2), 20)
    }

    private fun TreeCursor.describe() = "$name[$from,$to]"

    private fun SyntaxNode.describe() = "$name[$from,$to]"

    @Test
    fun treeBackedNodeCursorStartsOnTheNode() {
        val pa = assertNotNull(tree().topNode.lastChild)
        assertEquals("Pa[2,20]", pa.describe())

        val cursor = pa.cursor()
        assertEquals("Pa[2,20]", cursor.describe())
        assertTrue(cursor.firstChild())
        assertEquals("b[2,3]", cursor.describe())
        assertTrue(cursor.parent())
        assertTrue(cursor.parent())
        assertEquals("T[0,20]", cursor.describe())
    }

    @Test
    fun getChildFindsChildrenOfATreeBackedNode() {
        val pa = assertNotNull(tree().topNode.lastChild)
        assertEquals("Br[4,12]", pa.getChild("Br")?.describe())
        assertEquals(listOf("c[14,15]"), pa.getChildren("c").map { it.describe() })
        assertEquals(listOf("a[12,13]"), pa.getChildren("a", before = "Br").map { it.describe() })
    }

    @Test
    fun bufferNodeCursorStartsOnTheNodeWithItsBufferAncestors() {
        val c = tree().resolve(8, 1)
        assertEquals("c[8,9]", c.describe())

        val cursor = c.cursor()
        assertEquals("c[8,9]", cursor.describe())
        assertSame(c, cursor.node)
        assertTrue(cursor.matchContext(listOf("T", "Pa", "Br", "Br")))
        assertTrue(cursor.parent())
        assertEquals("Br[7,11]", cursor.describe())
        assertTrue(cursor.parent())
        assertEquals("Br[4,12]", cursor.describe())
        assertTrue(cursor.nextSibling())
        assertEquals("a[12,13]", cursor.describe())
        assertTrue(cursor.nextSibling())
        assertEquals("c[14,15]", cursor.describe())

        val climb = c.cursor()
        val ancestors = generateSequence { if (climb.parent()) climb.describe() else null }.toList()
        assertEquals(listOf("Br[7,11]", "Br[4,12]", "Pa[2,20]", "T[0,20]"), ancestors)
    }

    @Test
    fun getChildFindsChildrenOfABufferBackedNode() {
        val br = assertNotNull(tree().topNode.lastChild?.getChild("Br"))
        assertEquals("c[5,6]", br.getChild("c")?.describe())
        assertEquals(listOf("Br[7,11]"), br.getChildren("Br").map { it.describe() })
        val inner = assertNotNull(br.getChild("Br"))
        assertEquals("c[8,9]", inner.getChild("c")?.describe())
    }
}
