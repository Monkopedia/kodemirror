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
package com.monkopedia.kodemirror.search

import com.monkopedia.kodemirror.state.Text
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchQueryReplaceTest {

    @Test
    fun expandReplaceReturnsPlainReplacement() {
        val query = SearchQuery(search = "(a)(b)", replace = "xyz", regexp = true)
        val text = Text.of("ab cd".split("\n"))
        val cursor = RegExpCursor(text, "(a)(b)")
        cursor.next()
        assertEquals("xyz", query.expandReplace(cursor))
    }

    @Test
    fun expandReplaceHandlesDollarAmpersand() {
        val query = SearchQuery(search = "(a)(b)", replace = "[$&]", regexp = true)
        val text = Text.of("ab cd".split("\n"))
        val cursor = RegExpCursor(text, "(a)(b)")
        // matchGroups is populated by advance() during init, before next() is called
        assertEquals("[ab]", query.expandReplace(cursor))
    }

    @Test
    fun expandReplaceHandlesDollarOne() {
        val query = SearchQuery(search = "(a)(b)", replace = "$1-$2", regexp = true)
        val text = Text.of("ab cd".split("\n"))
        val cursor = RegExpCursor(text, "(a)(b)")
        // matchGroups is populated by advance() during init, before next() is called
        assertEquals("a-b", query.expandReplace(cursor))
    }

    @Test
    fun expandReplaceHandlesEscapedDollar() {
        val query = SearchQuery(search = "ab", replace = "$$10", regexp = true)
        val text = Text.of("ab cd".split("\n"))
        val cursor = RegExpCursor(text, "ab")
        cursor.next()
        assertEquals("\$10", query.expandReplace(cursor))
    }

    @Test
    fun expandReplaceLeavesUnresolvableGroupReferencesLiteral() {
        // Upstream `RegExpQuery.getReplacement` only substitutes group numbers
        // that are `> 0 && < match.length`; anything else falls through to the
        // literal match text.
        val text = Text.of("ab cd".split("\n"))
        val match = RegExpCursor(text, "(a)(b)").next()
        assertEquals(
            "$0",
            SearchQuery(search = "(a)(b)", replace = "$0", regexp = true)
                .expandReplace(match)
        )
        assertEquals(
            "$9",
            SearchQuery(search = "(a)(b)", replace = "$9", regexp = true)
                .expandReplace(match)
        )
    }

    @Test
    fun expandReplaceFallsBackToShorterGroupNumbers() {
        // `$10` with only two groups means group 1 followed by a literal "0".
        val text = Text.of("ab cd".split("\n"))
        val match = RegExpCursor(text, "(a)(b)").next()
        assertEquals(
            "a0",
            SearchQuery(search = "(a)(b)", replace = "$10", regexp = true)
                .expandReplace(match)
        )
    }

    @Test
    fun expandReplaceUsesTheGroupsOfTheGivenMatchNotTheLookahead() {
        // RegExpCursor.advance() runs ahead of the returned match, so groups
        // must travel on the match itself.
        val text = Text.of("ab xy".split("\n"))
        val cursor = RegExpCursor(text, "(\\w)(\\w)")
        val first = cursor.next()
        val second = cursor.next()
        val query = SearchQuery(search = "(\\w)(\\w)", replace = "$2$1", regexp = true)
        assertEquals("ba", query.expandReplace(first))
        assertEquals("yx", query.expandReplace(second))
    }

    @Test
    fun expandReplaceForNonRegex() {
        val query = SearchQuery(search = "hello", replace = "world")
        assertEquals("world", query.expandReplace())
    }
}
