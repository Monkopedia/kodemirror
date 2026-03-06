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

private const val EBNF_COMMENT_SLASH = 0
private const val EBNF_COMMENT_PARENTHESIS = 1
private const val EBNF_STATE_COMMENT = 0
private const val EBNF_STATE_STRING = 1
private const val EBNF_STATE_CHARACTER_CLASS = 2

data class EbnfState(
    var stringType: String? = null,
    var commentType: Int? = null,
    var braced: Int = 0,
    var lhs: Boolean = true,
    var localState: Any? = null,
    var stack: MutableList<Int> = mutableListOf(),
    var inDefinition: Boolean = false
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun ebnfTokenize(stream: StringStream, state: EbnfState): String? {
    if (state.stack.isEmpty()) {
        val peek = stream.peek()
        if (peek == "\"" || peek == "'") {
            state.stringType = peek
            stream.next()
            state.stack.add(0, EBNF_STATE_STRING)
        } else if (stream.match("/*")) {
            state.stack.add(0, EBNF_STATE_COMMENT)
            state.commentType = EBNF_COMMENT_SLASH
        } else if (stream.match("(*")) {
            state.stack.add(0, EBNF_STATE_COMMENT)
            state.commentType = EBNF_COMMENT_PARENTHESIS
        }
    }

    return when (state.stack.firstOrNull()) {
        EBNF_STATE_STRING -> {
            while (state.stack.firstOrNull() == EBNF_STATE_STRING && !stream.eol()) {
                if (stream.peek() == state.stringType) {
                    stream.next()
                    state.stack.removeFirst()
                } else if (stream.peek() == "\\") {
                    stream.next()
                    stream.next()
                } else {
                    stream.match(Regex("^.[^\\\\\"\\']*"))
                }
            }
            if (state.lhs) "property" else "string"
        }
        EBNF_STATE_COMMENT -> {
            while (state.stack.firstOrNull() == EBNF_STATE_COMMENT && !stream.eol()) {
                if (state.commentType == EBNF_COMMENT_SLASH && stream.match("*/")) {
                    state.stack.removeFirst()
                    state.commentType = null
                } else if (state.commentType == EBNF_COMMENT_PARENTHESIS &&
                    stream.match("*)")
                ) {
                    state.stack.removeFirst()
                    state.commentType = null
                } else {
                    stream.match(Regex("^.[^*]*"))
                }
            }
            "comment"
        }
        EBNF_STATE_CHARACTER_CLASS -> {
            while (state.stack.firstOrNull() == EBNF_STATE_CHARACTER_CLASS && !stream.eol()) {
                if (stream.match(Regex("^[^\\]\\\\]+")) == null && stream.next() == null) {
                    state.stack.removeFirst()
                }
            }
            "operator"
        }
        else -> {
            // No stack - base tokenizing
            val peek = stream.peek()
            when {
                peek == "[" -> {
                    stream.next()
                    state.stack.add(0, EBNF_STATE_CHARACTER_CLASS)
                    "bracket"
                }
                peek == ":" || peek == "|" || peek == ";" -> {
                    stream.next()
                    "operator"
                }
                peek == "%" -> {
                    when {
                        stream.match("%%") -> "header"
                        stream.match(Regex("^%[A-Za-z]+")) != null -> "keyword"
                        stream.match(Regex("^%}")) != null -> "bracket"
                        else -> {
                            stream.next()
                            null
                        }
                    }
                }
                peek == "/" -> {
                    when {
                        stream.match(Regex("^/[A-Za-z]+")) != null -> "keyword"
                        peek == "\\" -> {
                            if (stream.match(Regex("^\\\\[a-z]+")) != null) {
                                "string.special"
                            } else {
                                stream.next()
                                null
                            }
                        }
                        else -> {
                            stream.next()
                            null
                        }
                    }
                }
                peek == "\\" -> {
                    if (stream.match(
                            Regex(
                                "^\\\\[a-z]+"
                            )
                        ) != null
                    ) {
                        "string.special"
                    } else {
                        stream.next()
                        null
                    }
                }
                peek == "." -> {
                    if (stream.match(".")) {
                        "atom"
                    } else {
                        stream.next()
                        null
                    }
                }
                peek == "*" || peek == "-" || peek == "+" || peek == "^" -> {
                    if (stream.match(peek!!)) {
                        "atom"
                    } else {
                        stream.next()
                        null
                    }
                }
                peek == "$" -> {
                    when {
                        stream.match("$$") -> "builtin"
                        stream.match(Regex("^\\$[0-9]+")) != null -> "variableName.special"
                        else -> {
                            stream.next()
                            null
                        }
                    }
                }
                peek == "<" -> {
                    if (stream.match(Regex("^<<[a-zA-Z_]+>>")) != null) {
                        "builtin"
                    } else {
                        stream.next()
                        null
                    }
                }
                stream.match("//") -> {
                    stream.skipToEnd()
                    "comment"
                }
                stream.match("return") -> "operator"
                stream.match(Regex("^[a-zA-Z_][a-zA-Z0-9_]*")) != null -> {
                    when {
                        stream.match(Regex("(?=[(.])"), false) != null -> "variable"
                        stream.match(Regex("(?=[\\s\\n]*[:=])"), false) != null -> "def"
                        else -> "variableName.special"
                    }
                }
                peek == "[" || peek == "]" || peek == "(" || peek == ")" -> {
                    stream.next()
                    "bracket"
                }
                !stream.eatSpace() -> {
                    stream.next()
                    null
                }
                else -> null
            }
        }
    }
}

/** Stream parser for EBNF. */
val ebnf: StreamParser<EbnfState> = object : StreamParser<EbnfState> {
    override val name: String get() = "ebnf"

    override fun startState(indentUnit: Int) = EbnfState()

    override fun copyState(state: EbnfState) = EbnfState(
        stringType = state.stringType,
        commentType = state.commentType,
        braced = state.braced,
        lhs = state.lhs,
        localState = state.localState,
        stack = state.stack.toMutableList(),
        inDefinition = state.inDefinition
    )

    override fun token(stream: StringStream, state: EbnfState): String? {
        return ebnfTokenize(stream, state)
    }
}
