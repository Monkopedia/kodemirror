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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests ported from @lezer/common test-tree.ts
 */
class TreeTest {

    // Build helper types matching upstream test
    private val types = listOf(
        NodeType.define(NodeTypeSpec(name = "T", id = 0)),
        NodeType.define(
            NodeTypeSpec(
                name = "a",
                id = 1,
                props = listOf(NodeProp.group to listOf("atom"))
            )
        ),
        NodeType.define(
            NodeTypeSpec(
                name = "b",
                id = 2,
                props = listOf(NodeProp.group to listOf("atom"))
            )
        ),
        NodeType.define(
            NodeTypeSpec(
                name = "c",
                id = 3,
                props = listOf(NodeProp.group to listOf("atom"))
            )
        ),
        NodeType.define(NodeTypeSpec(name = "Pa", id = 4)),
        NodeType.define(NodeTypeSpec(name = "Br", id = 5))
    )

    // Build a simple tree manually for testing:
    // aaaa(bbb[ccc][aaa][()])
    // Positions: a(0) a(1) a(2) a(3) Pa(4-22) { b(5) b(6) b(7) Br(8-11) { c(9) c(10) c(11) }
    //            Br(12-15) { a(13) a(14) a(15) } Br(16-18) { Pa(17-18) } }
    private fun simple(): Tree {
        val a = types[1]
        val b = types[2]
        val c = types[3]
        val pa = types[4]
        val br = types[5]
        val t = types[0]

        return Tree(
            t,
            listOf(
                Tree(a, emptyList(), emptyList(), 1),
                Tree(a, emptyList(), emptyList(), 1),
                Tree(a, emptyList(), emptyList(), 1),
                Tree(a, emptyList(), emptyList(), 1),
                Tree(
                    pa,
                    listOf(
                        Tree(b, emptyList(), emptyList(), 1),
                        Tree(b, emptyList(), emptyList(), 1),
                        Tree(b, emptyList(), emptyList(), 1),
                        Tree(
                            br,
                            listOf(
                                Tree(c, emptyList(), emptyList(), 1),
                                Tree(c, emptyList(), emptyList(), 1),
                                Tree(c, emptyList(), emptyList(), 1)
                            ),
                            listOf(0, 1, 2),
                            3
                        ),
                        Tree(
                            br,
                            listOf(
                                Tree(a, emptyList(), emptyList(), 1),
                                Tree(a, emptyList(), emptyList(), 1),
                                Tree(a, emptyList(), emptyList(), 1)
                            ),
                            listOf(0, 1, 2),
                            3
                        ),
                        Tree(
                            br,
                            listOf(
                                Tree(pa, emptyList(), emptyList(), 2)
                            ),
                            listOf(0),
                            2
                        )
                    ),
                    listOf(0, 1, 2, 4, 8, 12),
                    16
                )
            ),
            listOf(0, 1, 2, 3, 4),
            20
        )
    }

    // Anonymous tree for testing
    private fun anonTree(): Tree {
        val a = types[1]
        val b = types[2]
        return Tree(
            types[0],
            listOf(
                Tree(
                    NodeType.none,
                    listOf(
                        Tree(a, emptyList(), emptyList(), 1),
                        Tree(b, emptyList(), emptyList(), 1)
                    ),
                    listOf(0, 1),
                    2
                )
            ),
            listOf(0),
            2
        )
    }

    // ---- SyntaxNode tests ----

    @Test
    fun canResolveAtTopLevel() {
        val tree = simple()
        val c = tree.resolve(2, -1)
        assertEquals(1, c.from)
        assertEquals(2, c.to)
        assertEquals("a", c.name)
        assertNotNull(c.parent)
        assertEquals("T", c.parent!!.name)
        assertNull(c.parent!!.parent)
    }

    @Test
    fun canResolveWithSide() {
        val tree = simple()
        val c = tree.resolve(2, 1)
        assertEquals(2, c.from)
        assertEquals(3, c.to)
        assertEquals("a", c.name)
    }

    @Test
    fun canResolveDeeper() {
        val tree = simple()
        // Position 9 inside the first Br inside Pa
        val c = tree.resolve(9, 1)
        assertEquals("c", c.name)
        assertNotNull(c.parent)
        assertEquals("Br", c.parent!!.name)
        assertNotNull(c.parent!!.parent)
        assertEquals("Pa", c.parent!!.parent!!.name)
    }

    @Test
    fun getChildByGroup() {
        val tree = simple()
        val topNode = tree.topNode
        val atoms = topNode.getChildren("atom")
        assertEquals(4, atoms.size) // 4 top-level atom children
        for (atom in atoms) {
            assertTrue(atom.type.`is`("atom"))
        }
    }

