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

    private fun mixedTree(): Tree {
        val inner = Tree(expr, listOf(leaf(ident, 4), leaf(ident, 4)), listOf(0, 8), 12)
        val mounted = MountedTree(
            inner,
            // Relative to Script (doc 4): doc 10-14 and doc 18-22.
            listOf(TextRange(6, 10), TextRange(14, 18)),
            dummyParser
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
}
