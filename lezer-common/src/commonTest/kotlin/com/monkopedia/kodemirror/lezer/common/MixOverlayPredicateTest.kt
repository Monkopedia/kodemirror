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
 * A [ParseOverlay.Predicate] sees every node below its mount that did not itself start a nest,
 * named nodes included (#457). `:lang-markdown`'s code-language overlay matches the named
 * `CodeText` node, so this is what mounts a language inside fenced code.
 */
class MixOverlayPredicateTest {

    private val doc = NodeType.define(NodeTypeSpec(name = "Doc", id = 0, top = true))
    private val block = NodeType.define(NodeTypeSpec(name = "Block", id = 1))
    private val code = NodeType.define(NodeTypeSpec(name = "Code", id = 2))
    private val mark = NodeType.define(NodeTypeSpec(name = "Mark", id = 3))
    private val innerTop = NodeType.define(NodeTypeSpec(name = "Inner", id = 4, top = true))

    private class ImmediateParse(val tree: Tree) : PartialParse {
        override var stoppedAt: Int? = null
            private set
        override val parsedPos: Int get() = tree.length
        override fun stopAt(pos: Int) {
            stoppedAt = pos
        }
        override fun advance(): Tree = tree
    }

    /** `Doc > Block > Code Mark Code` over `aa--bb`. */
    private val baseParser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse {
            val leaf = { type: NodeType -> Tree(type, emptyList(), emptyList(), 2) }
            val blockTree =
                Tree(block, listOf(leaf(code), leaf(mark), leaf(code)), listOf(0, 2, 4), 6)
            return ImmediateParse(Tree(doc, listOf(blockTree), listOf(0), 6))
        }
    }

    private val innerRanges = mutableListOf<List<TextRange>>()

    private val innerParser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse {
            innerRanges.add(ranges)
            return ImmediateParse(
                Tree(
                    innerTop,
                    emptyList(),
                    emptyList(),
                    ranges.last().to - ranges.first().from
                )
            )
        }
    }

    private val mixedParser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse = parseMixed { node, _ ->
            if (node.name == "Block") {
                NestedParse(
                    parser = innerParser,
                    overlay = ParseOverlay.Predicate { child ->
                        if (child.name == "Code") ParseOverlayMatch.FullNode else null
                    }
                )
            } else {
                null
            }
        }(baseParser.createParse(input, fragments, ranges), input, fragments, ranges)
    }

    @Test
    fun predicateMatchesNamedNodes() {
        val tree = mixedParser.parse("aa--bb")
        val expected = listOf(TextRange(0, 2), TextRange(4, 6))
        assertEquals(listOf(expected), innerRanges)
        val mount = tree.topNode.firstChild!!.tree!!.prop(NodeProp.mounted)
        assertEquals(expected, mount?.overlay)
        assertEquals("Inner", mount?.tree?.type?.name)
    }
}
