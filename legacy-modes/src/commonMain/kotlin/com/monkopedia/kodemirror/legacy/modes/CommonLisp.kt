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

private val clSpecialForm = Regex(
    "^(block|let\\*|return-from|catch|load-time-value|setq|eval-when|locally|" +
        "symbol-macrolet|flet|macrolet|tagbody|function|multiple-value-call|the|go|" +
        "multiple-value-prog1|throw|if|progn|unwind-protect|labels|progv|let|quote)$"
)
private val clAssumeBody = Regex("^with|^def|^do|^prog|case$|^cond$|bind$|when$|unless$")
private val clNumLiteral = Regex(
    "^(?:[+\\-]?(?:\\d+|\\d*\\.\\d+)(?:[efd][+\\-]?\\d+)?|" +
        "[+\\-]?\\d+(?:/[+\\-]?\\d+)?|" +
        "#b[+\\-]?[01]+|#o[+\\-]?[0-7]+|#x[+\\-]?[\\da-fA-F]+)"
)
private val clSymbol = Regex("[^\\s'`,@()\\[\\]\";]")

data class ClCtx(
    val prev: ClCtx?,
    val start: Int,
    var indentTo: Any? // Int, "next", or null
)

data class CommonLispState(
    var ctx: ClCtx = ClCtx(null, 0, 0),
    var lastType: String? = null,
    var tokenize: (StringStream, CommonLispState) -> String? = ::clBase
)

private var clTypeHolder: String? = null

private fun clReadSym(stream: StringStream): String {
    var ch: String?
    while (true) {
        ch = stream.next() ?: break
        if (ch == "\\") {
            stream.next()
        } else if (!clSymbol.containsMatchIn(ch)) {
            stream.backUp(1)
            break
        }
    }
    return stream.current()
}

private fun clInString(stream: StringStream, state: CommonLispState): String? {
    var escaped = false
    var next: String?
    while (true) {
        next = stream.next() ?: break
        if (next == "\"" && !escaped) {
            state.tokenize = ::clBase
            break
        }
        escaped = !escaped && next == "\\"
    }
    return "string"
}

private fun clInComment(stream: StringStream, state: CommonLispState): String? {
    var next: String?
    var last: String? = null
    while (true) {
        next = stream.next() ?: break
        if (next == "#" && last == "|") {
            state.tokenize = ::clBase
            break
        }
        last = next
    }
    clTypeHolder = "ws"
    return "comment"
}

private fun clBase(stream: StringStream, state: CommonLispState): String? {
    if (stream.eatSpace()) {
        clTypeHolder = "ws"
        return null
    }
    if (stream.match(clNumLiteral) != null) return "number"
    val ch = stream.next() ?: return null
    val escapedCh = if (ch == "\\") stream.next() else ch

    if (escapedCh == null) return null

    return when {
        ch == "\"" -> {
            state.tokenize = ::clInString
            clInString(stream, state)
        }
        ch == "(" -> {
            clTypeHolder = "open"
            "bracket"
        }
        ch == ")" -> {
            clTypeHolder = "close"
            "bracket"
        }
        ch == ";" -> {
            stream.skipToEnd()
            clTypeHolder = "ws"
            "comment"
        }
        Regex("['`,@]").containsMatchIn(ch) -> null
        ch == "|" -> {
            if (stream.skipTo("|")) {
                stream.next()
                "variableName"
            } else {
                stream.skipToEnd()
                "error"
            }
        }
        ch == "#" -> {
            val next = stream.next()
            when {
                next == "(" -> {
                    clTypeHolder = "open"
                    "bracket"
                }
                next != null && Regex("[+\\-=.']").containsMatchIn(next) -> null
                next != null && Regex("\\d").containsMatchIn(next) && stream.match(Regex("^\\d*#")) != null -> null
                next == "|" -> {
                    state.tokenize = ::clInComment
                    clInComment(stream, state)
                }
                next == ":" -> {
                    clReadSym(stream)
                    "meta"
                }
                next == "\\" -> {
                    stream.next()
                    clReadSym(stream)
                    "string.special"
                }
                else -> "error"
            }
        }
        else -> {
            val name = clReadSym(stream)
            if (name == ".") return null
            clTypeHolder = "symbol"
            when {
                name == "nil" || name == "t" || name.startsWith(":") -> "atom"
                state.lastType == "open" &&
                    (clSpecialForm.containsMatchIn(name) || clAssumeBody.containsMatchIn(name)) -> "keyword"
                name.startsWith("&") -> "variableName.special"
                else -> "variableName"
            }
        }
    }
}

val commonLisp: StreamParser<CommonLispState> = object : StreamParser<CommonLispState> {
    override val name: String get() = "commonlisp"

    override fun startState(indentUnit: Int) = CommonLispState()

    override fun copyState(state: CommonLispState): CommonLispState {
        fun copyCtx(ctx: ClCtx): ClCtx = ClCtx(
            prev = ctx.prev?.let { copyCtx(it) },
            start = ctx.start,
            indentTo = ctx.indentTo
        )
        return CommonLispState(
            ctx = copyCtx(state.ctx),
            lastType = state.lastType,
            tokenize = state.tokenize
        )
    }

    override fun token(stream: StringStream, state: CommonLispState): String? {
        if (stream.sol() && state.ctx.indentTo !is Int) {
            state.ctx.indentTo = state.ctx.start + 1
        }

        clTypeHolder = null
        val style = state.tokenize(stream, state)
        val type = clTypeHolder

        if (type != "ws") {
            if (state.ctx.indentTo == null) {
                if (type == "symbol" && clAssumeBody.containsMatchIn(stream.current())) {
                    state.ctx.indentTo = state.ctx.start + stream.indentUnit
                } else {
                    state.ctx.indentTo = "next"
                }
            } else if (state.ctx.indentTo == "next") {
                state.ctx.indentTo = stream.column()
            }
            state.lastType = type
        }

        if (type == "open") {
            state.ctx = ClCtx(prev = state.ctx, start = stream.column(), indentTo = null)
        } else if (type == "close") {
            state.ctx = state.ctx.prev ?: state.ctx
        }

        return style
    }

    override fun indent(state: CommonLispState, textAfter: String, context: IndentContext): Int {
        val i = state.ctx.indentTo
        return if (i is Int) i else state.ctx.start + 1
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to ";;",
                "block" to mapOf("open" to "#|", "close" to "|#")
            ),
            "closeBrackets" to mapOf(
                "brackets" to listOf("(", "[", "{", "\"")
            )
        )
}
