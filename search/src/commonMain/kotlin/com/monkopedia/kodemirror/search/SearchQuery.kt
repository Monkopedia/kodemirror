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
import com.monkopedia.kodemirror.state.endPos

/**
 * Represents a search query with options for how to match.
 *
 * @param search The search string.
 * @param caseSensitive Whether matching is case-sensitive.
 * @param literal If true, treat [search] as a literal string even if [regexp] is true.
 * @param regexp If true and [literal] is false, treat [search] as a regex pattern.
 * @param replace The replacement string.
 * @param wholeWord If true, only match whole words.
 * @param test Optional filter callback. When set, only matches for which
 *   `test(from, to, state)` returns true are included.
 */
data class SearchQuery(
    val search: String = "",
    val caseSensitive: Boolean = false,
    val literal: Boolean = false,
    val regexp: Boolean = false,
    val replace: String = "",
    val wholeWord: Boolean = false,
    val test: ((from: DocPos, to: DocPos, state: EditorState) -> Boolean)? = null
) {
    /** Whether this query is non-empty and (if regex) syntactically valid. */
    val valid: Boolean
        get() {
            if (search.isEmpty()) return false
            if (regexp && !literal) {
                return try {
                    Regex(search)
                    true
                } catch (_: Exception) {
                    false
                }
            }
            return true
        }

    /**
     * Get a cursor for this query over a given state.
     *
     * @param state The editor state to search.
     * @param from Start position (defaults to 0).
     * @param to End position (defaults to document length).
     */
    fun getCursor(
        state: EditorState,
        from: DocPos = DocPos.ZERO,
        to: DocPos = state.doc.endPos
    ): Iterator<SearchMatch> {
        val base = if (regexp && !literal) {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            RegExpCursor(state.doc, search, options, from, to)
        } else {
            val normalize: (String) -> String = if (caseSensitive) {
                { it }
            } else {
                { it.lowercase() }
            }
            SearchCursor(state.doc, search, from, to, normalize)
        }
        val cursor = if (wholeWord) WholeWordSearchCursor(base, state.doc) else base
        return if (test != null) FilteredSearchCursor(cursor, test, state) else cursor
    }

    /**
     * Expand replacement string, handling `$1`, `$&`, `$$` substitutions
     * for regex matches.
     *
     * @param match The regex cursor that produced the match (for group access).
     */
    fun expandReplace(match: RegExpCursor): String {
        val groups = match.matchGroups
        val sb = StringBuilder()
        var i = 0
        while (i < replace.length) {
            val ch = replace[i]
            if (ch == '$' && i + 1 < replace.length) {
                val next = replace[i + 1]
                when {
                    next == '$' -> {
                        sb.append('$')
                        i += 2
                    }
                    next == '&' -> {
                        if (groups.isNotEmpty()) sb.append(groups[0])
                        i += 2
                    }
                    next.isDigit() -> {
                        val groupIdx = next.digitToInt()
                        if (groupIdx < groups.size) {
                            sb.append(groups[groupIdx] ?: "")
                        }
                        i += 2
                    }
                    else -> {
                        sb.append(ch)
                        i++
                    }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    /** Expand replacement for a simple string match. */
    fun expandReplace(): String = replace

    companion object {
        /**
         * Create a [SearchQuery] only if the given parameters form a
         * valid query. Returns `null` for empty search strings or
         * invalid regex patterns.
         */
        fun validOrNull(
            search: String,
            caseSensitive: Boolean = false,
            literal: Boolean = false,
            regexp: Boolean = false,
            replace: String = "",
            wholeWord: Boolean = false,
            test: ((from: DocPos, to: DocPos, state: EditorState) -> Boolean)? = null
        ): SearchQuery? {
            val query = SearchQuery(
                search = search,
                caseSensitive = caseSensitive,
                literal = literal,
                regexp = regexp,
                replace = replace,
                wholeWord = wholeWord,
                test = test
            )
            return if (query.valid) query else null
        }
    }
}

/**
 * A search cursor that only matches whole words (bounded by non-word characters).
 * Wraps any [Iterator]<[SearchMatch]> and filters to whole-word boundaries.
 */
internal class WholeWordSearchCursor(
    private val inner: Iterator<SearchMatch>,
    private val doc: com.monkopedia.kodemirror.state.Text
) : Iterator<SearchMatch> {
    private var nextMatch: SearchMatch? = null

    init {
        advance()
    }

    private fun advance() {
        while (inner.hasNext()) {
            val match = inner.next()
            if (isWordBoundary(match.from) && isWordBoundary(match.to)) {
                nextMatch = match
                return
            }
        }
        nextMatch = null
    }

    private fun isWordBoundary(pos: DocPos): Boolean {
        if (pos == DocPos.ZERO || pos.value == doc.length) return true
        val before = doc.sliceString(pos - 1, pos)
        val after = doc.sliceString(pos, pos + 1)
        val wordBefore = before.isNotEmpty() && isWordChar(before[0])
        val wordAfter = after.isNotEmpty() && isWordChar(after[0])
        return wordBefore != wordAfter
    }

    override fun hasNext(): Boolean = nextMatch != null

    override fun next(): SearchMatch {
        val match = nextMatch ?: throw NoSuchElementException()
        advance()
        return match
    }
}

/**
 * A search cursor that filters matches using a test callback.
 */
private class FilteredSearchCursor(
    private val inner: Iterator<SearchMatch>,
    private val test: (DocPos, DocPos, EditorState) -> Boolean,
    private val state: EditorState
) : Iterator<SearchMatch> {
    private var nextMatch: SearchMatch? = null

    init {
        advance()
    }

    private fun advance() {
        while (inner.hasNext()) {
            val match = inner.next()
            if (test(match.from, match.to, state)) {
                nextMatch = match
                return
            }
        }
        nextMatch = null
    }

    override fun hasNext(): Boolean = nextMatch != null

    override fun next(): SearchMatch {
        val match = nextMatch ?: throw NoSuchElementException()
        advance()
        return match
    }
}

private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
