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
package com.monkopedia.kodemirror.lezer.markdown

import com.monkopedia.kodemirror.lezer.common.NodeProp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `CompositeBlock.toTree` balances every block, passing a `makeTree` that
 * builds *anonymous* grouping nodes. A block with more than
 * `BALANCE_BRANCH_FACTOR` (8) children therefore goes through `balanceRange`,
 * where the top node must keep the block's own type.
 *
 * Every expectation is the `Tree.toString()` of the same document parsed by
 * `@lezer/markdown@1.7.0`.
 */
class BalanceShapeTest {

    private fun parse(input: String): String = treeToString(markdownParser.parse(input))

    private fun bullets(n: Int) = (1..n).joinToString("\n") { "- item $it" } + "\n"

    @Test
    fun bulletListWithEightItemsKeepsItsNodeType() {
        // Control: at exactly the branch factor `balance()` short-circuits.
        val item = "ListItem(ListMark,Paragraph)"
        assertEquals(
            "Document(BulletList(" + List(8) { item }.joinToString(",") + "))",
            parse(bullets(8))
        )
    }

    @Test
    fun bulletListWithMoreThanEightItemsKeepsItsNodeType() {
        val item = "ListItem(ListMark,Paragraph)"
        assertEquals(
            "Document(BulletList(" + List(12) { item }.joinToString(",") + "))",
            parse(bullets(12))
        )
    }

    @Test
    fun orderedListWithMoreThanEightItemsKeepsItsNodeType() {
        val doc = (1..12).joinToString("\n") { "$it. item" } + "\n"
        val item = "ListItem(ListMark,Paragraph)"
        assertEquals(
            "Document(OrderedList(" + List(12) { item }.joinToString(",") + "))",
            parse(doc)
        )
    }

    @Test
    fun blockquoteWithMoreThanEightChildrenKeepsItsNodeType() {
        val doc = (1..12).joinToString("\n>\n") { "> para $it" } + "\n"
        assertEquals(
            "Document(Blockquote(QuoteMark,Paragraph" +
                List(11) { ",QuoteMark,QuoteMark,Paragraph" }.joinToString("") +
                "))",
            parse(doc)
        )
    }

    @Test
    fun balancedBlockKeepsItsContextHash() {
        // `CompositeBlock.toTree` passes `hashProp` to both the block itself
        // and its `makeTree`; balancing must not drop it from the top node.
        val tree = markdownParser.parse(bullets(12))
        val list = tree.topNode.firstChild
        assertNotNull(list)
        assertEquals("BulletList", list.name)
        assertNotNull(
            list.tree?.prop(NodeProp.contextHash),
            "balanced block lost its contextHash"
        )
    }
}
