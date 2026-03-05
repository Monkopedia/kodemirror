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

private val dylanUnnamedDefinition = listOf("interface")
private val dylanNamedDefinition = listOf(
    "module",
    "library",
    "macro",
    "C-struct",
    "C-union",
    "C-function",
    "C-callable-wrapper"
)
private val dylanTypeParameterizedDefinition = listOf("class", "C-subtype", "C-mapped-subtype")
private val dylanOtherParameterizedDefinition =
    listOf("method", "function", "C-variable", "C-address")
private val dylanConstantSimpleDefinition = listOf("constant")
private val dylanVariableSimpleDefinition = listOf("variable")
private val dylanOtherSimpleDefinition = listOf("generic", "domain", "C-pointer-type", "table")
private val dylanStatement = listOf(
    "if", "block", "begin", "method", "case", "for", "select", "when", "unless", "until",
    "while", "iterate", "profiling", "dynamic-bind"
)
private val dylanSeparator =
    listOf("finally", "exception", "cleanup", "else", "elseif", "afterwards")
private val dylanOther = listOf(
    "above", "below", "by", "from", "handler", "in", "instance", "let", "local", "otherwise",
    "slot", "subclass", "then", "to", "keyed-by", "virtual"
)
private val dylanSignalingCalls =
    listOf("signal", "error", "cerror", "break", "check-type", "abort")

private val dylanDefinition =
    dylanTypeParameterizedDefinition +
        dylanUnnamedDefinition +
        dylanNamedDefinition +
        dylanOtherParameterizedDefinition

private val dylanSimpleDefinition =
    dylanConstantSimpleDefinition + dylanVariableSimpleDefinition + dylanOtherSimpleDefinition

private val dylanKeyword = dylanStatement + dylanSeparator + dylanOther

// Lookup tables
private val dylanWordLookup: Map<String, String> = buildMap {
    dylanKeyword.forEach { put(it, "keyword") }
    dylanDefinition.forEach { put(it, "definition") }
    dylanSimpleDefinition.forEach { put(it, "simpleDefinition") }
    dylanSignalingCalls.forEach { put(it, "signalingCalls") }
}

private val dylanStyleLookup: Map<String, String> = buildMap {
    dylanKeyword.forEach { put(it, "keyword") }
    dylanDefinition.forEach { put(it, "def") }
    dylanSimpleDefinition.forEach { put(it, "def") }
    dylanSignalingCalls.forEach { put(it, "builtin") }
}

private val dylanSymbolPattern = "[-_a-zA-Z?!*@<>\$%]+"
private val dylanSymbol = Regex("^$dylanSymbolPattern")

private val dylanPatternSymbolKeyword = Regex("^$dylanSymbolPattern:")
private val dylanPatternSymbolClass = Regex("^<$dylanSymbolPattern>")
private val dylanPatternSymbolGlobal = Regex("^\\*$dylanSymbolPattern\\*")
private val dylanPatternSymbolConstant = Regex("^\\\$$dylanSymbolPattern")
private val dylanPatternKeyword = Regex("^with(?:out)?-[-_a-zA-Z?!*@<>\$%]+")

private val dylanPatternStyles = mapOf(
    "symbolKeyword" to "atom",
    "symbolClass" to "tag",
    "symbolGlobal" to "variableName.standard",
    "symbolConstant" to "variableName.constant"
)

data class DylanState(
    var tokenize: (StringStream, DylanState) -> String? = ::dylanTokenBase,
    var currentIndent: Int = 0
)

private fun dylanChain(
    stream: StringStream,
    state: DylanState,
    f: (StringStream, DylanState) -> String?
): String? {
    state.tokenize = f
    return f(stream, state)
}

