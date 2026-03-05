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

data class SpreadsheetState(
    var stringType: String? = null,
    var stack: MutableList<String> = mutableListOf()
)

val spreadsheet: StreamParser<SpreadsheetState> = object : StreamParser<SpreadsheetState> {
    override val name: String get() = "spreadsheet"

    override fun startState(indentUnit: Int) = SpreadsheetState()

    override fun copyState(state: SpreadsheetState) = SpreadsheetState(
        stringType = state.stringType,
        stack = state.stack.toMutableList()
    )

    override fun token(stream: StringStream, state: SpreadsheetState): String? {
        // check for state changes
        if (state.stack.isEmpty()) {
            val peek = stream.peek()
            if (peek == "\"" || peek == "'") {
                state.stringType = peek
                stream.next()
                state.stack.add(0, "string")
            }
        }

        // return state based on stack
        when (state.stack.firstOrNull()) {
            "string" -> {
                while (state.stack.firstOrNull() == "string" && !stream.eol()) {
                    if (stream.peek() == state.stringType) {
                        stream.next()
                        state.stack.removeFirst()
                    } else if (stream.peek() == "\\") {
                        stream.next()
                        stream.next()
                    } else {
                        stream.match(Regex("^.[^\\\\\"']*"))
                    }
                }
                return "string"
            }
            "characterClass" -> {
                while (state.stack.firstOrNull() == "characterClass" && !stream.eol()) {
                    if (!(
                            stream.match(Regex("^[^\\]\\\\]+")) != null ||
                                stream.match(Regex("^\\\\.")) != null
                            )
                    ) {
                        state.stack.removeFirst()
                    }
                }
                return "operator"
            }
        }

        val peek = stream.peek()

        // no stack
        when (peek) {
            "[" -> {
                stream.next()
                state.stack.add(0, "characterClass")
                return "bracket"
            }
            ":" -> {
                stream.next()
                return "operator"
            }
            "\\" -> {
                return if (stream.match(Regex("^\\\\[a-z]+")) != null) {
                    "string.special"
                } else {
                    stream.next()
                    "atom"
                }
            }
            ".", ",", ";", "*", "-", "+", "^", "<", "/", "=" -> {
                stream.next()
                return "atom"
            }
            "$" -> {
                stream.next()
                return "builtin"
            }
        }

        if (stream.match(Regex("^\\d+")) != null) {
            if (stream.match(Regex("^\\w+")) != null) return "error"
            return "number"
        } else if (stream.match(Regex("^[a-zA-Z_]\\w*")) != null) {
            if (stream.match(Regex("^(?=[(.])"), false) != null) return "keyword"
            return "variable"
        } else if (
            peek != null && listOf("[", "]", "(", ")", "{", "}").contains(peek)
        ) {
            stream.next()
            return "bracket"
        } else if (!stream.eatSpace()) {
            stream.next()
        }
        return null
    }
}
