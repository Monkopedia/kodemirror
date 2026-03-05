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

data class BrainfuckState(
    var commentLine: Boolean = false,
    var left: Int = 0,
    var right: Int = 0,
    var commentLoop: Boolean = false
)

private val reserve = setOf('>', '<', '+', '-', '.', ',', '[', ']')

val brainfuck: StreamParser<BrainfuckState> = object : StreamParser<BrainfuckState> {
    override val name: String get() = "brainfuck"
    override fun startState(indentUnit: Int) = BrainfuckState()
    override fun copyState(state: BrainfuckState) = state.copy()

    override fun token(stream: StringStream, state: BrainfuckState): String? {
        if (stream.eatSpace()) return null
        if (stream.sol()) state.commentLine = false
        val ch = stream.next() ?: return null
        val c = ch[0]
        if (c in reserve) {
            if (state.commentLine) {
                if (stream.eol()) state.commentLine = false
                return "comment"
            }
            if (c == '[' || c == ']') {
                if (c == '[') state.left++ else state.right++
                return "bracket"
            }
            if (c == '+' || c == '-') return "keyword"
            if (c == '<' || c == '>') return "atom"
            if (c == '.' || c == ',') return "variableName.definition"
        } else {
            state.commentLine = true
            if (stream.eol()) state.commentLine = false
            return "comment"
        }
        if (stream.eol()) state.commentLine = false
        return null
    }
}
