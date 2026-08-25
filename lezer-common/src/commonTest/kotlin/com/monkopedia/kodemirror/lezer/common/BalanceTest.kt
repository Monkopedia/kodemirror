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
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [Tree.balance].
 *
 * Every expectation here was derived by running the same construction against
 * `@lezer/common@1.5.2` (`dist/index.js`, `Tree.balance` at `:543`,
 * `nodeSize`/`balanceRange` at `:1508`/`:1525`) and recording its output.
 */
class BalanceTest {

    private val t = NodeType.define(NodeTypeSpec(name = "T", id = 0))
    private val a = NodeType.define(NodeTypeSpec(name = "a", id = 1))
    private val br = NodeType.define(NodeTypeSpec(name = "Br", id = 5))

    /** Flat tree of [n] leaves of type `a`, each one unit long. */
    private fun flat(n: Int): Tree =
        Tree(t, (0 until n).map { Tree(a, emptyList(), emptyList(), 1) }, (0 until n).toList(), n)

    /**
     * Tree of [n] anonymous grouping children, each holding [k] anonymous
     * leaves -- i.e. the shape a previous `balance()` pass leaves behind.
     */
    private fun nested(n: Int, k: Int): Tree {
        val kids = (0 until n).map {
            Tree(
                NodeType.none,
                (0 until k).map { Tree(NodeType.none, emptyList(), emptyList(), 1) },
                (0 until k).toList(),
                k
            )
        }
        return Tree(t, kids, (0 until n).map { it * k }, n * k)
    }

    /**
     * Structural dump that *shows* anonymous nodes as `_`. [Tree.toString]
     * flattens them away, so it cannot see the grouping layer at all.
     */
    private fun shape(node: Any): String = when (node) {
        is Tree -> {
            val name = if (node.type == NodeType.none) "_" else node.type.name
            if (node.children.isEmpty()) {
                name
            } else {
                name + node.children.joinToString(",", "(", ")") { shape(it) }
            }
        }
        else -> "buf"
    }

    private fun countType(node: Any, type: NodeType): Int = when (node) {
        is Tree -> (if (node.type == type) 1 else 0) +
            node.children.sumOf { countType(it, type) }
        else -> 0
    }

    private fun groupSizes(tree: Tree): List<Int> = tree.children.map { (it as Tree).children.size }

    // ---- control: at or below the branch factor, balance() is identity ----

    @Test
    fun balanceShortCircuitsAtBranchFactor() {
        // upstream: `this.children.length <= 8 ? this : ...`
        val tree = flat(8)
        assertSame(tree, tree.balance())
        assertEquals("T(a,a,a,a,a,a,a,a)", shape(tree.balance()))
    }

    // ---- divergence #2: inner grouping nodes must be NodeType.none ----

    @Test
    fun balanceInnerNodesAreAnonymous() {
        val balanced = flat(30).balance()
        // upstream ref: countT=1, shape T(_(a,a,a,a,a) x6)
        assertEquals(
            1,
            countType(balanced, t),
            "balance() must create inner grouping nodes with NodeType.none"
        )
        assertTrue(
            balanced.children.all { (it as Tree).type == NodeType.none },
            "grouping nodes must be anonymous, was ${shape(balanced)}"
        )
        assertEquals(
            "T(" + List(6) { "_(a,a,a,a,a)" }.joinToString(",") + ")",
            shape(balanced)
        )
    }

    @Test
    fun balanceLeavesToStringFlat() {
        // Anonymous nodes are invisible to toString, so a correctly balanced
        // tree prints exactly like the unbalanced one. Upstream prints the
        // same 30 `a`s.
        val tree = flat(30)
        assertEquals(tree.toString(), tree.balance().toString())
        assertEquals("T(" + List(30) { "a" }.joinToString(",") + ")", tree.balance().toString())
    }

    // ---- divergence #1: balanceType must be NodeType.none ----

    @Test
    fun balanceReFlattensExistingAnonymousGroupingLayer() {
        // `nodeSize` short-circuits on `!balanceType.isAnonymous`, so with a
        // named balanceType every child counts as size 1 and an existing
        // grouping layer can never be recognised and re-flattened.
        val tree = nested(12, 2)
        assertEquals("T(" + List(12) { "_(_,_)" }.joinToString(",") + ")", shape(tree))
        val balanced = tree.balance()
        // upstream ref: T(_(_,_,_,_) x6), positions [0,4,8,12,16,20]
        assertEquals("T(" + List(6) { "_(_,_,_,_)" }.joinToString(",") + ")", shape(balanced))
        assertEquals(listOf(0, 4, 8, 12, 16, 20), balanced.positions)
        assertEquals(listOf(4, 4, 4, 4, 4, 4), balanced.children.map { (it as Tree).length })
        assertEquals(24, balanced.length)
    }