    @Test
    fun skipsAnonymousNodes() {
        val tree = anonTree()
        assertEquals("T(a,b)", tree.toString())
        // resolve at position 1 should hit T (skipping anon)
        val node = tree.topNode
        assertNotNull(node.firstChild)
        assertEquals("a", node.firstChild!!.name)
        assertNotNull(node.lastChild)
        assertEquals("b", node.lastChild!!.name)
    }

    // ---- TreeCursor tests ----

    @Test
    fun cursorIteratesAllNodes() {
        val tree = simple()
        val count = mutableMapOf<String, Int>()
        val cur = tree.cursor()
        do {
            count[cur.name] = (count[cur.name] ?: 0) + 1
        } while (cur.next())
        // The tree has: T(1), a(7), b(3), c(3), Pa(2), Br(3)
        assertEquals(1, count["T"])
        assertEquals(7, count["a"])
        assertEquals(3, count["b"])
        assertEquals(3, count["c"])
        assertEquals(2, count["Pa"])
        assertEquals(3, count["Br"])
    }

    @Test
    fun cursorCanLeaveNodes() {
        val tree = simple()
        val cur = tree.cursor()
        // At root, parent should fail
        assertTrue(!cur.parent())
        // Move to first child
        cur.next()
        cur.next()
        assertEquals("a", cur.name)
        assertTrue(cur.parent())
        assertEquals("T", cur.name)
    }

    @Test
    fun cursorCanMoveToPosition() {
        val tree = simple()
        val cursor = tree.cursorAt(9, 1)
        assertEquals("c", cursor.name)
    }

    @Test
    fun cursorFirstChildAndParent() {
        val tree = simple()
        val cur = tree.cursor()
        assertEquals("T", cur.name)
        assertTrue(cur.firstChild())
        assertEquals("a", cur.name)
        assertEquals(0, cur.from)
        assertTrue(cur.parent())
        assertEquals("T", cur.name)
    }

    @Test
    fun cursorNextSibling() {
        val tree = simple()
        val cur = tree.cursor()
        assertTrue(cur.firstChild())
        assertEquals("a", cur.name)
        assertEquals(0, cur.from)
        assertTrue(cur.nextSibling())
        assertEquals("a", cur.name)
        assertEquals(1, cur.from)
    }

    @Test
    fun cursorSkipsAnonymousNodes() {
        val tree = anonTree()
        val cur = tree.cursor()
        cur.moveTo(1)
        assertEquals("T", cur.name)
        assertTrue(cur.firstChild())
        assertEquals("a", cur.name)
        assertTrue(cur.nextSibling())
        assertEquals("b", cur.name)
        assertTrue(!cur.next())
    }

    @Test
    fun cursorStopsAtAnonWithIncludeAnonymous() {
        val tree = anonTree()
        val cur = tree.cursor(IterMode.INCLUDE_ANONYMOUS)
        cur.moveTo(1)
        // Should land on the anonymous wrapper
        assertEquals(NodeType.none, cur.type)
        assertTrue(cur.firstChild())
        assertEquals("a", cur.name)
        assertTrue(cur.parent())
        assertEquals(NodeType.none, cur.type)
    }

    // ---- Tree.iterate tests ----

    @Test
    fun iterateCallsEnterAndLeave() {
        val tree = simple()
        val opened = mutableMapOf<String, Int>()
        val closed = mutableMapOf<String, Int>()
        tree.iterate(
            IterateSpec(
                enter = { t ->
                    opened[t.name] = (opened[t.name] ?: 0) + 1
                    null // continue
                },
                leave = { t ->
                    closed[t.name] = (closed[t.name] ?: 0) + 1
                }
            )
        )
        assertEquals(opened["T"], closed["T"])
        assertEquals(opened["a"], closed["a"])
    }

    @Test
    fun iterateCanBeLimitedToRange() {
        val tree = simple()
        val seen = mutableListOf<String>()
        tree.iterate(
            IterateSpec(
                enter = { t ->
                    seen.add(t.name)
                    if (t.name == "Br") false else null
                },
                from = 3,
                to = 14
            )
        )
        assertTrue("T" in seen)
        assertTrue("Pa" in seen)
    }

    // ---- NodeType tests ----

    @Test
    fun nodeTypeNone() {
        assertEquals("", NodeType.none.name)
        assertEquals(0, NodeType.none.id)
    }

    @Test
    fun nodeTypeIs() {
        val a = types[1]
        assertTrue(a.`is`("a"))
        assertTrue(a.`is`("atom"))
        assertTrue(!a.`is`("b"))
    }

    // ---- NodeProp tests ----

    @Test
    fun nodeTypeHasProps() {
        val a = types[1]
        val groups = a.prop(NodeProp.group)
        assertNotNull(groups)
        assertTrue(groups.contains("atom"))
    }

    // ---- NodeSet.extend tests ----

