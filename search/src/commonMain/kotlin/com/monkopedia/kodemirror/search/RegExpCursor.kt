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

/**
 * A cursor that iterates over regex matches in a [Text] document.
 *
 * @param text The document text to search through.
 * @param query The regex pattern string.
 * @param options Regex options (e.g. [RegexOption.IGNORE_CASE]).
 * @param from Start position of the search range.
 * @param to End position of the search range.
 */
class RegExpCursor(
    private val text: Text,
    query: String,
    options: Set<RegexOption> = emptySet(),
    private val from: Int = 0,
    private val to: Int = text.length
) : Iterator<SearchMatch> {
    private val regex: Regex?
    private var pos = from
    private var nextMatch: SearchMatch? = null

    /** The groups from the last match result, if any. */
    var matchGroups: List<String?> = emptyList()
        private set

    /** Whether the cursor has been exhausted. */
    var done: Boolean = false
        private set

    /** The current match, or null if iteration hasn't started or is done. */
    var value: SearchMatch? = null
        private set

    init {
        regex = try {
            Regex(query, options)
        } catch (_: Exception) {
            null
        }
        if (regex == null) {
            done = true
        } else {
            advance()
        }
    }

    private fun advance() {
        if (done || regex == null) {
            done = true
            nextMatch = null
            return
        }
        val content = text.sliceString(pos, to)
        val result = regex.find(content)
        if (result != null && result.range.first + pos < to) {
            val matchFrom = pos + result.range.first
            val matchTo = pos + result.range.last + 1
            matchGroups = result.groupValues
            nextMatch = SearchMatch(matchFrom, matchTo)
        } else {
            done = true
            nextMatch = null
            matchGroups = emptyList()
        }
    }

    override fun hasNext(): Boolean = nextMatch != null

    override fun next(): SearchMatch {
        val match = nextMatch ?: throw NoSuchElementException()
        value = match
        pos = if (match.from == match.to) match.to + 1 else match.to
        advance()
        return match
    }

    /** Return the next match, or null if there are no more matches. */
    fun nextMatch(): SearchMatch? {
        if (!hasNext()) return null
        return next()
    }
}
