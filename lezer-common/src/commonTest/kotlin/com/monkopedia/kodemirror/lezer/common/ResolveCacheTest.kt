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

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * `Tree.resolve` / `resolveInner` remember their last result and start the next query from it
 * (#330, upstream `CachedNode` / `CachedInnerNode`). On [fixture] upstream's cached answer
 * happens to equal a cold one, which [cachedResolutionMatchesAColdOne] pins. In general it does
 * not: a cached node that already covers the position is answered without climbing, so an
 * overlay mounted on one of its ancestors is never entered. Upstream returns that different
 * answer and so must the port ([cacheChangesTheAnswerLikeUpstream]).
 *
 * ```
 * Doc(0-30)
 *   Text(0-4)
 *   Script(4-24)            overlay mount over doc 10-14 and 18-22
 *     Attr(4-10) Attr(10-14)
 *     <buffer> Attr(14-18) { Word(15-17) }
 *     Attr(18-22) Attr(22-24)
 *   Text(24-30)
 *
 * overlay tree: Expr(10-22) { Ident(10-14) Ident(18-22) }
 * ```
 */
class ResolveCacheTest {

    private val types = listOf(
        "Doc", "Text", "Script", "Attr", "Expr", "Ident", "Word",
        "Name", "Value", "Q", "Code", "Stmt"
    ).mapIndexed { id, name -> NodeType.define(NodeTypeSpec(name = name, id = id)) }

    private val dummyParser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse = error("Not implemented")
    }

    private fun leaf(type: Int, length: Int) = Tree(types[type], emptyList(), emptyList(), length)

    private fun fixture(): Tree {
        val inner = Tree(types[4], listOf(leaf(5, 4), leaf(5, 4)), listOf(0, 8), 12)
        val mount = MountedTree(inner, listOf(TextRange(6, 10), TextRange(14, 18)), dummyParser)
        val buffer = TreeBuffer(intArrayOf(3, 0, 4, 8, 6, 1, 3, 8), 4, NodeSet(types))
        val script = Tree(
            types[2],
            listOf(leaf(3, 6), leaf(3, 4), buffer, leaf(3, 4), leaf(3, 2)),
            listOf(0, 6, 10, 14, 18),
            20,
            mapOf(NodeProp.mounted.id to mount)
        )
        return Tree(types[0], listOf(leaf(1, 4), script, leaf(1, 6)), listOf(0, 4, 24), 30)
    }

    private fun chain(node: SyntaxNode) = generateSequence(node) {
        it.parent
    }.joinToString(" < ") { "${it.name}(${it.from}-${it.to})" }

    @Test
    fun repeatedQueryReusesTheCachedNode() {
        val tree = fixture()
        assertSame(tree.resolve(16, 1), tree.resolve(16, 1))
        assertSame(tree.resolveInner(12, 1), tree.resolveInner(12, 1))
    }

    @Test
    fun cachedResolutionMatchesAColdOne() {
        // `topNode.resolve*` never uses the cache, so it is the cold reference. Ascending,
        // descending and random orders put every kind of node (buffer, overlay, overlay root,
        // host) in the cache ahead of every query.
        val tree = fixture()
        val random = Random(330)
        val order = (0..30) + (30 downTo 0) + List(300) { random.nextInt(31) }
        for (pos in order) {
            for (side in -1..1) {
                assertEquals(chain(tree.topNode.resolve(pos, side)), chain(tree.resolve(pos, side)))
                assertEquals(
                    chain(tree.topNode.resolveInner(pos, side)),
                    chain(tree.resolveInner(pos, side))
                )
            }
        }
    }

    private fun tree(
        type: Int,
        children: List<Tree>,
        positions: List<Int>,
        length: Int,
        mount: MountedTree
    ) = Tree(types[type], children, positions, length, mapOf(NodeProp.mounted.id to mount))

    /**
     * An overlay strictly inside a host child, like an attribute value:
     * `Doc(0-30){Text(0-4), Attr(4-24){Name(4-8), Value(8-24){Q(8-9), Q(23-24)}}, Text(24-30)}`,
     * with Attr mounting `Expr{Ident(11-14), Ident(17-21)}` over doc 11-14 and 17-21.
     */
    private fun overlayInsideHostChild(): Tree {
        val inner = Tree(types[4], listOf(leaf(5, 3), leaf(5, 4)), listOf(0, 6), 10)
        val mount = MountedTree(inner, listOf(TextRange(7, 10), TextRange(13, 17)), dummyParser)
        val value = Tree(types[8], listOf(leaf(9, 1), leaf(9, 1)), listOf(0, 15), 16)
        val attr = tree(3, listOf(leaf(7, 4), value), listOf(0, 4), 20, mount)
        return Tree(types[0], listOf(leaf(1, 4), attr, leaf(1, 6)), listOf(0, 4, 24), 30)
    }

    /**
     * A full mount `Code(8-22){Word(8-12), Stmt(12-18){Word(12-18)}, Word(18-22)}` replacing a
     * Script node, with Stmt mounting `Expr{Ident(14-16)}` over doc 14-16.
     */
    private fun overlayInsideFullMount(): Tree {
        val overlay = MountedTree(
            Tree(types[4], listOf(leaf(5, 2)), listOf(0), 2),
            listOf(TextRange(2, 4)),
            dummyParser
        )
        val stmt = tree(11, listOf(leaf(6, 6)), listOf(0), 6, overlay)
        val code = Tree(types[10], listOf(leaf(6, 4), stmt, leaf(6, 4)), listOf(0, 4, 10), 14)
        val script =
            tree(2, listOf(leaf(1, 14)), listOf(0), 14, MountedTree(code, null, dummyParser))
        return Tree(
            types[0],
            listOf(leaf(1, 4), leaf(3, 4), script, leaf(1, 8)),
            listOf(0, 4, 8, 22),
            30
        )
    }

    @Test
    fun cacheChangesTheAnswerLikeUpstream() {
        // Expected values are from @lezer/common 1.5.1. The cached node already covers the next
        // position, so upstream answers from it without climbing to the overlay's mount point.
        overlayInsideHostChild().let { tree ->
            assertEquals(
                "Ident(11-14) < Expr(11-21) < Attr(4-24) < Doc(0-30)",
                chain(tree.topNode.resolveInner(12, 1))
            )
            assertEquals("Value(8-24) < Attr(4-24) < Doc(0-30)", chain(tree.resolveInner(9, 1)))
            assertEquals("Value(8-24) < Attr(4-24) < Doc(0-30)", chain(tree.resolveInner(12, 1)))
        }
        overlayInsideFullMount().let { tree ->
            assertEquals(
                "Ident(14-16) < Expr(14-16) < Stmt(12-18) < Code(8-22) < Doc(0-30)",
                chain(tree.topNode.resolveInner(15, 1))
            )
            assertEquals(
                "Word(12-18) < Stmt(12-18) < Code(8-22) < Doc(0-30)",
                chain(tree.resolveInner(13, 1))
            )
            assertEquals(
                "Word(12-18) < Stmt(12-18) < Code(8-22) < Doc(0-30)",
                chain(tree.resolveInner(15, 1))
            )
        }
    }
}
