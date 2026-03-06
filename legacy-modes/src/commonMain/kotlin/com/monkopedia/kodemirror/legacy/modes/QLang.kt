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

@Suppress("ktlint:standard:max-line-length")
private val qKeywords = Regex(
    "^(abs|acos|aj|aj0|all|and|any|asc|asin|asof|atan|attr|avg|avgs|bin|by|ceiling|cols|cor|cos|count|cov|cross|csv|cut|delete|deltas|desc|dev|differ|distinct|div|do|each|ej|enlist|eval|except|exec|exit|exp|fby|fills|first|fkeys|flip|floor|from|get|getenv|group|gtime|hclose|hcount|hdel|hopen|hsym|iasc|idesc|if|ij|in|insert|inter|inv|key|keys|last|like|list|lj|load|log|lower|lsq|ltime|ltrim|mavg|max|maxs|mcount|md5|mdev|med|meta|min|mins|mmax|mmin|mmu|mod|msum|neg|next|not|null|or|over|parse|peach|pj|plist|prd|prds|prev|prior|rand|rank|ratios|raze|read0|read1|reciprocal|reverse|rload|rotate|rsave|rtrim|save|scan|select|set|setenv|show|signum|sin|sqrt|ss|ssr|string|sublist|sum|sums|sv|system|tables|tan|til|trim|txf|type|uj|ungroup|union|update|upper|upsert|value|var|view|views|vs|wavg|where|where|while|within|wj|wj1|wsum|xasc|xbar|xcol|xcols|xdesc|xexp|xgroup|xkey|xlog|xprev|xrank)$"
)

// Matches characters that terminate a token in Q
private val qTokenEnd = Regex("[|/&^!+:\\\\\\-*%\$=~#;@><,?_'\"\\[\\(\\]\\)\\s{}]")

data class QContext(
    val prev: QContext?,
    val indent: Int,
    val col: Int,
    val type: String,
    var align: Boolean? = null
)

data class QLangState(
    var tokenize: (StringStream, QLangState) -> String? = ::qTokenBase,
    var context: QContext? = null,
    var indent: Int = 0,
    var col: Int = 0
)

private var qCurPunc: String? = null

private fun qTokenBase(stream: StringStream, state: QLangState): String? {
    val sol = stream.sol()
    val c = stream.next() ?: return null
    qCurPunc = null

    if (sol) {
        if (c == "/") {
            return qTokenLineComment(stream, state)
        }
    } else if (c == "\\") {
        if (stream.eol() || stream.peek()?.let { Regex("\\s").containsMatchIn(it) } == true) {
            stream.skipToEnd()
            return if (Regex("^\\\\\\s*$").containsMatchIn(stream.current())) {
                state.tokenize = ::qTokenCommentToEOF
                qTokenCommentToEOF(stream, state)
                "comment"
            } else {
                state.tokenize = ::qTokenBase
                "comment"
            }
        } else {
            state.tokenize = ::qTokenBase
            return "builtin"
        }
    }

    if (Regex("\\s").containsMatchIn(c)) {
        return if (stream.peek() == "/") {
            stream.skipToEnd()
            "comment"
        } else {
            null
        }
    }

    if (c == "\"") {
        return qTokenString(stream, state)
    }

    if (c == "`") {
        stream.eatWhile(Regex("[A-Za-z\\d_:/.]"))
        return "macroName"
    }

    if ((c == "." && stream.peek()?.let { Regex("\\d").containsMatchIn(it) } == true) ||
        Regex("\\d").containsMatchIn(c)
    ) {
        var t: String? = null
        stream.backUp(1)
        if (stream.match(
                Regex(
                    "^\\d{4}\\.\\d{2}(m|\\.\\d{2}([DT](\\d{2}(:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?)?))?)"
                )
            ) != null ||
            stream.match(
                Regex("^\\d+D(\\d{2}(:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?)?)")
            ) != null ||
            stream.match(Regex("^\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?")) != null ||
            stream.match(Regex("^\\d+[ptuv]{1}")) != null
        ) {
            t = "temporal"
        } else if (
            stream.match(Regex("^0[NwW]{1}")) != null ||
            stream.match(Regex("^0x[\\da-fA-F]*")) != null ||
            stream.match(Regex("^[01]+[b]{1}")) != null ||
            stream.match(Regex("^\\d+[chijn]{1}")) != null ||
            stream.match(Regex("-?\\d*(\\.\\d*)?(e[+\\-]?\\d+)?(e|f)?")) != null
        ) {
            t = "number"
        }
        val peek = stream.peek()
        return if (t != null && (peek == null || qTokenEnd.containsMatchIn(peek))) {
            t
        } else {
            stream.next()
            "error"
        }
    }

    if (Regex("[A-Za-z]|\\.").containsMatchIn(c)) {
        stream.eatWhile(Regex("[A-Za-z._\\d]"))
        return if (qKeywords.matches(stream.current())) "keyword" else "variable"
    }

    if (Regex("[|/&^!+:\\\\\\-*%\$=~#;@><.,?_']").containsMatchIn(c)) {
        return null
    }

    if (Regex("[{}()\\[\\]]").containsMatchIn(c)) {
        qCurPunc = c
        return null
    }

    return "error"
}

