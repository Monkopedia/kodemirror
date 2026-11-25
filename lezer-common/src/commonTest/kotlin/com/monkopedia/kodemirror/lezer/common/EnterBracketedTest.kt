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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [IterMode.ENTER_BRACKETED] handling. Expected values are from npm `@lezer/common` 1.5.1 run
 * on the same fixture.
 *
 * ```
 * Doc(0-30)
 *   Text(0-4)
 *   Script(4-24)       mounted overlay over doc 10-14 and 18-22
 *     Attr(4-10) Attr(10-14) Attr(14-18) Attr(18-22) Attr(22-24)
 *   Text(24-30)
 *
 * mounted inner tree: Expr(10-22) > Ident(10-14) Ident(18-22)
 * ```
 *
 * and, for non-overlay mounts, an HTML-like `<script>` element:
 *
 * ```
 * Doc(0-20) > Element(0-20) > OpenTag(0-8) ScriptText(8-11) CloseTag(11-20)
 *   ScriptText has a full mount: Script(8-11) > Var(8-9)
 * ```
 */
class EnterBracketedTest {

    private val doc = NodeType.define(NodeTypeSpec(name = "Doc", id = 0))
    private val text = NodeType.define(NodeTypeSpec(name = "Text", id = 1))
    private val script = NodeType.define(NodeTypeSpec(name = "Script", id = 2))
    private val attr = NodeType.define(NodeTypeSpec(name = "Attr", id = 3))
    private val expr = NodeType.define(NodeTypeSpec(name = "Expr", id = 4))
    private val ident = NodeType.define(NodeTypeSpec(name = "Ident", id = 5))

