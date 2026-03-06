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

private val yacasBodiedOps = (
    "Assert BackQuote D Defun Deriv For ForEach FromFile " +
        "FromString Function Integrate InverseTaylor Limit " +
        "LocalSymbols Macro MacroRule MacroRulePattern " +
        "NIntegrate Rule RulePattern Subst TD TExplicitSum " +
        "TSum Taylor Taylor1 Taylor2 Taylor3 ToFile " +
        "ToStdout ToString TraceRule Until While"
    ).split(" ").toSet()

private val pFloatForm = "(?:(?:\\.\\d+|\\d+\\.\\d*|\\d+)(?:[eE][+-]?\\d+)?)"
private val pIdentifier = "(?:[a-zA-Z\$'][a-zA-Z0-9\$']*)"

private val reFloatForm = Regex("^$pFloatForm")
private val reIdentifier = Regex("^$pIdentifier")
private val rePattern = Regex("^$pIdentifier?_$pIdentifier")
private val reFunctionLike = Regex("^${pIdentifier}\\s*\\(")

data class YacasState(
    var tokenize: (StringStream, YacasState) -> String?,
    var scopes: MutableList<String> = mutableListOf()
)

/** Stream parser for Yacas. */
val yacas: StreamParser<YacasState> = object : StreamParser<YacasState> {
    override val name: String get() = "yacas"
    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )

    private fun currentScope(state: YacasState): String? {
        return if (state.scopes.isNotEmpty()) state.scopes.last() else null
    }

    private fun tokenString(stream: StringStream, state: YacasState): String? {
        var next: String?
        var escaped = false
        while (true) {
            next = stream.next()
            if (next == null) break
            if (next == "\"" && !escaped) {
                state.tokenize = ::tokenBase
                break
            }
            escaped = !escaped && next == "\\"
        }
        return "string"
    }

    private fun tokenComment(stream: StringStream, state: YacasState): String? {
        var prev: String? = null
        var next: String?
        while (true) {
            next = stream.next()
            if (next == null) break
            if (prev == "*" && next == "/") {
                state.tokenize = ::tokenBase
                break
            }
            prev = next
        }
        return "comment"
    }

    private fun tokenBase(stream: StringStream, state: YacasState): String? {
        val ch = stream.next() ?: return "error"

        if (ch == "\"") {
            state.tokenize = ::tokenString
            return state.tokenize(stream, state)
        }

        if (ch == "/") {
            if (stream.eat("*") != null) {
                state.tokenize = ::tokenComment
                return state.tokenize(stream, state)
            }
            if (stream.eat("/") != null) {
                stream.skipToEnd()
                return "comment"
            }
        }

        stream.backUp(1)

        // update scope info
        val m = stream.match(Regex("^(\\w+)\\s*\\("), false)
        if (m != null && yacasBodiedOps.contains(m.groupValues[1])) {
            state.scopes.add("bodied")
        }

        var scope = currentScope(state)

        if (scope == "bodied" && stream.peek() == "[") {
            state.scopes.removeLastOrNull()
        }

        val peeked = stream.peek()
        if (peeked == "[" || peeked == "{" || peeked == "(") {
            state.scopes.add(peeked)
        }

        scope = currentScope(state)

        if ((scope == "[" && peeked == "]") ||
            (scope == "{" && peeked == "}") ||
            (scope == "(" && peeked == ")")
        ) {
            state.scopes.removeLastOrNull()
        }

        if (peeked == ";") {
            while (currentScope(state) == "bodied") {
                state.scopes.removeLastOrNull()
            }
        }

        // look for ordered rules
        if (stream.match(Regex("^\\d+ *#")) != null) {
            return "qualifier"
        }

        // look for numbers
        if (stream.match(reFloatForm) != null) {
            return "number"
        }

        // look for placeholders
        if (stream.match(rePattern) != null) {
            return "variableName.special"
        }

        // match all braces separately
        if (stream.match(Regex("^(?:\\[|\\]|\\{|\\}|\\(|\\))")) != null) {
            return "bracket"
        }

        // literals looking like function calls
        if (stream.match(reFunctionLike) != null) {
            stream.backUp(1)
            return "variableName.function"
        }

        // all other identifiers
        if (stream.match(reIdentifier) != null) {
            return "variable"
        }

        // operators
        if (stream.match(
                Regex("^(?:\\\\|\\+|\\-|\\*|\\/|,|;|\\.|:|@|~|=|>|<|&|\\||_|`|'|\\^|\\?|!|%|#)")
            ) != null
        ) {
            return "operator"
        }

        stream.next()
        return "error"
    }

    override fun startState(indentUnit: Int) = YacasState(
        tokenize = ::tokenBase
    )

    override fun copyState(state: YacasState) = YacasState(
        tokenize = state.tokenize,
        scopes = state.scopes.toMutableList()
    )

    override fun token(stream: StringStream, state: YacasState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }

    override fun indent(state: YacasState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != ::tokenBase) return null

        var delta = 0
        if (textAfter == "]" || textAfter == "];" ||
            textAfter == "}" || textAfter == "};" ||
            textAfter == ");"
        ) {
            delta = -1
        }

        return (state.scopes.size + delta) * context.unit
    }
}