private fun qTokenLineComment(stream: StringStream, state: QLangState): String {
    stream.skipToEnd()
    return if (Regex("^/\\s*$").containsMatchIn(stream.current())) {
        state.tokenize = ::qTokenBlockComment
        qTokenBlockComment(stream, state)
        "comment"
    } else {
        state.tokenize = ::qTokenBase
        "comment"
    }
}

private fun qTokenBlockComment(stream: StringStream, state: QLangState): String {
    val isStartOfLine = stream.sol() && stream.peek() == "\\"
    stream.skipToEnd()
    if (isStartOfLine && Regex("^\\\\\\s*$").containsMatchIn(stream.current())) {
        state.tokenize = ::qTokenBase
    }
    return "comment"
}

private fun qTokenCommentToEOF(
    stream: StringStream,
    @Suppress(
        "UNUSED_PARAMETER"
    ) state: QLangState
): String {
    stream.skipToEnd()
    return "comment"
}

private fun qTokenString(stream: StringStream, state: QLangState): String {
    var escaped = false
    var end = false
    while (true) {
        val next = stream.next() ?: break
        if (next == "\"" && !escaped) {
            end = true
            break
        }
        escaped = !escaped && next == "\\"
    }
    if (end) state.tokenize = ::qTokenBase
    return "string"
}

private fun qPushContext(state: QLangState, type: String, col: Int) {
    state.context = QContext(
        prev = state.context,
        indent = state.indent,
        col = col,
        type = type
    )
}

private fun qPopContext(state: QLangState) {
    val ctx = state.context ?: return
    state.indent = ctx.indent
    state.context = ctx.prev
}

val q: StreamParser<QLangState> = object : StreamParser<QLangState> {
    override val name: String get() = "q"

    override fun startState(indentUnit: Int) = QLangState()
    override fun copyState(state: QLangState) = state.copy()

    @Suppress("CyclomaticComplexMethod")
    override fun token(stream: StringStream, state: QLangState): String? {
        if (stream.sol()) {
            val ctx = state.context
            if (ctx != null && ctx.align == null) ctx.align = false
            state.indent = stream.indentation()
        }
        val style = state.tokenize(stream, state)

        val ctx = state.context
        if (style != "comment" && ctx != null && ctx.align == null && ctx.type != "pattern") {
            ctx.align = true
        }

        val cp = qCurPunc
        when {
            cp == "(" -> qPushContext(state, ")", stream.column())
            cp == "[" -> qPushContext(state, "]", stream.column())
            cp == "{" -> qPushContext(state, "}", stream.column())
            cp != null && Regex("[\\]})]]").containsMatchIn(cp) -> {
                var c = state.context
                while (c != null && c.type == "pattern") {
                    qPopContext(state)
                    c = state.context
                }
                if (state.context != null && cp == state.context?.type) {
                    qPopContext(state)
                }
            }
            cp == "." && ctx != null && ctx.type == "pattern" -> qPopContext(state)
            style != null &&
                Regex("atom|string|variable").containsMatchIn(style) && ctx != null -> {
                if (Regex("[}\\]]").containsMatchIn(ctx.type)) {
                    qPushContext(state, "pattern", stream.column())
                } else if (ctx.type == "pattern" && ctx.align != true) {
                    ctx.align = true
                    state.col = stream.column()
                }
            }
        }

        return style
    }

    override fun indent(state: QLangState, textAfter: String, context: IndentContext): Int {
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        var ctx = state.context
        if (Regex("[\\]}]").containsMatchIn(firstChar)) {
            while (ctx != null && ctx.type == "pattern") ctx = ctx.prev
        }
        val closing = ctx != null && firstChar == ctx.type
        return when {
            ctx == null -> 0
            ctx.type == "pattern" -> ctx.col
            ctx.align == true -> ctx.col + if (closing) 0 else 1
            else -> ctx.indent + if (closing) 0 else context.unit
        }
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf("line" to "/")
        )
}
