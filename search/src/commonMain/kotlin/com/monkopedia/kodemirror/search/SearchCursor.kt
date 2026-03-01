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

/** A match found by a search cursor. */
data class SearchMatch(val from: Int, val to: Int)

/**
 * A cursor that iterates over string matches in a [Text] document.
 *
 * @param text The document text to search through.
 * @param query The string to search for.
 * @param from Start position of the search range (inclusive).
 * @param to End position of the search range (exclusive, defaults to document length).
 * @param normalize Optional normalization function (e.g. [String.lowercase] for case-insensitive).
 */
class SearchCursor(
    private val text: Text,
    private val query: String,
    private val from: Int = 0,
    private val to: Int = text.length,
    private val normalize: (String) -> String = { it }
) : Iterator<SearchMatch> {
    private var pos = from
    private var nextMatch: SearchMatch? = null
    private val normalizedQuery = normalize(query)

    /** Whether the cursor has been exhausted. */
    var done: Boolean = false
        private set

    /** The current match, or null if iteration hasn't started or is done. */
    var value: SearchMatch? = null
        private set

    init {
        advance()
    }

    private fun advance() {
        if (done || normalizedQuery.isEmpty()) {
            done = true
            nextMatch = null
            return
        }
        while (pos <= to - normalizedQuery.length) {
            val chunk = text.sliceString(pos, minOf(to, pos + 1024))
            val normalizedChunk = normalize(chunk)
            val idx = normalizedChunk.indexOf(normalizedQuery)
            if (idx >= 0) {
                val matchFrom = pos + idx
                val matchTo = matchFrom + normalizedQuery.length
                if (matchTo <= to) {
                    nextMatch = SearchMatch(matchFrom, matchTo)
                    return
                }
            }
            // Move forward, but make sure we don't miss matches spanning chunks
            pos += maxOf(1, chunk.length - normalizedQuery.length + 1)
        }
        done = true
        nextMatch = null
    }

    override fun hasNext(): Boolean = nextMatch != null

    override fun next(): SearchMatch {
        val match = nextMatch ?: throw NoSuchElementException()
        value = match
        pos = match.to
        advance()
        return match
    }

    /** Return the next match, or null if there are no more matches. */
    fun nextMatch(): SearchMatch? {
        if (!hasNext()) return null
        return next()
    }
}
