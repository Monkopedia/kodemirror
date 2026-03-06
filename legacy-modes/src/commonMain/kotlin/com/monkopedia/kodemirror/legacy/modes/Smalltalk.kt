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

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

private val stSpecialChars = Regex("[+\\-/\\\\*~<>=@%|&?!.,:;^]")
private val stKeywords = Regex("^(true|false|nil|self|super|thisContext)$")

class StContext(
    val next: (StringStream, StContext, SmalltalkState) -> StToken,
    val parent: StContext?
)

data class StToken(
    val name: String?,
    val context: StContext,
    val eos: Boolean
)

data class SmalltalkState(
    var context: Any? = null,
    var expectVariable: Boolean = true,
    var indentation: Int = 0,
    var userIndentationDelta: Int = 0
) {
    internal var stContext: StContext
        get() = context as StContext
        set(value) {
            context = value
        }

    fun userIndent(indentation: Int, indentUnit: Int) {
        userIndentationDelta = if (indentation > 0) {
            indentation / indentUnit - this.indentation
        } else {
            0
        }
    }
}

private fun stNext(stream: StringStream, context: StContext, state: SmalltalkState): StToken {
    var token = StToken(null, context, false)
    val aChar = stream.next() ?: return token

    if (aChar == "\"") {
        token = stNextComment(stream, StContext(::stNextComment, context), state)
    } else if (aChar == "'") {
        token = stNextString(stream, StContext(::stNextString, context), state)
    } else if (aChar == "#") {
        if (stream.peek() == "'") {
            stream.next()
            token = stNextSymbol(stream, StContext(::stNextSymbol, context), state)
        } else {
            if (stream.eatWhile(Regex("[^\\s.{}\\[\\]()]")) != null) {
                token = StToken("string.special", context, false)
            } else {
                token = StToken("meta", context, false)
            }
        }
    } else if (aChar == "$") {
        if (stream.next() == "<") {
            stream.eatWhile(Regex("[^\\s>]"))
            stream.next()
        }
        token = StToken("string.special", context, false)
    } else if (aChar == "|" && state.expectVariable) {
        token = StToken(null, StContext(::stNextTemporaries, context), false)
    } else if (Regex("[\\[\\]{}()]").containsMatchIn(aChar)) {
        token = StToken(
            "bracket",
            context,
            Regex("[\\[{(]").containsMatchIn(aChar)
        )
        if (aChar == "[") {
            state.indentation++
        } else if (aChar == "]") {
            state.indentation = maxOf(0, state.indentation - 1)
        }
    } else if (stSpecialChars.containsMatchIn(aChar)) {
        stream.eatWhile(stSpecialChars)
        token = StToken("operator", context, aChar != ";")
    } else if (Regex("\\d").containsMatchIn(aChar)) {
        stream.eatWhile(Regex("[\\w\\d]"))
        token = StToken("number", context, false)
    } else if (Regex("[\\w_]").containsMatchIn(aChar)) {
        stream.eatWhile(Regex("[\\w\\d_]"))
        token = StToken(
            if (state.expectVariable) {
                if (stKeywords.containsMatchIn(stream.current())) {
                    "keyword"
                } else {
                    "variable"
                }
            } else {
                null
            },
            context,
            false
        )
    } else {
        token = StToken(null, context, state.expectVariable)
    }

    return token
}

private fun stNextComment(
    stream: StringStream,
    context: StContext,
    @Suppress("UNUSED_PARAMETER") state: SmalltalkState
): StToken {
    stream.eatWhile(Regex("[^\"]"))
    return StToken(
        "comment",
        if (stream.eat("\"") != null) context.parent!! else context,
        true
    )
}

private fun stNextString(
    stream: StringStream,
    context: StContext,
    @Suppress("UNUSED_PARAMETER") state: SmalltalkState
): StToken {
    stream.eatWhile(Regex("[^']"))
    return StToken(
        "string",
        if (stream.eat("'") != null) context.parent!! else context,
        false
    )
}

private fun stNextSymbol(
    stream: StringStream,
    context: StContext,
    @Suppress("UNUSED_PARAMETER") state: SmalltalkState
): StToken {
    stream.eatWhile(Regex("[^']"))
    return StToken(
        "string.special",
        if (stream.eat("'") != null) context.parent!! else context,
        false
    )
}

private fun stNextTemporaries(
    stream: StringStream,
    context: StContext,
    @Suppress("UNUSED_PARAMETER") state: SmalltalkState
): StToken {
    val aChar = stream.next()
    if (aChar == "|") {
        return StToken(null, context.parent!!, true)
    }
    stream.eatWhile(Regex("[^|]"))
    return StToken("variable", context, false)
}

/** Stream parser for Smalltalk. */
val smalltalk: StreamParser<SmalltalkState> = object : StreamParser<SmalltalkState> {
    override val name: String get() = "smalltalk"

    override val languageData: Map<String, Any>
        get() = mapOf("indentOnInput" to Regex("^\\s*\\]$"))

    override fun startState(indentUnit: Int): SmalltalkState {
        val state = SmalltalkState()
        state.stContext = StContext(::stNext, null)
        return state
    }

    override fun copyState(state: SmalltalkState): SmalltalkState {
        // StContext objects are immutable linked lists, safe to share
        return state.copy()
    }

    override fun token(stream: StringStream, state: SmalltalkState): String? {
        state.userIndent(stream.indentation(), stream.indentUnit)

        if (stream.eatSpace()) return null

        val token = state.stContext.next(stream, state.stContext, state)
        state.stContext = token.context
        state.expectVariable = token.eos

        return token.name
    }

    override fun blankLine(state: SmalltalkState, indentUnit: Int) {
        state.userIndent(0, indentUnit)
    }

    override fun indent(state: SmalltalkState, textAfter: String, context: IndentContext): Int {
        val i = if (
            state.stContext.next == ::stNext &&
            textAfter.isNotEmpty() && textAfter[0] == ']'
        ) {
            -1
        } else {
            state.userIndentationDelta
        }
        return (state.indentation + i) * context.unit
    }
}
