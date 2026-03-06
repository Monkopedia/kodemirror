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

private fun octaveWordRegexp(words: List<String>): Regex {
    return Regex("^(?:(?:${words.joinToString(")|(?:")}))\\b")
}

private val octaveSingleOperators = Regex("^[+\\-*/&|^~<>!@'\\\\]")
private val octaveSingleDelimiters = Regex("^[(\\[{},;.:=]")
private val octaveDoubleOperators =
    Regex("^(?:==|~=|<=|>=|<<|>>|\\.[+\\-*/^\\\\])")
private val octaveDoubleDelimiters =
    Regex("^(?:!=|\\+=|-=|\\*=|/=|&=|\\|=|\\^=)")
private val octaveTripleDelimiters = Regex("^(?:>>=|<<=)")
private val octaveExpressionEnd = Regex("^[\\])]")
private val octaveIdentifiers =
    Regex("^[_A-Za-z\u00A1-\uFFFF][_A-Za-z0-9\u00A1-\uFFFF]*")

private val octaveBuiltins = octaveWordRegexp(
    listOf(
        "error", "eval", "function", "abs", "acos", "atan", "asin", "cos",
        "cosh", "exp", "log", "prod", "sum", "log10", "max", "min", "sign",
        "sin", "sinh", "sqrt", "tan", "reshape", "break", "zeros", "default",
        "margin", "round", "ones", "rand", "syn", "ceil", "floor", "size",
        "clear", "zeros", "eye", "mean", "std", "cov", "det", "eig", "inv",
        "norm", "rank", "trace", "expm", "logm", "sqrtm", "linspace", "plot",
        "title", "xlabel", "ylabel", "legend", "text", "grid", "meshgrid",
        "mesh", "num2str", "fft", "ifft", "arrayfun", "cellfun", "input",
        "fliplr", "flipud", "ismember"
    )
)

private val octaveKeywords = octaveWordRegexp(
    listOf(
        "return", "case", "switch", "else", "elseif", "end", "endif",
        "endfunction", "if", "otherwise", "do", "for", "while", "try",
        "catch", "classdef", "properties", "events", "methods", "global",
        "persistent", "endfor", "endwhile", "printf", "sprintf", "disp",
        "until", "continue", "pkg"
    )
)

private val octaveNanInf = octaveWordRegexp(listOf("nan", "NaN", "inf", "Inf"))

data class OctaveState(
    var tokenize: (StringStream, OctaveState) -> String?
)

private fun tokenTranspose(stream: StringStream, state: OctaveState): String? {
    if (!stream.sol() && stream.peek() == "'") {
        stream.next()
        state.tokenize = ::tokenOctaveBase
        return "operator"
    }
    state.tokenize = ::tokenOctaveBase
    return tokenOctaveBase(stream, state)
}

private fun tokenOctaveComment(stream: StringStream, state: OctaveState): String? {
    if (stream.match(Regex("^.*%\\}")) != null) {
        state.tokenize = ::tokenOctaveBase
        return "comment"
    }
    stream.skipToEnd()
    return "comment"
}

private fun tokenOctaveBase(stream: StringStream, state: OctaveState): String? {
    if (stream.eatSpace()) return null

    // Handle multi-line Comments
    if (stream.match("%{")) {
        state.tokenize = ::tokenOctaveComment
        stream.skipToEnd()
        return "comment"
    }

    // Handle one-line comments
    if (stream.match(Regex("^[%#]")) != null) {
        stream.skipToEnd()
        return "comment"
    }

    // Handle Number Literals
    if (stream.match(Regex("^[0-9.+-]"), consume = false) != null) {
        if (stream.match(Regex("^[+-]?0x[0-9a-fA-F]+[ij]?")) != null) {
            state.tokenize = ::tokenOctaveBase
            return "number"
        }
        if (stream.match(Regex("^[+-]?\\d*\\.\\d+(?:[EeDd][+-]?\\d+)?[ij]?")) != null) {
            return "number"
        }
        if (stream.match(Regex("^[+-]?\\d+(?:[EeDd][+-]?\\d+)?[ij]?")) != null) {
            return "number"
        }
    }
    if (stream.match(octaveNanInf) != null) return "number"

    // Handle Strings
    val m = stream.match(Regex("^\"(?:[^\"]|\"\")*(\"|$)"))
        ?: stream.match(Regex("^'(?:[^']|'')*('|$)"))
    if (m != null) {
        return if (m.groupValues[1].isNotEmpty()) "string" else "error"
    }

    // Handle words
    if (stream.match(octaveKeywords) != null) return "keyword"
    if (stream.match(octaveBuiltins) != null) return "builtin"
    if (stream.match(octaveIdentifiers) != null) return "variable"

    if (stream.match(octaveSingleOperators) != null ||
        stream.match(octaveDoubleOperators) != null
    ) {
        return "operator"
    }
    if (stream.match(octaveSingleDelimiters) != null ||
        stream.match(octaveDoubleDelimiters) != null ||
        stream.match(octaveTripleDelimiters) != null
    ) {
        return null
    }

    if (stream.match(octaveExpressionEnd) != null) {
        state.tokenize = ::tokenTranspose
        return null
    }

    // Handle non-detected items
    stream.next()
    return "error"
}

/** Stream parser for Octave/MATLAB. */
val octave: StreamParser<OctaveState> = object : StreamParser<OctaveState> {
    override val name: String get() = "octave"

    override fun startState(indentUnit: Int) = OctaveState(
        tokenize = ::tokenOctaveBase
    )

    override fun copyState(state: OctaveState) = state.copy()

    override fun token(stream: StringStream, state: OctaveState): String? {
        val style = state.tokenize(stream, state)
        if (style == "number" || style == "variable") {
            state.tokenize = ::tokenTranspose
        }
        return style
    }

    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "%"))
}
