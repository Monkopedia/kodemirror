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

import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.NodeTypeSpec
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Fragment-reuse parity with `@lezer/lr` 1.4.10's `LRParse.advanceStack`
 * (`dist/index.js:1411-1428`), the block the port factors out into
 * `Parse.useCachedResult`.
 *
 * Every expectation below is what `@lezer/lr` 1.4.10 itself produces for the
 * same parser table, the same input and the same hand-built fragment.
 *
 * The document is `"12345"`, which a fresh parse reads as a `Number`. Each
 * scenario offers the parser a fragment holding a `String` node covering the
 * same five characters, so "was the cached node reused" is legible directly in
 * the tree: `JsonText(String)` means reused, `JsonText(Number)` means not.
 */
class UseCachedResultTest {

    // `bufferLength` 1 is load-bearing: upstream only builds a
    // `FragmentCursor` when `stream.end - from > bufferLength * 4`, so a
    // five-character document needs a small buffer length for upstream to
    // consider reuse at all. Without it the comparison runs would be vacuous
    // on the upstream side.
    private val parser = jsonParser.configure(ParserConfig(bufferLength = 1))

    private val doc = "12345"

    private fun type(name: String): NodeType = parser.nodeSet.types.first { it.name == name }

    /** Wrap [node] as the sole, zero-offset child of a whole-document fragment. */
    private fun fragmentOf(node: Tree): List<TreeFragment> {
        val root = Tree(NodeType.none, listOf(node), listOf(0), doc.length)
        return listOf(TreeFragment(0, doc.length, root, 0))
    }

    /**
     * Parse [doc] with [fragments], capping the number of `advance()` calls.
     *
     * The cap is what keeps a missing `cached.length` guard from being able to
     * hang CI: reusing a zero-length node does not move `stack.pos`, and every
     * caller of `advanceStack` loops while it returns `true`, so the failure
     * mode this file guards against is an unbounded parse. A complete parse of
     * this five-character document takes a handful of steps; 200 is several
     * orders of magnitude of headroom, and exceeding it fails the test rather
     * than spinning.
     */
    private fun parseBounded(fragments: List<TreeFragment>): Tree {
        val partial = parser.startParse(doc, fragments)
        var steps = 0
        while (true) {
            val tree = partial.advance()
            if (tree != null) return tree
            if (++steps > MAX_ADVANCE_STEPS) {
                fail(
                    "Parse of \"$doc\" did not finish within $MAX_ADVANCE_STEPS " +
                        "advance() calls (parsedPos=${partial.parsedPos})"
                )
            }
        }
    }

    private fun shape(fragments: List<TreeFragment>): String = treeToString(parseBounded(fragments))

    // --- Fixture guards -------------------------------------------------

    @Test
    fun plainParseReadsTheDocumentAsANumber() {
        // If this ever stops holding, every "not reused" expectation below
        // becomes meaningless.
        assertEquals("JsonText(Number)", shape(emptyList()))
    }

    @Test
    fun matchingCachedNodeIsReused() {
        // Positive control for the whole file: the reuse path is genuinely
        // reached with these fragments, so a "not reused" result elsewhere is
        // a decision, not a dead code path.
        val cached = Tree(type("String"), emptyList(), emptyList(), doc.length)
        assertEquals("JsonText(String)", shape(fragmentOf(cached)))
    }

    // --- 1. `cached.length` ---------------------------------------------

    @Test
    fun zeroLengthCachedNodeIsNotReused() {
        // Upstream: `match > -1 && cached.length && ...`. A zero-length node
        // is refused, because `useNode` would not move `stack.pos` and the
        // caller loops while `advanceStack` returns true.
        val cached = Tree(type("String"), emptyList(), emptyList(), 0)
        val tree = parseBounded(fragmentOf(cached))
        val detail = "tree=${treeToString(tree)} length=${tree.length}"
        assertEquals(
            "JsonText(Number)",
            treeToString(tree),
            "Zero-length cached node must not be reused; $detail"
        )
        assertEquals(5, tree.length, "Tree length; $detail")
    }

    // --- 2. `nodeSet.types[cached.type.id] == cached.type` ---------------

