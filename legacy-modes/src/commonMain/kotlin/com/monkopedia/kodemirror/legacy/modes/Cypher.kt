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

private fun cypherWordRegexp(words: List<String>): Regex =
    Regex("^(?:${words.joinToString("|")})$", RegexOption.IGNORE_CASE)

private val cypherFuncs = cypherWordRegexp(
    listOf(
        "abs", "acos", "allShortestPaths", "asin", "atan", "atan2", "avg", "ceil", "coalesce",
        "collect", "cos", "cot", "count", "degrees", "e", "endnode", "exp", "extract", "filter",
        "floor", "haversin", "head", "id", "keys", "labels", "last", "left", "length", "log",
        "log10", "lower", "ltrim", "max", "min", "node", "nodes", "percentileCont",
        "percentileDisc", "pi", "radians", "rand", "range", "reduce", "rel", "relationship",
        "relationships", "replace", "reverse", "right", "round", "rtrim", "shortestPath", "sign",
        "sin", "size", "split", "sqrt", "startnode", "stdev", "stdevp", "str", "substring", "sum",
        "tail", "tan", "timestamp", "toFloat", "toInt", "toString", "trim", "type", "upper"
    )
)

private val cypherPreds = cypherWordRegexp(
    listOf(
        "all", "and", "any", "contains", "exists", "has", "in", "none", "not", "or", "single", "xor"
    )
)

private val cypherKeywords = cypherWordRegexp(
    listOf(
        "as", "asc", "ascending", "assert", "by", "case", "commit", "constraint", "create",
        "csv", "cypher", "delete", "desc", "descending", "detach", "distinct", "drop", "else",
        "end", "ends", "explain", "false", "fieldterminator", "foreach", "from", "headers", "in",
        "index", "is", "join", "limit", "load", "match", "merge", "null", "on", "optional",
        "order", "periodic", "profile", "remove", "return", "scan", "set", "skip", "start",
        "starts", "then", "true", "union", "unique", "unwind", "using", "when", "where", "with",
        "call", "yield"
    )
)

private val cypherSystemKeywords = cypherWordRegexp(
    listOf(
        "access", "active", "assign", "all", "alter", "as", "catalog", "change", "copy", "create",
        "constraint", "constraints", "current", "database", "databases", "dbms", "default", "deny",
        "drop", "element", "elements", "exists", "from", "grant", "graph", "graphs", "if", "index",
        "indexes", "label", "labels", "management", "match", "name", "names", "new", "node",
        "nodes", "not", "of", "on", "or", "password", "populated", "privileges", "property",
        "read", "relationship", "relationships", "remove", "replace", "required", "revoke", "role",
        "roles", "set", "show", "start", "status", "stop", "suspended", "to", "traverse", "type",
        "types", "user", "users", "with", "write"
    )
)

private val cypherOperatorChars = Regex("[*+\\-<>=&|~%^]")

data class CypherContext(
    val prev: CypherContext?,
    val indent: Int,
    val col: Int,
    val type: String,
    var align: Boolean? = null
)

data class CypherState(
    var context: CypherContext? = null,
    var indent: Int = 0,
    var col: Int = 0
)

private var cypherCurPunc: String? = null

private fun cypherPushContext(state: CypherState, type: String, col: Int) {
    state.context = CypherContext(
        prev = state.context,
        indent = state.indent,
        col = col,
        type = type
    )
}

private fun cypherPopContext(state: CypherState) {
    state.indent = state.context?.indent ?: 0
    state.context = state.context?.prev
}

private fun cypherTokenBase(stream: StringStream, state: CypherState): String? {
    cypherCurPunc = null
    val ch = stream.next() ?: return null
    if (ch == "\"") {
        stream.match(Regex("^.*?\""))
        return "string"
    }
    if (ch == "'") {
        stream.match(Regex("^.*?'"))
        return "string"
    }
    if (Regex("[{}(),.;\\[\\]]").containsMatchIn(ch)) {
        cypherCurPunc = ch
        return "punctuation"
    }
    if (ch == "/" && stream.eat("/") != null) {
        stream.skipToEnd()
        return "comment"
    }
    if (cypherOperatorChars.containsMatchIn(ch)) {
        stream.eatWhile(cypherOperatorChars)
        return null
    }
    stream.eatWhile(Regex("[_\\w\\d]"))
    if (stream.eat(":") != null) {
        stream.eatWhile(Regex("[\\w\\d_\\-]"))
        return "atom"
    }
    val word = stream.current()
    if (cypherFuncs.matches(word)) return "builtin"
    if (cypherPreds.matches(word)) return "def"
    if (cypherKeywords.matches(word) || cypherSystemKeywords.matches(word)) return "keyword"
    return "variable"
}

val cypher: StreamParser<CypherState> = object : StreamParser<CypherState> {
    override val name: String get() = "cypher"

    override fun startState(indentUnit: Int) = CypherState()

    override fun copyState(state: CypherState) = state.copy(
        context = state.context?.copy()
    )

    override fun token(stream: StringStream, state: CypherState): String? {
        if (stream.sol()) {
            if (state.context?.align == null) {
                state.context?.align = false
            }
            state.indent = stream.indentation()
        }
        if (stream.eatSpace()) return null

        val style = cypherTokenBase(stream, state)
        if (style != "comment" && state.context?.align == null &&
            state.context?.type != "pattern"
        ) {
            state.context?.align = true
        }

        val punc = cypherCurPunc
        if (punc == "(") {
            cypherPushContext(state, ")", stream.column())
        } else if (punc == "[") {
            cypherPushContext(state, "]", stream.column())
        } else if (punc == "{") {
            cypherPushContext(state, "}", stream.column())
        } else if (punc != null && Regex("[\\])}]").containsMatchIn(punc)) {
            while (state.context?.type == "pattern") {
                cypherPopContext(state)
            }
            if (state.context != null && punc == state.context?.type) {
                cypherPopContext(state)
            }
        } else if (punc == "." && state.context?.type == "pattern") {
            cypherPopContext(state)
        } else if (style != null && Regex("atom|string|variable").containsMatchIn(style) &&
            state.context != null
        ) {
            val ctxType = state.context?.type ?: ""
            if (Regex("[}\\]]").containsMatchIn(ctxType)) {
                cypherPushContext(state, "pattern", stream.column())
            } else if (ctxType == "pattern" && state.context?.align == false) {
                state.context?.align = true
                state.context = state.context?.copy(col = stream.column())
            }
        }

        return style
    }

    override fun indent(state: CypherState, textAfter: String, context: IndentContext): Int? {
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        var ctx = state.context
        if (Regex("[\\]}]").containsMatchIn(firstChar)) {
            while (ctx?.type == "pattern") {
                ctx = ctx.prev
            }
        }
        val closing = ctx != null && firstChar == ctx.type
        if (ctx == null) return 0
        if (ctx.type == "keywords") return null
        if (ctx.align == true) return ctx.col + if (closing) 0 else 1
        return ctx.indent + if (closing) 0 else context.unit
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf("line" to "//")
        )
}
