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
package com.monkopedia.kodemirror.autocomplete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterTest {

    private fun options(vararg labels: String) = labels.map { Completion(label = it) }

    @Test
    fun exactPrefixScoresHighest() {
        val results = filterCompletions(options("one", "ones", "other"), "one")
        assertTrue(results.isNotEmpty())
        assertEquals("one", results[0].completion.label)
        assertEquals(300, results[0].score)
    }

    @Test
    fun caseInsensitivePrefixScoresSecond() {
        val results = filterCompletions(options("One", "one"), "one")
        val oneResult = results.first { it.completion.label == "One" }
        assertEquals(200, oneResult.score)
        val exactResult = results.first { it.completion.label == "one" }
        assertEquals(300, exactResult.score)
        assertTrue(exactResult.score > oneResult.score)
    }

    @Test
    fun fuzzySubsequenceScoresLower() {
        val results = filterCompletions(options("aXbXc"), "abc")
        assertEquals(1, results.size)
        assertTrue(results[0].score < 200)
        assertTrue(results[0].score > 0)
    }

    @Test
    fun noMatchReturnsEmpty() {
        val results = filterCompletions(options("hello", "world"), "xyz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun emptyQueryReturnsAllOptions() {
        val opts = options("alpha", "beta", "gamma")
        val results = filterCompletions(opts, "")
        assertEquals(3, results.size)
    }

    @Test
    fun resultsSortedByScoreDescending() {
        val results = filterCompletions(
            options("one", "One", "aXoXnXe"),
            "one"
        )
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].score >= results[i + 1].score)
        }
    }

    @Test
    fun boostAffectsRanking() {
        val opts = listOf(
            Completion(label = "alpha", boost = 50),
            Completion(label = "alpha2", boost = 0)
        )
        val results = filterCompletions(opts, "alp")
        assertEquals(2, results.size)
        assertEquals("alpha", results[0].completion.label)
        assertTrue(results[0].score > results[1].score)
    }

    @Test
    fun filterFalseBypassesFiltering() {
        val opts = options("hello", "world")
        val results = filterCompletions(opts, "xyz", filter = false)
        assertEquals(2, results.size)
    }

    @Test
    fun highlightsMarkMatchedRanges() {
        val results = filterCompletions(options("hello"), "hel")
        assertEquals(1, results.size)
        assertTrue(results[0].highlighted.isNotEmpty())
        assertEquals(0 until 3, results[0].highlighted[0])
    }

    @Test
    fun alphabeticalTiebreak() {
        val results = filterCompletions(
            options("Banana", "Apple"),
            "a"
        )
        val filtered = results.filter { it.score == results[0].score }
        if (filtered.size > 1) {
            assertTrue(
                filtered[0].completion.label <= filtered[1].completion.label
            )
        }
    }

    @Test
    fun fuzzyGapPenalty() {
        val smallGap = filterCompletions(options("abcd"), "acd")
        val largeGap = filterCompletions(options("aXXXcXXXd"), "acd")
        assertTrue(smallGap.isNotEmpty())
        assertTrue(largeGap.isNotEmpty())
        assertTrue(smallGap[0].score > largeGap[0].score)
    }

    @Test
    fun singleCharQueryMatchesFuzzy() {
        val results = filterCompletions(options("apple", "banana"), "a")
        // "a" matches "apple" (prefix, score 300) and "banana" (fuzzy, lower score)
        assertEquals(2, results.size)
        assertEquals("apple", results[0].completion.label)
        assertTrue(results[0].score > results[1].score)
    }
}
