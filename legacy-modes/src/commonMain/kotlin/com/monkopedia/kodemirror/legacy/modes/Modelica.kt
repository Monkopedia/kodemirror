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

@Suppress("ktlint:standard:max-line-length")
private val modelicaKeywords = (
    "algorithm and annotation assert block break class connect connector constant constrainedby " +
        "der discrete each else elseif elsewhen encapsulated end enumeration equation expandable " +
        "extends external false final flow for function if import impure in initial inner input " +
        "loop model not operator or outer output package parameter partial protected public pure " +
        "record redeclare replaceable return stream then true type when while within"
    ).split(" ").toSet()

@Suppress("ktlint:standard:max-line-length")
private val modelicaBuiltin = (
    "abs acos actualStream asin atan atan2 cardinality ceil cos cosh delay div edge exp floor " +
        "getInstanceName homotopy inStream integer log log10 mod pre reinit rem semiLinear sign " +
        "sin sinh spatialDistribution sqrt tan tanh"
    ).split(" ").toSet()

private val modelicaAtoms = setOf("Real", "Boolean", "Integer", "String")

private val modelicaIsSingleOperatorChar = Regex("[;=(:\\),{}.*<>+\\-/^\\[\\]]")
private val modelicaIsDoubleOperatorChar =
    Regex("^(:=|<=|>=|==|<>|\\.\\+|\\.\\-|\\.\\*|\\./|\\.\\^)")
private val modelicaIsDigit = Regex("[0-9]")
private val modelicaIsNonDigit = Regex("[_a-zA-Z]")

data class ModelicaState(
    var tokenize: ((StringStream, ModelicaState) -> String?)? = null,
    var level: Int = 0,
    var sol: Boolean = true
)

private fun modelicaTokenLineComment(stream: StringStream, state: ModelicaState): String {
    stream.skipToEnd()
    state.tokenize = null
    return "comment"
}

private fun modelicaTokenBlockComment(stream: StringStream, state: ModelicaState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (maybeEnd && ch == "/") {
            state.tokenize = null
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun modelicaTokenString(stream: StringStream, state: ModelicaState): String {
    var escaped = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "\"" && !escaped) {
            state.tokenize = null
            state.sol = false
            break
        }
        escaped = !escaped && ch == "\\"
    }
    return "string"
}

private fun modelicaTokenIdent(stream: StringStream, state: ModelicaState): String {
    stream.eatWhile(modelicaIsDigit)
    while (stream.eat(modelicaIsDigit) != null || stream.eat(modelicaIsNonDigit) != null) {
        // consume
    }

    val cur = stream.current()

    if (state.sol && (cur == "package" || cur == "model" || cur == "when" || cur == "connector")) {
        state.level++
    } else if (state.sol && cur == "end" && state.level > 0) {
        state.level--
    }

    state.tokenize = null
    state.sol = false

    if (cur in modelicaKeywords) return "keyword"
    if (cur in modelicaBuiltin) return "builtin"
    if (cur in modelicaAtoms) return "atom"
    return "variable"
}

private fun modelicaTokenQIdent(stream: StringStream, state: ModelicaState): String {
    stream.eatWhile(Regex("[^']"))

    state.tokenize = null
    state.sol = false

    return if (stream.eat("'") != null) "variable" else "error"
}

private fun modelicaTokenUnsignedNumber(stream: StringStream, state: ModelicaState): String {
    stream.eatWhile(modelicaIsDigit)
    if (stream.eat(".") != null) {
        stream.eatWhile(modelicaIsDigit)
    }
    if (stream.eat("e") != null || stream.eat("E") != null) {
        if (stream.eat("-") == null) stream.eat("+")
        stream.eatWhile(modelicaIsDigit)
    }

    state.tokenize = null
    state.sol = false
    return "number"
}

val modelica: StreamParser<ModelicaState> = object : StreamParser<ModelicaState> {
    override val name: String get() = "modelica"

    override fun startState(indentUnit: Int) = ModelicaState()
    override fun copyState(state: ModelicaState) = state.copy()

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun token(stream: StringStream, state: ModelicaState): String? {
        if (state.tokenize != null) {
            return state.tokenize!!(stream, state)
        }

        if (stream.sol()) state.sol = true

        if (stream.eatSpace()) {
            state.tokenize = null
            return null
        }

        val ch = stream.next() ?: return null

        if (ch == "/" && stream.eat("/") != null) {
            state.tokenize = ::modelicaTokenLineComment
        } else if (ch == "/" && stream.eat("*") != null) {
            state.tokenize = ::modelicaTokenBlockComment
        } else if (modelicaIsDoubleOperatorChar.containsMatchIn(ch + (stream.peek() ?: ""))) {
            stream.next()
            state.tokenize = null
            return "operator"
        } else if (modelicaIsSingleOperatorChar.containsMatchIn(ch)) {
            state.tokenize = null
            return "operator"
        } else if (modelicaIsNonDigit.containsMatchIn(ch)) {
            state.tokenize = ::modelicaTokenIdent
        } else if (ch == "'" && stream.peek() != null && stream.peek() != "'") {
            state.tokenize = ::modelicaTokenQIdent
        } else if (ch == "\"") {
            state.tokenize = ::modelicaTokenString
        } else if (modelicaIsDigit.containsMatchIn(ch)) {
            state.tokenize = ::modelicaTokenUnsignedNumber
        } else {
            state.tokenize = null
            return "error"
        }

        return state.tokenize!!(stream, state)
    }

    override fun indent(state: ModelicaState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != null) return null

        var level = state.level
        if (Regex("(algorithm)").containsMatchIn(textAfter)) level--
        if (Regex("(equation)").containsMatchIn(textAfter)) level--
        if (Regex("(initial algorithm)").containsMatchIn(textAfter)) level--
        if (Regex("(initial equation)").containsMatchIn(textAfter)) level--
        if (Regex("(end)").containsMatchIn(textAfter)) level--

        return if (level > 0) context.unit * level else 0
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
