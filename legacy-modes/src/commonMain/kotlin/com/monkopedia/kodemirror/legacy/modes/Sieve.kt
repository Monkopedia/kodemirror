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

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

private val sieveKeywords = "if elsif else stop require".split(" ").toSet()
private val sieveAtoms = "true false not".split(" ").toSet()

private fun sieveTokenBase(stream: StringStream, state: SieveState): String? {
    val ch = stream.next() ?: return null

    if (ch == "/" && stream.eat("*") != null) {
        state.tokenize = ::sieveTokenCComment
        return sieveTokenCComment(stream, state)
    }

    if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    }

    if (ch == "\"") {
        state.tokenize = sieveTokenString(ch)
        return state.tokenize(stream, state)
    }

    if (ch == "(") {
        state.indent.add("(")
        state.indent.add("{")
        return null
    }

    if (ch == "{") {
        state.indent.add("{")
        return null
    }

    if (ch == ")") {
        state.indent.removeLastOrNull()
        state.indent.removeLastOrNull()
    }

    if (ch == "}") {
        state.indent.removeLastOrNull()
        return null
    }

    if (ch == ",") return null
    if (ch == ";") return null

    if (Regex("[{}(),;]").containsMatchIn(ch)) return null

    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("\\d"))
        stream.eat(Regex("[KkMmGg]"))
        return "number"
    }

    if (ch == ":") {
        stream.eatWhile(Regex("[a-zA-Z_]"))
        stream.eatWhile(Regex("[a-zA-Z0-9_]"))
        return "operator"
    }

    stream.eatWhile(Regex("\\w"))
    val cur = stream.current()

    if (cur == "text" && stream.eat(":") != null) {
        state.tokenize = ::sieveTokenMultiLineString
        return "string"
    }

    if (cur in sieveKeywords) return "keyword"
    if (cur in sieveAtoms) return "atom"

    return null
}

private fun sieveTokenMultiLineString(stream: StringStream, state: SieveState): String {
    state.multiLineString = true
    if (!stream.sol()) {
        stream.eatSpace()
        if (stream.peek() == "#") {
            stream.skipToEnd()
            return "comment"
        }
        stream.skipToEnd()
        return "string"
    }

    if (stream.next() == "." && stream.eol()) {
        state.multiLineString = false
        state.tokenize = ::sieveTokenBase
    }

    return "string"
}

private fun sieveTokenCComment(stream: StringStream, state: SieveState): String {
    var maybeEnd = false
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (maybeEnd && ch == "/") {
            state.tokenize = ::sieveTokenBase
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun sieveTokenString(quote: String): (StringStream, SieveState) -> String {
    return { stream, state ->
        var escaped = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == quote && !escaped) break
            escaped = !escaped && ch == "\\"
        }
        if (!escaped) state.tokenize = ::sieveTokenBase
        "string"
    }
}

data class SieveState(
    var tokenize: (StringStream, SieveState) -> String? = ::sieveTokenBase,
    var baseIndent: Int = 0,
    var indent: MutableList<String> = mutableListOf(),
    var multiLineString: Boolean = false
)

/** Stream parser for Sieve (email filtering). */
val sieve: StreamParser<SieveState> = object : StreamParser<SieveState> {
    override val name: String get() = "sieve"

    override val languageData: Map<String, Any>
        get() = mapOf("indentOnInput" to Regex("^\\s*\\}$"))

    override fun startState(indentUnit: Int) = SieveState(baseIndent = indentUnit)

    override fun copyState(state: SieveState) = SieveState(
        tokenize = state.tokenize,
        baseIndent = state.baseIndent,
        indent = state.indent.toMutableList(),
        multiLineString = state.multiLineString
    )

    override fun token(stream: StringStream, state: SieveState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }

    override fun indent(state: SieveState, textAfter: String, context: IndentContext): Int {
        var length = state.indent.size
        if (textAfter.isNotEmpty() && textAfter[0] == '}') length--
        if (length < 0) length = 0
        return length * context.unit
    }
}
