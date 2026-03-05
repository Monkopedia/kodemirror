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

private fun ozWordRegexp(words: List<String>): Regex {
    return Regex("^(?:(?:${words.joinToString(")|(?:")}))\b")
}

private val ozSingleOperators = Regex("[^@!|<>#~.*\\-+\\\\/,=]")
private val ozDoubleOperators =
    Regex("^(?:<-|:=|=<|>=|<=|<:|>:|=:|\\\\=|\\\\=:|!!|==|::)")
private val ozTripleOperators = Regex("^(?:::|\\.\\.\\.|=<:|>=:)")

private val ozMiddle = listOf(
    "in", "then", "else", "of", "elseof", "elsecase", "elseif",
    "catch", "finally", "with", "require", "prepare", "import",
    "export", "define", "do"
)
private val ozEnd = listOf("end")

private val ozAtoms = ozWordRegexp(listOf("true", "false", "nil", "unit"))
private val ozCommonKeywords = ozWordRegexp(
    listOf(
        "andthen", "at", "attr", "declare", "feat", "from", "lex",
        "mod", "div", "mode", "orelse", "parser", "prod", "prop",
        "scanner", "self", "syn", "token"
    )
)
private val ozOpeningKeywords = ozWordRegexp(
    listOf(
        "local", "proc", "fun", "case", "class", "if", "cond", "or",
        "dis", "choice", "not", "thread", "try", "raise", "lock",
        "for", "suchthat", "meth", "functor"
    )
)
private val ozMiddleKeywords = ozWordRegexp(ozMiddle)
private val ozEndKeywords = ozWordRegexp(ozEnd)

data class OzState(
    var tokenize: (StringStream, OzState) -> String?,
    var currentIndent: Int = 0,
    var doInCurrentLine: Boolean = false,
    var hasPassedFirstStage: Boolean = false
)

private fun tokenOzClass(stream: StringStream, state: OzState): String? {
    if (stream.eatSpace()) return null
    stream.match(Regex("^(?:[A-Z][A-Za-z0-9_]*|`.+`)"))
    state.tokenize = ::tokenOzBase
    return "type"
}

private fun tokenOzMeth(stream: StringStream, state: OzState): String? {
    if (stream.eatSpace()) return null
    stream.match(Regex("^(?:[a-zA-Z][A-Za-z0-9_]*|`.+`)"))
    state.tokenize = ::tokenOzBase
    return "def"
}

private fun tokenOzFunProc(stream: StringStream, state: OzState): String? {
    if (stream.eatSpace()) return null

    if (!state.hasPassedFirstStage && stream.eat("{") != null) {
        state.hasPassedFirstStage = true
        return "bracket"
    } else if (state.hasPassedFirstStage) {
        stream.match(Regex("^(?:[A-Z][A-Za-z0-9_]*|`.+`|\\$)"))
        state.hasPassedFirstStage = false
        state.tokenize = ::tokenOzBase
        return "def"
    } else {
        state.tokenize = ::tokenOzBase
        return null
    }
}

private fun tokenOzComment(stream: StringStream, state: OzState): String? {
    var maybeEnd = false
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (ch == "/" && maybeEnd) {
            state.tokenize = ::tokenOzBase
            break
        }
        maybeEnd = (ch == "*")
    }
    return "comment"
}

private fun tokenOzString(quote: String): (StringStream, OzState) -> String? {
    return fun(stream: StringStream, state: OzState): String? {
        var escaped = false
        var ch: String?
        var end = false
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == quote && !escaped) {
                end = true
                break
            }
            escaped = !escaped && ch == "\\"
        }
        if (end || !escaped) {
            state.tokenize = ::tokenOzBase
        }
        return "string"
    }
}

private fun tokenOzBase(stream: StringStream, state: OzState): String? {
    if (stream.eatSpace()) return null

    // Brackets
    if (stream.match(Regex("^[{}]")) != null) return "bracket"

    // Special [] keyword
    if (stream.match("[]")) return "keyword"

    // Operators
    if (stream.match(ozTripleOperators) != null ||
        stream.match(ozDoubleOperators) != null
    ) {
        return "operator"
    }

    // Atoms
    if (stream.match(ozAtoms) != null) return "atom"

    // Opening keywords
    val matched = stream.match(ozOpeningKeywords)
    if (matched != null) {
        if (!state.doInCurrentLine) {
            state.currentIndent++
        } else {
            state.doInCurrentLine = false
        }

        val word = matched.value
        if (word == "proc" || word == "fun") {
            state.tokenize = ::tokenOzFunProc
        } else if (word == "class") {
            state.tokenize = ::tokenOzClass
        } else if (word == "meth") {
            state.tokenize = ::tokenOzMeth
        }

        return "keyword"
    }

    // Middle and other keywords
    if (stream.match(ozMiddleKeywords) != null ||
        stream.match(ozCommonKeywords) != null
    ) {
        return "keyword"
    }

    // End keywords
    if (stream.match(ozEndKeywords) != null) {
        state.currentIndent--
        return "keyword"
    }

    // Eat the next char for next comparisons
    val ch = stream.next() ?: return null

    // Strings
    if (ch == "\"" || ch == "'") {
        state.tokenize = tokenOzString(ch)
        return state.tokenize(stream, state)
    }

    // Numbers
    if (Regex("[~\\d]").matches(ch)) {
        if (ch == "~") {
            if (stream.peek()?.let { Regex("^[0-9]").matches(it) } != true) {
                return null
            } else if (
                (
                    stream.next() == "0" &&
                        stream.match(Regex("^[xX][0-9a-fA-F]+")) != null
                    ) ||
                stream.match(
                    Regex("^[0-9]*(?:\\.[0-9]+)?(?:[eE][~+]?[0-9]+)?")
                ) != null
            ) {
                return "number"
            }
        }

        if ((ch == "0" && stream.match(Regex("^[xX][0-9a-fA-F]+")) != null) ||
            stream.match(
                Regex("^[0-9]*(?:\\.[0-9]+)?(?:[eE][~+]?[0-9]+)?")
            ) != null
        ) {
            return "number"
        }

        return null
    }

    // Comments
    if (ch == "%") {
        stream.skipToEnd()
        return "comment"
    } else if (ch == "/") {
        if (stream.eat("*") != null) {
            state.tokenize = ::tokenOzComment
            return tokenOzComment(stream, state)
        }
    }

    // Single operators
    if (Regex("[^@!|<>#~.*\\-+\\\\/,=]").matches(ch)) {
        return "operator"
    }

    // If nothing matches, skip the entire alphanumerical block
    stream.eatWhile(Regex("\\w"))

    return "variable"
}

val oz: StreamParser<OzState> = object : StreamParser<OzState> {
    override val name: String get() = "oz"

    override fun startState(indentUnit: Int) = OzState(
        tokenize = ::tokenOzBase
    )

    override fun copyState(state: OzState) = state.copy()

    override fun token(stream: StringStream, state: OzState): String? {
        if (stream.sol()) {
            state.doInCurrentLine = false
        }
        return state.tokenize(stream, state)
    }

    override fun indent(state: OzState, textAfter: String, context: IndentContext): Int? {
        val trueText = textAfter.trim()

        if (ozEndKeywords.find(trueText) != null ||
            ozMiddleKeywords.find(trueText) != null ||
            Regex("\\[]").find(trueText) != null
        ) {
            return context.unit * (state.currentIndent - 1)
        }

        if (state.currentIndent < 0) return 0

        return state.currentIndent * context.unit
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "%",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
