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

private fun sparqlWordRegexp(words: List<String>): Regex =
    Regex("^(?:${words.joinToString("|")})$", RegexOption.IGNORE_CASE)

private val sparqlOps = sparqlWordRegexp(
    listOf(
        "str", "lang", "langmatches", "datatype", "bound", "sameterm", "isiri", "isuri",
        "iri", "uri", "bnode", "count", "sum", "min", "max", "avg", "sample",
        "group_concat", "rand", "abs", "ceil", "floor", "round", "concat", "substr", "strlen",
        "replace", "ucase", "lcase", "encode_for_uri", "contains", "strstarts", "strends",
        "strbefore", "strafter", "year", "month", "day", "hours", "minutes", "seconds",
        "timezone", "tz", "now", "uuid", "struuid", "md5", "sha1", "sha256", "sha384",
        "sha512", "coalesce", "if", "strlang", "strdt", "isnumeric", "regex", "exists",
        "isblank", "isliteral", "a", "bind"
    )
)
private val sparqlKeywords = sparqlWordRegexp(
    listOf(
        "base", "prefix", "select", "distinct", "reduced", "construct", "describe",
        "ask", "from", "named", "where", "order", "limit", "offset", "filter", "optional",
        "graph", "by", "asc", "desc", "as", "having", "undef", "values", "group",
        "minus", "in", "not", "service", "silent", "using", "insert", "delete", "union",
        "true", "false", "with",
        "data", "copy", "to", "move", "add", "create", "drop", "clear", "load", "into"
    )
)
private val sparqlOperatorChars = Regex("[*+\\-<>=&|^/!?]")
private val sparqlPnChars = "[A-Za-z_\\-0-9]"
private val sparqlPrefixStart = Regex("[A-Za-z]")
private val sparqlPrefixRemainder = Regex("(($sparqlPnChars|\\.)*($sparqlPnChars))?:")

data class SparqlContext(
    val prev: SparqlContext?,
    val indent: Int,
    val col: Int,
    val type: String,
    var align: Boolean?
)

data class SparqlState(
    var tokenize: (StringStream, SparqlState) -> String? = ::sparqlTokenBase,
    var context: SparqlContext? = null,
    var indent: Int = 0,
    var col: Int = 0,
    var curPunc: String? = null
)

private fun sparqlEatPnLocal(stream: StringStream) {
    stream.match(
        Regex(
            "(\\.(?=[\\w_\\-\\\\%])|[:\\w_-]|\\\\[-\\\\_~.!\$&'()*+,;=/?#@%]|" +
                "%[a-fA-F\\d][a-fA-F\\d])+"
        )
    )
}

private fun sparqlTokenLiteral(quote: String): (StringStream, SparqlState) -> String? {
    return fn@{ stream, state ->
        var escaped = false
        var ch: String?
        while (stream.next().also { ch = it } != null) {
            if (ch == quote && !escaped) {
                state.tokenize = ::sparqlTokenBase
                break
            }
            escaped = !escaped && ch == "\\"
        }
        "string"
    }
}

private fun sparqlTokenBase(stream: StringStream, state: SparqlState): String? {
    val ch = stream.next() ?: return null
    state.curPunc = null

    if (ch == "\$" || ch == "?") {
        if (ch == "?" && stream.match(Regex("\\s"), consume = false) != null) {
            return "operator"
        }
        stream.match(
            Regex(
                "^[A-Za-z0-9_\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D" +
                    "\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF" +
                    "\uF900-\uFDCF\uFDF0-\uFFFD]" +
                    "[A-Za-z0-9_\u00B7\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u037D" +
                    "\u037F-\u1FFF\u200C-\u200D\u203F-\u2040\u2070-\u218F\u2C00-\u2FEF" +
                    "\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD]*"
            )
        )
        return "variableName.local"
    } else if (ch == "<" && stream.match(Regex("^[\\s\u00a0=]"), consume = false) == null) {
        stream.match(Regex("^[^\\s\u00a0>]*>?"))
        return "atom"
    } else if (ch == "\"" || ch == "'") {
        state.tokenize = sparqlTokenLiteral(ch)
        return state.tokenize(stream, state)
    } else if (Regex("[{}(),;.\\[\\]]").containsMatchIn(ch)) {
        state.curPunc = ch
        return "bracket"
    } else if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    } else if (sparqlOperatorChars.containsMatchIn(ch)) {
        return "operator"
    } else if (ch == ":") {
        sparqlEatPnLocal(stream)
        return "atom"
    } else if (ch == "@") {
        stream.eatWhile(Regex("[a-z\\d\\-]"))
        return "meta"
    } else if (sparqlPrefixStart.containsMatchIn(ch) &&
        stream.match(sparqlPrefixRemainder) != null
    ) {
        sparqlEatPnLocal(stream)
        return "atom"
    }

    stream.eatWhile(Regex("[_\\w\\d]"))
    val word = stream.current()
    return when {
        sparqlOps.matches(word) -> "builtin"
        sparqlKeywords.matches(word) -> "keyword"
        else -> "variable"
    }
}

