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

data class TurtleContext(
    val prev: TurtleContext?,
    var indent: Int,
    var col: Int,
    val type: String,
    var align: Boolean? = null
)

data class TurtleState(
    var tokenize: (StringStream, TurtleState) -> String?,
    var context: TurtleContext?,
    var indent: Int,
    var col: Int
)

private val keywords = Regex("^(?:@prefix|@base|a)$", RegexOption.IGNORE_CASE)
private val operatorChars = Regex("[*+\\-<>=&|]")

/** Stream parser for Turtle (RDF). */
val turtle: StreamParser<TurtleState> = object : StreamParser<TurtleState> {
    override val name: String get() = "turtle"
    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "#"))

    private var curPunc: String? = null

    private fun tokenBase(stream: StringStream, state: TurtleState): String? {
        val ch = stream.next() ?: return null
        curPunc = null
        if (ch == "<" && stream.match(Regex("^[^\\s\\u00a0=]"), false) == null) {
            // not an atom
        } else if (ch == "<") {
            stream.match(Regex("^[^\\s\\u00a0>]*>?"))
            return "atom"
        }
        if (ch == "\"" || ch == "'") {
            state.tokenize = tokenLiteral(ch)
            return state.tokenize(stream, state)
        }
        if (Regex("[{}(),\\.;\\[\\]]").containsMatchIn(ch)) {
            curPunc = ch
            return null
        }
        if (ch == "#") {
            stream.skipToEnd()
            return "comment"
        }
        if (operatorChars.containsMatchIn(ch)) {
            stream.eatWhile(operatorChars)
            return null
        }
        if (ch == ":") {
            return "operator"
        }
        stream.eatWhile(Regex("[_\\w\\d]"))
        if (stream.peek() == ":") {
            return "variableName.special"
        }
        val word = stream.current()
        if (keywords.containsMatchIn(word)) {
            return "meta"
        }
        if (ch[0] in 'A'..'Z') {
            return "comment"
        }
        return "keyword"
    }

    private fun tokenLiteral(quote: String): (StringStream, TurtleState) -> String? {
        return fun(stream: StringStream, state: TurtleState): String? {
            var escaped = false
            var ch: String?
            while (true) {
                ch = stream.next()
                if (ch == null) break
                if (ch == quote && !escaped) {
                    state.tokenize = ::tokenBase
                    break
                }
                escaped = !escaped && ch == "\\"
            }
            return "string"
        }
    }

    private fun pushContext(state: TurtleState, type: String, col: Int) {
        state.context = TurtleContext(
            prev = state.context,
            indent = state.indent,
            col = col,
            type = type
        )
    }

    private fun popContext(state: TurtleState) {
        val ctx = state.context ?: return
        state.indent = ctx.indent
        state.context = ctx.prev
    }

    override fun startState(indentUnit: Int) = TurtleState(
        tokenize = ::tokenBase,
        context = null,
        indent = 0,
        col = 0
    )

    override fun copyState(state: TurtleState) = TurtleState(
        tokenize = state.tokenize,
        context = state.context?.copy(),
        indent = state.indent,
        col = state.col
    )

    override fun token(stream: StringStream, state: TurtleState): String? {
        if (stream.sol()) {
            if (state.context != null && state.context!!.align == null) {
                state.context!!.align = false
            }
            state.indent = stream.indentation()
        }
        if (stream.eatSpace()) return null
        val style = state.tokenize(stream, state)

        if (style != "comment" && state.context != null &&
            state.context!!.align == null && state.context!!.type != "pattern"
        ) {
            state.context!!.align = true
        }

        if (curPunc == "(") {
            pushContext(state, ")", stream.column())
        } else if (curPunc == "[") {
            pushContext(state, "]", stream.column())
        } else if (curPunc == "{") {
            pushContext(state, "}", stream.column())
        } else if (curPunc != null && Regex("[\\]})]]").containsMatchIn(curPunc!!)) {
            while (state.context != null && state.context!!.type == "pattern") popContext(state)
            if (state.context != null && curPunc == state.context!!.type) popContext(state)
        } else if (curPunc == "." && state.context != null &&
            state.context!!.type == "pattern"
        ) {
            popContext(state)
        } else if (style != null && Regex("atom|string|variable").containsMatchIn(style) &&
            state.context != null
        ) {
            if (Regex("[}\\]]").containsMatchIn(state.context!!.type)) {
                pushContext(state, "pattern", stream.column())
            } else if (state.context!!.type == "pattern" && state.context!!.align != true) {
                state.context!!.align = true
                state.context!!.col = stream.column()
            }
        }

        return style
    }

    override fun indent(state: TurtleState, textAfter: String, context: IndentContext): Int? {
        val firstChar = if (textAfter.isNotEmpty()) textAfter[0].toString() else ""
        var ctx = state.context
        if (Regex("[\\]})]]").containsMatchIn(firstChar)) {
            while (ctx != null && ctx.type == "pattern") ctx = ctx.prev
        }
        val closing = ctx != null && firstChar == ctx.type
        if (ctx == null) {
            return 0
        } else if (ctx.type == "pattern") {
            return ctx.col
        } else if (ctx.align == true) {
            return ctx.col + (if (closing) 0 else 1)
        } else {
            return ctx.indent + (if (closing) 0 else context.unit)
        }
    }
}