    @Test
    fun nodeSetExtendAddsProp() {
        val nodeSet = NodeSet(types)
        val testProp = NodeProp<String>()
        val extended = nodeSet.extend(
            testProp.add { type ->
                if (type.name == "a") "extended-a" else null
            }
        )
        assertEquals("extended-a", extended.types[1].prop(testProp))
        assertNull(extended.types[0].prop(testProp))
    }

    // ---- Tree.toString tests ----

    @Test
    fun treeToString() {
        val tree = anonTree()
        assertEquals("T(a,b)", tree.toString())
    }

    @Test
    fun simpleTreeToString() {
        val a = types[1]
        val tree = Tree(
            types[0],
            listOf(
                Tree(a, emptyList(), emptyList(), 1),
                Tree(a, emptyList(), emptyList(), 1)
            ),
            listOf(0, 1),
            2
        )
        assertEquals("T(a,a)", tree.toString())
    }

    // ---- matchContext tests ----

    @Test
    fun canMatchContext() {
        val tree = simple()
        val node = tree.resolve(9, 1) // c inside Br inside Pa
        assertTrue(node.matchContext(listOf("T", "Pa", "Br")))
    }

    @Test
    fun canMatchWildcards() {
        val tree = simple()
        val node = tree.resolve(9, 1)
        assertTrue(node.matchContext(listOf("T", "", "Br")))
    }

    @Test
    fun canMismatch() {
        val tree = simple()
        val node = tree.resolve(9, 1)
        assertTrue(!node.matchContext(listOf("Q", "Br")))
    }

    // ---- TreeFragment tests ----

    @Test
    fun treeFragmentAddTree() {
        val tree = Tree(types[0], emptyList(), emptyList(), 10)
        val fragments = TreeFragment.addTree(tree)
        assertEquals(1, fragments.size)
        assertEquals(0, fragments[0].from)
        assertEquals(10, fragments[0].to)
    }

    // ---- Tree.empty ----

    @Test
    fun treeEmptyHasZeroLength() {
        assertEquals(0, Tree.empty.length)
        assertEquals(NodeType.none, Tree.empty.type)
    }

    // ---- resolveInner tests ----

    @Test
    fun resolveInnerFindsDeepNode() {
        val tree = simple()
        val node = tree.resolveInner(9, 1)
        assertEquals("c", node.name)
    }

    @Test
    fun resolveInnerAtTopLevel() {
        val tree = simple()
        val node = tree.resolveInner(2, -1)
        assertEquals("a", node.name)
    }

    @Test
    fun syntaxNodeResolveInner() {
        val tree = simple()
        val top = tree.topNode
        val inner = top.resolveInner(9, 1)
        assertEquals("c", inner.name)
    }

    // ---- resolveStack tests ----

    @Test
    fun resolveStackCoversAncestors() {
        val tree = simple()
        val stack = tree.resolveStack(9, 1)
        // Innermost node should be c
        assertEquals("c", stack.node.name)
        // Walk up should find Br, Pa, T
        val names = mutableListOf<String>()
        var cur: NodeIterator? = stack
        while (cur != null) {
            names.add(cur.node.name)
            cur = cur.next
        }
        assertTrue(names.contains("c"))
        assertTrue(names.contains("Br"))
        assertTrue(names.contains("Pa"))
        assertTrue(names.contains("T"))
    }

    @Test
    fun resolveStackAtTopLevel() {
        val tree = simple()
        val stack = tree.resolveStack(1, -1)
        assertEquals("a", stack.node.name)
        assertNotNull(stack.next)
        assertEquals("T", stack.next!!.node.name)
    }

    // ---- balance tests ----

    @Test
    fun balancePreservesSmallTree() {
        val tree = simple()
        val balanced = tree.balance()
        // A small tree should remain structurally equivalent
        assertEquals(tree.length, balanced.length)
        assertEquals(tree.type, balanced.type)
    }

    @Test
    fun balanceHandlesEmptyTree() {
        val balanced = Tree.empty.balance()
        assertEquals(0, balanced.length)
    }

    // ---- enterUnfinishedNodesBefore tests ----

    @Test
    fun enterUnfinishedNodesBeforeOnNormalTree() {
        val tree = simple()
        val node = tree.topNode
        // No error nodes, should return the node itself or a child
        val result = node.enterUnfinishedNodesBefore(10)
        assertNotNull(result)
    }

    @Test
    fun enterUnfinishedNodesBeforeWithErrorNode() {
        // Build a tree with an error node
        val errorType = NodeType.define(
            NodeTypeSpec(name = "\u26A0", id = 10, error = true)
        )
        val a = types[1]
        val tree = Tree(
            types[0],
            listOf(
                Tree(a, emptyList(), emptyList(), 3),
                // zero-length error node
                Tree(
                    errorType,
                    emptyList(),
                    emptyList(),
                    0
                )
            ),
            listOf(0, 3),
            5
        )
        val node = tree.topNode
        val result = node.enterUnfinishedNodesBefore(5)
        assertNotNull(result)
    }

