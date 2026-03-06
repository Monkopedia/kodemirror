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

private val rCommonAtoms = listOf(
    "NULL", "NA", "Inf", "NaN", "NA_integer_", "NA_real_",
    "NA_complex_", "NA_character_", "TRUE", "FALSE"
)
private val rCommonBuiltins =
    listOf("list", "quote", "bquote", "eval", "return", "call", "parse", "deparse")
private val rCommonKeywords =
    listOf("if", "else", "repeat", "while", "function", "for", "in", "next", "break")
private val rCommonBlockKeywords = listOf("if", "else", "repeat", "while", "function", "for")

private val rAtoms = rCommonAtoms.toSet()
private val rBuiltins = rCommonBuiltins.toSet()
private val rKeywords = rCommonKeywords.toSet()
private val rBlockKeywords = rCommonBlockKeywords.toSet()
private val rOpChars = Regex("[+\\-*/^<>=!&|~\$:]")

// R context flags
private const val R_ALIGN_YES = 1
private const val R_ALIGN_NO = 2
private const val R_BRACELESS = 4

data class RContext(
    val type: String,
    val indent: Int,
    var flags: Int,
    val column: Int,
    val prev: RContext?,
    var argList: Boolean = false
)

data class RState(
    var tokenize: (StringStream, RState) -> String? = ::rTokenBase,
    var ctx: RContext = RContext("top", 0, R_ALIGN_NO, 0, null),
    var indent: Int = 0,
    var afterIdent: Boolean = false,
    var curPunc: String? = null
)

private fun rPush(state: RState, type: String, stream: StringStream) {
    state.ctx = RContext(
        type = type,
        indent = state.indent,
        flags = 0,
        column = stream.column(),
        prev = state.ctx
    )
}

private fun rSetFlag(state: RState, flag: Int) {
    val ctx = state.ctx
    state.ctx = ctx.copy(flags = ctx.flags or flag)
}

private fun rPop(state: RState) {
    state.indent = state.ctx.indent
    state.ctx = state.ctx.prev ?: state.ctx
}

private fun rTokenBase(stream: StringStream, state: RState): String? {
    state.curPunc = null
    val ch = stream.next() ?: return null

    if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    } else if (ch == "0" && stream.eat("x") != null) {
        stream.eatWhile(Regex("[\\da-f]", RegexOption.IGNORE_CASE))
        return "number"
    } else if (ch == "." && stream.eat(Regex("\\d")) != null) {
        stream.match(Regex("\\d*(?:e[+\\-]?\\d+)?"))
        return "number"
    } else if (Regex("\\d").containsMatchIn(ch)) {
        stream.match(Regex("\\d*(?:\\.\\d+)?(?:e[+\\-]\\d+)?L?"))
        return "number"
    } else if (ch == "'" || ch == "\"") {
        state.tokenize = rTokenString(ch)
        return "string"
    } else if (ch == "`") {
        stream.match(Regex("[^`]+`"))
        return "string.special"
    } else if (ch == "." && stream.match(Regex(".(?:[.]|\\d+)")) != null) {
        return "keyword"
    } else if (Regex("[a-zA-Z.]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        val word = stream.current()
        if (word in rAtoms) return "atom"
        if (word in rKeywords) {
            if (word in rBlockKeywords &&
                stream.match(Regex("\\s*if(\\s+|\$)"), consume = false) == null
            ) {
                state.curPunc = "block"
            }
            return "keyword"
        }
        if (word in rBuiltins) return "builtin"
        return "variable"
    } else if (ch == "%") {
        if (stream.skipTo("%")) stream.next()
        return "variableName.special"
    } else if (
        (ch == "<" && stream.eat("-") != null) ||
        (ch == "<" && stream.match("<-") != null) ||
        (ch == "-" && stream.match(Regex(">>?")) != null)
    ) {
        return "operator"
    } else if (ch == "=" && state.ctx.argList) {
        return "operator"
    } else if (rOpChars.containsMatchIn(ch)) {
        if (ch == "\$") return "operator"
        stream.eatWhile(rOpChars)
        return "operator"
    } else if (Regex("[(){}\\[\\];]").containsMatchIn(ch)) {
        state.curPunc = ch
        if (ch == ";") return "punctuation"
        return null
    }
    return null
}

