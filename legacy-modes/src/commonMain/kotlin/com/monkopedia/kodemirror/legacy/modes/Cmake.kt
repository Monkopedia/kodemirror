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

private val cmakeVariableRegex = Regex("^(\\{)?[a-zA-Z0-9_]+(\\})?")

data class CmakeState(
    var inDefinition: Boolean = false,
    var inInclude: Boolean = false,
    var continueString: Boolean = false,
    var pending: String? = null
)

private fun cmakeTokenString(stream: StringStream, state: CmakeState): String {
    var current: String?
    var prev: String? = null
    var foundVar = false
    while (!stream.eol()) {
        current = stream.next()
        if (current == "$" && prev != "\\" && state.pending == "\"") {
            foundVar = true
            break
        }
        prev = current
        if (current == state.pending) {
            break
        }
    }
    if (foundVar) {
        stream.backUp(1)
    }
    @Suppress("ComplexCondition")
    if (!foundVar && (prev == state.pending || stream.eol())) {
        state.continueString = false
    } else {
        state.continueString = true
    }
    return "string"
}

private fun cmakeTokenize(stream: StringStream, state: CmakeState): String? {
    val ch = stream.next() ?: return null

    if (ch == "$") {
        if (stream.match(cmakeVariableRegex) != null) {
            return "variableName.special"
        }
        return "variable"
    }
    if (state.continueString) {
        stream.backUp(1)
        return cmakeTokenString(stream, state)
    }
    if (stream.match(Regex("^(\\s+)?\\w+\\(")) != null ||
        stream.match(Regex("^(\\s+)?\\w+ \\(")) != null
    ) {
        stream.backUp(1)
        return "def"
    }
    if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    }
    if (ch == "'" || ch == "\"") {
        state.pending = ch
        return cmakeTokenString(stream, state)
    }
    if (ch == "(" || ch == ")") {
        return "bracket"
    }
    if (Regex("[0-9]").containsMatchIn(ch)) {
        return "number"
    }
    stream.eatWhile(Regex("[\\w-]"))
    return null
}

/** Stream parser for CMake. */
val cmake: StreamParser<CmakeState> = object : StreamParser<CmakeState> {
    override val name: String get() = "cmake"

    override fun startState(indentUnit: Int) = CmakeState()
    override fun copyState(state: CmakeState) = state.copy()

    override fun token(stream: StringStream, state: CmakeState): String? {
        if (stream.eatSpace()) return null
        return cmakeTokenize(stream, state)
    }
}
