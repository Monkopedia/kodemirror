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

private val juliaDelimiters = Regex("^[;,()\\[\\]{}]")
private val juliaIdentifiers = Regex(
    "^[_A-Za-z\\u00A1-\\u2217\\u2219-\\uFFFF][\\w\\u00A1-\\u2217\\u2219-\\uFFFF]*!*"
)

private val juliaOpenersList = listOf(
    "begin", "function", "type", "struct", "immutable", "let",
    "macro", "for", "while", "quote", "if", "else", "elseif", "try",
    "finally", "catch", "do"
)
private val juliaClosersList = listOf("end", "else", "elseif", "catch", "finally")

private val juliaKeywordsList = listOf(
    "if", "else", "elseif", "while", "for", "begin", "let",
    "end", "do", "try", "catch", "finally", "return", "break", "continue",
    "global", "local", "const", "export", "import", "importall", "using",
    "function", "where", "macro", "module", "baremodule", "struct", "type",
    "mutable", "immutable", "quote", "typealias", "abstract", "primitive",
    "bitstype"
)

private val juliaBuiltinsList = listOf("true", "false", "nothing", "NaN", "Inf")

private val juliaOpeners = Regex(
    "^\\b((" + juliaOpenersList.joinToString(")|(") + "))\\b"
)
private val juliaClosers = Regex(
    "^\\b((" + juliaClosersList.joinToString(")|(") + "))\\b"
)
private val juliaKeywords = Regex(
    "^\\b((" + juliaKeywordsList.joinToString(")|(") + "))\\b"
)
private val juliaBuiltins = Regex(
    "^\\b((" + juliaBuiltinsList.joinToString(")|(") + "))\\b"
)

private val juliaOperators = Regex(
    "^(?:" +
        "[<>]:?|[<>=]=|[!=]==|<<=?|>>>?=?|=>?|--?>|<--[->]?|\\/\\/|" +
        "[\\\\%*+\\-<>!\\/^|&\\u00F7\\u22BB]=?|\\?|\\$|~|:|" +
        "\\u00D7|\\u2208|\\u2209|\\u220B|\\u220C|\\u2218|" +
        "\\u221A|\\u221B|\\u2229|\\u222A|\\u2260|\\u2264|" +
        "\\u2265|\\u2286|\\u2288|\\u228A|\\u22C5|" +
        "\\b(in|isa)\\b(?!\\.?\\()" +
        ")"
)

private val juliaMacro = Regex("^@[_A-Za-z\\u00A1-\\uFFFF][\\w\\u00A1-\\uFFFF]*!*")
private val juliaSymbol = Regex("^:[_A-Za-z\\u00A1-\\uFFFF][\\w\\u00A1-\\uFFFF]*!*")
private val juliaStringPrefixes = Regex("^(`|([_A-Za-z\\u00A1-\\uFFFF]*\"(\"\")?))")

private val juliaChars = Regex(
    "^(?:" +
        "\\\\[0-7]{1,3}|\\\\x[A-Fa-f0-9]{1,2}|" +
        "\\\\[abefnrtv0%?'\"\\\\]|" +
        "([^\\u0027\\u005C\\uD800-\\uDFFF]|[\\uD800-\\uDFFF][\\uDC00-\\uDFFF])" +
        ")'"
)

private val juliaAsciiOperatorsList = listOf(
    "[<>]:", "[<>=]=", "<<=?", ">>>?=?", "=>", "--?>", "<--[->]?",
    "\\/\\/", "\\.{2,3}",
    "[\\.\\\\%*+\\-<>!\\/^|&]=?", "\\?", "\\$", "~", ":"
)
private val juliaMacroOperators = Regex(
    "^@(?:" + juliaAsciiOperatorsList.joinToString("|") + ")"
)
private val juliaSymbolOperators = Regex(
    "^:(?:" + juliaAsciiOperatorsList.joinToString("|") + ")"
)

