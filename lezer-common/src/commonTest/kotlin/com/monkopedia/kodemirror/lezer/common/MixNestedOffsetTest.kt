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
 * `parseMixed` nested inside another `parseMixed` — the three-deep shape used by every
 * template language whose expressions are a third language (`:lang-vue` and `:lang-angular`
 * are `html` -> template grammar -> `javascript`).
 *
 * A parse started over ranges builds its tree in coordinates relative to `ranges[0].from`
 * (`@lezer/lr`'s `Parse.finish` passes `start = this.ranges[0].from` to `Tree.build`), so
 * `MixedParse.startInner` has to walk that tree from an origin of `ranges[0].from` to get
 * document coordinates back. At the top level `ranges[0].from` is 0 and the distinction is
 * invisible; one level down it is the mounting node's start, and getting it wrong shifts the
 * ranges handed to the *next* parser down by exactly that much — so the innermost parser
 * silently lexes the wrong characters (#341).
 *
 * ## What these tests assert
 *
 * The property is **offset invariance**: the same fragment, parsed at several different
 * document offsets, must produce the same tree shape with correspondingly shifted ranges.
 * That is the mechanism itself, and it cannot pass vacuously — a shifted read produces a
 * different shape at every offset but zero.
 *
 * Asserting one expected tree would be weaker. Under the defect the mounted subtree keeps its
 * *length*, so it re-bases onto the correct document range at its mount point: the node
 * positions look right and only the node **types** — which are derived from the characters
 * actually read — are wrong. `assertOffsetInvariant` therefore compares full shape, not just
 * positions.
 *
 * ## Fixture
 *
 * ```
 * <pad>[[abc123]]<tail>
 * ```
 *
 * - `outerParser` (level 1) finds `[[` ... `]]` and emits `Outer` over the brackets and their
 *   contents. It nests `midParser` on that node.
 * - `midParser` (level 2) emits `Mid` over its whole range with an `Inner` child covering the
 *   contents between the two-character brackets. It nests `charParser` on `Inner`.
 * - `charParser` (level 3) emits one leaf per character, typed `Letter` or `Digit`, so its
 *   output is a direct readout of the text it was given.
 *
 * `abc123` must therefore always come back as `Letter Letter Letter Digit Digit Digit`. With
 * the origin lost, level 3 is asked for `doc[from - pad, to - pad]`: at `pad = "xx"` that is
 * `[[abc1`, and the readout changes to `Other Other Letter Letter Letter Digit`.
 */
class MixNestedOffsetTest {

    // ---- Node types ----

    private val outerTop = NodeType.define(NodeTypeSpec(name = "Doc", id = 0, top = true))
    private val outerNode = NodeType.define(NodeTypeSpec(name = "Outer", id = 1))
    private val midTop = NodeType.define(NodeTypeSpec(name = "Mid", id = 2, top = true))
    private val innerNode = NodeType.define(NodeTypeSpec(name = "Inner", id = 3))
    private val charTop = NodeType.define(NodeTypeSpec(name = "Chars", id = 4, top = true))
    private val letter = NodeType.define(NodeTypeSpec(name = "Letter", id = 5))
    private val digit = NodeType.define(NodeTypeSpec(name = "Digit", id = 6))
    private val other = NodeType.define(NodeTypeSpec(name = "Other", id = 7))

    /**
     * A parse that reports its tree in coordinates relative to `ranges[0].from`, the way
     * `@lezer/lr` does, and builds it from the text it is handed. Single contiguous range —
     * these fixtures use no overlays, so `punchRanges` never splits one.
     */
    private class ImmediateParse(val tree: Tree) : PartialParse {
        override var stoppedAt: Int? = null
            private set
        override val parsedPos: Int get() = tree.length
        override fun stopAt(pos: Int) {
            stoppedAt = pos
        }
        override fun advance(): Tree = tree
    }

    private fun parserOf(build: (text: String) -> Tree): Parser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse {
            val from = ranges.first().from
            val to = ranges.last().to
            return ImmediateParse(build(input.read(from, to)))
        }
    }

    /** Level 3: one leaf per character, typed by what that character actually is. */
    private val charParser: Parser = parserOf { text ->
        Tree(
            charTop,
            text.map { c ->
                val type = when {
                    c in 'a'..'z' -> letter
                    c in '0'..'9' -> digit
                    else -> other
                }
                Tree(type, emptyList(), emptyList(), 1)
            },
            text.indices.toList(),
            text.length
        )
    }

    /**
     * Level 2: `Mid` over the whole range with an `Inner` child covering everything but the
     * two-character brackets at each end.
     */
    private val midBase: Parser = parserOf { text ->
        Tree(
            midTop,
            listOf(Tree(innerNode, emptyList(), emptyList(), text.length - 4)),
            listOf(2),
            text.length
        )
    }

    private val midParser: Parser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse = parseMixed { node, _ ->
            // Name-based, like `:lang-vue`'s `"Text" -> textMixed`: the nesting decision itself
            // must not depend on the coordinates under test, so that a failure isolates the
            // range handed to the inner parser rather than whether nesting happened at all.
            if (node.name == "Inner") NestedParse(parser = charParser) else null
        }(midBase.createParse(input, fragments, ranges), input, fragments, ranges)
    }

    /** Level 1: `Outer` over `[[` ... `]]`, with `midParser` nested on it. */
    private val outerBase: Parser = parserOf { text ->
        val start = text.indexOf("[[")
        val end = text.indexOf("]]") + 2
        Tree(
            outerTop,
            listOf(Tree(outerNode, emptyList(), emptyList(), end - start)),
            listOf(start),
            text.length
        )
    }

    private val outerParser: Parser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse = parseMixed { node, _ ->
            if (node.name == "Outer") NestedParse(parser = midParser) else null
        }(outerBase.createParse(input, fragments, ranges), input, fragments, ranges)
    }

    // ---- Helpers ----

    /** Full shape including ranges, descending through mounted trees. */
    private fun shape(tree: Tree): String = buildString {
        tree.iterate(
            IterateSpec(
                enter = { node ->
                    if (isNotEmpty()) append(' ')
                    append("${node.name}[${node.from},${node.to}]")
                    null
                }
            )
        )
    }

    /** Just the node names, so a shape can be compared across offsets. */
    private fun names(tree: Tree): String = buildString {
        tree.iterate(
            IterateSpec(
                enter = { node ->
                    if (isNotEmpty()) append(' ')
                    append(node.name)
                    null
                }
            )
        )
    }

    private fun parseAt(pad: String, body: String = "abc123", tail: String = "!") =
        outerParser.parse("$pad[[$body]]$tail")

    /**
     * Asserts the property: identical markup at different document offsets yields identical
     * node names, and ranges that differ by exactly the offset.
     */
    private fun assertOffsetInvariant(pads: List<String>) {
        val reference = parseAt(pads.first())
        val referenceNames = names(reference)
        val referencePad = pads.first().length
        for (pad in pads.drop(1)) {
            val tree = parseAt(pad)
            assertEquals(
                referenceNames,
                names(tree),
                "same fragment at offset ${pad.length} produced a different tree shape than at " +
                    "offset $referencePad — the nested parse read shifted text"
            )
            // The document root always starts at 0 and simply grows with the padding, so it is
            // the one node whose range does not translate; every node inside it must.
            val delta = pad.length - referencePad
            assertEquals(
                shiftRanges(shape(reference).substringAfter(' '), delta),
                shape(tree).substringAfter(' '),
                "ranges at offset ${pad.length} are not the offset-$referencePad ranges " +
                    "shifted by $delta"
            )
        }
    }

    private val rangeRegex = Regex("""\[(\d+),(\d+)]""")

    private fun shiftRanges(s: String, delta: Int): String = rangeRegex.replace(s) { m ->
        "[${m.groupValues[1].toInt() + delta},${m.groupValues[2].toInt() + delta}]"
    }

    // ---- Tests ----

    @Test
    fun oneLevelOfNestingIsUnaffected() {
        // Control: level 1 -> level 2 only. `ranges[0].from` is 0 for the outer parse, so this
        // path is correct with or without the fix, and its passing is what made the defect
        // invisible.
        val single = object : Parser() {
            override fun createParse(
                input: Input,
                fragments: List<TreeFragment>,
                ranges: List<TextRange>
            ): PartialParse = parseMixed { node, _ ->
                if (node.name == "Outer") NestedParse(parser = midBase) else null
            }(outerBase.createParse(input, fragments, ranges), input, fragments, ranges)
        }
        // `iterate` descends through a non-overlay mount, so `Outer` is replaced by the `Mid`
        // tree mounted on it — the level-2 parse is reported directly at document coordinates.
        assertEquals(
            "Doc[0,15] Mid[2,12] Inner[4,10]",
            shape(single.parse("xx[[abc123]]!!!"))
        )
    }

    @Test
    fun twoLevelsDeepReadsTheDocumentAtTheRightOffset() {
        assertEquals(
            "Doc[0,11] Mid[0,10] Chars[2,8] " +
                "Letter[2,3] Letter[3,4] Letter[4,5] Digit[5,6] Digit[6,7] Digit[7,8]",
            shape(parseAt(""))
        )
        assertEquals(
            "Doc[0,13] Mid[2,12] Chars[4,10] " +
                "Letter[4,5] Letter[5,6] Letter[6,7] Digit[7,8] Digit[8,9] Digit[9,10]",
            shape(parseAt("xx"))
        )
    }

    @Test
    fun twoLevelsDeepIsOffsetInvariant() {
        assertOffsetInvariant(listOf("", "x", "xx", "xxxxxxx", "xxxxxxxxxxxxxxxxxxxx"))
    }

    @Test
    fun offsetInvariantForAllDigitContent() {
        // A body whose readout differs from the reference body, so a fixture that accidentally
        // agreed with a shifted read of `abc123` cannot carry the suite.
        val pads = listOf("", "xy", "xyzxyzx")
        val reference = names(outerParser.parse("[[9a8b7c]]!"))
        assertEquals("Doc Mid Chars Digit Letter Digit Letter Digit Letter", reference)
        for (pad in pads.drop(1)) {
            assertEquals(
                reference,
                names(outerParser.parse("$pad[[9a8b7c]]!")),
                "offset ${pad.length} changed the readout of the innermost parse"
            )
        }
    }

    @Test
    fun cursorOverAnOffsetRootReportsDocumentCoordinatesBeforeMoving() {
        // `startInner` calls `nest(cursor, input)` on the root of the base tree before moving the
        // cursor anywhere, so the root's own `from`/`to` are observable — a nest predicate whose
        // node name matches the nested grammar's top rule reads them directly, and `input.read`
        // against them would be off by the offset. Asserted here rather than through a parse
        // because none of the fixtures above nest on a top node.
        val tree = Tree(
            midTop,
            listOf(Tree(innerNode, emptyList(), emptyList(), 6)),
            listOf(2),
            10
        )
        val cursor = TreeCursor(TreeNode(tree, 7, null, 0), IterMode.INCLUDE_ANONYMOUS)
        assertEquals("Mid[7,17]", "${cursor.name}[${cursor.from},${cursor.to}]")
        cursor.firstChild()
        assertEquals("Inner[9,15]", "${cursor.name}[${cursor.from},${cursor.to}]")
        cursor.parent()
        assertEquals("Mid[7,17]", "${cursor.name}[${cursor.from},${cursor.to}]")

        // And the Tree-rooted convenience constructor keeps its zero origin.
        val plain = TreeCursor(tree, IterMode.INCLUDE_ANONYMOUS)
        assertEquals("Mid[0,10]", "${plain.name}[${plain.from},${plain.to}]")
    }

    @Test
    fun shiftedReadIsNotDetectableFromPositionsAlone() {
        // A negative control, and the standing argument against a weaker bar: this assertion
        // passes both WITH and WITHOUT the #341 fix (verified — it was the one test in this
        // class that stayed green against the defect). The mounted subtree keeps its length and
        // re-bases onto the correct document range, so positions and node count come out
        // identical to a correct parse and only the node types differ. Angular's
        // `(click)="go()"` was the real-world instance: it lexed `clic` into a bare
        // four-character `VariableName` sitting on the right four-character range, with no error
        // node anywhere in the tree. Any future test here must assert node names, not spans.
        val tree = parseAt("xx")
        val positions = buildString {
            tree.iterate(
                IterateSpec(
                    enter = { node ->
                        if (isNotEmpty()) append(' ')
                        append("[${node.from},${node.to}]")
                        null
                    }
                )
            )
        }
        assertEquals(
            "[0,13] [2,12] [4,10] [4,5] [5,6] [6,7] [7,8] [8,9] [9,10]",
            positions,
            "positions must stay correct — this is the assertion that does NOT catch #341"
        )
    }
}
