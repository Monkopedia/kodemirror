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

private val elmLowerRE = Regex("[a-z]")
private val elmUpperRE = Regex("[A-Z]")
private val elmInnerRE = Regex("[a-zA-Z0-9_]")
private val elmDigitRE = Regex("[0-9]")
private val elmHexRE = Regex("[0-9A-Fa-f]")
private val elmSymbolRE = Regex("[-&*+.\\\\/<>=?^|:]")
private val elmSpecialRE = Regex("[(),\\[\\]{}]")
private val elmSpacesRE = Regex("[ \\u000B\\u000C]")

private val elmWellKnownWords = setOf(
    "case", "of", "as", "if", "then", "else", "let", "in",
    "type", "alias", "module", "where", "import", "exposing", "port"
)

fun interface ElmTokenFn {
    fun invoke(stream: StringStream, setState: (ElmTokenFn) -> Unit): String?
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun elmNormal(): ElmTokenFn {
    return ElmTokenFn { source, setState ->
        if (source.eatWhile(elmSpacesRE)) {
            return@ElmTokenFn null
        }

        val char = source.next() ?: return@ElmTokenFn "error"

        if (elmSpecialRE.containsMatchIn(char)) {
            return@ElmTokenFn if (char == "{" && source.eat("-") != null) {
                elmSwitchState(source, setState, elmChompMultiComment(1))
            } else if (char == "[" && source.match("glsl|")) {
                elmSwitchState(source, setState, elmChompGlsl)
            } else {
                "builtin"
            }
        }

        if (char == "'") {
            return@ElmTokenFn elmSwitchState(source, setState, elmChompChar)
        }

        if (char == "\"") {
            return@ElmTokenFn if (source.eat("\"") != null) {
                if (source.eat("\"") != null) {
                    elmSwitchState(source, setState, elmChompMultiString)
                } else {
                    "string"
                }
            } else {
                elmSwitchState(source, setState, elmChompSingleString)
            }
        }

        if (elmUpperRE.containsMatchIn(char)) {
            source.eatWhile(elmInnerRE)
            return@ElmTokenFn "type"
        }

        if (elmLowerRE.containsMatchIn(char)) {
            val isDef = source.pos == 1
            source.eatWhile(elmInnerRE)
            return@ElmTokenFn if (isDef) "def" else "variable"
        }

        if (elmDigitRE.containsMatchIn(char)) {
            if (char == "0") {
                if (source.eat(Regex("[xX]")) != null) {
                    source.eatWhile(elmHexRE)
                    return@ElmTokenFn "number"
                }
            } else {
                source.eatWhile(elmDigitRE)
            }
            if (source.eat(".") != null) {
                source.eatWhile(elmDigitRE)
            }
            if (source.eat(Regex("[eE]")) != null) {
                source.eat(Regex("[-+]"))
                source.eatWhile(elmDigitRE)
            }
            return@ElmTokenFn "number"
        }

        if (elmSymbolRE.containsMatchIn(char)) {
            if (char == "-" && source.eat("-") != null) {
                source.skipToEnd()
                return@ElmTokenFn "comment"
            }
            source.eatWhile(elmSymbolRE)
            return@ElmTokenFn "keyword"
        }

        if (char == "_") {
            return@ElmTokenFn "keyword"
        }

        "error"
    }
}

private fun elmSwitchState(
    source: StringStream,
    setState: (ElmTokenFn) -> Unit,
    f: ElmTokenFn
): String? {
    setState(f)
    return f.invoke(source, setState)
}

private fun elmChompMultiComment(nest: Int): ElmTokenFn {
    if (nest == 0) {
        return elmNormal()
    }
    return ElmTokenFn { source, setState ->
        var currentNest = nest
        while (!source.eol()) {
            val char = source.next()
            if (char == "{" && source.eat("-") != null) {
                currentNest++
            } else if (char == "-" && source.eat("}") != null) {
                currentNest--
                if (currentNest == 0) {
                    setState(elmNormal())
                    return@ElmTokenFn "comment"
                }
            }
        }
        setState(elmChompMultiComment(currentNest))
        "comment"
    }
}

private val elmChompMultiString: ElmTokenFn = ElmTokenFn { source, setState ->
    while (!source.eol()) {
        val char = source.next()
        if (char == "\"" && source.eat("\"") != null && source.eat("\"") != null) {
            setState(elmNormal())
            return@ElmTokenFn "string"
        }
    }
    "string"
}

private val elmChompSingleString: ElmTokenFn = ElmTokenFn { source, setState ->
    while (source.skipTo("\\\"")) {
        source.next()
        source.next()
    }
    if (source.skipTo("\"")) {
        source.next()
        setState(elmNormal())
        return@ElmTokenFn "string"
    }
    source.skipToEnd()
    setState(elmNormal())
    "error"
}

private val elmChompChar: ElmTokenFn = ElmTokenFn { source, setState ->
    while (source.skipTo("\\'")) {
        source.next()
        source.next()
    }
    if (source.skipTo("'")) {
        source.next()
        setState(elmNormal())
        return@ElmTokenFn "string"
    }
    source.skipToEnd()
    setState(elmNormal())
    "error"
}

private val elmChompGlsl: ElmTokenFn = ElmTokenFn { source, setState ->
    while (!source.eol()) {
        val char = source.next()
        if (char == "|" && source.eat("]") != null) {
            setState(elmNormal())
            return@ElmTokenFn "string"
        }
    }
    "string"
}

data class ElmState(
    var f: ElmTokenFn = elmNormal()
)

/** Stream parser for Elm. */
val elm: StreamParser<ElmState> = object : StreamParser<ElmState> {
    override val name: String get() = "elm"

    override fun startState(indentUnit: Int) = ElmState(f = elmNormal())

    override fun copyState(state: ElmState) = state.copy()

    override fun token(stream: StringStream, state: ElmState): String? {
        val type = state.f.invoke(stream) { s -> state.f = s }
        val word = stream.current()
        return if (word in elmWellKnownWords) "keyword" else type
    }

    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "--"))
}
