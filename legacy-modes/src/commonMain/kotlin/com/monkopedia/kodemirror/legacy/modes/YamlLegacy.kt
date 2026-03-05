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

data class YamlState(
    var pair: Boolean = false,
    var pairStart: Boolean = false,
    var keyCol: Int = 0,
    var inlinePairs: Int = 0,
    var inlineList: Int = 0,
    var literal: Boolean = false,
    var escaped: Boolean = false
)

private val cons = listOf("true", "false", "on", "off", "yes", "no")
private val keywordRegex =
    Regex("\\b((" + cons.joinToString(")|(") + "))$", RegexOption.IGNORE_CASE)

val yamlLegacy: StreamParser<YamlState> = object : StreamParser<YamlState> {
    override val name: String get() = "yaml"
    override fun startState(indentUnit: Int) = YamlState()
    override fun copyState(state: YamlState) = state.copy()

    override fun token(stream: StringStream, state: YamlState): String? {
        val ch = stream.peek()
        val esc = state.escaped
        state.escaped = false

        // comments
        if (ch == "#" &&
            (
                stream.pos == 0 ||
                    Regex("\\s").containsMatchIn(
                        stream.string[stream.pos - 1].toString()
                    )
                )
        ) {
            stream.skipToEnd()
            return "comment"
        }

        if (stream.match(Regex("^('([^']|\\\\.)*'?|\"([^\"]|\\\\.)*\"?)")) != null) {
            return "string"
        }

        if (state.literal && stream.indentation() > state.keyCol) {
            stream.skipToEnd()
            return "string"
        } else if (state.literal) {
            state.literal = false
        }

        if (stream.sol()) {
            state.keyCol = 0
            state.pair = false
            state.pairStart = false
            if (stream.match("---")) return "def"
            if (stream.match("...")) return "def"
            if (stream.match(Regex("^\\s*-\\s+")) != null) return "meta"
        }

        // inline pairs/lists
        if (stream.match(Regex("^(\\{|\\}|\\[|\\])")) != null) {
            if (ch == "{") {
                state.inlinePairs++
            } else if (ch == "}") {
                state.inlinePairs--
            } else if (ch == "[") {
                state.inlineList++
            } else {
                state.inlineList--
            }
            return "meta"
        }

        // list separator
        if (state.inlineList > 0 && !esc && ch == ",") {
            stream.next()
            return "meta"
        }
        // pairs separator
        if (state.inlinePairs > 0 && !esc && ch == ",") {
            state.keyCol = 0
            state.pair = false
            state.pairStart = false
            stream.next()
            return "meta"
        }

        // start of value of a pair
        if (state.pairStart) {
            if (stream.match(Regex("^\\s*(\\||>)\\s*")) != null) {
                state.literal = true
                return "meta"
            }
            if (stream.match(
                    Regex("^\\s*(\\&|\\*)[a-z0-9\\._-]+\\b", RegexOption.IGNORE_CASE)
                ) != null
            ) {
                return "variable"
            }
            if (state.inlinePairs == 0 &&
                stream.match(Regex("^\\s*-?[0-9\\.\\,]+\\s?\$")) != null
            ) {
                return "number"
            }
            if (state.inlinePairs > 0 &&
                stream.match(Regex("^\\s*-?[0-9\\.\\,]+\\s?(?=(,|\\}))")) != null
            ) {
                return "number"
            }
            if (stream.match(keywordRegex) != null) return "keyword"
        }

        // pairs (associative arrays) -> key
        if (!state.pair &&
            stream.match(
                Regex(
                    "^\\s*(?:[,\\[\\]{}\\&*!|>'\"%@`][^\\s'\":]" +
                        "|[^,\\[\\]{}#\\&*!|>'\"%@`])[^#]*?(?=\\s*:(\$|\\s))"
                )
            ) != null
        ) {
            state.pair = true
            state.keyCol = stream.indentation()
            return "atom"
        }
        if (state.pair && stream.match(Regex("^:\\s*")) != null) {
            state.pairStart = true
            return "meta"
        }

        state.pairStart = false
        state.escaped = (ch == "\\")
        stream.next()
        return null
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf("line" to "#")
        )
}
