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

private val dLangBlockKeywordsStr =
    "body catch class do else enum for foreach foreach_reverse if in interface mixin " +
        "out scope struct switch try union unittest version while with"

private val dLangKeywords = (
    "abstract alias align asm assert auto break case cast cdouble cent cfloat const continue " +
        "debug default delegate delete deprecated export extern final finally function goto " +
        "immutable import inout invariant is lazy macro module new nothrow override package " +
        "pragma private protected public pure ref return shared short static super synchronized " +
        "template this throw typedef typeid typeof volatile __FILE__ __LINE__ __gshared " +
        "__traits __vector __parameters $dLangBlockKeywordsStr"
    ).split(" ").toSet()

private val dLangBlockKeywords = dLangBlockKeywordsStr.split(" ").toSet()

private val dLangBuiltin = (
    "bool byte char creal dchar double float idouble ifloat int ireal long real short ubyte " +
        "ucent uint ulong ushort wchar wstring void size_t sizediff_t"
    ).split(" ").toSet()

private val dLangAtoms = setOf("exit", "failure", "success", "true", "false", "null")

private val dLangIsOperatorChar = Regex("[+\\-*&%=<>!?|/]")

internal data class DContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: DContext?
)

data class DState(
    var tokenize: ((StringStream, DState) -> String?)? = null,
    var context: DContext,
    var indented: Int = 0,
    var startOfLine: Boolean = true,
    var curPunc: String? = null
)

private fun dPushContext(state: DState, col: Int, type: String) {
    val indent = if (state.context.type == "statement") state.context.indented else state.indented
    state.context = DContext(indent, col, type, null, state.context)
}

private fun dPopContext(state: DState): DContext? {
    val t = state.context.type
    if (t == ")" || t == "]" || t == "}") {
        state.indented = state.context.indented
    }
    state.context = state.context.prev ?: return null
    return state.context
}

private fun dTokenBase(stream: StringStream, state: DState): String? {
    val ch = stream.next() ?: return null
    if (ch == "@") {
        stream.eatWhile(Regex("[\\w$_]"))
        return "meta"
    }
    if (ch == "\"" || ch == "'" || ch == "`") {
        state.tokenize = dTokenString(ch)
        return state.tokenize!!(stream, state)
    }
    if (Regex("[\\[\\]{}(),;:.]").containsMatchIn(ch)) {
        state.curPunc = ch
        return null
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    }
    if (ch == "/") {
        if (stream.eat("+") != null) {
            state.tokenize = ::dTokenNestedComment
            return dTokenNestedComment(stream, state)
        }
        if (stream.eat("*") != null) {
            state.tokenize = ::dTokenComment
            return dTokenComment(stream, state)
        }
        if (stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
    }
    if (dLangIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(dLangIsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w$_\u00a1-\uffff]"))
    val cur = stream.current()
    if (cur in dLangKeywords) {
        if (cur in dLangBlockKeywords) state.curPunc = "newstatement"
        return "keyword"
    }
    if (cur in dLangBuiltin) {
        if (cur in dLangBlockKeywords) state.curPunc = "newstatement"
        return "builtin"
    }
    if (cur in dLangAtoms) return "atom"
    return "variable"
}

private fun dTokenString(quote: String): (StringStream, DState) -> String? = { stream, state ->
    var escaped = false
    var next: String?
    var end = false
    while (true) {
        next = stream.next()
        if (next == null) break
        if (next == quote && !escaped) {
            end = true
            break
        }
        escaped = !escaped && next == "\\"
    }
    if (end || !(escaped)) state.tokenize = null
    "string"
}

private fun dTokenComment(stream: StringStream, state: DState): String {
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

private fun dTokenNestedComment(stream: StringStream, state: DState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "/" && maybeEnd) {
            state.tokenize = null
            break
        }
        maybeEnd = ch == "+"
    }
    return "comment"
}

val d: StreamParser<DState> = object : StreamParser<DState> {
    override val name: String get() = "d"

    override fun startState(indentUnit: Int) = DState(
        context = DContext(-indentUnit, 0, "top", false, null)
    )

    override fun copyState(state: DState) = state.copy(
        context = state.context.copy()
    )

    override fun token(stream: StringStream, state: DState): String? {
        val ctx = state.context
        if (stream.sol()) {
            if (ctx.align == null) ctx.align = false
            state.indented = stream.indentation()
            state.startOfLine = true
        }
        if (stream.eatSpace()) return null
        state.curPunc = null
        val style = (state.tokenize ?: ::dTokenBase)(stream, state)
        if (style == "comment" || style == "meta") return style
        if (ctx.align == null) ctx.align = true

        val punc = state.curPunc
        if ((punc == ";" || punc == ":" || punc == ",") && ctx.type == "statement") {
            dPopContext(state)
        } else if (punc == "{") {
            dPushContext(state, stream.column(), "}")
        } else if (punc == "[") {
            dPushContext(state, stream.column(), "]")
        } else if (punc == "(") {
            dPushContext(state, stream.column(), ")")
        } else if (punc == "}") {
            var c = state.context
            while (c.type == "statement") {
                dPopContext(state)
                c = state.context
            }
            if (c.type == "}") dPopContext(state)
            c = state.context
            while (c.type == "statement") {
                dPopContext(state)
                c = state.context
            }
        } else if (punc == ctx.type) {
            dPopContext(state)
        } else if (((ctx.type == "}" || ctx.type == "top") && punc != ";") ||
            (ctx.type == "statement" && punc == "newstatement")
        ) {
            dPushContext(state, stream.column(), "statement")
        }
        state.startOfLine = false
        return style
    }

    override fun indent(state: DState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != null && state.tokenize != ::dTokenBase) return null
        var ctx = state.context
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        if (ctx.type == "statement" && firstChar == "}") ctx = ctx.prev ?: ctx
        val closing = firstChar == ctx.type
        return when {
            ctx.type == "statement" ->
                ctx.indented + if (firstChar == "{") 0 else context.unit
            ctx.align == true -> ctx.column + if (closing) 0 else 1
            else -> ctx.indented + if (closing) 0 else context.unit
        }
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s*[{}]$"),
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
