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
package com.monkopedia.kodemirror.lang.json

import com.monkopedia.kodemirror.lezer.highlight.TagStyleRule
import com.monkopedia.kodemirror.lezer.highlight.highlightTree
import com.monkopedia.kodemirror.lezer.highlight.tagHighlighter
import com.monkopedia.kodemirror.lezer.highlight.tags as t
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonHighlightTest {

    private val highlighter = tagHighlighter(
        listOf(
            TagStyleRule(t.string, "string"),
            TagStyleRule(t.number, "number"),
            TagStyleRule(t.bool, "bool"),
            TagStyleRule(t.propertyName, "propertyName"),
            TagStyleRule(t.`null`, "null"),
            TagStyleRule(t.separator, "separator"),
            TagStyleRule(t.squareBracket, "squareBracket"),
            TagStyleRule(t.brace, "brace")
        )
    )

    private fun highlight(input: String): List<Triple<Int, Int, String>> {
        val tree = parser.parse(input)
        val spans = mutableListOf<Triple<Int, Int, String>>()
        highlightTree(tree, highlighter, { from, to, cls -> spans.add(Triple(from, to, cls)) })
        return spans
    }

    @Test
    fun highlightsSimpleObject() {
        // {"key": 42}
        val spans = highlight("{\"key\": 42}")
        // {              "key"          :          42        }
        val expected = listOf(
            Triple(0, 1, "brace"),
            Triple(1, 6, "propertyName"),
            Triple(6, 7, "separator"),
            Triple(8, 10, "number"),
            Triple(10, 11, "brace")
        )
        assertEquals(expected, spans)
    }

    @Test
    fun highlightsArray() {
        val spans = highlight("[1, true, null]")
        // [              1              ,             true          ,            null            ]
        val expected = listOf(
            Triple(0, 1, "squareBracket"),
            Triple(1, 2, "number"),
            Triple(2, 3, "separator"),
            Triple(4, 8, "bool"),
            Triple(8, 9, "separator"),
            Triple(10, 14, "null"),
            Triple(14, 15, "squareBracket")
        )
        assertEquals(expected, spans)
    }

    @Test
    fun highlightsString() {
        val spans = highlight("\"hello\"")
        assertEquals(listOf(Triple(0, 7, "string")), spans)
    }

    @Test
    fun highlightsNumber() {
        val spans = highlight("42")
        assertEquals(listOf(Triple(0, 2, "number")), spans)
    }

    @Test
    fun highlightsBoolean() {
        val spans = highlight("true")
        assertEquals(listOf(Triple(0, 4, "bool")), spans)
    }

    @Test
    fun highlightsNull() {
        val spans = highlight("null")
        assertEquals(listOf(Triple(0, 4, "null")), spans)
    }
}
