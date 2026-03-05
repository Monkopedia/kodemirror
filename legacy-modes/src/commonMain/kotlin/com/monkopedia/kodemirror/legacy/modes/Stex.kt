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

import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

class StexPlugin(
    val name: String,
    val style: String,
    val styles: List<String?> = emptyList()
) {
    var bracketNo: Int = 0
    var argument: String? = null

    fun styleIdentifier(): String? {
        return styles.getOrNull(bracketNo - 1)
    }

    fun openBracket(): String {
        bracketNo++
        return "bracket"
    }

    @Suppress("UNUSED_PARAMETER")
    fun closeBracket(ch: String? = null) {
    }

    fun copy(): StexPlugin {
        val p = StexPlugin(name, style, styles)
        p.bracketNo = bracketNo
        p.argument = argument
        return p
    }
}

private val stexPlugins: Map<String, () -> StexPlugin> = mapOf(
    "importmodule" to { StexPlugin("importmodule", "tag", listOf("string", "builtin")) },
    "documentclass" to { StexPlugin("documentclass", "tag", listOf("", "atom")) },
    "usepackage" to { StexPlugin("usepackage", "tag", listOf("atom")) },
    "begin" to { StexPlugin("begin", "tag", listOf("atom")) },
    "end" to { StexPlugin("end", "tag", listOf("atom")) },
    "label" to { StexPlugin("label", "tag", listOf("atom")) },
    "ref" to { StexPlugin("ref", "tag", listOf("atom")) },
    "eqref" to { StexPlugin("eqref", "tag", listOf("atom")) },
    "cite" to { StexPlugin("cite", "tag", listOf("atom")) },
    "bibitem" to { StexPlugin("bibitem", "tag", listOf("atom")) },
    "Bibitem" to { StexPlugin("Bibitem", "tag", listOf("atom")) },
    "RBibitem" to { StexPlugin("RBibitem", "tag", listOf("atom")) }
)

private fun defaultPlugin(): StexPlugin = StexPlugin("DEFAULT", "tag")

data class StexState(
    var cmdState: MutableList<StexPlugin> = mutableListOf(),
    var f: (StringStream, StexState) -> String?
)

