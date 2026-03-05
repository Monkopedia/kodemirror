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

data class DtdState(
    var tokenize: (StringStream, DtdState) -> String? = ::dtdTokenBase,
    var baseIndent: Int = 0,
    var stack: MutableList<String> = mutableListOf()
)

private var dtdType: String? = null

private fun dtdRet(style: String?, tp: String?): String? {
    dtdType = tp
    return style
}

private fun dtdTokenBase(stream: StringStream, state: DtdState): String? {
    val ch = stream.next() ?: return null
    if (ch == "<" && stream.eat("!") != null) {
        return if (stream.eatWhile(Regex("-")) != false) {
            state.tokenize = ::dtdTokenSGMLComment
            dtdTokenSGMLComment(stream, state)
        } else if (stream.eatWhile(Regex("\\w")) != false) {
            dtdRet("keyword", "doindent")
        } else {
            dtdRet(null, null)
        }
    } else if (ch == "<" && stream.eat("?") != null) {
        state.tokenize = dtdInBlock("meta", "?>")
        return dtdRet("meta", ch)
    } else if (ch == "#" && stream.eatWhile(Regex("\\w")) != false) {
        return dtdRet("atom", "tag")
    } else if (ch == "|") {
        return dtdRet("keyword", "separator")
    } else if (Regex("[()\\[\\]\\-.,+?>]").containsMatchIn(ch)) {
        return dtdRet(null, ch)
    } else if (Regex("[\\[\\]]").containsMatchIn(ch)) {
        return dtdRet("rule", ch)
    } else if (ch == "\"" || ch == "'") {
        state.tokenize = dtdTokenString(ch)
        return state.tokenize(stream, state)
    } else if (stream.eatWhile(Regex("[a-zA-Z?+\\d]")) != false) {
        val sc = stream.current()
        val last = sc.lastOrNull()?.toString() ?: ""
        if (last == "?" || last == "+") stream.backUp(1)
        return dtdRet("tag", "tag")
    } else if (ch == "%" || ch == "*") {
        return dtdRet("number", "number")
    } else {
        stream.eatWhile(Regex("[\\w\\\\\\-_%.{,]"))
        return dtdRet(null, null)
    }
}

private fun dtdTokenSGMLComment(stream: StringStream, state: DtdState): String? {
    var dashes = 0
    while (true) {
        val ch = stream.next() ?: break
        if (dashes >= 2 && ch == ">") {
            state.tokenize = ::dtdTokenBase
            break
        }
        dashes = if (ch == "-") dashes + 1 else 0
    }
    return dtdRet("comment", "comment")
}

private fun dtdTokenString(quote: String): (StringStream, DtdState) -> String? = { stream, state ->
    var escaped = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == quote && !escaped) {
            state.tokenize = ::dtdTokenBase
            break
        }
        escaped = !escaped && ch == "\\"
    }
    dtdRet("string", "tag")
}

private fun dtdInBlock(style: String, terminator: String): (StringStream, DtdState) -> String? =
    { stream, state ->
        while (!stream.eol()) {
            if (stream.match(terminator) != null) {
                state.tokenize = ::dtdTokenBase
                break
            }
            stream.next()
        }
        style
    }

@Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
private fun dtdComputeIndent(state: DtdState, textAfter: String, cx: IndentContext): Int {
    var n = state.stack.size
    if (textAfter.firstOrNull()?.toString() == "]") {
        n--
    } else if (textAfter.endsWith(">")) {
        val last = textAfter.lastOrNull()?.toString() ?: ""
        val first = textAfter.firstOrNull()?.toString() ?: ""
        val t = dtdType
        if (first == "<") {
            // opening tag on this line: no change
        } else if (t == "doindent" && textAfter.length > 1) {
            // no change
        } else if (t == "doindent") {
            n--
        } else if (t == ">" && textAfter.length > 1) {
            // no change
        } else if (t == "tag" && last != ">") {
            // no change
        } else if (t == "tag" && state.stack.lastOrNull() == "rule") {
            n--
        } else if (t == "tag") {
            n++
        } else if (last == ">" && state.stack.lastOrNull() == "rule" && t == ">") {
            n--
        } else if (last == ">" && state.stack.lastOrNull() == "rule") {
            // no change
        } else if (first != "<" && first == ">") {
            n -= 1
        } else if (last == ">") {
            // no change
        } else {
            n -= 1
        }
        if (t == null || t == "]") n--
    }
    return state.baseIndent + n * cx.unit
}

val dtd: StreamParser<DtdState> = object : StreamParser<DtdState> {
    override val name: String get() = "dtd"

    override fun startState(indentUnit: Int) = DtdState()

    override fun copyState(state: DtdState) = DtdState(
        tokenize = state.tokenize,
        baseIndent = state.baseIndent,
        stack = state.stack.toMutableList()
    )

    override fun token(stream: StringStream, state: DtdState): String? {
        if (stream.eatSpace()) return null
        val style = state.tokenize(stream, state)

        val context = state.stack.lastOrNull()
        val cur = stream.current()
        val t = dtdType
        when {
            cur == "[" || t == "doindent" || t == "[" -> state.stack.add("rule")
            t == "endtag" -> {
                if (state.stack.isNotEmpty()) {
                    state.stack[state.stack.size - 1] = "endtag"
                }
            }
            cur == "]" || t == "]" || (t == ">" && context == "rule") -> {
                if (state.stack.isNotEmpty()) state.stack.removeAt(state.stack.size - 1)
            }
            t == "[" -> state.stack.add("[")
        }
        return style
    }

    override fun indent(state: DtdState, textAfter: String, context: IndentContext): Int {
        return dtdComputeIndent(state, textAfter, context)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s*[\\]>]$")
        )
}
