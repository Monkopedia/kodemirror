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

private val goKeywords = setOf(
    "break", "case", "chan", "const", "continue", "default", "defer",
    "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
    "interface", "map", "package", "range", "return", "select", "struct",
    "switch", "type", "var", "bool", "byte", "complex64", "complex128",
    "float32", "float64", "int8", "int16", "int32", "int64", "string",
    "uint8", "uint16", "uint32", "uint64", "int", "uint", "uintptr",
    "error", "rune", "any", "comparable"
)

private val goAtoms = setOf(
    "true", "false", "iota", "nil", "append", "cap", "close", "complex",
    "copy", "delete", "imag", "len", "make", "new", "panic", "print",
    "println", "real", "recover"
)

private val goIsOperatorChar = Regex("[+\\-*&^%:=<>!|/]")

data class GoContext(
    val indented: Int,
    val column: Int,
    var type: String,
    var align: Boolean?,
    val prev: GoContext?
)

data class GoState(
    var tokenize: ((StringStream, GoState) -> String?)? = null,
    var context: GoContext,
    var indented: Int = 0,
    var startOfLine: Boolean = true,
    var curPunc: String? = null
)

private fun goTokenBase(stream: StringStream, state: GoState): String? {
    val ch = stream.next() ?: return null
    if (ch == "\"" || ch == "'" || ch == "`") {
        state.tokenize = goTokenString(ch)
        return state.tokenize!!(stream, state)
    }
    if (Regex("[\\d.]").containsMatchIn(ch)) {
        if (ch == ".") {
            stream.match(Regex("^[0-9]+([eE][\\-+]?[0-9]+)?"))
        } else if (ch == "0") {
            stream.match(Regex("^[xX][0-9a-fA-F]+")) ?: stream.match(Regex("^0[0-7]+"))
        } else {
            stream.match(Regex("^[0-9]*\\.?[0-9]*([eE][\\-+]?[0-9]+)?"))
        }
        return "number"
    }
    if (Regex("[\\[\\]{}(),;:.]").containsMatchIn(ch)) {
        state.curPunc = ch
        return null
    }
    if (ch == "/") {
        if (stream.eat("*") != null) {
            state.tokenize = ::goTokenComment
            return goTokenComment(stream, state)
        }
        if (stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
    }
    if (goIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(goIsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w\$_\\u00a1-\\uffff]"))
    val cur = stream.current()
    if (cur in goKeywords) {
        if (cur == "case" || cur == "default") state.curPunc = "case"
        return "keyword"
    }
    if (cur in goAtoms) return "atom"
    return "variable"
}

private fun goTokenString(quote: String): (StringStream, GoState) -> String {
    return fn@{ stream, state ->
        var escaped = false
        while (true) {
            val next = stream.next() ?: break
            if (next == quote && !escaped) {
                state.tokenize = null
                break
            }
            escaped = !escaped && quote != "`" && next == "\\"
        }
        "string"
    }
}

private fun goTokenComment(stream: StringStream, state: GoState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "/" && maybeEnd) {
            state.tokenize = null
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun goPushContext(state: GoState, col: Int, type: String) {
    state.context = GoContext(
        indented = state.indented,
        column = col,
        type = type,
        align = null,
        prev = state.context
    )
}

private fun goPopContext(state: GoState): GoContext? {
    val prev = state.context.prev ?: return null
    val t = state.context.type
    if (t == ")" || t == "]" || t == "}") {
        state.indented = state.context.indented
    }
    state.context = prev
    return state.context
}

val goLang: StreamParser<GoState> = object : StreamParser<GoState> {
    override val name: String get() = "go"

    override fun startState(indentUnit: Int) = GoState(
        context = GoContext(
            indented = -indentUnit,
            column = 0,
            type = "top",
            align = false,
            prev = null
        )
    )

    override fun copyState(state: GoState) = state.copy(
        context = state.context.copy()
    )

    override fun token(stream: StringStream, state: GoState): String? {
        val ctx = state.context
        if (stream.sol()) {
            if (ctx.align == null) ctx.align = false
            state.indented = stream.indentation()
            state.startOfLine = true
            if (ctx.type == "case") ctx.type = "}"
        }
        if (stream.eatSpace()) return null
        state.curPunc = null
        val style = (state.tokenize ?: ::goTokenBase)(stream, state)
        if (style == "comment") return style
        if (ctx.align == null) ctx.align = true

        val curPunc = state.curPunc
        if (curPunc == "{") {
            goPushContext(state, stream.column(), "}")
        } else if (curPunc == "[") {
            goPushContext(state, stream.column(), "]")
        } else if (curPunc == "(") {
            goPushContext(state, stream.column(), ")")
        } else if (curPunc == "case") {
            ctx.type = "case"
        } else if (curPunc == "}" && ctx.type == "}") {
            goPopContext(state)
        } else if (curPunc == ctx.type) goPopContext(state)
        state.startOfLine = false
        return style
    }

    override fun indent(state: GoState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != null) return null
        val ctx = state.context
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        if (ctx.type == "case" && Regex("^(?:case|default)\\b").containsMatchIn(textAfter)) {
            return ctx.indented
        }
        val closing = firstChar == ctx.type
        if (ctx.align == true) return ctx.column + if (closing) 0 else 1
        return ctx.indented + if (closing) 0 else context.unit
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s([{}]|case |default\\s*:)$"),
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
