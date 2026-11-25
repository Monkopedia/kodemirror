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
import com.monkopedia.kodemirror.state.codePointAt
import com.monkopedia.kodemirror.state.codePointSize
import com.monkopedia.kodemirror.state.fromCodePoint

/**
 * An option in the completion list, as upstream's `Option`.
 *
 * @param completion The original completion.
 * @param score Its rank: the match score plus boost (and section offset), or, for a
 *   result with `filter = false`, its place in source order.
 * @param highlighted Ranges in the label that matched.
 * @param source The source this option came from. Accepting it replaces that source's
 *   current `from`..`to` range, so options from sources with different ranges each
 *   insert over their own.
 */
internal data class FilterResult(
    val completion: Completion,
    val score: Int,
    val highlighted: List<IntRange>,
    val source: QuerySource? = null
)

// Upstream's `Penalty` values.
private const val PENALTY_GAP = -1100
private const val PENALTY_NOT_START = -700
private const val PENALTY_CASE_FOLD = -200
private const val PENALTY_BY_WORD = -100
private const val PENALTY_NOT_FULL = -100

/** Upstream only scans this far into a label. */
private const val MAX_SCAN = 200

// Upstream's `Tp`: character types for finding word starts.
private const val TP_NON_WORD = 0
private const val TP_UPPER = 1
private const val TP_LOWER = 2

private val asciiLower = Regex("[a-z]")

/**
 * Upstream's `FuzzyMatcher`: matches labels against [pattern], scoring each match
 * (more negative is worse) and recording the matched ranges. Create one per pattern and
 * match every label with it, in order: like upstream, it keeps its buffers between
 * calls, and whether the by-word buffer has ever been written changes how a later
 * label scores.
 */
internal class FuzzyMatcher(private val pattern: String) {
    private val chars = mutableListOf<Int>()
    private val folded = mutableListOf<Int>()
    private val astral: Boolean
    private val any = IntArray(pattern.length)
    private val byWord = IntArray(pattern.length)

    /** The length of upstream's `byWord` array, which only ever grows. */
    private var byWordLength = 0

    /** The last match's score. */
    var score = 0
        private set

    /** The last match's ranges, as `from, to` pairs. */
    var matched: List<Int> = emptyList()
        private set

    init {
        var p = 0
        while (p < pattern.length) {
            val char = codePointAt(pattern, p)
            val size = codePointSize(char)
            chars += char
            val part = pattern.substring(p, p + size)
            val upper = part.uppercase()
            folded += codePointAt(if (upper == part) part.lowercase() else upper, 0)
            p += size
        }
        astral = pattern.length != chars.size
    }

    private fun ret(score: Int, matched: List<Int>): FuzzyMatcher {
        this.score = score
        this.matched = matched
        return this
    }

