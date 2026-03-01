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
import kotlin.test.assertFalse

class SearchCursorTest {

    private fun collectMatches(cursor: SearchCursor): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        while (cursor.hasNext()) {
            val match = cursor.next()
            results.add(match.from to match.to)
        }
        return results
    }

    @Test
    fun findsAllMatchesInSimpleString() {
        val text = Text.of("one two one two one".split("\n"))
        val cursor = SearchCursor(text, "one")
        val matches = collectMatches(cursor)
        assertEquals(
            listOf(0 to 3, 8 to 11, 16 to 19),
            matches
        )
    }

    @Test
    fun findsOnlyMatchesInGivenRegion() {
        val text = Text.of("one two one two one".split("\n"))
        val cursor = SearchCursor(text, "one", from = 2, to = 17)
        val matches = collectMatches(cursor)
        assertEquals(listOf(8 to 11), matches)
    }

    @Test
    fun canCrossLines() {
        val text = Text.of(listOf("one two", "one two", "one"))
        val cursor = SearchCursor(text, "one")
        val matches = collectMatches(cursor)
        assertEquals(3, matches.size)
    }

    @Test
    fun canNormalizeCase() {
        val text = Text.of("ONE two One two one".split("\n"))
        val cursor = SearchCursor(text, "one", normalize = String::lowercase)
        val matches = collectMatches(cursor)
        assertEquals(3, matches.size)
        assertEquals(0, matches[0].first)
        assertEquals(8, matches[1].first)
        assertEquals(16, matches[2].first)
    }

    @Test
    fun canMatchAcrossLines() {
        val text = Text.of(listOf("one two", "three four"))
        val cursor = SearchCursor(text, "two\nthree")
        val matches = collectMatches(cursor)
        assertEquals(1, matches.size)
        assertEquals(4, matches[0].first)
        assertEquals(13, matches[0].second)
    }

    @Test
    fun canSearchEmptyDocument() {
        val text = Text.empty
        val cursor = SearchCursor(text, "one")
        assertFalse(cursor.hasNext())
    }

    @Test
    fun doesNotIncludeOverlappingResults() {
        val text = Text.of("fofofofo".split("\n"))
        val cursor = SearchCursor(text, "fofo")
        val matches = collectMatches(cursor)
        assertEquals(listOf(0 to 4, 4 to 8), matches)
    }

    @Test
    fun handlesLongDocuments() {
        val word = "abc "
        val doc = word.repeat(250)
        val text = Text.of(doc.split("\n"))
        val cursor = SearchCursor(text, "abc")
        val matches = collectMatches(cursor)
        assertEquals(250, matches.size)
    }

    @Test
    fun handlesQueryLongerThanText() {
        val text = Text.of("hi".split("\n"))
        val cursor = SearchCursor(text, "hello world this is long")
        assertFalse(cursor.hasNext())
    }

    @Test
    fun emptyQueryReturnsNoMatches() {
        val text = Text.of("hello world".split("\n"))
        val cursor = SearchCursor(text, "")
        assertFalse(cursor.hasNext())
    }
}
