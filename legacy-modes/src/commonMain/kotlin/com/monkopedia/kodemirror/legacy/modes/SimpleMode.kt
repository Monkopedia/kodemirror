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

/**
 * State for a [simpleMode]-based parser.
 */
data class SimpleModeState(
    var state: String = "start",
    var pending: MutableList<PendingToken>? = null,
    var indent: MutableList<Int>? = null,
    var stack: MutableList<String>? = null
)

data class PendingToken(val text: String, val token: String?)

/**
 * A rule in a simple mode state.
 */
class SimpleModeRule(
    val regex: Regex,
    // String?, List<String?>, or (MatchResult) -> String?
    val token: Any?,
    val sol: Boolean = false,
    val next: String? = null,
    val push: String? = null,
    val pop: Boolean = false,
    val indent: Boolean = false,
    val dedent: Boolean = false,
    val dedentIfLineStart: Boolean = true
)

/**
 * A configuration object for [simpleMode].
 * Keys are state names, values are lists of rule configs.
 */
class SimpleModeConfig(
    val states: Map<String, List<SimpleModeRule>>,
    val languageData: Map<String, Any> = emptyMap(),
    val name: String = "",
    val mergeTokens: Boolean = true
)

/**
 * Create a [StreamParser] from a simple mode spec — a set of named states,
 * each containing a list of regex-based rules.
 */
fun simpleMode(config: SimpleModeConfig): StreamParser<SimpleModeState> {
    require("start" in config.states) { "Undefined state start in simple mode" }
    val hasIndentation = config.states.values.any { rules ->
        rules.any { it.indent || it.dedent }
    }
    return object : StreamParser<SimpleModeState> {
        override val name: String get() = config.name
        override val languageData: Map<String, Any> get() = config.languageData
        override val mergeTokens: Boolean get() = config.mergeTokens

        override fun startState(indentUnit: Int) = SimpleModeState(
            indent = if (hasIndentation) mutableListOf() else null
        )

        override fun copyState(state: SimpleModeState): SimpleModeState {
            return SimpleModeState(
                state = state.state,
                pending = state.pending?.toMutableList(),
                indent = state.indent?.toMutableList(),
                stack = state.stack?.toMutableList()
            )
        }

        override fun token(stream: StringStream, state: SimpleModeState): String? {
            val pend = state.pending
            if (pend != null && pend.isNotEmpty()) {
                val p = pend.removeFirst()
                if (pend.isEmpty()) state.pending = null
                stream.pos += p.text.length
                return p.token
            }
            val curState = config.states[state.state] ?: return run {
                stream.next()
                null
            }
            for (rule in curState) {
                if (rule.sol && !stream.sol()) continue
                val matches = stream.match(rule.regex)
                if (matches != null) {
                    if (rule.next != null) {
                        state.state = rule.next
                    } else if (rule.push != null) {
                        val stack = state.stack ?: mutableListOf<String>().also {
                            state.stack = it
                        }
                        stack.add(state.state)
                        state.state = rule.push
                    } else if (rule.pop) {
                        val stack = state.stack
                        if (stack != null && stack.isNotEmpty()) {
                            state.state = stack.removeLast()
                        }
                    }
                    if (rule.indent) {
                        state.indent?.add(
                            stream.indentation() + stream.indentUnit
                        )
                    }
                    if (rule.dedent) {
                        state.indent?.removeLast()
                    }
                    val token = resolveToken(rule.token, matches)
                    val groups = matches.groupValues
                    if (groups.size > 2 && rule.token is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        val tokenList = rule.token as List<String?>
                        state.pending = mutableListOf()
                        for (j in 2 until groups.size) {
                            if (groups[j].isNotEmpty()) {
                                state.pending!!.add(
                                    PendingToken(
                                        groups[j],
                                        tokenList.getOrNull(j - 1)
                                    )
                                )
                            }
                        }
                        val consumed = groups[1].length
                        stream.backUp(matches.value.length - consumed)
                        return tokenList.firstOrNull()
                    } else if (token is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        return (token as List<String?>).firstOrNull()
                    }
                    return token as? String
                }
            }
            stream.next()
            return null
        }

        override fun indent(
            state: SimpleModeState,
            textAfter: String,
            context: IndentContext
        ): Int? {
            val indent = state.indent ?: return null
            var pos = indent.size - 1
            val rules = config.states[state.state] ?: return null
            var remaining = textAfter
            outer@ while (true) {
                for (rule in rules) {
                    if (rule.dedent && rule.dedentIfLineStart) {
                        val m = rule.regex.find(remaining)
                        if (m != null && m.value.isNotEmpty()) {
                            pos--
                            remaining = remaining.substring(m.value.length)
                            continue@outer
                        }
                    }
                }
                break
            }
            return if (pos < 0) 0 else indent.getOrElse(pos) { 0 }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun resolveToken(token: Any?, matches: kotlin.text.MatchResult): Any? {
    if (token == null) return null
    if (token is Function1<*, *>) {
        return (token as (kotlin.text.MatchResult) -> String?)(matches)
    }
    if (token is String) return token.replace(".", " ")
    if (token is List<*>) {
        return (token as List<String?>).map { it?.replace(".", " ") }
    }
    return token
}
