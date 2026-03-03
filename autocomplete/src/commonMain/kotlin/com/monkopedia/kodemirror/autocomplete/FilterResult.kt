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

/**
 * Result of filtering a completion option against a query.
 *
 * @param completion The original completion.
 * @param score Match score (higher = better match).
 * @param highlighted Ranges in the label that matched.
 */
internal data class FilterResult(
    val completion: Completion,
    val score: Int,
    val highlighted: List<IntRange>
)

/**
 * Filter and score a list of completions against a query string.
 *
 * Scoring:
 * - Exact prefix: 300 + boost
 * - Case-insensitive prefix: 200 + boost
 * - Fuzzy subsequence: 100 + boost - penalty
 * - No match: excluded
 */
internal fun filterCompletions(
    options: List<Completion>,
    query: String,
    filter: Boolean = true
): List<FilterResult> {
    if (query.isEmpty() || !filter) {
        return options.map {
            FilterResult(it, it.boost, emptyList())
        }
    }

    return options.mapNotNull { completion ->
        val label = completion.label
        val score = scoreMatch(label, query)
        if (score != null) {
            FilterResult(
                completion,
                score.first + completion.boost,
                score.second
            )
        } else {
            null
        }
    }.sortedByDescending { it.score }
}

/**
 * Score a label against a query.
 * Returns (score, highlighted ranges) or null if no match.
 */
private fun scoreMatch(label: String, query: String): Pair<Int, List<IntRange>>? {
    // Exact prefix match
    if (label.startsWith(query)) {
        return 300 to listOf(0 until query.length)
    }

    // Case-insensitive prefix match
    if (label.lowercase().startsWith(query.lowercase())) {
        return 200 to listOf(0 until query.length)
    }

    // Fuzzy subsequence match
    val highlights = mutableListOf<IntRange>()
    var qi = 0
    var li = 0
    val lowerLabel = label.lowercase()
    val lowerQuery = query.lowercase()

    while (qi < lowerQuery.length && li < lowerLabel.length) {
        if (lowerLabel[li] == lowerQuery[qi]) {
            val start = li
            while (qi < lowerQuery.length && li < lowerLabel.length &&
                lowerLabel[li] == lowerQuery[qi]
            ) {
                qi++
                li++
            }
            highlights.add(start until li)
        } else {
            li++
        }
    }

    return if (qi == lowerQuery.length) {
        // Penalty based on how spread out the match is
        val penalty = (li - highlights.first().first) - query.length
        (100 - penalty) to highlights
    } else {
        null
    }
}
