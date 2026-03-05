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

private val TOKEN_NAMES = mapOf(
    '+' to "inserted",
    '-' to "deleted",
    '@' to "meta"
)

val diff: StreamParser<Unit> = object : StreamParser<Unit> {
    override val name: String get() = "diff"
    override fun startState(indentUnit: Int) = Unit
    override fun copyState(state: Unit) = Unit

    override fun token(stream: StringStream, state: Unit): String? {
        val twPos = Regex("[\\t ]+?$").find(stream.string)?.range?.first ?: -1
        if (!stream.sol() || twPos == 0) {
            stream.skipToEnd()
            val base = TOKEN_NAMES[stream.string[0]] ?: ""
            return "error $base".trimEnd()
        }
        val peek = stream.peek()
        val tokenName = if (peek != null) TOKEN_NAMES[peek[0]] else null
        if (tokenName == null) {
            stream.skipToEnd()
            return null
        }
        if (twPos == -1) {
            stream.skipToEnd()
        } else {
            stream.pos = twPos
        }
        return tokenName
    }
}
