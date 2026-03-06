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

data class PegjsState(
    var inString: Boolean = false,
    var stringType: String? = null,
    var inComment: Boolean = false,
    var inCharacterClass: Boolean = false,
    var braced: Int = 0,
    var lhs: Boolean = true,
    var localState: Unit? = null
)

private fun pegjsIdentifier(stream: StringStream): MatchResult? {
    return stream.match(Regex("^[a-zA-Z_][a-zA-Z0-9_]*"))
}

/** Stream parser for PEG.js. */
val pegjs: StreamParser<PegjsState> = object : StreamParser<PegjsState> {
    override val name: String get() = "pegjs"

    override fun startState(indentUnit: Int) = PegjsState()

    override fun copyState(state: PegjsState) = state.copy()

    override fun token(stream: StringStream, state: PegjsState): String? {
        if (!state.inString && !state.inComment &&
            (stream.peek() == "\"" || stream.peek() == "'")
        ) {
            state.stringType = stream.peek()
            stream.next()
            state.inString = true
        }
        if (!state.inString && !state.inComment && stream.match("/*")) {
            state.inComment = true
        }

        if (state.inString) {
            while (state.inString && !stream.eol()) {
                if (stream.peek() == state.stringType) {
                    stream.next()
                    state.inString = false
                } else if (stream.peek() == "\\") {
                    stream.next()
                    stream.next()
                } else {
                    stream.match(Regex("^.[^\\\\\"\\']*"))
                }
            }
            return if (state.lhs) "property string" else "string"
        } else if (state.inComment) {
            while (state.inComment && !stream.eol()) {
                if (stream.match("*/")) {
                    state.inComment = false
                } else {
                    stream.match(Regex("^.[^*]*"))
                }
            }
            return "comment"
        } else if (state.inCharacterClass) {
            while (state.inCharacterClass && !stream.eol()) {
                if (stream.match(Regex("^[^\\]\\\\]+")) == null &&
                    stream.match(Regex("^\\\\.")) == null
                ) {
                    state.inCharacterClass = false
                }
            }
        } else if (stream.peek() == "[") {
            stream.next()
            state.inCharacterClass = true
            return "bracket"
        } else if (stream.match("//")) {
            stream.skipToEnd()
            return "comment"
        } else if (state.braced > 0 || stream.peek() == "{") {
            // Simplified: we don't embed a full JS parser, just track braces
            val ch = stream.next() ?: return null
            if (ch == "{") {
                state.braced++
                return null
            } else if (ch == "}") {
                state.braced--
                return null
            }
            stream.eatWhile(Regex("[^{}]"))
            return null
        } else if (pegjsIdentifier(stream) != null) {
            return if (stream.peek() == ":") "variable" else "variable-2"
        } else if (
            listOf("[", "]", "(", ")").contains(stream.peek() ?: "")
        ) {
            stream.next()
            return "bracket"
        } else if (!stream.eatSpace()) {
            stream.next()
        }
        return null
    }
}