    // ---- divergence #3: propValues carried onto the rebuilt top node ----

    @Test
    fun balancePreservesPropValues() {
        val flat = flat(30)
        val tree = Tree(
            t,
            flat.children,
            flat.positions,
            flat.length,
            mapOf(NodeProp.contextHash.id to 4242)
        )
        assertEquals(
            4242,
            tree.balance().prop(NodeProp.contextHash),
            "balance() must carry propValues onto the new top node"
        )
    }

    // ---- divergence #4: config.makeTree is the factory, mkTop is this.type ----

    @Test
    fun balanceUsesMakeTreeResult() {
        val balanced = flat(30).balance(
            BalanceConfig(
                makeTree = { ch, pos, len ->
                    Tree(br, ch, pos, len, mapOf(NodeProp.contextHash.id to 7))
                }
            )
        )
        // upstream ref: topType=T, childTypes=[Br x6], childProps=[7 x6]
        assertEquals(t, balanced.type, "the top node always uses this.type")
        assertEquals("T(" + List(6) { "Br(a,a,a,a,a)" }.joinToString(",") + ")", shape(balanced))
        assertEquals(
            List(6) { 7 },
            balanced.children.map { (it as Tree).prop(NodeProp.contextHash) },
            "balance() must use the tree returned by config.makeTree, not just its type"
        )
    }

    @Test
    fun balanceTopKeepsOwnTypeWhenMakeTreeIsAnonymous() {
        // The `:lang-markdown` shape: makeTree returns an anonymous node (as
        // upstream's own callers do). The top node must still be `this.type`.
        val flat = flat(30)
        val tree = Tree(
            t,
            flat.children,
            flat.positions,
            flat.length,
            mapOf(NodeProp.contextHash.id to 99)
        )
        val balanced = tree.balance(
            BalanceConfig(
                makeTree = { ch, pos, len ->
                    Tree(NodeType.none, ch, pos, len, mapOf(NodeProp.contextHash.id to 99))
                }
            )
        )
        // upstream ref: topType=T, topHash=99
        assertEquals(t, balanced.type)
        assertEquals(99, balanced.prop(NodeProp.contextHash))
        assertEquals("T(" + List(30) { "a" }.joinToString(",") + ")", balanced.toString())
    }

    // ---- divergence #5: maxChild is ceil(total * 1.5 / 8), not floor(..) + 1 ----

    @Test
    fun balanceMaxChildUsesCeilingNotFloorPlusOne() {
        // total == 16 -> 16 * 1.5 / 8 == 3 exactly, so ceil is 3 and
        // floor + 1 is 4. Upstream ref: 8 groups of 2 (floor+1 gives 6:
        // five groups of 3 plus one bare leaf).
        val balanced = flat(16).balance()
        assertEquals(8, balanced.children.size)
        assertEquals(List(8) { 2 }, groupSizes(balanced))
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14), balanced.positions)
        assertEquals("T(" + List(8) { "_(a,a)" }.joinToString(",") + ")", shape(balanced))
    }

    @Test
    fun balanceMaxChildMatchesUpstreamAcrossSizes() {
        // upstream ref (`@lezer/common@1.5.2`), flat trees of n `a` leaves:
        //   n=12 -> [2,2,2,2,2,2]   n=17 -> [3,3,3,3,3,2]
        //   n=24 -> [4,4,4,4,4,4]   n=32 -> [5,5,5,5,5,5,2]
        //   n=48 -> [8,8,8,8,8,8]
        assertEquals(listOf(2, 2, 2, 2, 2, 2), groupSizes(flat(12).balance()))
        assertEquals(listOf(3, 3, 3, 3, 3, 2), groupSizes(flat(17).balance()))
        assertEquals(listOf(4, 4, 4, 4, 4, 4), groupSizes(flat(24).balance()))
        assertEquals(listOf(5, 5, 5, 5, 5, 5, 2), groupSizes(flat(32).balance()))
        assertEquals(listOf(8, 8, 8, 8, 8, 8), groupSizes(flat(48).balance()))
    }

    @Test
    fun balanceNestsDeeplyForLargeTrees() {
        // upstream ref, n=64:
        // T(_(_(a,a),_(a,a),_(a,a),_(a,a),_(a,a),a) x5, _(a,a,a,a,a,a,a,a,a))
        val balanced = flat(64).balance()
        val group = List(5) { "_(a,a)" }.joinToString(",") + ",a"
        assertEquals(
            "T(" + List(5) { "_($group)" }.joinToString(",") +
                ",_(" + List(9) { "a" }.joinToString(",") + "))",
            shape(balanced)
        )
    }
}
