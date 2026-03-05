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

private val eiffelKeywords = setOf(
    "note", "across", "when", "variant", "until", "unique", "undefine", "then", "strip",
    "select", "retry", "rescue", "require", "rename", "reference", "redefine", "prefix",
    "once", "old", "obsolete", "loop", "local", "like", "is", "inspect", "infix", "include",
    "if", "frozen", "from", "external", "export", "ensure", "end", "elseif", "else", "do",
    "creation", "create", "check", "alias", "agent", "separate", "invariant", "inherit",
    "indexing", "feature", "expanded", "deferred", "class", "Void", "True", "Result",
    "Precursor", "False", "Current", "create", "attached", "detachable", "as", "and",
    "implies", "not", "or"
)

private val eiffelOperators = setOf(":=", "and then", "and", "or", "<<", ">>")

data class EiffelState(
    var tokenize: MutableList<(StringStream, EiffelState) -> String?> = mutableListOf()
)

private fun eiffelChain(
    newTok: (StringStream, EiffelState) -> String?,
    stream: StringStream,
    state: EiffelState
): String? {
    state.tokenize.add(newTok)
    return newTok(stream, state)
}

private fun eiffelTokenBase(stream: StringStream, state: EiffelState): String? {
    if (stream.eatSpace()) return null
    val ch = stream.next() ?: return null
    if (ch == "\"" || ch == "'") {
        return eiffelChain(eiffelReadQuoted(ch, "string"), stream, state)
    } else if (ch == "-" && stream.eat("-") != null) {
        stream.skipToEnd()
        return "comment"
    } else if (ch == ":" && stream.eat("=") != null) {
        return "operator"
    } else if (Regex("[0-9]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[xXbBCc0-9.]"))
        stream.eat(Regex("[?!]"))
        return "variable"
    } else if (Regex("[a-zA-Z_0-9]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[a-zA-Z_0-9]"))
        stream.eat(Regex("[?!]"))
        return "variable"
    } else if (Regex("[=+\\-/*^%<>~]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[=+\\-/*^%<>~]"))
        return "operator"
    }
    return null
}

private fun eiffelReadQuoted(quote: String, style: String): (StringStream, EiffelState) -> String? =
    { stream, state ->
        var escaped = false
        while (true) {
            val ch = stream.next() ?: break
            if (ch == quote && !escaped) {
                state.tokenize.removeLast()
                break
            }
            escaped = !escaped && ch == "%"
        }
        style
    }

val eiffel: StreamParser<EiffelState> = object : StreamParser<EiffelState> {
    override val name: String get() = "eiffel"

    override fun startState(indentUnit: Int) = EiffelState(
        tokenize = mutableListOf(::eiffelTokenBase)
    )

    override fun copyState(state: EiffelState) = EiffelState(
        tokenize = state.tokenize.toMutableList()
    )

    override fun token(stream: StringStream, state: EiffelState): String? {
        var style = state.tokenize.last()(stream, state)
        if (style == "variable") {
            val word = stream.current()
            style = when {
                word in eiffelKeywords -> "keyword"
                word in eiffelOperators -> "operator"
                Regex("^[A-Z][A-Z_0-9]*$").matches(word) -> "tag"
                Regex("^0[bB][01]+$").matches(word) -> "number"
                Regex("^0[cC][0-7]+$").matches(word) -> "number"
                Regex("^0[xX][a-fA-F0-9]+$").matches(word) -> "number"
                Regex("^([0-9]+\\.[0-9]*)|([0-9]*\\.[0-9]+)$").matches(word) -> "number"
                Regex("^[0-9]+$").matches(word) -> "number"
                else -> "variable"
            }
        }
        return style
    }

    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "--"))
}