data class JuliaState(
    var tokenize: (StringStream, JuliaState) -> String? = ::juliaTokenBase,
    var scopes: MutableList<String> = mutableListOf(),
    var lastToken: String? = null,
    var leavingExpr: Boolean = false,
    var isDefinition: Boolean = false,
    var nestedArrays: Int = 0,
    var nestedComments: Int = 0,
    var nestedGenerators: Int = 0,
    var nestedParameters: Int = 0
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun juliaTokenBase(stream: StringStream, state: JuliaState): String? {
    if (stream.match(Regex("^#="), consume = false) != null) {
        state.tokenize = ::juliaTokenComment
        return state.tokenize(stream, state)
    }

    var leavingExpr = state.leavingExpr
    if (stream.sol()) leavingExpr = false
    state.leavingExpr = false

    if (leavingExpr) {
        if (stream.match(Regex("^'+")) != null) return "operator"
    }

    if (stream.match(Regex("^\\.{4,}")) != null) {
        return "error"
    } else if (stream.match(Regex("^\\.{1,3}")) != null) {
        return "operator"
    }

    if (stream.eatSpace()) return null

    val ch = stream.peek()

    if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    }

    if (ch == "[") {
        state.scopes.add("[")
        state.nestedArrays++
    }
    if (ch == "(") {
        state.scopes.add("(")
        state.nestedGenerators++
    }

    if (state.nestedArrays > 0 && ch == "]") {
        while (state.scopes.isNotEmpty() && state.scopes.last() != "[") state.scopes.removeLast()
        if (state.scopes.isNotEmpty()) state.scopes.removeLast()
        state.nestedArrays--
        state.leavingExpr = true
    }

    if (state.nestedGenerators > 0 && ch == ")") {
        while (state.scopes.isNotEmpty() && state.scopes.last() != "(") state.scopes.removeLast()
        if (state.scopes.isNotEmpty()) state.scopes.removeLast()
        state.nestedGenerators--
        state.leavingExpr = true
    }

    if (state.nestedArrays > 0) {
        if (state.lastToken == "end" && stream.match(Regex("^:")) != null) {
            return "operator"
        }
        if (stream.match("end")) return "number"
    }

    val openerMatch = stream.match(juliaOpeners, consume = false)
    if (openerMatch != null) {
        state.scopes.add(openerMatch.value)
    }

    if (stream.match(juliaClosers, consume = false) != null) {
        if (state.scopes.isNotEmpty()) state.scopes.removeLast()
    }

    if (stream.match(Regex("^::(?![:\$])")) != null) {
        state.tokenize = ::juliaTokenAnnotation
        return state.tokenize(stream, state)
    }

    if (!leavingExpr &&
        (stream.match(juliaSymbol) != null || stream.match(juliaSymbolOperators) != null)
    ) {
        return "builtin"
    }

    if (stream.match(juliaOperators) != null) return "operator"

    if (stream.match(Regex("^\\.?\\d"), consume = false) != null) {
        var numberLiteral = false
        if (stream.match(Regex("^0x\\.[0-9a-f_]+p[+-]?[_\\d]+", RegexOption.IGNORE_CASE)) != null) {
            numberLiteral = true
        }
        if (!numberLiteral &&
            stream.match(Regex("^0x[0-9a-f_]+", RegexOption.IGNORE_CASE)) != null
        ) {
            numberLiteral = true
        }
        if (!numberLiteral && stream.match(Regex("^0b[01_]+", RegexOption.IGNORE_CASE)) != null) {
            numberLiteral = true
        }
        if (!numberLiteral && stream.match(Regex("^0o[0-7_]+", RegexOption.IGNORE_CASE)) != null) {
            numberLiteral = true
        }
        if (!numberLiteral && stream.match(
                Regex(
                    "^(?:(?:\\d[_\\d]*)?\\." +
                        "(?!\\.)" +
                        "(?:\\d[_\\d]*)?|\\d[_\\d]*\\.(?!\\.)" +
                        "(?:\\d[_\\d]*)?)([Eef][+-]?[_\\d]+)?",
                    RegexOption.IGNORE_CASE
                )
            ) != null
        ) {
            numberLiteral = true
        }
        if (!numberLiteral && stream.match(
                Regex("^\\d[_\\d]*(e[+-]?\\d+)?", RegexOption.IGNORE_CASE)
            ) != null
        ) {
            numberLiteral = true
        }
        if (numberLiteral) {
            stream.match(Regex("^im\\b"))
            state.leavingExpr = true
            return "number"
        }
    }

    if (stream.match("'")) {
        state.tokenize = ::juliaTokenChar
        return state.tokenize(stream, state)
    }

    if (stream.match(juliaStringPrefixes) != null) {
        state.tokenize = juliaTokenStringFactory(stream.current())
        return state.tokenize(stream, state)
    }

    if (stream.match(juliaMacro) != null || stream.match(juliaMacroOperators) != null) {
        return "meta"
    }

    if (stream.match(juliaDelimiters) != null) return null

    if (stream.match(juliaKeywords) != null) return "keyword"
    if (stream.match(juliaBuiltins) != null) return "builtin"

    val isDefinition = state.isDefinition || state.lastToken == "function" ||
        state.lastToken == "macro" || state.lastToken == "type" ||
        state.lastToken == "struct" || state.lastToken == "immutable"

    if (stream.match(juliaIdentifiers) != null) {
        if (isDefinition) {
            if (stream.peek() == ".") {
                state.isDefinition = true
                return "variable"
            }
            state.isDefinition = false
            return "def"
        }
        state.leavingExpr = true
        return "variable"
    }

    stream.next()
    return "error"
}