    @Test
    fun cachedNodeFromAForeignNodeSetIsNotReused() {
        // Upstream computes `match` only when the cached node's type is the
        // very object this parser's node set holds at that id. A same-id type
        // object from somewhere else (a reconfigured parser, a mixed-language
        // fragment) is refused.
        val foreign = NodeType.define(
            NodeTypeSpec(name = "Foreign", id = type("String").id)
        )
        val cached = Tree(foreign, emptyList(), emptyList(), doc.length)
        val tree = parseBounded(fragmentOf(cached))
        val detail = "tree=${treeToString(tree)} length=${tree.length}"
        assertEquals(
            "JsonText(Number)",
            treeToString(tree),
            "Foreign-node-set cached node must not be reused; $detail"
        )
    }

    // --- 3. strict `contextHash` -----------------------------------------

    private fun contextualParser(startContext: Int) = jsonParser.configure(
        ParserConfig(
            bufferLength = 1,
            contextTracker = ContextTracker(
                start = startContext as Any?,
                hash = { it as Int },
                strict = true
            )
        )
    )

    private fun contextualShape(startContext: Int, cachedHash: Int?): String {
        val contextual = contextualParser(startContext)
        val props = if (cachedHash == null) {
            emptyMap()
        } else {
            mapOf(NodeProp.contextHash.id to cachedHash as Any?)
        }
        val stringType = contextual.nodeSet.types.first { it.name == "String" }
        val cached = Tree(stringType, emptyList(), emptyList(), doc.length, props)
        val root = Tree(NodeType.none, listOf(cached), listOf(0), doc.length)
        val fragments = listOf(TreeFragment(0, doc.length, root, 0))
        val partial = contextual.startParse(doc, fragments)
        var steps = 0
        while (true) {
            val tree = partial.advance()
            if (tree != null) return treeToString(tree)
            if (++steps > MAX_ADVANCE_STEPS) {
                fail("Contextual parse of \"$doc\" did not finish in $MAX_ADVANCE_STEPS steps")
            }
        }
    }

    @Test
    fun strictContextTrackerAllowsReuseWhenTheHashMatches() {
        // Positive control for the two expectations below: with a strict
        // tracker in place, a matching hash still reuses.
        assertEquals("JsonText(String)", contextualShape(7, 7))
    }

    @Test
    fun strictContextTrackerBlocksReuseWhenTheHashDiffers() {
        assertEquals("JsonText(Number)", contextualShape(7, 5))
    }

    @Test
    fun strictContextTrackerBlocksReuseWhenTheNodeCarriesNoHash() {
        // Upstream reads a missing `contextHash` prop as 0, which does not
        // match a non-zero stack context hash.
        assertEquals("JsonText(Number)", contextualShape(7, null))
    }

    // --- 4. descent into a zero-offset first child ------------------------

    @Test
    fun reuseDescendsIntoAZeroOffsetFirstChild() {
        // The outer `Property` has no goto from the start state, so upstream
        // retries with its first child, which starts at offset 0 and does
        // match. Giving up on the outer node loses that reuse.
        val inner = Tree(type("String"), emptyList(), emptyList(), doc.length)
        val outer = Tree(
            type("Property"),
            listOf(inner),
            listOf(0),
            doc.length
        )
        val tree = parseBounded(fragmentOf(outer))
        val detail = "tree=${treeToString(tree)} length=${tree.length}"
        assertEquals(
            "JsonText(String)",
            treeToString(tree),
            "Reuse must descend into a zero-offset first child; $detail"
        )
    }

    @Test
    fun reuseDoesNotDescendIntoAnOffsetFirstChild() {
        // Guard on the descent's own condition: upstream only descends when
        // `positions[0] == 0`. A first child that starts later is not a
        // stand-in for the node at this position.
        val inner = Tree(type("String"), emptyList(), emptyList(), doc.length - 1)
        val outer = Tree(
            type("Property"),
            listOf(inner),
            listOf(1),
            doc.length
        )
        assertEquals("JsonText(Number)", shape(fragmentOf(outer)))
    }

    private companion object {
        const val MAX_ADVANCE_STEPS = 200
    }
}