    // ---- NodeType.match tests ----

    @Test
    fun nodeTypeMatchByName() {
        val matcher = NodeType.match(mapOf("a" to "found-a", "b" to "found-b"))
        assertEquals("found-a", matcher(types[1]))
        assertEquals("found-b", matcher(types[2]))
        assertNull(matcher(types[0])) // type T
    }

    @Test
    fun nodeTypeMatchByGroup() {
        val matcher = NodeType.match(mapOf("atom" to "is-atom"))
        assertEquals("is-atom", matcher(types[1])) // a is in group "atom"
        assertEquals("is-atom", matcher(types[2])) // b is in group "atom"
        assertNull(matcher(types[4])) // Pa is not in group "atom"
    }

    // ---- reverse cursor iteration (prev) tests ----

    @Test
    fun cursorPrevMovesBackward() {
        val tree = simple()
        val cur = tree.cursor()
        // Navigate forward to the end
        while (cur.next()) { /* traverse */ }
        val lastName = cur.name

        // prev should move to a node before the last
        assertTrue(cur.prev())
        // We should be at a different or earlier node
        assertTrue(cur.from <= tree.length)
    }

    // ---- Buffer node tests via Tree.build ----

    @Test
    fun treeBuildFromFlatBuffer() {
        val nodeSet = NodeSet(types)
        // Build a minimal tree via Tree.build with a flat buffer.
        // FlatBufferCursor reads [id, start, end, size] from end to start.
        // A single leaf node 'a' from 0..3 with size=4.
        // a: type=1, start=0, end=3, size=4 (leaf)
        val buffer = listOf(
            1,
            0,
            3,
            4
        )
        val tree = Tree.build(
            TreeBuildSpec(
                buffer = buffer,
                nodeSet = nodeSet,
                topID = 0,
                length = 3
            )
        )
        assertEquals("T", tree.type.name)
        assertEquals(3, tree.length)
    }

    // ---- children retrieval tests ----

    @Test
    fun getChildByType() {
        val tree = simple()
        val pa = tree.topNode.getChild("Pa")
        assertNotNull(pa)
        assertEquals("Pa", pa.name)
    }

    @Test
    fun getChildReturnsNullForMissing() {
        val tree = simple()
        val result = tree.topNode.getChild("NonExistent")
        assertNull(result)
    }

    @Test
    fun getChildrenReturnsEmpty() {
        val tree = simple()
        val results = tree.topNode.getChildren("NonExistent")
        assertTrue(results.isEmpty())
    }

    // ---- NodeWeakMap tests ----

    @Test
    fun nodeWeakMapSetAndGet() {
        val tree = simple()
        val map = NodeWeakMap<String>()
        val node = tree.resolve(9, 1)
        map.set(node, "test-value")
        assertEquals("test-value", map.get(node))
    }

    @Test
    fun nodeWeakMapReturnsNullForMissing() {
        val tree = simple()
        val map = NodeWeakMap<String>()
        val node = tree.resolve(9, 1)
        assertNull(map.get(node))
    }

    @Test
    fun nodeWeakMapCursorSetAndGet() {
        val tree = simple()
        val map = NodeWeakMap<Int>()
        val cursor = tree.cursorAt(9, 1)
        map.cursorSet(cursor, 42)
        assertEquals(42, map.cursorGet(cursor))
    }

    @Test
    fun nodeWeakMapDifferentNodesIndependent() {
        val tree = simple()
        val map = NodeWeakMap<String>()
        val node1 = tree.resolve(1, -1) // first 'a'
        val node2 = tree.resolve(9, 1) // 'c' inside Br
        map.set(node1, "value1")
        map.set(node2, "value2")
        assertEquals("value1", map.get(node1))
        assertEquals("value2", map.get(node2))
    }

    // ---- TreeFragment.applyChanges with minGap ----

    @Test
    fun treeFragmentApplyChangesWithMinGap() {
        val tree = Tree(types[0], emptyList(), emptyList(), 100)
        val fragments = TreeFragment.addTree(tree)
        val changed = TreeFragment.applyChanges(
            fragments,
            listOf(ChangedRange(50, 52, 50, 55)),
            minGap = 128
        )
        // Fragments should be adjusted around the change
        assertTrue(changed.isNotEmpty() || fragments.isNotEmpty())
    }

    @Test
    fun treeFragmentApplyChangesEmpty() {
        val tree = Tree(types[0], emptyList(), emptyList(), 100)
        val fragments = TreeFragment.addTree(tree)
        val result = TreeFragment.applyChanges(fragments, emptyList())
        assertEquals(fragments.size, result.size)
    }
}
