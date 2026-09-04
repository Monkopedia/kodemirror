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
import com.monkopedia.kodemirror.state.Text
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
     * [search] with `\n`, `\r`, `\t` and `\\` escapes resolved -- upstream's
     * `SearchQuery.unquoted`, which every plain-string cursor searches for.
     * When [literal] is set the escapes are left alone.
     *
     * `internal`, matching upstream. `unquoted` and `unquote` carry
     * `/// @internal` there (`codemirror/search` at `4db1811`,
     * `src/search.ts:105,144`); `getReplacement` is narrower still, being a
     * member of the `QueryType` class that upstream never exports, reachable
     * only through the also-`@internal` `SearchQuery.create()`. None of the
     * three appears in the 19-symbol export list of `dist/index.d.ts`.
     */
    internal val unquoted: String
        get() = unquote(search)

    /**
     * Resolve `\n`, `\r`, `\t` and `\\` escapes in [text], unless [literal]
     * is set. Mirrors upstream `SearchQuery.unquote`.
     */
    internal fun unquote(text: String): String {
        if (literal) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < text.length) {
                val escaped = when (text[i + 1]) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '\\' -> '\\'
                    else -> null
                }
                if (escaped != null) {
                    sb.append(escaped)
                    i += 2
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
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
            SearchCursor(state.doc, unquoted, from, to, normalize)
        }
        val cursor = if (wholeWord) wholeWordSearchCursor(base, state.doc) else base
        return if (test != null) {
            FilteringSearchCursor(cursor) { test(it.from, it.to, state) }
        } else {
            cursor
        }
    }

    /**
     * The text that should replace [match], mirroring upstream
     * `QueryType.getReplacement`: group references are expanded only for
     * regex queries; plain-string queries insert [replace] verbatim.
     */
    internal fun getReplacement(match: SearchMatch): String =
        if (regexp && !literal) expandReplace(match) else expandReplace()

    /**
     * Expand replacement string, handling `$1`, `$&`, `$$` substitutions
     * for regex matches.
     *
     * @param match The match whose capture groups should be substituted.
     */
    fun expandReplace(match: SearchMatch): String = expandGroups(unquote(replace), match.groups)

    /**
     * Expand replacement string, handling `$1`, `$&`, `$$` substitutions
     * for regex matches.
     *
     * @param match The regex cursor that produced the match (for group access).
     */
    fun expandReplace(match: RegExpCursor): String =
        expandGroups(unquote(replace), match.matchGroups)

    /** Expand replacement for a simple string match. */
    fun expandReplace(): String = unquote(replace)

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
 * A filtering decorator over an [Iterator]<[SearchMatch]>: lazily pulls from
 * [inner], yielding only the matches for which [predicate] returns true.
 */
private class FilteringSearchCursor(
    private val inner: Iterator<SearchMatch>,
    private val predicate: (SearchMatch) -> Boolean
) : Iterator<SearchMatch> {
    private var nextMatch: SearchMatch? = null

    init {
        advance()
    }

    private fun advance() {
        while (inner.hasNext()) {
            val match = inner.next()
            if (predicate(match)) {
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

/**
 * A search cursor that only matches whole words (bounded by non-word characters).
 * Wraps any [Iterator]<[SearchMatch]> and filters to whole-word boundaries.
 */
private fun wholeWordSearchCursor(inner: Iterator<SearchMatch>, doc: Text): Iterator<SearchMatch> {
    fun isWordBoundary(pos: DocPos): Boolean {
        if (pos == DocPos.ZERO || pos.value == doc.length) return true
        val before = doc.sliceString(pos - 1, pos)
        val after = doc.sliceString(pos, pos + 1)
        val wordBefore = before.isNotEmpty() && isWordChar(before[0])
        val wordAfter = after.isNotEmpty() && isWordChar(after[0])
        // Upstream's `stringWordTest`/`regexpWordTest` only require that the
        // two characters straddling a boundary are not *both* word characters
        // (NAND). Using XOR additionally rejected boundaries where neither
        // side is a word character, which made all-punctuation tokens such as
        // `+`, `->`, `!=` or `^_^` unfindable with whole-word enabled.
        return !(wordBefore && wordAfter)
    }
    return FilteringSearchCursor(inner) {
        isWordBoundary(it.from) && isWordBoundary(it.to)
    }
}

private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

/**
 * Substitute `$$`, `$&` and `$<n>` references in [template] from [groups],
 * following upstream `RegExpQuery.getReplacement` (`/\$([$&]|\d+)/g`):
 *
 *  - `$$` yields a literal `$`.
 *  - `$&` yields the whole match.
 *  - `$<n>` prefers the longest in-range group number, so `$10` picks group 10
 *    when it exists and otherwise falls back to group 1 followed by `0`.
 *  - A reference that resolves to no group (`$0`, or an out-of-range number) is
 *    left in the output verbatim rather than dropped.
 *
 * One measured divergence from upstream, recorded here so it is not "corrected"
 * back: for a group that *exists but did not participate* in the match -- `$2`
 * against `(a)|(b)` matching `a` -- upstream emits the literal text
 * `undefined`, because it interpolates `String(match[2])` where `match[2]` is
 * JavaScript's `undefined`. This emits the empty string. The port is
 * deliberately not bug-compatible there: `undefined` is an artefact of the host
 * language's stringification, not a substitution rule anyone chose, and writing
 * it into a user's document is not a behaviour worth reproducing. Every other
 * case in a 28-case comparison against the published `@codemirror/search`
 * 6.7.1 agrees exactly, on both JVM and wasm.
 */
private fun expandGroups(template: String, groups: List<String?>): String {
    val sb = StringBuilder()
    var i = 0
    while (i < template.length) {
        val ch = template[i]
        if (ch != '$' || i + 1 >= template.length) {
            sb.append(ch)
            i++
            continue
        }
        when (val next = template[i + 1]) {
            '$' -> {
                sb.append('$')
                i += 2
            }
            '&' -> {
                sb.append(groups.firstOrNull() ?: "")
                i += 2
            }
            else -> if (!next.isDigit()) {
                sb.append(ch)
                i++
            } else {
                var end = i + 1
                while (end < template.length && template[end].isDigit()) end++
                val digits = template.substring(i + 1, end)
                var handled = false
                for (len in digits.length downTo 1) {
                    val n = digits.substring(0, len).toIntOrNull() ?: continue
                    if (n > 0 && n < groups.size) {
                        sb.append(groups[n] ?: "")
                        sb.append(digits.substring(len))
                        handled = true
                        break
                    }
                }
                if (!handled) {
                    sb.append('$')
                    sb.append(digits)
                }
                i = end
            }
        }
    }
    return sb.toString()
}