private fun juliaTokenAnnotation(stream: StringStream, state: JuliaState): String {
    stream.match(Regex("^.*?(?=[,;{}()=\\s]|$)"))
    if (stream.match(Regex("^\\{")) != null) {
        state.nestedParameters++
    } else if (stream.match(Regex("^\\}")) != null && state.nestedParameters > 0) {
        state.nestedParameters--
    }
    if (state.nestedParameters > 0) {
        stream.match(Regex("^.*?(?=\\{|\\})")) ?: stream.next()
    } else if (state.nestedParameters == 0) {
        state.tokenize = ::juliaTokenBase
    }
    return "builtin"
}

private fun juliaTokenComment(stream: StringStream, state: JuliaState): String {
    if (stream.match(Regex("^#=")) != null) {
        state.nestedComments++
    }
    if (stream.match(Regex("^.*?(?=#=|=#)")) == null) {
        stream.skipToEnd()
    }
    if (stream.match(Regex("^=#")) != null) {
        state.nestedComments--
        if (state.nestedComments == 0) state.tokenize = ::juliaTokenBase
    }
    return "comment"
}

private fun juliaTokenChar(stream: StringStream, state: JuliaState): String {
    var isChar = false
    if (stream.match(juliaChars) != null) {
        isChar = true
    } else {
        val m = stream.match(Regex("^\\\\u([a-f0-9]{1,4})(?=')", RegexOption.IGNORE_CASE))
        if (m != null) {
            val value = m.groupValues[1].toInt(16)
            if (value <= 55295 || value >= 57344) {
                isChar = true
                stream.next()
            }
        } else {
            val m2 = stream.match(Regex("^\\\\U([A-Fa-f0-9]{5,8})(?=')"))
            if (m2 != null) {
                val value = m2.groupValues[1].toInt(16)
                if (value <= 1114111) {
                    isChar = true
                    stream.next()
                }
            }
        }
    }
    if (isChar) {
        state.leavingExpr = true
        state.tokenize = ::juliaTokenBase
        return "string"
    }
    if (stream.match(Regex("^[^']+(?=')")) == null) stream.skipToEnd()
    if (stream.match("'")) state.tokenize = ::juliaTokenBase
    return "error"
}

private fun juliaTokenStringFactory(delimiter: String): (StringStream, JuliaState) -> String {
    val effectiveDelimiter = when {
        delimiter.endsWith("\"\"\"") -> "\"\"\""
        delimiter.endsWith("\"") -> "\""
        else -> delimiter
    }
    return fn@{ stream, state ->
        if (stream.eat("\\") != null) {
            stream.next()
        } else if (stream.match(effectiveDelimiter)) {
            state.tokenize = ::juliaTokenBase
            state.leavingExpr = true
            return@fn "string"
        } else {
            stream.eat(Regex("[`\"]"))
        }
        stream.eatWhile(Regex("[^\\\\`\"]"))
        "string"
    }
}

/** Stream parser for Julia. */
val julia: StreamParser<JuliaState> = object : StreamParser<JuliaState> {
    override val name: String get() = "julia"

    override fun startState(indentUnit: Int) = JuliaState()

    override fun copyState(state: JuliaState) = state.copy(
        scopes = state.scopes.toMutableList()
    )

    override fun token(stream: StringStream, state: JuliaState): String? {
        val style = state.tokenize(stream, state)
        val current = stream.current()
        if (current.isNotEmpty() && style != null) {
            state.lastToken = current
        }
        return style
    }

    override fun indent(state: JuliaState, textAfter: String, context: IndentContext): Int {
        var delta = 0
        if (textAfter == "]" || textAfter == ")" ||
            Regex("^end\\b").containsMatchIn(textAfter) ||
            Regex("^else").containsMatchIn(textAfter) ||
            Regex("^catch\\b").containsMatchIn(textAfter) ||
            Regex("^elseif\\b").containsMatchIn(textAfter) ||
            Regex("^finally").containsMatchIn(textAfter)
        ) {
            delta = -1
        }
        return (state.scopes.size + delta) * context.unit
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s*(end|else|catch|finally)\\b$"),
            "commentTokens" to mapOf(
                "line" to "#",
                "block" to mapOf("open" to "#=", "close" to "=#")
            )
        )
}
