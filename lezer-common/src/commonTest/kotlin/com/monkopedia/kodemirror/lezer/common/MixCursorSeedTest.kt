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

/**
 * The cursor `MixedParse.startInner` walks the base tree with is seeded at `ranges[0].from` with
 * `INCLUDE_ANONYMOUS or IGNORE_MOUNTS`, and only offers `nest` the nodes upstream does (#353). Each
 * test pins one of those conditions; expected values are from npm `@lezer/common` 1.5.1 running
 * the same synthetic parsers.
 */
class MixCursorSeedTest {

    private class ImmediateParse(val tree: Tree) : PartialParse {
        override var stoppedAt: Int? = null
            private set
        override val parsedPos: Int get() = tree.length
        override fun stopAt(pos: Int) {
            stoppedAt = pos
        }
        override fun advance(): Tree = tree
    }

    private fun parserOf(build: (input: Input, ranges: List<TextRange>) -> Tree): Parser =
        object : Parser() {
            override fun createParse(
                input: Input,
                fragments: List<TreeFragment>,
                ranges: List<TextRange>
            ): PartialParse = ImmediateParse(build(input, ranges))
        }

    private fun mixedOf(base: Parser, nest: (SyntaxNodeRef, Input) -> NestedParse?): Parser =
        object : Parser() {
            override fun createParse(
                input: Input,
                fragments: List<TreeFragment>,
                ranges: List<TextRange>
            ): PartialParse = parseMixed(
                nest
            )(base.createParse(input, fragments, ranges), input, fragments, ranges)
        }

    private fun type(name: String, id: Int, top: Boolean = false) =
        NodeType.define(NodeTypeSpec(name = name, id = id, top = top))

    private fun leaf(type: NodeType, length: Int) = Tree(type, emptyList(), emptyList(), length)

    /**
     * `xx[[ab|zz|cd]]!`: the outer parse nests `mid` on `[[...]]` with an overlay over `ab` and
     * `cd`, so mid's base parse runs over two ranges and builds its tree relative to the first.
     * Mid nests a parser on each of its `Inner` nodes, which records the text it was given. With
     * the walk seeded at the last range's start, those reads shift past `cd`.
     */
    @Test
    fun multiRangeBaseParseIsWalkedFromItsFirstRange() {
        val doc = type("Doc", 0, top = true)
        val outerNode = type("Outer", 1)
        val mid = type("Mid", 2, top = true)
        val inner = type("Inner", 3)
        val chars = type("Chars", 4, top = true)
        val reads = mutableListOf<String>()
        val charParser = parserOf { input, ranges ->
            val text = input.read(ranges.first().from, ranges.last().to)
            reads.add(text)
            leaf(chars, text.length)
        }
        val midBase = parserOf { _, ranges ->
            val start = ranges.first().from
            Tree(
                mid,
                ranges.map { leaf(inner, it.to - it.from) },
                ranges.map { it.from - start },
                ranges.last().to - start
            )
        }
        val midParser = mixedOf(midBase) { node, _ ->
            if (node.name == "Inner") NestedParse(parser = charParser) else null
        }
        val outerBase = parserOf { input, _ ->
            val text = input.read(0, input.length)
            val start = text.indexOf("[[")
            val end = text.indexOf("]]") + 2
            Tree(doc, listOf(leaf(outerNode, end - start)), listOf(start), text.length)
        }
        val outerParser = mixedOf(outerBase) { node, input ->
            if (node.name == "Outer") {
                val text = input.read(node.from, node.to)
                NestedParse(
                    parser = midParser,
                    overlay = ParseOverlay.Ranges(
                        listOf(
                            TextRange(node.from + 2, node.from + text.indexOf('|')),
                            TextRange(node.from + text.lastIndexOf('|') + 1, node.to - 2)
                        )
                    )
                )
            } else {
                null
            }
        }
        outerParser.parse("xx[[ab|zz|cd]]!")
        assertEquals(listOf("ab", "cd"), reads)
    }

