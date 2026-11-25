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

/**
 * [Tree.resolveStack] yields every node around a position, small to big, across overlay layers
 * (#329). The expected stacks were recorded from npm `@lezer/common` 1.5.1 on the same fixture,
 * at every position and side.
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
 *   Ident(18-22) has its own overlay mount over doc 19-21: Num(19-21)
 * ```
 */
class ResolveStackTest {

    private val types = listOf("Doc", "Text", "Script", "Attr", "Expr", "Ident", "Word", "Num")
        .mapIndexed { id, name -> NodeType.define(NodeTypeSpec(name = name, id = id)) }
    private val doc = types[0]
    private val text = types[1]
    private val script = types[2]
    private val attr = types[3]
    private val expr = types[4]
    private val ident = types[5]
    private val num = types[7]

    private val dummyParser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse = error("Not implemented")
    }

    private fun leaf(type: NodeType, length: Int) = Tree(type, emptyList(), emptyList(), length)

    private fun mounted(tree: Tree, vararg overlay: TextRange) =
        mapOf(NodeProp.mounted.id to MountedTree(tree, overlay.toList(), dummyParser))

    private fun fixture(): Tree {
        val ident2 =
            Tree(ident, emptyList(), emptyList(), 4, mounted(leaf(num, 2), TextRange(1, 3)))
        val inner = Tree(expr, listOf(leaf(ident, 4), ident2), listOf(0, 8), 12)
        val buffer = TreeBuffer(intArrayOf(3, 0, 4, 8, 6, 1, 3, 8), 4, NodeSet(types))
        val scriptTree = Tree(
            script,
            listOf(leaf(attr, 6), leaf(attr, 4), buffer, leaf(attr, 4), leaf(attr, 2)),
            listOf(0, 6, 10, 14, 18),
            20,
            mounted(inner, TextRange(6, 10), TextRange(14, 18))
        )
        return Tree(doc, listOf(leaf(text, 4), scriptTree, leaf(text, 6)), listOf(0, 4, 24), 30)
    }

    @Test
    fun resolveStackMatchesUpstreamEverywhere() {
        val tree = fixture()
        val actual = (0..30).flatMap { pos ->
            listOf(-1, 0, 1).map { side ->
                generateSequence(tree.resolveStack(pos, side)) { it.next }
                    .joinToString(" ", prefix = "$pos,$side: ") {
                        "${it.node.name}(${it.node.from}-${it.node.to})"
                    }
            }
        }
        assertEquals(
            """
            0,-1: Doc(0-30)
            0,0: Doc(0-30)
            0,1: Text(0-4) Doc(0-30)
            1,-1: Text(0-4) Doc(0-30)
            1,0: Text(0-4) Doc(0-30)
            1,1: Text(0-4) Doc(0-30)
            2,-1: Text(0-4) Doc(0-30)
            2,0: Text(0-4) Doc(0-30)
            2,1: Text(0-4) Doc(0-30)
            3,-1: Text(0-4) Doc(0-30)
            3,0: Text(0-4) Doc(0-30)
            3,1: Text(0-4) Doc(0-30)
            4,-1: Text(0-4) Doc(0-30)
            4,0: Doc(0-30)
            4,1: Attr(4-10) Script(4-24) Doc(0-30)
            5,-1: Attr(4-10) Script(4-24) Doc(0-30)
            5,0: Attr(4-10) Script(4-24) Doc(0-30)
            5,1: Attr(4-10) Script(4-24) Doc(0-30)
            6,-1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            6,0: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            6,1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            7,-1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            7,0: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            7,1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            8,-1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            8,0: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            8,1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            9,-1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            9,0: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            9,1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            10,-1: Expr(10-22) Attr(4-10) Script(4-24) Doc(0-30)
            10,0: Expr(10-22) Script(4-24) Doc(0-30)
            10,1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            11,-1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            11,0: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            11,1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            12,-1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            12,0: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            12,1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            13,-1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            13,0: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            13,1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            14,-1: Ident(10-14) Attr(10-14) Expr(10-22) Script(4-24) Doc(0-30)
            14,0: Expr(10-22) Script(4-24) Doc(0-30)
            14,1: Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            15,-1: Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            15,0: Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            15,1: Word(15-17) Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            16,-1: Word(15-17) Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            16,0: Word(15-17) Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            16,1: Word(15-17) Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            17,-1: Word(15-17) Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            17,0: Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            17,1: Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            18,-1: Attr(14-18) Expr(10-22) Script(4-24) Doc(0-30)
            18,0: Expr(10-22) Script(4-24) Doc(0-30)
            18,1: Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            19,-1: Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            19,0: Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            19,1: Num(19-21) Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            20,-1: Num(19-21) Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            20,0: Num(19-21) Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            20,1: Num(19-21) Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            21,-1: Num(19-21) Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            21,0: Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            21,1: Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            22,-1: Ident(18-22) Attr(18-22) Expr(10-22) Script(4-24) Doc(0-30)
            22,0: Script(4-24) Doc(0-30)
            22,1: Attr(22-24) Script(4-24) Doc(0-30)
            23,-1: Attr(22-24) Script(4-24) Doc(0-30)
            23,0: Attr(22-24) Script(4-24) Doc(0-30)
            23,1: Attr(22-24) Script(4-24) Doc(0-30)
            24,-1: Attr(22-24) Script(4-24) Doc(0-30)
            24,0: Doc(0-30)
            24,1: Text(24-30) Doc(0-30)
            25,-1: Text(24-30) Doc(0-30)
            25,0: Text(24-30) Doc(0-30)
            25,1: Text(24-30) Doc(0-30)
            26,-1: Text(24-30) Doc(0-30)
            26,0: Text(24-30) Doc(0-30)
            26,1: Text(24-30) Doc(0-30)
            27,-1: Text(24-30) Doc(0-30)
            27,0: Text(24-30) Doc(0-30)
            27,1: Text(24-30) Doc(0-30)
            28,-1: Text(24-30) Doc(0-30)
            28,0: Text(24-30) Doc(0-30)
            28,1: Text(24-30) Doc(0-30)
            29,-1: Text(24-30) Doc(0-30)
            29,0: Text(24-30) Doc(0-30)
            29,1: Text(24-30) Doc(0-30)
            30,-1: Text(24-30) Doc(0-30)
            30,0: Doc(0-30)
            30,1: Doc(0-30)
            """.trimIndent(),
            actual.joinToString("\n")
        )
    }
}
