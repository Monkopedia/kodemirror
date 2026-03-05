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
package com.monkopedia.kodemirror.legacy.modes

import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

private val solrIsStringChar = Regex("[^\\s|!+\\-*?~^&:()\\[\\]{}\"\\\\]")
private val solrIsOperatorChar = Regex("[|!+\\-*?~^&]")
private val solrIsOperatorString = Regex("^(OR|AND|NOT|TO)$")

private fun solrIsNumber(word: String): Boolean {
    return word.toDoubleOrNull()?.toString() == word
}

private fun solrTokenString(quote: String): (StringStream, SolrState) -> String? =
    { stream, state ->
        var escaped = false
        var next: String?
        while (true) {
            next = stream.next()
            if (next == null) break
            if (next == quote && !escaped) break
            escaped = !escaped && next == "\\"
        }
        if (!escaped) state.tokenize = ::solrTokenBase
        "string"
    }

private fun solrTokenOperator(operator: String): (StringStream, SolrState) -> String? =
    { stream, state ->
        if (operator == "|") {
            stream.eat(Regex("\\|"))
        } else if (operator == "&") {
            stream.eat(Regex("&"))
        }
        state.tokenize = ::solrTokenBase
        "operator"
    }

private fun solrTokenWord(ch: String): (StringStream, SolrState) -> String? = { stream, state ->
    var word = ch
    var next: String?
    while (true) {
        next = stream.peek()
        if (next == null || solrIsStringChar.find(next) == null) break
        word += stream.next()
    }
    state.tokenize = ::solrTokenBase
    when {
        solrIsOperatorString.containsMatchIn(word) -> "operator"
        solrIsNumber(word) -> "number"
        stream.peek() == ":" -> "propertyName"
        else -> "string"
    }
}

private fun solrTokenBase(stream: StringStream, state: SolrState): String? {
    val ch = stream.next() ?: return null
    if (ch == "\"") {
        state.tokenize = solrTokenString(ch)
    } else if (solrIsOperatorChar.containsMatchIn(ch)) {
        state.tokenize = solrTokenOperator(ch)
    } else if (solrIsStringChar.containsMatchIn(ch)) {
        state.tokenize = solrTokenWord(ch)
    }
    return if (state.tokenize != ::solrTokenBase) state.tokenize(stream, state) else null
}

data class SolrState(
    var tokenize: (StringStream, SolrState) -> String? = ::solrTokenBase
)

val solr: StreamParser<SolrState> = object : StreamParser<SolrState> {
    override val name: String get() = "solr"
    override fun startState(indentUnit: Int) = SolrState()
    override fun copyState(state: SolrState) = state.copy()

    override fun token(stream: StringStream, state: SolrState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }
}
