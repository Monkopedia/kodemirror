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

private val mmaIdentifier = "[a-zA-Z\\$][a-zA-Z0-9\\$]*"
private val mmaBase = "(?:\\d+)"
private val mmaFloat = "(?:\\.\\d+|\\d+\\.\\d*|\\d+)"
private val mmaFloatBase = "(?:\\.\\w+|\\w+\\.\\w*|\\w+)"
private val mmaPrecision = "(?:`(?:`?$mmaFloat)?)"

private val mmaBaseForm = Regex(
    "^(?:$mmaBase(?:\\^\\^$mmaFloatBase$mmaPrecision?(?:\\*\\^[+-]?\\d+)?))"
)
private val mmaFloatForm = Regex(
    "^(?:$mmaFloat$mmaPrecision?(?:\\*\\^[+-]?\\d+)?)"
)
private val mmaIdInContext = Regex(
    "^(?:`?)(?:$mmaIdentifier)(?:`(?:$mmaIdentifier))*(?:`?)"
)

data class MathematicaState(
    var tokenize: (StringStream, MathematicaState) -> String? = ::mmaTokenBase,
    var commentLevel: Int = 0
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun mmaTokenBase(stream: StringStream, state: MathematicaState): String? {
    val ch = stream.next() ?: return null

    if (ch == "\"") {
        state.tokenize = ::mmaTokenString
        return state.tokenize(stream, state)
    }

    if (ch == "(") {
        if (stream.eat("*") != null) {
            state.commentLevel++
            state.tokenize = ::mmaTokenComment
            return state.tokenize(stream, state)
        }
    }

    stream.backUp(1)

    if (stream.match(mmaBaseForm) != null) return "number"
    if (stream.match(mmaFloatForm) != null) return "number"

    if (stream.match(Regex("^(?:In|Out)\\[[0-9]*\\]")) != null) return "atom"

    if (stream.match(
            Regex("^([a-zA-Z\\$][a-zA-Z0-9\\$]*(?:`[a-zA-Z0-9\\$]+)*::usage)")
        ) != null
    ) {
        return "meta"
    }

    if (stream.match(
            Regex(
                "^([a-zA-Z\\$][a-zA-Z0-9\\$]*(?:`[a-zA-Z0-9\\$]+)*::" +
                    "[a-zA-Z\\$][a-zA-Z0-9\\$]*):?"
            )
        ) != null
    ) {
        return "string special"
    }

    if (stream.match(
            Regex(
                "^([a-zA-Z\\$][a-zA-Z0-9\\$]*\\s*:)" +
                    "(?:(?:[a-zA-Z\\$][a-zA-Z0-9\\$]*)|(?:[^:=>~@\\^&*\\)\\[\\]'?,|])).*"
            )
        ) != null
    ) {
        return "variableName special"
    }

    if (stream.match(
            Regex("^[a-zA-Z\\$][a-zA-Z0-9\\$]*_+[a-zA-Z\\$][a-zA-Z0-9\\$]*")
        ) != null
    ) {
        return "variableName special"
    }
    if (stream.match(Regex("^[a-zA-Z\\$][a-zA-Z0-9\\$]*_+")) != null) {
        return "variableName special"
    }
    if (stream.match(Regex("^_+[a-zA-Z\\$][a-zA-Z0-9\\$]*")) != null) {
        return "variableName special"
    }

    if (stream.match(Regex("^\\\\\\[[a-zA-Z\\$][a-zA-Z0-9\\$]*\\]")) != null) {
        return "character"
    }

    if (stream.match(Regex("^(?:\\[|\\]|\\{|\\}|\\(|\\))")) != null) {
        return "bracket"
    }

    if (stream.match(Regex("^(?:#[a-zA-Z\\$][a-zA-Z0-9\\$]*|#+[0-9]?)")) != null) {
        return "variableName constant"
    }

    if (stream.match(mmaIdInContext) != null) return "keyword"

    if (stream.match(
            Regex("^(?:\\\\|\\+|-|\\*|/|,|;|\\.|:|@|~|=|>|<|&|\\||_|`|'|\\^|\\?|!|%)")
        ) != null
    ) {
        return "operator"
    }

    stream.next()
    return "error"
}

private fun mmaTokenString(stream: StringStream, state: MathematicaState): String {
    var escaped = false
    while (true) {
        val next = stream.next() ?: break
        if (next == "\"" && !escaped) {
            state.tokenize = ::mmaTokenBase
            break
        }
        escaped = !escaped && next == "\\"
    }
    return "string"
}

private fun mmaTokenComment(stream: StringStream, state: MathematicaState): String {
    var prev: String? = null
    while (state.commentLevel > 0) {
        val next = stream.next() ?: break
        if (prev == "(" && next == "*") state.commentLevel++
        if (prev == "*" && next == ")") state.commentLevel--
        prev = next
    }
    if (state.commentLevel <= 0) {
        state.tokenize = ::mmaTokenBase
    }
    return "comment"
}

/** Stream parser for Mathematica. */
val mathematica: StreamParser<MathematicaState> = object : StreamParser<MathematicaState> {
    override val name: String get() = "mathematica"

    override fun startState(indentUnit: Int) = MathematicaState()
    override fun copyState(state: MathematicaState) = state.copy()

    override fun token(stream: StringStream, state: MathematicaState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "block" to mapOf("open" to "(*", "close" to "*)")
            )
        )
}
