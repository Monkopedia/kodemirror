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
 * (#330, upstream `CachedNode` / `CachedInnerNode`). The answer must not depend on that start.
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

    private val types = listOf("Doc", "Text", "Script", "Attr", "Expr", "Ident", "Word")
        .mapIndexed { id, name -> NodeType.define(NodeTypeSpec(name = name, id = id)) }

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
}