private fun dylanTokenBase(stream: StringStream, state: DylanState): String? {
    val ch = stream.peek() ?: return null
    if (ch == "'" || ch == "\"") {
        stream.next()
        return dylanChain(stream, state, dylanTokenString(ch, "string"))
    } else if (ch == "/") {
        stream.next()
        if (stream.eat("*") != null) {
            return dylanChain(stream, state, ::dylanTokenComment)
        } else if (stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
        stream.backUp(1)
    } else if (Regex("[+\\-\\d.]").containsMatchIn(ch)) {
        if (stream.match(Regex("^[+\\-]?[0-9]*\\.[0-9]*([esdx][+\\-]?[0-9]+)?"), true) != null ||
            stream.match(Regex("^[+\\-]?[0-9]+([esdx][+\\-]?[0-9]+)"), true) != null ||
            stream.match(Regex("^[+\\-]?\\d+")) != null
        ) {
            return "number"
        }
    } else if (ch == "#") {
        stream.next()
        val nextCh = stream.peek()
        if (nextCh == "\"") {
            stream.next()
            return dylanChain(stream, state, dylanTokenString("\"", "string"))
        } else if (nextCh == "b") {
            stream.next()
            stream.eatWhile(Regex("[01]"))
            return "number"
        } else if (nextCh == "x") {
            stream.next()
            stream.eatWhile(Regex("[\\da-f]"))
            return "number"
        } else if (nextCh == "o") {
            stream.next()
            stream.eatWhile(Regex("[0-7]"))
            return "number"
        } else if (nextCh == "#") {
            stream.next()
            return "punctuation"
        } else if (nextCh == "[" || nextCh == "(") {
            stream.next()
            return "bracket"
        } else if (stream.match(Regex("^(?:f|t|all-keys|include|key|next|rest)"), true) != null) {
            return "atom"
        } else {
            stream.eatWhile(Regex("[-a-zA-Z]"))
            return "error"
        }
    } else if (ch == "~") {
        stream.next()
        val next2 = stream.peek()
        if (next2 == "=") {
            stream.next()
            val next3 = stream.peek()
            if (next3 == "=") stream.next()
            return "operator"
        }
        return "operator"
    } else if (ch == ":") {
        stream.next()
        val next2 = stream.peek()
        if (next2 == "=") {
            stream.next()
            return "operator"
        } else if (next2 == ":") {
            stream.next()
            return "punctuation"
        }
    } else if ("[](){}".contains(ch)) {
        stream.next()
        return "bracket"
    } else if (".,".contains(ch)) {
        stream.next()
        return "punctuation"
    } else if (stream.match("end") != null) {
        return "keyword"
    }

    // Try patterns
    if (stream.match(dylanPatternKeyword) != null) return dylanPatternStyles["symbolKeyword"]
    if (stream.match(dylanPatternSymbolKeyword) != null) return dylanPatternStyles["symbolKeyword"]
    if (stream.match(dylanPatternSymbolClass) != null) return dylanPatternStyles["symbolClass"]
    if (stream.match(dylanPatternSymbolGlobal) != null) return dylanPatternStyles["symbolGlobal"]
    if (stream.match(dylanPatternSymbolConstant) != null) {
        return dylanPatternStyles["symbolConstant"]
    }

    if (Regex("[+\\-*/^=<>&|]").containsMatchIn(ch)) {
        stream.next()
        return "operator"
    }
    if (stream.match("define") != null) {
        return "def"
    }
    stream.eatWhile(Regex("[\\w\\-]"))
    val current = stream.current()
    val style = dylanStyleLookup[current]
    if (style != null) return style
    if (dylanSymbol.matches(current)) return "variable"
    stream.next()
    return "variableName.standard"
}

private fun dylanTokenComment(stream: StringStream, state: DylanState): String? {
    var maybeEnd = false
    var maybeNested = false
    var nestedCount = 0
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "/" && maybeEnd) {
            if (nestedCount > 0) {
                nestedCount--
            } else {
                state.tokenize = ::dylanTokenBase
                break
            }
        } else if (ch == "*" && maybeNested) {
            nestedCount++
        }
        maybeEnd = ch == "*"
        maybeNested = ch == "/"
    }
    return "comment"
}

private fun dylanTokenString(quote: String, style: String): (StringStream, DylanState) -> String? =
    { stream, state ->
        var escaped = false
        var end = false
        while (true) {
            val next = stream.next() ?: break
            if (next == quote && !escaped) {
                end = true
                break
            }
            escaped = !escaped && next == "\\"
        }
        if (end || !escaped) {
            state.tokenize = ::dylanTokenBase
        }
        style
    }

val dylan: StreamParser<DylanState> = object : StreamParser<DylanState> {
    override val name: String get() = "dylan"

    override fun startState(indentUnit: Int) = DylanState()

    override fun copyState(state: DylanState) = state.copy()

    override fun token(stream: StringStream, state: DylanState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