private fun rTokenString(quote: String): (StringStream, RState) -> String? = fn@{ stream, state ->
    if (stream.eat("\\") != null) {
        val ch = stream.next()
        if (ch == "x") {
            stream.match(Regex("^[a-f0-9]{2}", RegexOption.IGNORE_CASE))
        } else if ((ch == "u" || ch == "U") && stream.eat("{") != null && stream.skipTo("}")) {
            stream.next()
        } else if (ch == "u") {
            stream.match(Regex("^[a-f0-9]{4}", RegexOption.IGNORE_CASE))
        } else if (ch == "U") {
            stream.match(Regex("^[a-f0-9]{8}", RegexOption.IGNORE_CASE))
        } else if (ch != null && Regex("[0-7]").containsMatchIn(ch)) {
            stream.match(
                Regex("^[0-7]{1,2}")
            )
        }
        return@fn "string.special"
    } else {
        var next: String?
        while (true) {
            next = stream.next() ?: break
            if (next == quote) {
                state.tokenize = ::rTokenBase
                break
            }
            if (next == "\\") {
                stream.backUp(1)
                break
            }
        }
        return@fn "string"
    }
}

/** Stream parser for R. */
val r: StreamParser<RState> = object : StreamParser<RState> {
    override val name: String get() = "r"

    override fun startState(indentUnit: Int) = RState(
        ctx = RContext(
            type = "top",
            indent = -indentUnit,
            flags = R_ALIGN_NO,
            column = 0,
            prev = null
        )
    )

    override fun copyState(state: RState) = state.copy(ctx = state.ctx.copy())

    override fun token(stream: StringStream, state: RState): String? {
        if (stream.sol()) {
            if ((state.ctx.flags and 3) == 0) rSetFlag(state, R_ALIGN_NO)
            if ((state.ctx.flags and R_BRACELESS) != 0) rPop(state)
            state.indent = stream.indentation()
        }
        if (stream.eatSpace()) return null
        val style = state.tokenize(stream, state)
        if (style != "comment" && (state.ctx.flags and R_ALIGN_NO) == 0) {
            rSetFlag(
                state,
                R_ALIGN_YES
            )
        }

        val curPunc = state.curPunc
        if (curPunc == ";" || curPunc == "{" || curPunc == "}") {
            if (state.ctx.type == "block") rPop(state)
        }
        when {
            curPunc == "{" -> rPush(state, "}", stream)
            curPunc == "(" -> {
                rPush(state, ")", stream)
                if (state.afterIdent) state.ctx.argList = true
            }
            curPunc == "[" -> rPush(state, "]", stream)
            curPunc == "block" -> rPush(state, "block", stream)
            curPunc == state.ctx.type -> rPop(state)
            state.ctx.type == "block" && style != "comment" -> rSetFlag(state, R_BRACELESS)
        }
        state.afterIdent = style == "variable" || style == "keyword"
        return style
    }

    override fun indent(state: RState, textAfter: String, context: IndentContext): Int {
        if (state.tokenize != ::rTokenBase) return 0
        val firstChar = if (textAfter.isNotEmpty()) textAfter[0].toString() else ""
        var ctx = state.ctx
        val closing = firstChar == ctx.type
        if ((ctx.flags and R_BRACELESS) != 0) ctx = ctx.prev ?: ctx
        return when {
            ctx.type == "block" -> ctx.indent + (if (firstChar == "{") 0 else context.unit)
            (ctx.flags and R_ALIGN_YES) != 0 -> ctx.column + (if (closing) 0 else 1)
            else -> ctx.indent + (if (closing) 0 else context.unit)
        }
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "wordChars" to ".",
            "commentTokens" to mapOf("line" to "#"),
            "autocomplete" to (rCommonAtoms + rCommonBuiltins + rCommonKeywords)
        )
}
