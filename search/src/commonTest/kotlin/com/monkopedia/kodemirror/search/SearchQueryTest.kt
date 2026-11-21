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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {

    private fun state(doc: String): EditorState =
        EditorState.create(EditorStateConfig(doc = doc.asDoc()))

    private fun collectMatches(cursor: Iterator<SearchMatch>): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        while (cursor.hasNext()) {
            val match = cursor.next()
            results.add(match.from.value to match.to.value)
        }
        return results
    }

    @Test
    fun canMatchPlainStrings() {
        val query = SearchQuery(search = "one", caseSensitive = true)
        val matches = collectMatches(
            query.getCursor(state("one two one two one"))
        )
        assertEquals(
            listOf(0 to 3, 8 to 11, 16 to 19),
            matches
        )
    }

    @Test
    fun skipsOverlappingMatches() {
        val query = SearchQuery(search = "aba", caseSensitive = true)
        val matches = collectMatches(
            query.getCursor(state("abababa"))
        )
        assertEquals(listOf(0 to 3, 4 to 7), matches)
    }

    @Test
    fun canMatchCaseInsensitive() {
        val query = SearchQuery(search = "one", caseSensitive = false)
        val matches = collectMatches(
            query.getCursor(state("ONE One one"))
        )
        assertEquals(3, matches.size)
    }

    @Test
    fun canMatchAcrossLines() {
        val query = SearchQuery(search = "two\nthree", caseSensitive = true)
        val matches = collectMatches(
            query.getCursor(state("one two\nthree four"))
        )
        assertEquals(1, matches.size)
        assertEquals(4, matches[0].first)
    }

    @Test
    fun canMatchAcrossMultipleLines() {
        val query = SearchQuery(search = "b\nc\nd", caseSensitive = true)
        val matches = collectMatches(
            query.getCursor(state("a\nb\nc\nd\ne"))
        )
        assertEquals(1, matches.size)
    }

    @Test
    fun canMatchLiterally() {
        val query = SearchQuery(
            search = "a\\nb",
            caseSensitive = true,
            literal = true
        )
        val matches = collectMatches(
            query.getCursor(state("a\\nb c"))
        )
        assertEquals(1, matches.size)
        assertEquals(0, matches[0].first)
    }

    @Test
    fun unquotesEscapesInThePlainSearchString() {
        // CM6 `test-query.ts:30-32`: searching "a\\nb" in "a a\nb b" finds
        // the newline-separated occurrence at 2..5.
        val query = SearchQuery(search = "a\\nb", caseSensitive = true)
        assertEquals(
            listOf(2 to 5),
            collectMatches(query.getCursor(state("a a\nb b")))
        )
        assertEquals("a\nb", query.unquoted)
        assertEquals("a\tb", SearchQuery(search = "a\\tb").unquoted)
        assertEquals("a\\nb", SearchQuery(search = "a\\\\nb").unquoted)
    }

    @Test
    fun literalDisablesUnquoting() {
        val query = SearchQuery(search = "a\\nb", caseSensitive = true, literal = true)
        assertEquals("a\\nb", query.unquoted)
        assertEquals(
            emptyList<Pair<Int, Int>>(),
            collectMatches(query.getCursor(state("a a\nb b")))
        )
    }

    @Test
    fun canMatchByWord() {
        val query = SearchQuery(
            search = "word",
            caseSensitive = true,
            wholeWord = true
        )
        // "word sword wordy word" -> "word" at 0-4, "sword" has it embedded,
        // "wordy" has it as prefix only, final "word" at 17-21
        val matches = collectMatches(
            query.getCursor(state("word sword wordy word"))
        )
        assertEquals(listOf(0 to 4, 17 to 21), matches)
    }

    @Test
    fun matchesNonWordTokensByWord() {
        // Upstream's word test only rejects a boundary when the characters on
        // *both* sides are word characters, so an all-punctuation token is
        // findable. This is CM6's own `test-query.ts` case:
        // `test(new SearchQuery({search: "^_^", wholeWord: true}), "x[^_^]y [^_^]")`.
        val query = SearchQuery(
            search = "^_^",
            caseSensitive = true,
            wholeWord = true
        )
        assertEquals(
            listOf(1 to 4, 6 to 9),
            collectMatches(query.getCursor(state("x^_^y ^_^")))
        )
        assertEquals(
            listOf(6 to 9),
            collectMatches(query.getCursor(state("hello ^_^ world")))
        )
    }

    @Test
    fun canMatchRegularExpressions() {
        val query = SearchQuery(search = "a..b", regexp = true, caseSensitive = true)
        val matches = collectMatches(
            query.getCursor(state("axxb ayyb azzb"))
        )
        assertEquals(3, matches.size)
    }

    @Test
    fun caseInsensitiveRegex() {
        val query = SearchQuery(
            search = "hello",
            regexp = true,
            caseSensitive = false
        )
        val matches = collectMatches(
            query.getCursor(state("Hello HELLO hello"))
        )
        assertEquals(3, matches.size)
    }

    @Test
    fun testCallbackFiltersMatches() {
        // Only keep matches in the second half of the doc
        val query = SearchQuery(
            search = "ab",
            caseSensitive = true,
            test = { from, _, _ -> from >= DocPos(6) }
        )
        val matches = collectMatches(query.getCursor(state("ab cd ab cd ab")))
        assertEquals(listOf(6 to 8, 12 to 14), matches)
    }

    @Test
    fun testCallbackWithRegex() {
        // Regex search + test filter: only matches of length > 2
        val query = SearchQuery(
            search = "\\w+",
            regexp = true,
            caseSensitive = true,
            test = { from, to, _ -> to - from > 2 }
        )
        val matches = collectMatches(query.getCursor(state("hi hey hello")))
        assertEquals(listOf(3 to 6, 7 to 12), matches)
    }

    @Test
    fun testCallbackReceivesCorrectPositions() {
        val recorded = mutableListOf<Pair<Int, Int>>()
        val query = SearchQuery(
            search = "x",
            caseSensitive = true,
            test = { from, to, _ ->
                recorded.add(from.value to to.value)
                true
            }
        )
        collectMatches(query.getCursor(state("axbxc")))
        assertEquals(listOf(1 to 2, 3 to 4), recorded)
    }

    @Test
    fun regexWithWholeWord() {
        val query = SearchQuery(
            search = "\\w+",
            regexp = true,
            wholeWord = true,
            caseSensitive = true
        )
        val matches = collectMatches(query.getCursor(state("one two three")))
        assertEquals(listOf(0 to 3, 4 to 7, 8 to 13), matches)
    }

    @Test
    fun validProperty() {
        assertFalse(SearchQuery(search = "").valid)
        assertFalse(SearchQuery(search = "[bad", regexp = true).valid)
        assertTrue(SearchQuery(search = "good").valid)
        assertTrue(SearchQuery(search = "a.*b", regexp = true).valid)
        assertTrue(SearchQuery(search = "[bad", regexp = true, literal = true).valid)
    }
}