    private val dummyParser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse = error("Not implemented")
    }

    private fun leaf(type: NodeType, length: Int) = Tree(type, emptyList(), emptyList(), length)

    private fun mixedTree(bracketed: Boolean = true): Tree {
        val inner = Tree(expr, listOf(leaf(ident, 4), leaf(ident, 4)), listOf(0, 8), 12)
        val mounted = MountedTree(
            inner,
            // Relative to Script (doc 4): doc 10-14 and doc 18-22.
            listOf(TextRange(6, 10), TextRange(14, 18)),
            dummyParser,
            bracketed
        )
        val scriptTree = Tree(
            script,
            listOf(leaf(attr, 6), leaf(attr, 4), leaf(attr, 4), leaf(attr, 4), leaf(attr, 2)),
            listOf(0, 6, 10, 14, 18),
            20,
            mapOf(NodeProp.mounted.id to mounted)
        )
        return Tree(
            doc,
            listOf(leaf(text, 4), scriptTree, leaf(text, 6)),
            listOf(0, 4, 24),
            30
        )
    }

    private val element = NodeType.define(NodeTypeSpec(name = "Element", id = 6))
    private val openTag = NodeType.define(NodeTypeSpec(name = "OpenTag", id = 7))
    private val scriptText = NodeType.define(NodeTypeSpec(name = "ScriptText", id = 8))
    private val closeTag = NodeType.define(NodeTypeSpec(name = "CloseTag", id = 9))
    private val jsScript = NodeType.define(NodeTypeSpec(name = "Script", id = 10))
    private val jsVar = NodeType.define(NodeTypeSpec(name = "Var", id = 11))

    private fun scriptElement(bracketed: Boolean): SyntaxNode {
        val js = Tree(jsScript, listOf(leaf(jsVar, 1)), listOf(0), 3)
        val text = Tree(
            scriptText,
            emptyList(),
            emptyList(),
            3,
            mapOf(NodeProp.mounted.id to MountedTree(js, null, dummyParser, bracketed))
        )
        val el =
            Tree(element, listOf(leaf(openTag, 8), text, leaf(closeTag, 9)), listOf(0, 8, 11), 20)
        return assertNotNull(Tree(doc, listOf(el), listOf(0), 20).topNode.firstChild)
    }

    /** `pos,side=result` for [SyntaxNode.enter] at every combination, as the npm probe prints. */
    private fun enterTable(node: SyntaxNode, positions: List<Int>, mode: Int) =
        positions.flatMap { pos ->
            listOf(-1, 0, 1).map { side ->
                val n = node.enter(pos, side, mode)
                "$pos,$side=" + (n?.let { "${it.name}(${it.from}-${it.to})" } ?: "null")
            }
        }.joinToString(" ")

    private fun TreeCursor.describe() = "$name($from-$to)"

    @Test
    fun cursorDoesNotKeepEnterBracketed() {
        // Doc 14 is the end of the first overlay range. Only ENTER_BRACKETED makes a side-0
        // `enter` accept it, and the cursor drops that flag when it is created (#352).
        val cursor = mixedTree().cursor(IterMode.ENTER_BRACKETED)
        cursor.firstChild()
        cursor.nextSibling()
        assertEquals("Script(4-24)", cursor.describe())
        assertFalse(cursor.enter(14, 0))
        assertEquals("Script(4-24)", cursor.describe())
    }

    /**
     * With ENTER_BRACKETED, an overlay mount is entered at the edges of its ranges only when the
     * mount is bracketed (#331).
     */
    @Test
    fun enterBracketedOnlyRelaxesBracketedOverlays() {
        assertEquals(
            "10,-1=Attr(4-10) 10,0=null 10,1=Expr(10-22) 14,-1=Expr(10-22) 14,0=null " +
                "14,1=Attr(14-18) 18,-1=Attr(14-18) 18,0=null 18,1=Expr(10-22) " +
                "22,-1=Expr(10-22) 22,0=null 22,1=Attr(22-24)",
            enterTable(
                assertNotNull(mixedTree(false).topNode.childAfter(5)),
                listOf(10, 14, 18, 22),
                0
            ),
            "bracketed=false, mode=0"
        )
        assertEquals(
            "10,-1=Attr(4-10) 10,0=null 10,1=Expr(10-22) 14,-1=Expr(10-22) 14,0=null " +
                "14,1=Attr(14-18) 18,-1=Attr(14-18) 18,0=null 18,1=Expr(10-22) " +
                "22,-1=Expr(10-22) 22,0=null 22,1=Attr(22-24)",
            enterTable(
                assertNotNull(mixedTree(false).topNode.childAfter(5)),
                listOf(10, 14, 18, 22),
                IterMode.ENTER_BRACKETED
            ),
            "bracketed=false, mode=EB"
        )
        assertEquals(
            "10,-1=Attr(4-10) 10,0=null 10,1=Expr(10-22) 14,-1=Expr(10-22) 14,0=null " +
                "14,1=Attr(14-18) 18,-1=Attr(14-18) 18,0=null 18,1=Expr(10-22) " +
                "22,-1=Expr(10-22) 22,0=null 22,1=Attr(22-24)",
            enterTable(
                assertNotNull(mixedTree(true).topNode.childAfter(5)),
                listOf(10, 14, 18, 22),
                0
            ),
            "bracketed=true, mode=0"
        )
        assertEquals(
            "10,-1=Expr(10-22) 10,0=Expr(10-22) 10,1=Expr(10-22) 14,-1=Expr(10-22) " +
                "14,0=Expr(10-22) 14,1=Expr(10-22) 18,-1=Expr(10-22) 18,0=Expr(10-22) " +
                "18,1=Expr(10-22) 22,-1=Expr(10-22) 22,0=Expr(10-22) 22,1=Expr(10-22)",
            enterTable(
                assertNotNull(mixedTree(true).topNode.childAfter(5)),
                listOf(10, 14, 18, 22),
                IterMode.ENTER_BRACKETED
            ),
            "bracketed=true, mode=EB"
        )
    }

    /**
     * With ENTER_BRACKETED, a child holding a bracketed non-overlay mount is entered at its edges,
     * so `enter` at either end of a `<script>` body lands in the script (#462).
     */
    @Test
    fun enterBracketedEntersBracketedMountAtItsEdges() {
        assertEquals(
            "7,-1=OpenTag(0-8) 7,0=OpenTag(0-8) 7,1=OpenTag(0-8) 8,-1=OpenTag(0-8) 8,0=null " +
                "8,1=Script(8-11) 9,-1=Script(8-11) 9,0=Script(8-11) 9,1=Script(8-11) " +
                "11,-1=Script(8-11) 11,0=null 11,1=CloseTag(11-20) 12,-1=CloseTag(11-20) " +
                "12,0=CloseTag(11-20) 12,1=CloseTag(11-20)",
            enterTable(scriptElement(false), listOf(7, 8, 9, 11, 12), 0),
            "bracketed=false, mode=0"
        )
        assertEquals(
            "7,-1=OpenTag(0-8) 7,0=OpenTag(0-8) 7,1=OpenTag(0-8) 8,-1=OpenTag(0-8) 8,0=null " +
                "8,1=Script(8-11) 9,-1=Script(8-11) 9,0=Script(8-11) 9,1=Script(8-11) " +
                "11,-1=Script(8-11) 11,0=null 11,1=CloseTag(11-20) 12,-1=CloseTag(11-20) " +
                "12,0=CloseTag(11-20) 12,1=CloseTag(11-20)",
            enterTable(scriptElement(false), listOf(7, 8, 9, 11, 12), IterMode.ENTER_BRACKETED),
            "bracketed=false, mode=EB"
        )
        assertEquals(
            "7,-1=OpenTag(0-8) 7,0=OpenTag(0-8) 7,1=OpenTag(0-8) 8,-1=OpenTag(0-8) 8,0=null " +
                "8,1=Script(8-11) 9,-1=Script(8-11) 9,0=Script(8-11) 9,1=Script(8-11) " +
                "11,-1=Script(8-11) 11,0=null 11,1=CloseTag(11-20) 12,-1=CloseTag(11-20) " +
                "12,0=CloseTag(11-20) 12,1=CloseTag(11-20)",
            enterTable(scriptElement(true), listOf(7, 8, 9, 11, 12), 0),
            "bracketed=true, mode=0"
        )
        assertEquals(
            "7,-1=OpenTag(0-8) 7,0=OpenTag(0-8) 7,1=OpenTag(0-8) 8,-1=OpenTag(0-8) " +
                "8,0=Script(8-11) 8,1=Script(8-11) 9,-1=Script(8-11) 9,0=Script(8-11) " +
                "9,1=Script(8-11) 11,-1=Script(8-11) 11,0=Script(8-11) 11,1=Script(8-11) " +
                "12,-1=CloseTag(11-20) 12,0=CloseTag(11-20) 12,1=CloseTag(11-20)",
            enterTable(scriptElement(true), listOf(7, 8, 9, 11, 12), IterMode.ENTER_BRACKETED),
            "bracketed=true, mode=EB"
        )
    }

    /**
     * The edge rule for mounted children only covers non-overlay mounts: a child whose bracketed
     * mount is an overlay is not entered at its edges (#462).
     */
    @Test
    fun enterBracketedSkipsOverlayMountsAtChildEdges() {
        val top = mixedTree(bracketed = true).topNode
        assertNull(top.enter(4, 0, IterMode.ENTER_BRACKETED))
        assertNull(top.enter(24, 0, IterMode.ENTER_BRACKETED))
    }

    /** Mounts are not bracketed unless the nest says so, as upstream's `bracketed = false`. */
    @Test
    fun mountedTreeIsNotBracketedByDefault() {
        assertFalse(MountedTree(leaf(ident, 1), null, dummyParser).bracketed)
    }
}
