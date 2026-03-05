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

data class TroffState(
    var tokens: MutableList<((StringStream, TroffState) -> String?)> = mutableListOf()
)

val troff: StreamParser<TroffState> = object : StreamParser<TroffState> {
    override val name: String get() = "troff"

    override fun startState(indentUnit: Int) = TroffState()

    override fun copyState(state: TroffState) = TroffState(
        tokens = state.tokens.toMutableList()
    )

    private fun tokenBase(
        stream: StringStream,
        @Suppress(
            "UNUSED_PARAMETER"
        ) state: TroffState
    ): String? {
        if (stream.eatSpace()) return null

        val sol = stream.sol()
        val ch = stream.next() ?: return null

        if (ch == "\\") {
            if (stream.match("fB") || stream.match("fR") || stream.match("fI") ||
                stream.match("u") || stream.match("d") ||
                stream.match("%") || stream.match("&")
            ) {
                return "string"
            }
            if (stream.match("m[")) {
                stream.skipTo("]")
                stream.next()
                return "string"
            }
            if (stream.match("s+") || stream.match("s-")) {
                stream.eatWhile(Regex("""[\d-]"""))
                return "string"
            }
            if (stream.match("\\(") || stream.match("*\\(")) {
                stream.eatWhile(Regex("""[\w-]"""))
                return "string"
            }
            return "string"
        }
        if (sol && (ch == "." || ch == "'")) {
            if (stream.eat("\\") != null && stream.eat("\"") != null) {
                stream.skipToEnd()
                return "comment"
            }
        }
        if (sol && ch == ".") {
            if (stream.match("B ") || stream.match("I ") || stream.match("R ")) {
                return "attribute"
            }
            if (stream.match("TH ") || stream.match("SH ") ||
                stream.match("SS ") || stream.match("HP ")
            ) {
                stream.skipToEnd()
                return "quote"
            }
            if ((stream.match(Regex("^[A-Z]")) != null && stream.match(Regex("^[A-Z]")) != null) ||
                (stream.match(Regex("^[a-z]")) != null && stream.match(Regex("^[a-z]")) != null)
            ) {
                return "attribute"
            }
        }
        stream.eatWhile(Regex("""[\w-]"""))
        return null
    }

    override fun token(stream: StringStream, state: TroffState): String? {
        val tokenizer = if (state.tokens.isNotEmpty()) state.tokens[0] else ::tokenBase
        return tokenizer(stream, state)
    }
}
