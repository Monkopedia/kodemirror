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

data class TomlState(
    var inString: Boolean = false,
    var stringType: String = "",
    var lhs: Boolean = true,
    var inArray: Int = 0
)

/** Stream parser for TOML. */
val toml: StreamParser<TomlState> = object : StreamParser<TomlState> {
    override val name: String get() = "toml"
    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "#"))

    override fun startState(indentUnit: Int) = TomlState()
    override fun copyState(state: TomlState) = state.copy()

    override fun token(stream: StringStream, state: TomlState): String? {
        if (!state.inString) {
            val quote = stream.match(Regex("^('''|\"\"\"|'|\")"))
            if (quote != null) {
                state.stringType = quote.value
                state.inString = true
            }
        }
        if (stream.sol() && !state.inString && state.inArray == 0) {
            state.lhs = true
        }
        if (state.inString) {
            while (state.inString) {
                if (stream.match(state.stringType)) {
                    state.inString = false
                } else if (stream.peek() == "\\") {
                    stream.next()
                    stream.next()
                } else if (stream.eol()) {
                    break
                } else {
                    stream.match(Regex("""^.[^\\\"\\']*"""))
                }
            }
            return if (state.lhs) "propertyName" else "string"
        } else if (state.inArray > 0 && stream.peek() == "]") {
            stream.next()
            state.inArray--
            return "bracket"
        } else if (state.lhs && stream.peek() == "[" && stream.skipTo("]")) {
            stream.next()
            if (stream.peek() == "]") stream.next()
            return "atom"
        } else if (stream.peek() == "#") {
            stream.skipToEnd()
            return "comment"
        } else if (stream.eatSpace()) {
            return null
        } else if (state.lhs && stream.eatWhile { it != "=" && it != " " }) {
            return "propertyName"
        } else if (state.lhs && stream.peek() == "=") {
            stream.next()
            state.lhs = false
            return null
        } else if (!state.lhs && stream.match(Regex("""^\d\d\d\d[\d\-:\.T]*Z""")) != null) {
            return "atom"
        } else if (!state.lhs && (stream.match("true") || stream.match("false"))) {
            return "atom"
        } else if (!state.lhs && stream.peek() == "[") {
            state.inArray++
            stream.next()
            return "bracket"
        } else if (!state.lhs && stream.match(Regex("""^-?\d+(?:\.\d+)?""")) != null) {
            return "number"
        } else if (!stream.eatSpace()) {
            stream.next()
        }
        return null
    }
}
