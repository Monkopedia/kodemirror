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

data class PropertiesState(
    var position: String = "def",
    var nextMultiline: Boolean = false,
    var inMultiline: Boolean = false,
    var afterSection: Boolean = false
)

val properties: StreamParser<PropertiesState> = object : StreamParser<PropertiesState> {
    override val name: String get() = "properties"
    override fun startState(indentUnit: Int) = PropertiesState()
    override fun copyState(state: PropertiesState) = state.copy()

    override fun token(stream: StringStream, state: PropertiesState): String? {
        val sol = stream.sol() || state.afterSection
        val eol = stream.eol()
        state.afterSection = false
        if (sol) {
            if (state.nextMultiline) {
                state.inMultiline = true
                state.nextMultiline = false
            } else {
                state.position = "def"
            }
        }
        if (eol && !state.nextMultiline) {
            state.inMultiline = false
            state.position = "def"
        }
        if (sol) {
            while (stream.eatSpace()) { /* consume */ }
        }
        val ch = stream.next() ?: return null
        if (sol && (ch == "#" || ch == "!" || ch == ";")) {
            state.position = "comment"
            stream.skipToEnd()
            return "comment"
        } else if (sol && ch == "[") {
            state.afterSection = true
            stream.skipTo("]")
            stream.eat("]")
            return "heading"
        } else if (ch == "=" || ch == ":") {
            state.position = "quote"
            return null
        } else if (ch == "\\" && state.position == "quote") {
            if (stream.eol()) {
                state.nextMultiline = true
            }
        }
        return state.position
    }
}
