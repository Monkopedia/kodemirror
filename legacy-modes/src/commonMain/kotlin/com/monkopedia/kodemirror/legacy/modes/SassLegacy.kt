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

private val sassPropertyKeywords = CssKeywords.properties.toSet()
private val sassColorKeywords = CssKeywords.colors.toSet()
private val sassValueKeywords = CssKeywords.values.toSet()
private val sassFontProperties = CssKeywords.fonts.toSet()

private val sassKeywordsRegexp = Regex("^(true|false|null|auto)")
private val sassOpRegexp = Regex(
    "^(\\(|\\)|=|>|<|==|>=|<=|\\+|-|!=|/|\\*|%|and|or|not|;|\\{|\\}|:)"
)
private val sassPseudoElementsRegexp = Regex("^::?[a-zA-Z_][\\w-]*")

private data class SassScope(val offset: Int, val type: String = "sass")

class SassState(
    var tokenize: Int = 0, // 0=base, 1=comment-multiline, 2=comment-single, 3=string
    var stringQuote: String = "",
    var commentIndent: Int = 0,
    var commentMultiLine: Boolean = false,
    var scopes: MutableList<SassScope> = mutableListOf(SassScope(0)),
    var indentCount: Int = 0,
    var cursorHalf: Int = 0,
    var prevProp: String = "",
    var lastToken: String? = null,
    var lastContent: String = ""
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun sassIsEndLine(stream: StringStream): Boolean {
    return stream.peek() == null || stream.match(Regex("\\s+\$"), false) != null
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun sassTokenBase(stream: StringStream, state: SassState): String? {
    val ch = stream.peek() ?: run {
        stream.next()
        return null
    }

    // Comment
    if (stream.match("/*")) {
        state.tokenize = 1
        state.commentIndent = stream.indentation()
        state.commentMultiLine = true
        return sassTokenComment(stream, state)
    }
    if (stream.match("//")) {
        state.tokenize = 2
        state.commentIndent = stream.indentation()
        state.commentMultiLine = false
        return sassTokenComment(stream, state)
    }

    // Interpolation
    if (stream.match("#{")) {
        state.tokenize = 4 // interpolation base
        return "operator"
    }

    // Strings
    if (ch == "\"" || ch == "'") {
        stream.next()
        state.stringQuote = ch
        state.tokenize = 3
        return sassTokenString(stream, state)
    }

    if (state.cursorHalf == 0) {
        // first half: before : for key-value pairs, including selectors
        if (ch == "-") {
            if (stream.match(Regex("^-\\w+-"))) return "meta"
        }

        if (ch == ".") {
            stream.next()
            if (stream.match(Regex("^[\\w-]+"))) {
                sassIndent(state, stream)
                return "qualifier"
            } else if (stream.peek() == "#") {
                sassIndent(state, stream)
                return "tag"
            }
        }

        if (ch == "#") {
            stream.next()
            if (stream.match(Regex("^[\\w-]+"))) {
                sassIndent(state, stream)
                return "builtin"
            }
            if (stream.peek() == "#") {
                sassIndent(state, stream)
                return "tag"
            }
        }

        // Variables
        if (ch == "$") {
            stream.next()
            stream.eatWhile(Regex("[\\w-]"))
            return "variable-2"
        }

        // Numbers
        if (stream.match(Regex("^-?[0-9.]+")) != null) return "number"

        // Units
        if (stream.match(Regex("^(px|em|in)\\b")) != null) return "unit"

        if (stream.match(sassKeywordsRegexp) != null) return "keyword"

        if (stream.match(Regex("^url")) != null && stream.peek() == "(") {
            state.tokenize = 5 // url tokens
            return "atom"
        }

        if (ch == "=") {
            if (stream.match(Regex("^=[\\w-]+")) != null) {
                sassIndent(state, stream)
                return "meta"
            }
        }

        if (ch == "+") {
            if (stream.match(Regex("^\\+[\\w-]+")) != null) return "meta"
        }

        if (ch == "@") {
            if (stream.match("@extend")) {
                if (stream.match(Regex("^\\s*[\\w]")) == null) {
                    sassDedent(state)
                }
            }
        }

        // Indent Directives
        if (stream.match(
                Regex("^@(else if|if|media|else|for|each|while|mixin|function)")
            ) != null
        ) {
            sassIndent(state, stream)
            return "def"
        }

        // Other Directives
        if (ch == "@") {
            stream.next()
            stream.eatWhile(Regex("[\\w-]"))
            return "def"
        }

        if (stream.eatWhile(Regex("[\\w-]"))) {
            if (stream.match(Regex(" *: *[\\w-+\$#!\"'(]"), false) != null) {
                val word = stream.current().lowercase()
                val prop = state.prevProp + "-" + word
                if (sassPropertyKeywords.contains(prop)) {
                    return "property"
                } else if (sassPropertyKeywords.contains(word)) {
                    state.prevProp = word
                    return "property"
                } else if (sassFontProperties.contains(word)) {
                    return "property"
                }
                return "tag"
            } else if (stream.match(Regex(" *:"), false) != null) {
                sassIndent(state, stream)
                state.cursorHalf = 1
                state.prevProp = stream.current().lowercase()
                return "property"
            } else if (stream.match(Regex(" *,"), false) != null) {
                return "tag"
            } else {
                sassIndent(state, stream)
                return "tag"
            }
        }

        if (ch == ":") {
            if (stream.match(sassPseudoElementsRegexp) != null) {
                return "type"
            }
            stream.next()
            state.cursorHalf = 1
            return "operator"
        }
    } else {
        // second half: after colon
        if (ch == "#") {
            stream.next()
            if (stream.match(Regex("^[0-9a-fA-F]{6}|[0-9a-fA-F]{3}")) != null) {
                if (sassIsEndLine(stream)) state.cursorHalf = 0
                return "number"
            }
        }

        if (stream.match(Regex("^-?[0-9.]+")) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "number"
        }

        if (stream.match(Regex("^(px|em|in)\\b")) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "unit"
        }

        if (stream.match(sassKeywordsRegexp) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "keyword"
        }

        if (stream.match(Regex("^url")) != null && stream.peek() == "(") {
            state.tokenize = 5
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "atom"
        }

        if (ch == "$") {
            stream.next()
            stream.eatWhile(Regex("[\\w-]"))
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "variable-2"
        }

        if (ch == "!") {
            stream.next()
            state.cursorHalf = 0
            return if (stream.match(Regex("^[\\w]+")) != null) "keyword" else "operator"
        }

        if (stream.match(sassOpRegexp) != null) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            return "operator"
        }

        if (stream.eatWhile(Regex("[\\w-]"))) {
            if (sassIsEndLine(stream)) state.cursorHalf = 0
            val word = stream.current().lowercase()
            return when {
                sassValueKeywords.contains(word) -> "atom"
                sassColorKeywords.contains(word) -> "keyword"
                sassPropertyKeywords.contains(word) -> {
                    state.prevProp = stream.current().lowercase()
                    "property"
                }
                else -> "tag"
            }
        }

        if (sassIsEndLine(stream)) {
            state.cursorHalf = 0
            return null
        }
    }

    if (stream.match(sassOpRegexp) != null) return "operator"

    stream.next()
    return null
}

private fun sassTokenComment(stream: StringStream, state: SassState): String? {
    if (stream.sol() && stream.indentation() <= state.commentIndent) {
        state.tokenize = 0
        return sassTokenBase(stream, state)
    }

    if (state.commentMultiLine && stream.skipTo("*/")) {
        stream.next()
        stream.next()
        state.tokenize = 0
    } else {
        stream.skipToEnd()
    }
    return "comment"
}

private fun sassTokenString(stream: StringStream, state: SassState): String? {
    val nextChar = stream.next() ?: return "string"
    val peekChar = stream.peek()
    val previousChar = if (stream.pos >= 2) {
        stream.string[stream.pos - 2].toString()
    } else {
        ""
    }

    val endingString = (nextChar != "\\" && peekChar == state.stringQuote) ||
        (nextChar == state.stringQuote && previousChar != "\\")

    return if (endingString) {
        if (nextChar != state.stringQuote) stream.next()
        if (sassIsEndLine(stream)) state.cursorHalf = 0
        state.tokenize = 0
        "string"
    } else if (nextChar == "#" && peekChar == "{") {
        state.tokenize = 6 // interpolation in string
        stream.next()
        "operator"
    } else {
        "string"
    }
}

private fun sassIndent(state: SassState, stream: StringStream) {
    if (state.indentCount == 0) {
        state.indentCount++
        val lastScopeOffset = state.scopes[0].offset
        val currentOffset = lastScopeOffset + stream.indentUnit
        state.scopes.add(0, SassScope(currentOffset))
    }
}

private fun sassDedent(state: SassState) {
    if (state.scopes.size == 1) return
    state.scopes.removeAt(0)
}

val sassLegacy: StreamParser<SassState> = object : StreamParser<SassState> {
    override val name: String get() = "sass"

    override fun startState(indentUnit: Int): SassState {
        return SassState()
    }

    override fun copyState(state: SassState): SassState {
        return SassState(
            tokenize = state.tokenize,
            stringQuote = state.stringQuote,
            commentIndent = state.commentIndent,
            commentMultiLine = state.commentMultiLine,
            scopes = state.scopes.toMutableList(),
            indentCount = state.indentCount,
            cursorHalf = state.cursorHalf,
            prevProp = state.prevProp,
            lastToken = state.lastToken,
            lastContent = state.lastContent
        )
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun token(stream: StringStream, state: SassState): String? {
        if (stream.sol()) state.indentCount = 0

        val style: String? = when (state.tokenize) {
            1, 2 -> sassTokenComment(stream, state)
            3 -> sassTokenString(stream, state)
            4 -> {
                // interpolation base
                if (stream.peek() == "}") {
                    stream.next()
                    state.tokenize = 0
                    "operator"
                } else {
                    sassTokenBase(stream, state)
                }
            }
            5 -> {
                // url tokens
                val ch = stream.peek()
                if (ch == ")") {
                    stream.next()
                    state.tokenize = 0
                    "operator"
                } else if (ch == "(") {
                    stream.next()
                    stream.eatSpace()
                    "operator"
                } else if (ch == "'" || ch == "\"") {
                    state.stringQuote = stream.next()!!
                    state.tokenize = 3
                    "string"
                } else {
                    // read as unquoted string until )
                    var escaped = false
                    while (true) {
                        val c = stream.next() ?: break
                        if (c == ")" && !escaped) {
                            stream.backUp(1)
                            break
                        }
                        escaped = !escaped && c == "\\"
                    }
                    "string"
                }
            }
            6 -> {
                // interpolation in string
                if (stream.peek() == "}") {
                    stream.next()
                    state.tokenize = 3 // back to string
                    "operator"
                } else {
                    sassTokenBase(stream, state)
                }
            }
            else -> sassTokenBase(stream, state)
        }

        val current = stream.current()

        if (current == "@return" || current == "}") {
            sassDedent(state)
        }

        if (style != null) {
            val startOfToken = stream.pos - current.length
            val withCurrentIndent = startOfToken + (stream.indentUnit * state.indentCount)

            state.scopes = state.scopes.filter { scope ->
                scope.offset <= withCurrentIndent
            }.toMutableList()
            if (state.scopes.isEmpty()) {
                state.scopes.add(SassScope(0))
            }
        }

        state.lastToken = style
        state.lastContent = current

        return style
    }

    override fun indent(state: SassState, textAfter: String, context: IndentContext): Int? {
        return state.scopes[0].offset
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
