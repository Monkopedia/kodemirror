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

class CrystalState(
    var tokenize: MutableList<(StringStream, CrystalState) -> String?> =
        mutableListOf(::crystalTokenBase),
    var currentIndent: Int = 0,
    var lastToken: String? = null,
    var lastStyle: String? = null,
    var blocks: MutableList<String> = mutableListOf()
)

private fun wordRegExp(words: List<String>, end: Boolean = false): Regex {
    val pattern = (if (end) "" else "^") +
        "(?:" + words.joinToString("|") + ")" +
        (if (end) "$" else "\\b")
    return Regex(pattern)
}

private val crystalOperators = Regex("^(?:[-+/%|&^]|\\*\\*?|[<>]{2})")
private val crystalConditionalOperators = Regex("^(?:[=!]~|===|<=>|[<>=!]=?|[|&]{2}|~)")
private val crystalIndexingOperators = Regex("^(?:\\[\\][?=]?)")
private val crystalAnotherOperators = Regex("^(?:\\.(?:\\.{2})?|->|[?:])")
private val crystalIdents = Regex("^[a-z_\\u009F-\\uFFFF][a-zA-Z0-9_\\u009F-\\uFFFF]*")
private val crystalTypes = Regex("^[A-Z_\\u009F-\\uFFFF][a-zA-Z0-9_\\u009F-\\uFFFF]*")

private val crystalKeywords = wordRegExp(
    listOf(
        "abstract", "alias", "as", "asm", "begin", "break", "case", "class",
        "def", "do", "else", "elsif", "end", "ensure", "enum", "extend", "for",
        "fun", "if", "include", "instance_sizeof", "lib", "macro", "module",
        "next", "of", "out", "pointerof", "private", "protected", "rescue",
        "return", "require", "select", "sizeof", "struct", "super", "then",
        "type", "typeof", "uninitialized", "union", "unless", "until", "when",
        "while", "with", "yield", "__DIR__", "__END_LINE__", "__FILE__", "__LINE__"
    )
)
private val crystalAtomWords = wordRegExp(listOf("true", "false", "nil", "self"))
private val indentKeywordsArray = listOf(
    "def", "fun", "macro", "class", "module", "struct", "lib", "enum",
    "union", "do", "for"
)
private val indentKeywords = wordRegExp(indentKeywordsArray)
private val indentExpressionKeywordsArray =
    listOf("if", "unless", "case", "while", "until", "begin", "then")
private val indentExpressionKeywords = wordRegExp(indentExpressionKeywordsArray)
private val dedentKeywordsArray = listOf("end", "else", "elsif", "rescue", "ensure")
private val dedentKeywords = wordRegExp(dedentKeywordsArray)
private val dedentPunctualsArray = listOf("\\)", "\\}", "\\]")
private val dedentPunctuals =
    Regex("^(?:" + dedentPunctualsArray.joinToString("|") + ")$")
private val crystalMatching = mapOf("[" to "]", "{" to "}", "(" to ")", "<" to ">")