    /**
     * The base tree already carries a mount on the node `nest` matches, as a reused node does.
     * The walk must see the host node, not the stale mounted tree, so the node is re-nested.
     */
    @Test
    fun existingMountsAreNotEntered() {
        val doc = type("Doc", 0, top = true)
        val outerNode = type("Outer", 1)
        val stale = type("Stale", 2, top = true)
        val fresh = type("Fresh", 3, top = true)
        val freshParser = parserOf { _, ranges ->
            leaf(fresh, ranges.last().to - ranges.first().from)
        }
        val base = parserOf { _, _ ->
            val staleMount = MountedTree(leaf(stale, 4), null, freshParser)
            val outerTree = Tree(
                outerNode,
                emptyList(),
                emptyList(),
                4,
                mapOf(NodeProp.mounted.id to staleMount)
            )
            Tree(doc, listOf(outerTree), listOf(0), 4)
        }
        val parser = mixedOf(base) { node, _ ->
            if (node.name == "Outer") NestedParse(parser = freshParser) else null
        }
        val tree = parser.parse("abcd")
        assertEquals("Fresh", tree.resolveInner(2, 0).name)
    }

    /**
     * An overlay predicate is offered anonymous nodes too, which the walk only visits because it
     * includes them.
     */
    @Test
    fun overlayPredicateSeesAnonymousNodes() {
        val doc = type("Doc", 0, top = true)
        val block = type("Block", 1)
        val anon = NodeType.define(NodeTypeSpec(id = 2))
        val mark = type("Mark", 3)
        val innerTop = type("Inner", 4, top = true)
        val innerRanges = mutableListOf<List<TextRange>>()
        val innerParser = parserOf { _, ranges ->
            innerRanges.add(ranges)
            leaf(innerTop, ranges.last().to - ranges.first().from)
        }
        val base = parserOf { _, _ ->
            val blockTree = Tree(
                block,
                listOf(leaf(anon, 2), leaf(mark, 2), leaf(anon, 2)),
                listOf(0, 2, 4),
                6
            )
            Tree(doc, listOf(blockTree), listOf(0), 6)
        }
        val parser = mixedOf(base) { node, _ ->
            if (node.name == "Block") {
                NestedParse(
                    parser = innerParser,
                    overlay = ParseOverlay.Predicate { child ->
                        if (child.type.id == 2) ParseOverlayMatch.FullNode else null
                    }
                )
            } else {
                null
            }
        }
        parser.parse("aa--bb")
        assertEquals(listOf(listOf(TextRange(0, 2), TextRange(4, 6))), innerRanges)
    }

    // ---- `startInner` guards on calling `nest` ----

    /** A node inside a range an overlay nest already covers is never offered to `nest`. */
    @Test
    fun coveredNodesAreNotOfferedToNest() {
        val doc = type("Doc", 0, top = true)
        val block = type("Block", 1)
        val kid = type("Kid", 2)
        val innerTop = type("In", 3, top = true)
        val innerParser = parserOf { _, ranges ->
            leaf(
                innerTop,
                ranges.last().to - ranges.first().from
            )
        }
        val base = parserOf { _, _ ->
            Tree(doc, listOf(Tree(block, listOf(leaf(kid, 2)), listOf(2), 6)), listOf(0), 6)
        }
        val calls = mutableListOf<String>()
        val parser = mixedOf(base) { node, _ ->
            calls.add(node.name)
            if (node.name == "Block") {
                NestedParse(
                    parser = innerParser,
                    overlay = ParseOverlay.Ranges(listOf(TextRange(1, 5)))
                )
            } else {
                null
            }
        }
        parser.parse("abcdef")
        assertEquals(listOf("Doc", "Block"), calls)
    }

    /** Anonymous nodes are never offered to `nest`, so nothing mounts on them. */
    @Test
    fun anonymousNodesAreNotOfferedToNest() {
        val doc = type("Doc", 0, top = true)
        val anon = NodeType.define(NodeTypeSpec(id = 1))
        val innerTop = type("In", 2, top = true)
        val started = mutableListOf<List<TextRange>>()
        val recording = parserOf { _, ranges ->
            started.add(ranges)
            leaf(innerTop, ranges.last().to - ranges.first().from)
        }
        val base = parserOf { _, _ -> Tree(doc, listOf(leaf(anon, 4)), listOf(0), 4) }
        val calls = mutableListOf<Int>()
        val parser = mixedOf(base) { node, _ ->
            calls.add(node.type.id)
            if (node.type.id == 1) NestedParse(parser = recording) else null
        }
        val tree = parser.parse("abcd")
        assertEquals(listOf(0), calls)
        assertEquals(emptyList(), started)
        assertEquals("Doc", tree.resolveInner(2, 0).name)
    }

