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
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity with `@lezer/lr` 1.4.10's `LRParse.advanceStack`
 * (`dist/index.js:1406-1457`) for the order of its steps and the routing of
 * split stacks.
 *
 * Upstream runs, in this order: the `stoppedAt` brake, fragment reuse, the
 * default reduce (which returns), the cut-depth guard, and only then
 * `this.tokens.getActions(stack)`. The port used to call `getActions` first,
 * which both tokenised in states upstream never tokenises in and made the
 * fragment-reuse block conditional on a main token upstream does not have
 * there.
 *
 * Every expectation below is what `@lezer/lr` 1.4.10 itself produces for the
 * same parser table and the same input.
 */
class AdvanceStackTest {

    // --- 1. `getActions` is called after the default-reduce branch ---

    /**
     * The document is one `X` followed by six `Wide` tokens. Tokenising at the
     * position after the first `X` scans [WIDE_SCAN_PEEK] characters ahead but
     * accepts only [WIDE_SCAN_TOKEN_LENGTH] of them, so the token cache records
     * a lookahead 40 characters past that position while the token itself ends
     * 8 characters past it.
     *
     * Both orders record the same lookahead values (41 at position 1, 49 at
     * position 9); what differs is *where in the stack buffer* the record
     * lands. `Stack.setLookAhead` writes it at the current buffer position, and
     * the state at position 1 has a default reduce — so calling `getActions`
     * first puts the record inside the node that reduce is about to build,
     * where upstream puts it after that node. `Tree.build` reads the buffer
     * backwards and gives a node the last record it passed, so the port's
     * `Item` picks up the *next* position's 49 instead of its own 41: it claims
     * to depend on 40 characters past its end rather than 32. That number is
     * what `TreeFragment.applyChanges` consults to decide how far an edit
     * invalidates reuse.
     *
     * `bufferLength` 1 is load-bearing: `NodeProp.lookAhead` is only kept on
     * nodes that are materialised as `Tree` objects, not on nodes packed into a
     * buffer.
     */
    @Test
    fun defaultReduceStateDoesNotRecordLookahead() {
        val doc = "x" + "w".repeat(48)
        val tree = wideScanParser.configure(ParserConfig(bufferLength = 1)).parse(doc)
        assertEquals(
            "Program(Item(Wrap(Inner(X)))," +
                "Item[la=32](Wrap[la=32](Inner[la=32](Wide[la=32])))," +
                "Item[la=32](Wrap[la=32](Inner[la=32](Wide[la=32])))," +
                "Item(Wrap(Inner(Wide))),Item(Wrap(Inner(Wide)))," +
                "Item(Wrap(Inner(Wide))),Item(Wrap(Inner(Wide))))",
            dumpWithLookAhead(tree),
            "Recorded lookahead"
        )
        assertEquals(49, tree.length)
    }

    /**
     * Control for [defaultReduceStateDoesNotRecordLookahead]: the same grammar
     * and tokenizer on a document short enough that no scan ever reaches
     * `Lookahead.MARGIN` past its token, so no lookahead is recorded on either
     * side. Without this, the assertion above could be passing because the
     * fixture never records anything.
     */
    @Test
    fun wideScanGrammarRecordsNoLookaheadOnAShortDocument() {
        val doc = "x" + "w".repeat(16)
        val tree = wideScanParser.configure(ParserConfig(bufferLength = 1)).parse(doc)
        assertEquals(
            "Program(Item(Wrap(Inner(X))),Item(Wrap(Inner(Wide)))," +
                "Item(Wrap(Inner(Wide))))",
            dumpWithLookAhead(tree),
            "Short document records no lookahead"
        )
        assertEquals(17, tree.length)
    }

    // --- 2. Split stacks that advanced `pos` go to the caller's list ---

    /**
     * Three `Num` tokens in a grammar that cannot decide between `X { Num }`
     * and `Y { Num Num }` fork the parse. Upstream applies the *last* action to
     * the stack it was given and forks the earlier ones, sending any fork that
     * consumed input straight to the caller's `newStacks`; the port applied the
     * *first* action to the given stack and appended every fork to the list it
     * was iterating.
     *
     * Both end up with the same set of stacks, but in the opposite order, and
     * `advance`'s pruning keeps the *last* of two stacks it considers equally
     * good — so the order decides which of the two readings of `"1 1 1"`
     * survives.
     */
    @Test
    fun ambiguitySplitsResolveLikeUpstream() {
        assertEquals(
            "Program(X(Num),Y(Num,Num))",
            treeToString(ambiguousParser.parse("1 1 1")),
            "Ambiguous parse of three numbers"
        )
        assertEquals(
            "Program(X(Num),X(Num),Y(Num,Num))",
            treeToString(ambiguousParser.parse("1 1 1 1")),
            "Ambiguous parse of four numbers"
        )
        assertEquals(
            "Program(X(Num),Y(Num,Num))",
            treeToString(ambiguousParser.parse("22 333 1")),
            "Ambiguous parse of three multi-digit numbers"
        )
    }

