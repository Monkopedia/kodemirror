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
package com.monkopedia.kodemirror.lezer.highlight

import com.monkopedia.kodemirror.lezer.common.NodeSet
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.NodeTypeSpec
import com.monkopedia.kodemirror.lezer.common.Tree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighlightTest {

    // Helper to build a simple tree with highlight tags
    private fun buildTestTree(): Pair<Tree, HighlightTestData> {
        val types = listOf(
            NodeType.define(NodeTypeSpec(name = "Program", id = 0, top = true)),
            NodeType.define(NodeTypeSpec(name = "Keyword", id = 1)),
            NodeType.define(NodeTypeSpec(name = "String", id = 2)),
            NodeType.define(NodeTypeSpec(name = "Number", id = 3)),
            NodeType.define(NodeTypeSpec(name = "Comment", id = 4)),
            NodeType.define(NodeTypeSpec(name = "Identifier", id = 5))
        )

        val nodeSet = NodeSet(types)
        val extended = nodeSet.extend(
            styleTags(
                mapOf(
                    "Keyword" to tags.keyword,
                    "String" to tags.string,
                    "Number" to tags.number,
                    "Comment" to tags.comment,
                    "Identifier" to tags.variableName
                )
            )
        )

        // Build tree: "let x = 42" (positions: let=0-3, x=4-5, ==6-7, 42=8-10)
        val tree = Tree(
            extended.types[0],
            listOf(
                // Keyword: let
                Tree(extended.types[1], emptyList(), emptyList(), 3),
                // Identifier: x
                Tree(extended.types[5], emptyList(), emptyList(), 1),
                // Number: 42
                Tree(extended.types[3], emptyList(), emptyList(), 2)
            ),
            listOf(0, 4, 8),
            10
        )

        return tree to HighlightTestData(extended)
    }

    private data class HighlightTestData(val nodeSet: NodeSet)

    @Test
    fun styleTagsAssignsTags() {
        val (tree, _) = buildTestTree()
        // Check that nodes have rules
        val keywordNode = tree.children[0] as Tree
        val rule = keywordNode.type.prop(ruleNodeProp)
        assertNotNull(rule)
        assertTrue(rule.tags.contains(tags.keyword))
    }

    @Test
    fun tagHighlighterMatchesTags() {
        val highlighter = tagHighlighter(
            listOf(
                TagStyleRule(tags.keyword, "kw"),
                TagStyleRule(tags.string, "str"),
                TagStyleRule(tags.number, "num")
            )
        )

        assertEquals("kw", highlighter.style(listOf(tags.keyword)))
        assertEquals("str", highlighter.style(listOf(tags.string)))
        assertEquals("num", highlighter.style(listOf(tags.number)))
        assertNull(highlighter.style(listOf(tags.comment)))
    }

    @Test
    fun tagHighlighterFallsBackToParent() {
        val highlighter = tagHighlighter(
            listOf(
                TagStyleRule(tags.keyword, "kw")
            )
        )

        // controlKeyword is a subtag of keyword
        assertEquals("kw", highlighter.style(listOf(tags.controlKeyword)))
    }

    @Test
    fun tagHighlighterPrefersSpecificTag() {
        val highlighter = tagHighlighter(
            listOf(
                TagStyleRule(tags.keyword, "kw"),
                TagStyleRule(tags.controlKeyword, "ctrl-kw")
            )
        )

        assertEquals("ctrl-kw", highlighter.style(listOf(tags.controlKeyword)))
        assertEquals("kw", highlighter.style(listOf(tags.keyword)))
    }

    @Test
    fun tagHighlighterWithAll() {
        val highlighter = tagHighlighter(
            listOf(
                TagStyleRule(tags.keyword, "kw")
            ),
            all = "base"
        )

        assertEquals("base kw", highlighter.style(listOf(tags.keyword)))
        assertEquals("base", highlighter.style(listOf(tags.comment)))
    }

    @Test
    fun highlightTreeProducesStyledRanges() {
        val (tree, _) = buildTestTree()
        val highlighter = tagHighlighter(
            listOf(
                TagStyleRule(tags.keyword, "kw"),
                TagStyleRule(tags.variableName, "var"),
                TagStyleRule(tags.number, "num")
            )
        )

        val ranges = mutableListOf<Triple<Int, Int, String>>()
        highlightTree(tree, highlighter, { from, to, cls ->
            ranges.add(Triple(from, to, cls))
        })

        assertEquals(3, ranges.size)
        assertEquals(Triple(0, 3, "kw"), ranges[0])
        assertEquals(Triple(4, 5, "var"), ranges[1])
        assertEquals(Triple(8, 10, "num"), ranges[2])
    }

    @Test
    fun highlightTreeWithRange() {
        val (tree, _) = buildTestTree()
        val highlighter = tagHighlighter(
            listOf(
                TagStyleRule(tags.keyword, "kw"),
                TagStyleRule(tags.variableName, "var"),
                TagStyleRule(tags.number, "num")
            )
        )

        val ranges = mutableListOf<Triple<Int, Int, String>>()
        highlightTree(tree, highlighter, { from, to, cls ->
            ranges.add(Triple(from, to, cls))
        }, from = 3, to = 9)

        // Should only include ranges that overlap [3, 9)
        assertTrue(ranges.any { it.third == "var" })
    }

    @Test
    fun highlightTreeEmptyTree() {
        val ranges = mutableListOf<Triple<Int, Int, String>>()
        highlightTree(
            Tree.empty,
            tagHighlighter(listOf(TagStyleRule(tags.keyword, "kw"))),
            { from, to, cls -> ranges.add(Triple(from, to, cls)) }
        )
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun styleTagsWithContext() {
        val types = listOf(
            NodeType.define(NodeTypeSpec(name = "Program", id = 0, top = true)),
            NodeType.define(NodeTypeSpec(name = "String", id = 1)),
            NodeType.define(NodeTypeSpec(name = "Escape", id = 2))
        )
        val nodeSet = NodeSet(types)
        val extended = nodeSet.extend(
            styleTags(
                mapOf(
                    "String" to tags.string,
                    "String/Escape" to tags.escape
                )
            )
        )

        // Check that Escape has a rule with context
        val escapeRule = extended.types[2].prop(ruleNodeProp)
        assertNotNull(escapeRule)
        assertNotNull(escapeRule.context)
        assertEquals(listOf("String"), escapeRule.context)
    }

    @Test
    fun styleTagsMultipleNames() {
        val types = listOf(
            NodeType.define(NodeTypeSpec(name = "Program", id = 0)),
            NodeType.define(NodeTypeSpec(name = "A", id = 1)),
            NodeType.define(NodeTypeSpec(name = "B", id = 2))
        )
        val nodeSet = NodeSet(types)
        val extended = nodeSet.extend(
            styleTags(
                mapOf(
                    "A B" to tags.keyword
                )
            )
        )

        assertNotNull(extended.types[1].prop(ruleNodeProp))
        assertNotNull(extended.types[2].prop(ruleNodeProp))
    }

    @Test
    fun getStyleTagsReturnsNullForNoRule() {
        val type = NodeType.define(NodeTypeSpec(name = "Foo", id = 0))
        val tree = Tree(type, emptyList(), emptyList(), 0)
        val cursor = tree.cursor()
        val result = getStyleTags(cursor)
        assertNull(result)
    }

    @Test
    fun highlighterMultipleHighlighters() {
        val h1 = tagHighlighter(listOf(TagStyleRule(tags.keyword, "kw")))
        val h2 = tagHighlighter(listOf(TagStyleRule(tags.keyword, "key")))

        val (tree, _) = buildTestTree()
        val ranges = mutableListOf<Triple<Int, Int, String>>()
        highlightTree(tree, listOf(h1, h2), { from, to, cls ->
            ranges.add(Triple(from, to, cls))
        })

        // keyword range should get combined classes
        val kwRange = ranges.find { it.first == 0 && it.second == 3 }
        assertNotNull(kwRange)
        assertEquals("kw key", kwRange.third)
    }
}