    /** Match [word] against the pattern: this matcher with its [score] and [matched] set, or null. */
    fun match(word: String): FuzzyMatcher? {
        if (pattern.isEmpty()) return ret(PENALTY_NOT_FULL, emptyList())
        if (word.length < pattern.length) return null
        val len = chars.size
        // Single-character queries only match at the start.
        if (len == 1) {
            val first = codePointAt(word, 0)
            val firstSize = codePointSize(first)
            var score = if (firstSize == word.length) 0 else PENALTY_NOT_FULL
            when (first) {
                chars[0] -> {}
                folded[0] -> score += PENALTY_CASE_FOLD
                else -> return null
            }
            return ret(score, listOf(0, firstSize))
        }
        val direct = word.indexOf(pattern)
        if (direct == 0) {
            val score = if (word.length == pattern.length) 0 else PENALTY_NOT_FULL
            return ret(score, listOf(0, pattern.length))
        }
        val end = minOf(word.length, MAX_SCAN)
        var anyTo = 0
        if (direct < 0) {
            var i = 0
            while (i < end && anyTo < len) {
                val next = codePointAt(word, i)
                if (next == chars[anyTo] || next == folded[anyTo]) any[anyTo++] = i
                i += codePointSize(next)
            }
            if (anyTo < len) return null
        }
        // A match that hits only characters that appear to start words, and whether it
        // case-folded any of them.
        var byWordTo = 0
        var byWordFolded = false
        // A partial adjacent match, if found.
        var adjacentTo = 0
        var adjacentStart = -1
        var adjacentEnd = -1
        val hasLower = asciiLower.containsMatchIn(word)
        var wordAdjacent = true
        var i = 0
        var prevType = TP_NON_WORD
        while (i < end && byWordTo < len) {
            val next = codePointAt(word, i)
            if (direct < 0 && adjacentTo < len) {
                if (next == chars[adjacentTo] || next == folded[adjacentTo]) {
                    if (adjacentTo == 0) adjacentStart = i
                    adjacentEnd = i + 1
                    adjacentTo++
                } else {
                    adjacentTo = 0
                }
            }
            val type = charType(next)
            if (i == 0 ||
                type == TP_UPPER &&
                hasLower ||
                prevType == TP_NON_WORD &&
                type != TP_NON_WORD
            ) {
                if (chars[byWordTo] == next || folded[byWordTo] == next) {
                    if (chars[byWordTo] != next) byWordFolded = true
                    byWord[byWordTo++] = i
                    byWordLength = maxOf(byWordLength, byWordTo)
                } else if (byWordLength > 0) {
                    wordAdjacent = false
                }
            }
            prevType = type
            i += codePointSize(next)
        }
        val byWordPenalty = PENALTY_BY_WORD + if (byWordFolded) PENALTY_CASE_FOLD else 0
        return when {
            byWordTo == len && byWord[0] == 0 && wordAdjacent ->
                result(byWordPenalty, byWord, len, word)
            adjacentTo == len && adjacentStart == 0 -> ret(
                PENALTY_CASE_FOLD - word.length +
                    if (adjacentEnd == word.length) 0 else PENALTY_NOT_FULL,
                listOf(0, adjacentEnd)
            )
            direct > -1 -> ret(
                PENALTY_NOT_START - word.length,
                listOf(
                    direct,
                    direct + pattern.length
                )
            )
            adjacentTo == len -> ret(
                PENALTY_CASE_FOLD + PENALTY_NOT_START - word.length,
                listOf(adjacentStart, adjacentEnd)
            )
            byWordTo == len -> result(
                byWordPenalty + PENALTY_NOT_START + if (wordAdjacent) 0 else PENALTY_GAP,
                byWord,
                len,
                word
            )
            len == 2 -> null
            else -> result(
                (if (any[0] != 0) PENALTY_NOT_START else 0) + PENALTY_CASE_FOLD + PENALTY_GAP,
                any,
                len,
                word
            )
        }
    }

    /** Upstream's `result`: [positions] merged into ranges, scored [score] less the word length. */
    private fun result(score: Int, positions: IntArray, count: Int, word: String): FuzzyMatcher {
        val result = mutableListOf<Int>()
        for (k in 0 until count) {
            val pos = positions[k]
            val to = pos + if (astral) codePointSize(codePointAt(word, pos)) else 1
            if (result.isNotEmpty() && result.last() == pos) {
                result[result.lastIndex] = to
            } else {
                result += pos
                result += to
            }
        }
        return ret(score - word.length, result)
    }
}

/** Upstream's character type: ASCII by range, others by whether case mapping changes them. */
private fun charType(next: Int): Int = if (next < 0xff) {
    when (next) {
        in 48..57, in 97..122 -> TP_LOWER
        in 65..90 -> TP_UPPER
        else -> TP_NON_WORD
    }
} else {
    val ch = fromCodePoint(next)
    when {
        ch != ch.lowercase() -> TP_UPPER
        ch != ch.uppercase() -> TP_LOWER
        else -> TP_NON_WORD
    }
}

/**
 * Upstream's `score` of a completion, used to pick between options that are otherwise
 * the same: boost, then having an apply, info or type.
 */
private fun dedupScore(c: Completion): Int = c.boost * 100 +
    (if (c.apply != null || c.applyFn != null) 10 else 0) +
    (if (c.info != null) 5 else 0) +
    (if (c.type != null) 1 else 0)

/** A section's place in [sortOptions]: no rank sorts after every ranked section. */
private const val UNRANKED = 1_000_000_000

/** How far apart [sortOptions] puts sections' scores. */
private const val SECTION_STEP = 100_000

