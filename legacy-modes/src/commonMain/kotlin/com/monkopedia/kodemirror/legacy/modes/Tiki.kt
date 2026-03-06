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

data class TikiContext(
    val prev: TikiContext?,
    val pluginName: String?,
    val indent: Int,
    val startOfLine: Boolean,
    val noIndent: Boolean
)

data class TikiState(
    var tokenize: (StringStream, TikiState) -> String?,
    var cc: MutableList<(String?) -> Boolean> = mutableListOf(),
    var indented: Int = 0,
    var startOfLine: Boolean = true,
    var pluginName: String? = null,
    var context: TikiContext? = null
)

/** Stream parser for Tiki Wiki. */
val tiki: StreamParser<TikiState> = object : StreamParser<TikiState> {
    override val name: String get() = "tiki"

    private var curState: TikiState? = null
    private var setStyle: String? = null
    private var pluginName: String? = null
    private var type: String? = null

    private fun inBlock(
        style: String,
        terminator: String,
        returnTokenizer: ((StringStream, TikiState) -> String?)? = null
    ): (StringStream, TikiState) -> String? {
        return fun(stream: StringStream, state: TikiState): String? {
            while (!stream.eol()) {
                if (stream.match(terminator)) {
                    state.tokenize = ::inText
                    break
                }
                stream.next()
            }
            if (returnTokenizer != null) state.tokenize = returnTokenizer
            return style
        }
    }

    private fun inLine(style: String): (StringStream, TikiState) -> String? {
        return fun(stream: StringStream, state: TikiState): String? {
            while (!stream.eol()) {
                stream.next()
            }
            state.tokenize = ::inText
            return style
        }
    }

    private fun inAttribute(quote: String): (StringStream, TikiState) -> String? {
        return fun(stream: StringStream, state: TikiState): String? {
            while (!stream.eol()) {
                if (stream.next() == quote) {
                    state.tokenize = ::inPlugin
                    break
                }
            }
            return "string"
        }
    }

    private fun inAttributeNoQuote(): (StringStream, TikiState) -> String? {
        return fun(stream: StringStream, state: TikiState): String? {
            while (!stream.eol()) {
                val ch = stream.next()
                val peek = stream.peek()
                if (ch == " " || ch == "," ||
                    (peek != null && Regex("[ )}]").containsMatchIn(peek))
                ) {
                    state.tokenize = ::inPlugin
                    break
                }
            }
            return "string"
        }
    }

    private fun inPlugin(stream: StringStream, state: TikiState): String? {
        val ch = stream.next() ?: return null
        val peek = stream.peek()

        if (ch == "}") {
            state.tokenize = ::inText
            return "tag"
        } else if (ch == "(" || ch == ")") {
            return "bracket"
        } else if (ch == "=") {
            type = "equals"
            if (peek == ">") {
                stream.next()
                @Suppress("UNUSED_VARIABLE")
                val nextPeek = stream.peek()
            }
            val afterPeek = stream.peek()
            if (afterPeek != null && !Regex("['\"]").containsMatchIn(afterPeek)) {
                state.tokenize = inAttributeNoQuote()
            }
            return "operator"
        } else if (ch == "'" || ch == "\"") {
            state.tokenize = inAttribute(ch)
            return state.tokenize(stream, state)
        } else {
            stream.eatWhile(Regex("[^\\s\\u00a0=\"'/?]"))
            return "keyword"
        }
    }

    private fun inText(stream: StringStream, state: TikiState): String? {
        fun chain(parser: (StringStream, TikiState) -> String?): String? {
            state.tokenize = parser
            return parser(stream, state)
        }

        val sol = stream.sol()
        val ch = stream.next() ?: return null

        when (ch) {
            "{" -> {
                stream.eat("/")
                stream.eatSpace()
                stream.eatWhile(Regex("[^\\s\\u00a0=\"'/?}(]"))
                state.tokenize = ::inPlugin
                return "tag"
            }
            "_" -> if (stream.eat("_") != null) {
                return chain(inBlock("strong", "__", ::inText))
            }
            "'" -> if (stream.eat("'") != null) {
                return chain(inBlock("em", "''", ::inText))
            }
            "(" -> if (stream.eat("(") != null) {
                return chain(inBlock("link", "))", ::inText))
            }
            "[" -> return chain(inBlock("url", "]", ::inText))
            "|" -> if (stream.eat("|") != null) {
                return chain(inBlock("comment", "||"))
            }
            "-" -> {
                if (stream.eat("=") != null) {
                    return chain(inBlock("header string", "=-", ::inText))
                } else if (stream.eat("-") != null) {
                    return chain(inBlock("error tw-deleted", "--", ::inText))
                }
            }
            "=" -> if (stream.match("==")) {
                return chain(inBlock("tw-underline", "===", ::inText))
            }
            ":" -> if (stream.eat(":") != null) {
                return chain(inBlock("comment", "::"))
            }
            "^" -> return chain(inBlock("tw-box", "^"))
            "~" -> if (stream.match("np~")) {
                return chain(inBlock("meta", "~/np~"))
            }
        }

        if (sol) {
            when (ch) {
                "!" -> {
                    if (stream.match("!!!!!")) {
                        return chain(inLine("header string"))
                    } else if (stream.match("!!!!")) {
                        return chain(inLine("header string"))
                    } else if (stream.match("!!!")) {
                        return chain(inLine("header string"))
                    } else if (stream.match("!!")) {
                        return chain(inLine("header string"))
                    } else {
                        return chain(inLine("header string"))
                    }
                }
                "*", "#", "+" -> return chain(inLine("tw-listitem bracket"))
            }
        }

        return null
    }

    override fun startState(indentUnit: Int) = TikiState(
        tokenize = ::inText
    )

    override fun copyState(state: TikiState) = TikiState(
        tokenize = state.tokenize,
        cc = state.cc.toMutableList(),
        indented = state.indented,
        startOfLine = state.startOfLine,
        pluginName = state.pluginName,
        context = state.context
    )

    override fun token(stream: StringStream, state: TikiState): String? {
        if (stream.sol()) {
            state.startOfLine = true
            state.indented = stream.indentation()
        }
        if (stream.eatSpace()) return null

        setStyle = null
        type = null
        pluginName = null
        val style = state.tokenize(stream, state)
        state.startOfLine = false
        return setStyle ?: style
    }

    override fun indent(state: TikiState, textAfter: String, cx: IndentContext): Int? {
        var context = state.context
        if (context != null && context.noIndent) return 0
        if (context != null && Regex("^\\{/").containsMatchIn(textAfter)) {
            context = context.prev
        }
        var ctx = context
        while (ctx != null && !ctx.startOfLine) {
            ctx = ctx.prev
        }
        return if (ctx != null) ctx.indent + cx.unit else 0
    }
}