    /**
     * Control for [ambiguitySplitsResolveLikeUpstream]: inputs this grammar
     * parses without an ambiguity, which agree on either routing. They guard
     * the fixture itself — a mis-transcribed table would make the expectations
     * above meaningless.
     */
    @Test
    fun ambiguousGrammarParsesUnambiguousInput() {
        assertEquals("Program(X(Num))", treeToString(ambiguousParser.parse("1")))
        assertEquals("Program(Y(Num,Num))", treeToString(ambiguousParser.parse("1 1")))
    }

    // --- 3. Fragment reuse is not gated on having tokenised ---

    private val jsonSmallBuffer = jsonParser.configure(ParserConfig(bufferLength = 1))

    private fun stringFragment(doc: String): List<TreeFragment> {
        val stringType = jsonSmallBuffer.nodeSet.types.first { it.name == "String" }
        val cached = Tree(stringType, emptyList(), emptyList(), doc.length)
        val root = Tree(NodeType.none, listOf(cached), listOf(0), doc.length)
        return listOf(TreeFragment(0, doc.length, root, 0))
    }

    /**
     * `"@@@@@"` contains no token this grammar can produce, so `getActions`
     * leaves `mainToken` null there. Upstream has not tokenised at that point
     * at all — it reaches the fragment-reuse block first — and reuses the
     * cached `String` node. The port's `main != null` gate, which only exists
     * because it tokenised early, refused the reuse and dropped into error
     * recovery instead.
     */
    @Test
    fun cachedNodeIsReusedWhereNoTokenMatches() {
        val doc = "@@@@@"
        assertEquals(
            "JsonText(String)",
            treeToString(jsonSmallBuffer.parse(doc, stringFragment(doc))),
            "Reuse at a position with no matching token"
        )
    }

    /**
     * Control for [cachedNodeIsReusedWhereNoTokenMatches]: without a fragment
     * the same document is an error, so the expectation above is about reuse
     * and not about the grammar quietly accepting `"@"`.
     */
    @Test
    fun untokenizableDocumentIsAnErrorWithoutAFragment() {
        assertEquals(
            "JsonText(\u26A0)",
            treeToString(jsonSmallBuffer.parse("@@@@@")),
            "Fresh parse of an untokenizable document"
        )
    }

    /**
     * Control for [cachedNodeIsReusedWhereNoTokenMatches]: the same fragment
     * over a document that *does* tokenise is reused on either side, so the
     * fragment and the cursor are known to work and a refusal above is a
     * decision rather than a dead path.
     */
    @Test
    fun cachedNodeIsReusedWhereATokenMatches() {
        val doc = "12345"
        assertEquals(
            "JsonText(Number)",
            treeToString(jsonSmallBuffer.parse(doc)),
            "Fresh parse reads the document as a number"
        )
        assertEquals(
            "JsonText(String)",
            treeToString(jsonSmallBuffer.parse(doc, stringFragment(doc))),
            "Reuse at a position with a matching token"
        )
    }
}

/**
 * Render [tree] as `Name[la=N](child,child)`, where `[la=N]` is the node's
 * `NodeProp.lookAhead` when it carries one.
 *
 * Unlike [treeToString] this keeps every node the cursor visits, since the
 * point of the comparison is which node the recorded lookahead lands on.
 */
private fun dumpWithLookAhead(tree: Tree): String {
    val cursor = tree.cursor()
    fun render(): String {
        val name = if (cursor.type.isError) "\u26A0" else cursor.name
        val lookAhead = cursor.tree?.prop(NodeProp.lookAhead)
        val children = mutableListOf<String>()
        if (cursor.firstChild()) {
            do {
                children.add(render())
            } while (cursor.nextSibling())
            cursor.parent()
        }
        return buildString {
            append(name)
            if (lookAhead != null) append("[la=").append(lookAhead).append("]")
            if (children.isNotEmpty()) {
                append("(").append(children.joinToString(",")).append(")")
            }
        }
    }
    return render()
}