/** The score a `filter = false` result's first option gets; later ones get one less each. */
private const val UNFILTERED_SCORE = 1_000_000_000

/**
 * Upstream's `sortOptions`: the options of every [result][ActiveResult] in [active],
 * each filtered by the text between its own `from` and `to` (unless its result has
 * `filter = false`, which keeps them in source order), ordered by score with sections
 * by rank, ties broken by [localeCompare] on `sortText ?: label`, and with adjacent
 * duplicates collapsed to the one with the most information.
 */
internal fun sortOptions(active: List<ActiveSource>, state: EditorState): List<FilterResult> {
    val options = mutableListOf<FilterResult>()
    val sections = mutableListOf<CompletionSection>()
    val dynamicSectionScore = mutableMapOf<String, Int>()
    fun addOption(option: FilterResult) {
        options += option
        val section = option.completion.section ?: return
        if (sections.none { it.name == section.name }) sections += section
    }
    for (a in active) {
        if (a !is ActiveResult) continue
        if (!a.result.filter) {
            for (option in a.result.options) {
                addOption(
                    FilterResult(option, UNFILTERED_SCORE - options.size, emptyList(), a.source)
                )
            }
            continue
        }
        val matcher = FuzzyMatcher(state.sliceDoc(a.from, a.to))
        for (option in a.result.options) {
            val match = matcher.match(option.label) ?: continue
            val matched = if (option.displayLabel == null) match.matched else emptyList()
            val score = match.score + option.boost
            addOption(FilterResult(option, score, matched.toRanges(), a.source))
            val section = option.section
            if (section != null && section.dynamicRank) {
                // Upstream's `|| -1e9` also treats a best score of 0 as unset.
                val best = dynamicSectionScore[section.name]?.takeIf { it != 0 } ?: -UNRANKED
                dynamicSectionScore[section.name] = maxOf(score, best)
            }
        }
    }
    val ranked = if (sections.isEmpty()) {
        options
    } else {
        val sectionOrder = sections.sortedWith { a, b ->
            val dynamic = if (a.dynamicRank && b.dynamicRank) {
                (dynamicSectionScore[b.name] ?: 0) - (dynamicSectionScore[a.name] ?: 0)
            } else {
                0
            }
            dynamic.takeIf { it != 0 }
                ?: (a.rankOrder() compareTo b.rankOrder()).takeIf { it != 0 }
                ?: if (a.name < b.name) -1 else 1
        }.mapIndexed { i, s -> s.name to -SECTION_STEP * (i + 1) }.toMap()
        options.map { option ->
            val section = option.completion.section ?: return@map option
            option.copy(score = option.score + sectionOrder.getValue(section.name))
        }
    }
    val result = mutableListOf<FilterResult>()
    var prev: Completion? = null
    for (opt in ranked.sortedWith(optionOrder)) {
        val cur = opt.completion
        val p = prev
        if (p == null ||
            p.label != cur.label ||
            p.detail != cur.detail ||
            p.type != null &&
            cur.type != null &&
            p.type != cur.type ||
            p.apply != cur.apply ||
            p.applyFn !== cur.applyFn ||
            p.boost != cur.boost
        ) {
            result += opt
        } else if (dedupScore(cur) > dedupScore(p)) {
            result[result.lastIndex] = opt
        }
        prev = cur
    }
    return result
}

/** A section's numeric rank, as upstream: `rank` when it is a number, else last. */
private fun CompletionSection.rankOrder(): Int = if (dynamicRank) UNRANKED else rank ?: UNRANKED

/** Score descending, then upstream's default `compareCompletions`. */
private val optionOrder: Comparator<FilterResult> = Comparator { a, b ->
    compareValues(b.score, a.score).takeIf { it != 0 }
        ?: localeCompare(
            a.completion.sortText ?: a.completion.label,
            b.completion.sortText ?: b.completion.label
        )
}

/** `from, to` pairs as ranges. */
private fun List<Int>.toRanges(): List<IntRange> = chunked(2) { (from, to) -> from until to }

/**
 * Compare [a] and [b] as JavaScript's `String.localeCompare` does in the current
 * locale, which upstream uses to order completions of equal score.
 */
internal expect fun localeCompare(a: String, b: String): Int
