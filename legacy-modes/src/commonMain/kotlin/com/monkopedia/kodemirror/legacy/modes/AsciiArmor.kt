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

data class AsciiArmorState(
    var state: String = "top",
    var type: String? = null
)

private fun errorIfNotEmpty(stream: StringStream): String? {
    val nonWS = stream.match(Regex("^\\s*\\S"))
    stream.skipToEnd()
    return if (nonWS != null) "error" else null
}

val asciiArmor: StreamParser<AsciiArmorState> =
    object : StreamParser<AsciiArmorState> {
        override val name: String get() = "asciiarmor"

        override fun startState(indentUnit: Int) = AsciiArmorState()
        override fun copyState(state: AsciiArmorState) = state.copy()

        override fun blankLine(state: AsciiArmorState, indentUnit: Int) {
            if (state.state == "headers") state.state = "body"
        }

        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        override fun token(stream: StringStream, state: AsciiArmorState): String? {
            if (state.state == "top") {
                if (stream.sol()) {
                    val m = stream.match(
                        Regex("^-----BEGIN (.*)?-----\\s*$")
                    )
                    if (m != null) {
                        state.state = "headers"
                        state.type = m.groupValues[1]
                        return "tag"
                    }
                }
                return errorIfNotEmpty(stream)
            } else if (state.state == "headers") {
                if (stream.sol() &&
                    stream.match(Regex("^\\w+:")) != null
                ) {
                    state.state = "header"
                    return "atom"
                } else {
                    val result = errorIfNotEmpty(stream)
                    if (result != null) state.state = "body"
                    return result
                }
            } else if (state.state == "header") {
                stream.skipToEnd()
                state.state = "headers"
                return "string"
            } else if (state.state == "body") {
                if (stream.sol()) {
                    val m = stream.match(
                        Regex("^-----END (.*)?-----\\s*$")
                    )
                    if (m != null) {
                        if (m.groupValues[1] != state.type) {
                            return "error"
                        }
                        state.state = "end"
                        return "tag"
                    }
                }
                if (stream.eatWhile(Regex("[A-Za-z0-9+/=]"))) {
                    return null
                } else {
                    stream.next()
                    return "error"
                }
            } else if (state.state == "end") {
                return errorIfNotEmpty(stream)
            }
            return null
        }
    }