private fun crystalChain(
    tokenize: (StringStream, CrystalState) -> String?,
    stream: StringStream,
    state: CrystalState
): String? {
    state.tokenize.add(tokenize)
    return tokenize(stream, state)
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun crystalTokenBase(stream: StringStream, state: CrystalState): String? {
    if (stream.eatSpace()) return null

    // Macros
    if (state.lastToken != "\\" && stream.match("{%", false)) {
        return crystalChain(crystalTokenMacro("%", "%"), stream, state)
    }
    if (state.lastToken != "\\" && stream.match("{{", false)) {
        return crystalChain(crystalTokenMacro("{", "}"), stream, state)
    }

    // Comments
    if (stream.peek() == "#") {
        stream.skipToEnd()
        return "comment"
    }

    // Variables and keywords
    if (stream.match(crystalIdents) != null) {
        stream.eat(Regex("[?!]"))
        val matched = stream.current()
        if (stream.eat(":") != null) {
            return "atom"
        } else if (state.lastToken == ".") {
            return "property"
        } else if (crystalKeywords.containsMatchIn(matched)) {
            if (indentKeywords.containsMatchIn(matched)) {
                if (!(matched == "fun" && state.blocks.indexOf("lib") >= 0) &&
                    !(matched == "def" && state.lastToken == "abstract")
                ) {
                    state.blocks.add(matched)
                    state.currentIndent += 1
                }
            } else if ((state.lastStyle == "operator" || state.lastStyle == null) &&
                indentExpressionKeywords.containsMatchIn(matched)
            ) {
                state.blocks.add(matched)
                state.currentIndent += 1
            } else if (matched == "end") {
                if (state.blocks.isNotEmpty()) state.blocks.removeAt(state.blocks.lastIndex)
                state.currentIndent -= 1
            }
            val nextTok = crystalNextTokenizer[matched]
            if (nextTok != null) state.tokenize.add(nextTok)
            return "keyword"
        } else if (crystalAtomWords.containsMatchIn(matched)) {
            return "atom"
        }
        return "variable"
    }

    // Class variables, instance variables, or attributes
    if (stream.eat("@") != null) {
        if (stream.peek() == "[") {
            return crystalChain(crystalTokenNest("[", "]", "meta"), stream, state)
        }
        stream.eat("@")
        stream.match(crystalIdents) != null || stream.match(crystalTypes) != null
        return "propertyName"
    }

    // Constants and types
    if (stream.match(crystalTypes) != null) return "tag"

    // Symbols or ':' operator
    if (stream.eat(":") != null) {
        if (stream.eat("\"") != null) {
            return crystalChain(
                crystalTokenQuote("\"", "atom", false),
                stream,
                state
            )
        } else if (stream.match(crystalIdents) != null ||
            stream.match(crystalTypes) != null ||
            stream.match(crystalOperators) != null ||
            stream.match(crystalConditionalOperators) != null ||
            stream.match(crystalIndexingOperators) != null
        ) {
            return "atom"
        }
        stream.eat(":")
        return "operator"
    }

    // Strings
    if (stream.eat("\"") != null) {
        return crystalChain(
            crystalTokenQuote("\"", "string", true),
            stream,
            state
        )
    }

    // Strings, regexps, macro variables, or '%' operator
    if (stream.peek() == "%") {
        var style = "string"
        var embed = true
        var delim: String?

        if (stream.match("%r") != null) {
            style = "string.special"
            delim = stream.next()
        } else if (stream.match("%w") != null) {
            embed = false
            delim = stream.next()
        } else if (stream.match("%q") != null) {
            embed = false
            delim = stream.next()
        } else {
            val m = stream.match(Regex("^%([^\\w\\s=])"))
            if (m != null) {
                delim = m.groupValues[1]
            } else if (stream.match(
                    Regex("^%[a-zA-Z_\\u009F-\\uFFFF][\\w\\u009F-\\uFFFF]*")
                ) != null
            ) {
                return "meta"
            } else if (stream.eat("%") != null) {
                return "operator"
            } else {
                delim = null
            }
        }

        val resolvedDelim = if (delim != null && crystalMatching.containsKey(delim)) {
            crystalMatching[delim]!!
        } else {
            delim
        }
        if (resolvedDelim != null) {
            return crystalChain(
                crystalTokenQuote(resolvedDelim, style, embed),
                stream,
                state
            )
        }
    }

    // Here Docs
    val hereDocMatch = stream.match(Regex("^<<-('?)([A-Z]\\w*)\\1"))
    if (hereDocMatch != null) {
        return crystalChain(
            crystalTokenHereDoc(hereDocMatch.groupValues[2], hereDocMatch.groupValues[1].isEmpty()),
            stream,
            state
        )
    }

    // Characters
    if (stream.eat("'") != null) {
        stream.match(
            Regex(
                "^(?:[^']|\\\\(?:[befnrtv0'\"']|[0-7]{3}" +
                    "|u(?:[0-9a-fA-F]{4}|\\{[0-9a-fA-F]{1,6}\\})))"
            )
        )
        stream.eat("'")
        return "atom"
    }

    // Numbers
    if (stream.eat("0") != null) {
        if (stream.eat("x") != null) {
            stream.match(Regex("^[0-9a-fA-F_]+"))
        } else if (stream.eat("o") != null) {
            stream.match(Regex("^[0-7_]+"))
        } else if (stream.eat("b") != null) {
            stream.match(Regex("^[01_]+"))
        }
        return "number"
    }
    if (stream.eat(Regex("^\\d")) != null) {
        stream.match(Regex("^[\\d_]*(?:\\.[\\d_]+)?(?:[eE][+-]?\\d+)?"))
        return "number"
    }

    // Operators
    if (stream.match(crystalOperators) != null) {
        stream.eat("=")
        return "operator"
    }
    if (stream.match(crystalConditionalOperators) != null ||
        stream.match(crystalAnotherOperators) != null
    ) {
        return "operator"
    }

    // Parens and braces
    val parenMatch = stream.match(Regex("[({\\[]"), false)
    if (parenMatch != null) {
        val matched = parenMatch.value
        return crystalChain(
            crystalTokenNest(matched, crystalMatching[matched]!!, null),
            stream,
            state
        )
    }

    // Escapes
    if (stream.eat("\\") != null) {
        stream.next()
        return "meta"
    }

    stream.next()
    return null
}

private fun crystalTokenNest(
    begin: String,
    end: String,
    style: String?,
    started: Boolean = false
): (StringStream, CrystalState) -> String? {
    return fun(stream: StringStream, state: CrystalState): String? {
        if (!started && stream.match(begin)) {
            state.tokenize[state.tokenize.lastIndex] =
                crystalTokenNest(begin, end, style, true)
            state.currentIndent += 1
            return style
        }
        val nextStyle = crystalTokenBase(stream, state)
        if (stream.current() == end) {
            state.tokenize.removeAt(state.tokenize.lastIndex)
            state.currentIndent -= 1
            return style
        }
        return nextStyle
    }
}

private fun crystalTokenMacro(
    begin: String,
    end: String,
    started: Boolean = false
): (StringStream, CrystalState) -> String? {
    return fun(stream: StringStream, state: CrystalState): String? {
        if (!started && stream.match("{$begin")) {
            state.currentIndent += 1
            state.tokenize[state.tokenize.lastIndex] =
                crystalTokenMacro(begin, end, true)
            return "meta"
        }
        if (stream.match("$end}")) {
            state.currentIndent -= 1
            state.tokenize.removeAt(state.tokenize.lastIndex)
            return "meta"
        }
        return crystalTokenBase(stream, state)
    }
}

private fun crystalTokenMacroDef(stream: StringStream, state: CrystalState): String? {
    if (stream.eatSpace()) return null
    val matched = stream.match(crystalIdents)
    if (matched != null) {
        if (matched.value == "def") return "keyword"
        stream.eat(Regex("[?!]"))
    }
    state.tokenize.removeAt(state.tokenize.lastIndex)
    return "def"
}

private fun crystalTokenFollowIdent(stream: StringStream, state: CrystalState): String? {
    if (stream.eatSpace()) return null
    if (stream.match(crystalIdents) != null) {
        stream.eat(Regex("[!?]"))
    } else {
        stream.match(crystalOperators) != null ||
            stream.match(crystalConditionalOperators) != null ||
            stream.match(crystalIndexingOperators) != null
    }
    state.tokenize.removeAt(state.tokenize.lastIndex)
    return "def"
}

private fun crystalTokenFollowType(stream: StringStream, state: CrystalState): String? {
    if (stream.eatSpace()) return null
    stream.match(crystalTypes)
    state.tokenize.removeAt(state.tokenize.lastIndex)
    return "def"
}

private val crystalNextTokenizer:
    Map<String, (StringStream, CrystalState) -> String?> = mapOf(
        "def" to ::crystalTokenFollowIdent,
        "fun" to ::crystalTokenFollowIdent,
        "macro" to ::crystalTokenMacroDef,
        "class" to ::crystalTokenFollowType,
        "module" to ::crystalTokenFollowType,
        "struct" to ::crystalTokenFollowType,
        "lib" to ::crystalTokenFollowType,
        "enum" to ::crystalTokenFollowType,
        "union" to ::crystalTokenFollowType
    )

@Suppress("NestedBlockDepth")
private fun crystalTokenQuote(
    end: String,
    style: String,
    embed: Boolean
): (StringStream, CrystalState) -> String? {
    return fun(stream: StringStream, state: CrystalState): String? {
        var escaped = false
        while (stream.peek() != null) {
            if (!escaped) {
                if (stream.match("{%", false)) {
                    state.tokenize.add(crystalTokenMacro("%", "%"))
                    return style
                }
                if (stream.match("{{", false)) {
                    state.tokenize.add(crystalTokenMacro("{", "}"))
                    return style
                }
                if (embed && stream.match("#{", false)) {
                    state.tokenize.add(crystalTokenNest("#{", "}", "meta"))
                    return style
                }
                val ch = stream.next()
                if (ch == end) {
                    state.tokenize.removeAt(state.tokenize.lastIndex)
                    return style
                }
                escaped = embed && ch == "\\"
            } else {
                stream.next()
                escaped = false
            }
        }
        return style
    }
}

@Suppress("NestedBlockDepth")
private fun crystalTokenHereDoc(
    phrase: String,
    embed: Boolean
): (StringStream, CrystalState) -> String? {
    return fun(stream: StringStream, state: CrystalState): String? {
        if (stream.sol()) {
            stream.eatSpace()
            if (stream.match(phrase)) {
                state.tokenize.removeAt(state.tokenize.lastIndex)
                return "string"
            }
        }
        var escaped = false
        while (stream.peek() != null) {
            if (!escaped) {
                if (stream.match("{%", false)) {
                    state.tokenize.add(crystalTokenMacro("%", "%"))
                    return "string"
                }
                if (stream.match("{{", false)) {
                    state.tokenize.add(crystalTokenMacro("{", "}"))
                    return "string"
                }
                if (embed && stream.match("#{", false)) {
                    state.tokenize.add(crystalTokenNest("#{", "}", "meta"))
                    return "string"
                }
                escaped = stream.next() == "\\" && embed
            } else {
                stream.next()
                escaped = false
            }
        }
        return "string"
    }
}

/** Stream parser for Crystal. */
val crystal: StreamParser<CrystalState> = object : StreamParser<CrystalState> {
    override val name: String get() = "crystal"

    override fun startState(indentUnit: Int) = CrystalState()

    override fun copyState(state: CrystalState): CrystalState {
        return CrystalState(
            tokenize = state.tokenize.toMutableList(),
            currentIndent = state.currentIndent,
            lastToken = state.lastToken,
            lastStyle = state.lastStyle,
            blocks = state.blocks.toMutableList()
        )
    }

    override fun token(stream: StringStream, state: CrystalState): String? {
        val style = state.tokenize.last()(stream, state)
        val token = stream.current()
        if (style != null && style != "comment") {
            state.lastToken = token
            state.lastStyle = style
        }
        return style
    }

    override fun indent(state: CrystalState, textAfter: String, context: IndentContext): Int? {
        val cleaned = textAfter
            .replace(Regex("^\\s*(?:\\{%)?\\s*"), "")
            .replace(Regex("\\s*(?:%\\})?\\s*$"), "")
        if (dedentKeywords.containsMatchIn(cleaned) ||
            dedentPunctuals.containsMatchIn(cleaned)
        ) {
            return context.unit * (state.currentIndent - 1)
        }
        return context.unit * state.currentIndent
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf("line" to "#")
        )
}