private fun mkStex(mathMode: Boolean): StreamParser<StexState> {
    fun pushCommand(state: StexState, command: StexPlugin) {
        state.cmdState.add(command)
    }

    fun peekCommand(state: StexState): StexPlugin? {
        return if (state.cmdState.isNotEmpty()) state.cmdState.last() else null
    }

    fun popCommand(state: StexState) {
        val plug = state.cmdState.removeLastOrNull()
        plug?.closeBracket()
    }

    fun getMostPowerful(state: StexState): StexPlugin {
        for (i in state.cmdState.indices.reversed()) {
            val plug = state.cmdState[i]
            if (plug.name == "DEFAULT") continue
            return plug
        }
        return StexPlugin("_fallback", "tag")
    }

    fun setState(state: StexState, f: (StringStream, StexState) -> String?) {
        state.f = f
    }

    // Forward references for mutually recursive local functions
    var normalRef: (StringStream, StexState) -> String? = { _, _ -> null }
    var beginParamsRef: (StringStream, StexState) -> String? = { _, _ -> null }

    fun inMathMode(source: StringStream, state: StexState, endModeSeq: String? = null): String? {
        if (source.eatSpace()) return null
        if (endModeSeq != null && source.match(endModeSeq)) {
            setState(state, normalRef)
            return "keyword"
        }
        if (source.match(Regex("^\\\\[a-zA-Z@]+")) != null) return "tag"
        if (source.match(Regex("^[a-zA-Z]+")) != null) return "variableName.special"
        if (source.match(Regex("^\\\\[\$&%#\\{}_]")) != null) return "tag"
        if (source.match(Regex("^\\\\[,;!/]")) != null) return "tag"
        if (source.match(Regex("^[\\^_&]")) != null) return "tag"
        if (source.match(Regex("^[+\\-<>|=,/@!*:;'\"`~#?]")) != null) return null
        if (source.match(Regex("^(\\d+\\.\\d*|\\d*\\.\\d+|\\d+)")) != null) return "number"
        val ch = source.next() ?: return null
        if (ch == "{" || ch == "}" || ch == "[" || ch == "]" || ch == "(" || ch == ")") {
            return "bracket"
        }
        if (ch == "%") {
            source.skipToEnd()
            return "comment"
        }
        return "error"
    }

    fun normal(source: StringStream, state: StexState): String? {
        if (source.match(Regex("^\\\\[a-zA-Z@\\xc0-\\u1fff\\u2060-\\uffff]+")) != null) {
            val cmdName = source.current().substring(1)
            val plugFactory = stexPlugins[cmdName]
            val plug = plugFactory?.invoke() ?: defaultPlugin()
            pushCommand(state, plug)
            setState(state, beginParamsRef)
            return plug.style
        }
        if (source.match(Regex("^\\\\[\$&%#\\{}_]")) != null) return "tag"
        if (source.match(Regex("^\\\\[,;!\\\\/]")) != null) return "tag"

        if (source.match("\\[")) {
            setState(state) { s, st -> inMathMode(s, st, "\\]") }
            return "keyword"
        }
        if (source.match("\\(")) {
            setState(state) { s, st -> inMathMode(s, st, "\\)") }
            return "keyword"
        }
        if (source.match("\$\$")) {
            setState(state) { s, st -> inMathMode(s, st, "\$\$") }
            return "keyword"
        }
        if (source.match("\$")) {
            setState(state) { s, st -> inMathMode(s, st, "\$") }
            return "keyword"
        }

        val ch = source.next() ?: return null
        if (ch == "%") {
            source.skipToEnd()
            return "comment"
        } else if (ch == "}" || ch == "]") {
            val plug = peekCommand(state)
            if (plug != null) {
                plug.closeBracket(ch)
                setState(state, beginParamsRef)
            } else {
                return "error"
            }
            return "bracket"
        } else if (ch == "{" || ch == "[") {
            val plug = defaultPlugin()
            pushCommand(state, plug)
            return "bracket"
        } else if (Regex("\\d").containsMatchIn(ch)) {
            source.eatWhile(Regex("[\\w.%]"))
            return "atom"
        } else {
            source.eatWhile(Regex("[\\w\\-_]"))
            val plug = getMostPowerful(state)
            if (plug.name == "begin") {
                plug.argument = source.current()
            }
            return plug.styleIdentifier()
        }
    }

    fun beginParams(source: StringStream, state: StexState): String? {
        val ch = source.peek()
        if (ch == "{" || ch == "[") {
            val lastPlug = peekCommand(state)
            lastPlug?.openBracket()
            source.eat(ch!!)
            setState(state, ::normal)
            return "bracket"
        }
        if (ch != null && Regex("[ \\t\\r]").containsMatchIn(ch)) {
            source.eat(ch)
            return null
        }
        setState(state, ::normal)
        popCommand(state)
        return normal(source, state)
    }

    normalRef = ::normal
    beginParamsRef = ::beginParams

    return object : StreamParser<StexState> {
        override val name: String get() = "stex"
        override val languageData: Map<String, Any>
            get() = mapOf("commentTokens" to mapOf("line" to "%"))

        override fun startState(indentUnit: Int): StexState {
            val f: (StringStream, StexState) -> String? = if (mathMode) {
                { s, st -> inMathMode(s, st) }
            } else {
                ::normal
            }
            return StexState(f = f)
        }

        override fun copyState(state: StexState) = StexState(
            cmdState = state.cmdState.map { it.copy() }.toMutableList(),
            f = state.f
        )

        override fun token(stream: StringStream, state: StexState): String? {
            return state.f(stream, state)
        }

        override fun blankLine(state: StexState, indentUnit: Int) {
            state.f = ::normal
            state.cmdState.clear()
        }
    }
}

val stex: StreamParser<StexState> = mkStex(false)
val stexMath: StreamParser<StexState> = mkStex(true)
