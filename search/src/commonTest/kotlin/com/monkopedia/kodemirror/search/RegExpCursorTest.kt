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
import kotlin.test.assertTrue

class RegExpCursorTest {

    private fun collectMatches(cursor: RegExpCursor): List<Pair<Int, Int>> {
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
        val cursor = RegExpCursor(text, "one")
        val matches = collectMatches(cursor)
        assertEquals(
            listOf(0 to 3, 8 to 11, 16 to 19),
            matches
        )
    }

    @Test
    fun matchesByLine() {
        val text = Text.of(listOf("abc|def", "ghi|jkl"))
        val cursor = RegExpCursor(text, "\\w+\\|\\w+")
        val matches = collectMatches(cursor)
        assertEquals(2, matches.size)
    }

    @Test
    fun handlesEmptyLines() {
        val text = Text.of(listOf("one", "", "two"))
        val cursor = RegExpCursor(text, ".*")
        val matches = collectMatches(cursor)
        assertTrue(matches.size >= 3)
    }

    @Test
    fun handlesEmptyDocuments() {
        val textEmpty = Text.empty
        // On empty doc, ".*" finds no match because the range check (pos < to)
        // fails when both are 0
        val cursor1 = RegExpCursor(textEmpty, ".*")
        assertFalse(cursor1.hasNext())

        val cursor2 = RegExpCursor(textEmpty, "okay")
        assertFalse(cursor2.hasNext())
    }

    @Test
    fun restrictsToSearchRegion() {
        val text = Text.of("abcdef".split("\n"))
        val cursor = RegExpCursor(text, ".*", from = 3, to = 6)
        val matches = collectMatches(cursor)
        assertEquals(1, matches.size)
        assertEquals(3 to 6, matches[0])
    }

    @Test
    fun canMatchCaseInsensitively() {
        val text = Text.of("Hello HELLO hello".split("\n"))
        val cursor = RegExpCursor(text, "hello", setOf(RegexOption.IGNORE_CASE))
        val matches = collectMatches(cursor)
        assertEquals(3, matches.size)
    }

    @Test
    fun matchesAcrossLines() {
        val text = Text.of(listOf("abc", "def"))
        val cursor = RegExpCursor(text, "c\nd")
        val matches = collectMatches(cursor)
        assertEquals(1, matches.size)
        assertEquals(2, matches[0].first)
        assertEquals(5, matches[0].second)
    }

    @Test
    fun handlesInvalidRegexGracefully() {
        val text = Text.of("hello".split("\n"))
        val cursor = RegExpCursor(text, "[invalid")
        assertTrue(cursor.done)
        assertFalse(cursor.hasNext())
    }
}