private fun sparqlPushContext(state: SparqlState, type: String, col: Int) {
    state.context = SparqlContext(
        prev = state.context,
        indent = state.indent,
        col = col,
        type = type,
        align = null
    )
}

private fun sparqlPopContext(state: SparqlState) {
    state.indent = state.context?.indent ?: 0
    state.context = state.context?.prev
}

val sparql: StreamParser<SparqlState> = object : StreamParser<SparqlState> {
    override val name: String get() = "sparql"

    override fun startState(indentUnit: Int) = SparqlState()

    override fun copyState(state: SparqlState): SparqlState {
        fun copyCtx(ctx: SparqlContext?): SparqlContext? = ctx?.let {
            SparqlContext(
                prev = copyCtx(it.prev),
                indent = it.indent,
                col = it.col,
                type = it.type,
                align = it.align
            )
        }
        return SparqlState(
            tokenize = state.tokenize,
            context = copyCtx(state.context),
            indent = state.indent,
            col = state.col,
            curPunc = state.curPunc
        )
    }

    override fun token(stream: StringStream, state: SparqlState): String? {
        if (stream.sol()) {
            if (state.context != null && state.context!!.align == null) {
                state.context!!.align = false
            }
            state.indent = stream.indentation()
        }
        if (stream.eatSpace()) return null

        state.curPunc = null
        val style = state.tokenize(stream, state)

        if (style != "comment" && state.context != null &&
            state.context!!.align == null && state.context!!.type != "pattern"
        ) {
            state.context!!.align = true
        }

        val curPunc = state.curPunc
        when {
            curPunc == "(" -> sparqlPushContext(state, ")", stream.column())
            curPunc == "[" -> sparqlPushContext(state, "]", stream.column())
            curPunc == "{" -> sparqlPushContext(state, "}", stream.column())
            curPunc != null && (curPunc == "]" || curPunc == "}" || curPunc == ")") -> {
                while (state.context?.type == "pattern") sparqlPopContext(state)
                val ctxAfter = state.context
                if (ctxAfter != null && curPunc == ctxAfter.type) {
                    sparqlPopContext(state)
                    if (curPunc == "}" && state.context?.type == "pattern") {
                        sparqlPopContext(state)
                    }
                }
            }
            curPunc == "." && state.context?.type == "pattern" ->
                sparqlPopContext(state)
            style != null && Regex("atom|string|variable").containsMatchIn(style) &&
                state.context != null -> {
                val ctxType = state.context!!.type
                if (ctxType == "}" || ctxType == "]") {
                    sparqlPushContext(state, "pattern", stream.column())
                } else if (ctxType == "pattern" && state.context!!.align != true) {
                    state.context!!.align = true
                }
            }
        }

        return style
    }

    override fun indent(state: SparqlState, textAfter: String, context: IndentContext): Int {
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        var ctx = state.context
        if (firstChar == "]" || firstChar == "}") {
            while (ctx != null && ctx.type == "pattern") ctx = ctx.prev
        }
        val finalCtx = ctx
        val closing = finalCtx != null && firstChar == finalCtx.type
        return when {
            finalCtx == null -> 0
            finalCtx.type == "pattern" -> finalCtx.col
            finalCtx.align == true -> finalCtx.col + if (closing) 0 else 1
            else -> finalCtx.indent + if (closing) 0 else context.unit
        }
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf("line" to "#")
        )
}