    /** An empty node whose nest has an overlay is skipped rather than parsed. */
    @Test
    fun emptyNodeWithOverlayNestIsSkipped() {
        val doc = type("Doc", 0, top = true)
        val empty = type("Empty", 1)
        val innerTop = type("In", 2, top = true)
        val started = mutableListOf<List<TextRange>>()
        val recording = parserOf { _, ranges ->
            started.add(ranges)
            leaf(innerTop, ranges.last().to - ranges.first().from)
        }
        val base = parserOf { _, _ -> Tree(doc, listOf(leaf(empty, 0)), listOf(2), 4) }
        val parser = mixedOf(base) { node, _ ->
            if (node.name == "Empty") {
                NestedParse(
                    parser = recording,
                    overlay = ParseOverlay.Ranges(listOf(TextRange(0, 2)))
                )
            } else {
                null
            }
        }
        parser.parse("abcd")
        assertEquals(emptyList(), started)
    }

    // ---- `StructureCursor.moveTo`, behind `FragmentCursor.hasNode` (#505) ----

    private class MoveToTypes {
        val doc = NodeType.define(NodeTypeSpec(name = "Doc", id = 0, top = true))
        val wrap = NodeType.define(NodeTypeSpec(name = "Wrap", id = 1))
        val x = NodeType.define(NodeTypeSpec(name = "X", id = 2))
        val a = NodeType.define(NodeTypeSpec(name = "A", id = 3))
        val b = NodeType.define(NodeTypeSpec(name = "B", id = 4))
    }

    private fun recordingMixed(base: Parser, calls: MutableList<String>): Parser =
        mixedOf(base) { node, _ ->
            calls.add("${node.name}@${node.from}")
            null
        }

    /**
     * After a successful `enter`, `moveTo` re-checks the loop condition rather than also stepping
     * with `next`. A reused `A` inside a fresh `Wrap` at the same position is then found in the old
     * tree, so `nest` is not offered it. Stepping on as well skips past it, and `A@2` is offered.
     */
    @Test
    fun moveToDoesNotStepPastAnEnteredNode() {
        val t = MoveToTypes()
        val a = leaf(t.a, 4)
        fun tree() = Tree(
            t.doc,
            listOf(Tree(t.wrap, listOf(leaf(t.x, 2), a), listOf(0, 2), 10)),
            listOf(0),
            10
        )
        val old = tree()
        val calls = mutableListOf<String>()
        recordingMixed(parserOf { _, _ -> tree() }, calls)
            .parse("0123456789", TreeFragment.addTree(old))
        assertEquals(listOf("Doc@0", "Wrap@0", "X@0"), calls)
    }

    /**
     * `moveTo` only enters a node that reaches `pos` (in document coordinates). With the fragment
     * shifted by 3, the old `Wrap` ends before `A`'s new position; entering it anyway matches the
     * shifted `A` against the old one, and `A@5` is never offered. The fragment is what npm's
     * `TreeFragment.applyChanges(addTree(old), [{fromA: 0, toA: 0, fromB: 0, toB: 3}])` returns.
     */
    @Test
    fun moveToOnlyEntersNodesReachingPos() {
        val t = MoveToTypes()
        val a = leaf(t.a, 2)
        val wrap = Tree(t.wrap, listOf(leaf(t.x, 2), a), listOf(0, 2), 4)
        val old = Tree(t.doc, listOf(wrap, leaf(t.b, 6)), listOf(0, 4), 10)
        val fresh = Tree(
            t.doc,
            listOf(Tree(t.wrap, listOf(leaf(t.x, 2), a), listOf(0, 2), 4), leaf(t.b, 6)),
            listOf(3, 7),
            13
        )
        val calls = mutableListOf<String>()
        recordingMixed(parserOf { _, _ -> fresh }, calls).parse(
            "xyz0123456789",
            listOf(TreeFragment(3, 13, old, -3, openStart = true, openEnd = false))
        )
        assertEquals(listOf("Doc@0", "Wrap@3", "X@3", "A@5", "B@7"), calls)
    }
}
