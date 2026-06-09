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

import com.monkopedia.kodemirror.state.EditorState

/**
 * Result of filtering a completion option against a query.
 *
 * @param completion The original completion.
 * @param score Match score (higher = better match).
 * @param highlighted Ranges in the label that matched.
 * @param result The source [CompletionResult] this option came from. Used by the
 *   apply path so each option is inserted over *its own* result's `from`..`to`
 *   range — different sources can return different ranges (see [mergeCompletions]).
 *   Null only when [filterCompletions] is called directly (e.g. unit tests).
 */
internal data class FilterResult(
    val completion: Completion,
    val score: Int,
    val highlighted: List<IntRange>,
    val result: CompletionResult? = null
)

/**
 * Filter, merge and dedup the options of multiple completion [results] into one
 * ordered list — the cross-source merge upstream `@codemirror/autocomplete`
 * performs in `sortOptions` (#137).
 *
 * Each result is filtered against the text between *its own* `from` and the
 * cursor, honouring its own [filter][CompletionResult.filter] flag (so a
 * `filter = false` source keeps all of its options alongside the filtered ones).
 * The combined options are then sorted by a SINGLE GLOBAL ordering
 * ([completionOrder] — score descending, then `sortText ?: label` ascending),
 * mirroring upstream `@codemirror/autocomplete`'s `sortOptions`, which globally
 * sorts all candidates across sources rather than concatenating per-source runs.
 * They are then deduplicated by label, keeping the first occurrence — which, under
 * the global order, is the highest-ranked one for that label.
 *
 * **Differing `from`:** sources may return different `from` positions. Each
 * option carries its originating [result][FilterResult.result], so accepting it
 * replaces that source's range correctly even when sources disagree. Filtering
 * also uses each result's own span. The popup is anchored at the smallest `from`
 * (see the tooltip provider). This faithfully covers both the common case
 * (sources sharing `from`) and differing-`from` results.
 */
internal fun mergeCompletions(
    results: List<CompletionResult>,
    state: EditorState
): List<FilterResult> {
    val head = state.selection.main.head
    val combined = results.flatMap { result ->
        val query = state.doc.sliceString(result.from, head)
        filterCompletions(result.options, query, result.filter)
            .map { it.copy(result = result) }
    }
    // A single global sort across all sources (completionOrder: score descending,
    // then sortText ?: label ascending), mirroring upstream sortOptions. Dedup by
    // label then keeps the first occurrence — the highest-ranked one for that label.
    val seen = HashSet<String>()
    return combined
        .sortedWith(completionOrder)
        .filter { seen.add(it.completion.label) }
}

/**
 * Filter and score a list of completions against a query string.
 *
 * Scoring:
 * - Exact prefix: 300 + boost
 * - Case-insensitive prefix: 200 + boost
 * - Fuzzy subsequence: 100 + boost - penalty
 * - No match: excluded
 *
 * Ordering: primary key is [score][FilterResult.score] descending (which folds
 * in each option's [boost][Completion.boost]). Ties are broken by the option's
 * explicit [sortText][Completion.sortText] ascending when present, otherwise by
 * its [label][Completion.label] ascending — mirroring upstream
 * `@codemirror/autocomplete`, where `sortText` (the LSP server's explicit
 * ranking key) lets the server order equal-scored items as it prefers even when
 * their labels would sort differently.
 */
internal fun filterCompletions(
    options: List<Completion>,
    query: String,
    filter: Boolean = true
): List<FilterResult> {
    if (query.isEmpty() || !filter) {
        return options.map {
            FilterResult(it, it.boost, emptyList())
        }.sortedWith(completionOrder)
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
    }.sortedWith(completionOrder)
}

/**
 * Ranking comparator: [score][FilterResult.score] descending, then the option's
 * [sortText][Completion.sortText] (falling back to its [label][Completion.label])
 * ascending. The sort-key fallback is per-option, so a `sortText`-bearing item
 * and a plain item with equal score are compared via the former's `sortText` and
 * the latter's `label`.
 */
private val completionOrder: Comparator<FilterResult> =
    compareByDescending<FilterResult> { it.score }
        .thenBy { it.completion.sortText ?: it.completion.label }

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
            while (qi < lowerQuery.length &&
                li < lowerLabel.length &&
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
