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

private val fclKeywords = setOf(
    "term", "method", "accu", "rule", "then", "is", "and", "or",
    "if", "default"
)

private val fclStartBlocks = setOf(
    "var_input",
    "var_output",
    "fuzzify",
    "defuzzify",
    "function_block",
    "ruleblock"
)

private val fclEndBlocks = setOf(
    "end_ruleblock",
    "end_defuzzify",
    "end_function_block",
    "end_fuzzify",
    "end_var"
)

private val fclAtoms = setOf(
    "true",
    "false",
    "nan",
    "real",
    "min",
    "max",
    "cog",
    "cogs"
)

private val fclIsOperatorChar = Regex("[+\\-*&^%:=<>!|/]")

data class FclContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: FclContext?
)

data class FclState(
    var tokenize: ((StringStream, FclState) -> String?)? = null,
    var context: FclContext,
    var indented: Int = 0,
    var startOfLine: Boolean = true
)

private fun fclTokenBase(stream: StringStream, state: FclState): String? {
    val ch = stream.next() ?: return null

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

    if (ch == "/" || ch == "(") {
        if (stream.eat("*") != null) {
            state.tokenize = ::fclTokenComment
            return fclTokenComment(stream, state)
        }
        if (stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
    }
    if (fclIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(fclIsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w\$_\\u00a1-\\uffff]"))

    val cur = stream.current().lowercase()
    if (cur in fclKeywords || cur in fclStartBlocks || cur in fclEndBlocks) {
        return "keyword"
    }
    if (cur in fclAtoms) return "atom"
    return "variable"
}

private fun fclTokenComment(stream: StringStream, state: FclState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if ((ch == "/" || ch == ")") && maybeEnd) {
            state.tokenize = null
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun fclPushContext(state: FclState, col: Int, type: String) {
    state.context = FclContext(
        indented = state.indented,
        column = col,
        type = type,
        align = null,
        prev = state.context
    )
}

private fun fclPopContext(state: FclState) {
    val prev = state.context.prev ?: return
    if (state.context.type == "end_block") {
        state.indented = state.context.indented
    }
    state.context = prev
}

val fcl: StreamParser<FclState> = object : StreamParser<FclState> {
    override val name: String get() = "fcl"

    override fun startState(indentUnit: Int) = FclState(
        context = FclContext(
            indented = -indentUnit,
            column = 0,
            type = "top",
            align = false,
            prev = null
        )
    )

    override fun copyState(state: FclState) = state.copy(
        context = state.context.copy()
    )

    override fun token(stream: StringStream, state: FclState): String? {
        val ctx = state.context
        if (stream.sol()) {
            if (ctx.align == null) ctx.align = false
            state.indented = stream.indentation()
            state.startOfLine = true
        }
        if (stream.eatSpace()) return null

        val style = (state.tokenize ?: ::fclTokenBase)(stream, state)
        if (style == "comment") return style
        if (ctx.align == null) ctx.align = true

        val cur = stream.current().lowercase()

        if (cur in fclStartBlocks) {
            fclPushContext(state, stream.column(), "end_block")
        } else if (cur in fclEndBlocks) {
            fclPopContext(state)
        }

        state.startOfLine = false
        return style
    }

    override fun indent(state: FclState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != null) return 0
        val ctx = state.context

        val closing = textAfter.lowercase().let { it in fclEndBlocks }
        if (ctx.align == true) return ctx.column + if (closing) 0 else 1
        return ctx.indented + if (closing) 0 else context.unit
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "(*", "close" to "*)")
            )
        )
}
