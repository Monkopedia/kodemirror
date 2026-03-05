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

private fun vbWordRegexp(words: List<String>): Regex {
    return Regex("^((" + words.joinToString(")|(") + "))\\b", RegexOption.IGNORE_CASE)
}

private val vbSingleOperators = Regex("^[+\\-*/%&\\\\|^~<>!]")
private val vbSingleDelimiters = Regex("^[()\\[\\]{}@,:`=;.]")
private val vbDoubleOperators = Regex("^((==)|(<>)|(<=)|(>=)|(<>)|(<<)|(>>)|(//)|(\\*\\*))")
private val vbDoubleDelimiters = Regex("^((\\+=)|(\\-=)|(\\*=)|(%=)|(/=)|(&=)|(\\|=)|(\\^=))")
private val vbTripleDelimiters = Regex("^((//=)|(>>=)|(<<=)|(\\*\\*=))")
private val vbIdentifiers = Regex("^[_A-Za-z][_A-Za-z0-9]*")

private val vbOpeningKeywords = listOf(
    "class", "module", "sub", "enum", "select", "while", "if",
    "function", "get", "set", "property", "try", "structure", "synclock", "using", "with"
)
private val vbMiddleKeywords = listOf("else", "elseif", "case", "catch", "finally")
private val vbEndKeywords = listOf("next", "loop")

private val vbOperatorKeywords = listOf(
    "and", "andalso", "or", "orelse", "xor", "in", "not", "is", "isnot", "like"
)
private val vbWordOperators = vbWordRegexp(vbOperatorKeywords)

private val vbCommonKeywords = listOf(
    "#const", "#else", "#elseif", "#end", "#if", "#region", "addhandler", "addressof",
    "alias", "as", "byref", "byval", "cbool", "cbyte", "cchar", "cdate", "cdbl", "cdec",
    "cint", "clng", "cobj", "compare", "const", "continue", "csbyte", "cshort", "csng",
    "cstr", "cuint", "culng", "cushort", "declare", "default", "delegate", "dim",
    "directcast", "each", "erase", "error", "event", "exit", "explicit", "false", "for",
    "friend", "gettype", "goto", "handles", "implements", "imports", "infer", "inherits",
    "interface", "isfalse", "istrue", "lib", "me", "mod", "mustinherit", "mustoverride",
    "my", "mybase", "myclass", "namespace", "narrowing", "new", "nothing",
    "notinheritable", "notoverridable", "of", "off", "on", "operator", "option",
    "optional", "out", "overloads", "overridable", "overrides", "paramarray", "partial",
    "private", "protected", "public", "raiseevent", "readonly", "redim", "removehandler",
    "resume", "return", "shadows", "shared", "static", "step", "stop", "strict", "then",
    "throw", "to", "true", "trycast", "typeof", "until", "until", "when", "widening",
    "withevents", "writeonly"
)

private val vbCommonTypes = listOf(
    "object", "boolean", "char", "string", "byte", "sbyte", "short", "ushort", "int16",
    "uint16", "integer", "uinteger", "int32", "uint32", "long", "ulong", "int64", "uint64",
    "decimal", "single", "double", "float", "date", "datetime", "intptr", "uintptr"
)

private val vbKeywordsRe = vbWordRegexp(vbCommonKeywords)
private val vbTypesRe = vbWordRegexp(vbCommonTypes)
private val vbOpening = vbWordRegexp(vbOpeningKeywords)
private val vbMiddle = vbWordRegexp(vbMiddleKeywords)
private val vbClosing = vbWordRegexp(vbEndKeywords)
private val vbDoubleClosing = vbWordRegexp(listOf("end"))
private val vbDoOpening = vbWordRegexp(listOf("do"))

data class VbState(
    var tokenize: (StringStream, VbState) -> String?,
    var lastToken: Any? = null,
    var currentIndent: Int = 0,
    var nextLineIndent: Int = 0,
    var doInCurrentLine: Boolean = false
)

val vb: StreamParser<VbState> = object : StreamParser<VbState> {
    override val name: String get() = "vb"
    override val languageData: Map<String, Any>
        get() = mapOf(
            "closeBrackets" to mapOf("brackets" to listOf("(", "[", "{", "\"")),
            "commentTokens" to mapOf("line" to "'")
        )

    private fun tokenStringFactory(delimiter: String): (StringStream, VbState) -> String? {
        val singleline = delimiter.length == 1
        return fun(stream: StringStream, state: VbState): String? {
            while (!stream.eol()) {
                stream.eatWhile(Regex("[^'\"]"))
                if (stream.match(delimiter)) {
                    state.tokenize = ::tokenBase
                    return "string"
                } else {
                    stream.eat(Regex("['\"]"))
                }
            }
            if (singleline) state.tokenize = ::tokenBase
            return "string"
        }
    }

    private fun tokenBase(stream: StringStream, state: VbState): String? {
        if (stream.eatSpace()) return null

        val ch = stream.peek()

        if (ch == "'") {
            stream.skipToEnd()
            return "comment"
        }

        if (stream.match(Regex("^((&H)|(&O))?[0-9.a-f]", RegexOption.IGNORE_CASE), false) != null) {
            var floatLiteral = false
            if (stream.match(Regex("^\\d*\\.\\d+F?", RegexOption.IGNORE_CASE)) != null) {
                floatLiteral = true
            } else if (stream.match(Regex("^\\d+\\.\\d*F?")) != null) {
                floatLiteral = true
            } else if (stream.match(Regex("^\\.\\d+F?")) != null) {
                floatLiteral = true
            }
            if (floatLiteral) {
                stream.eat(Regex("[Jj]", RegexOption.IGNORE_CASE))
                return "number"
            }
            var intLiteral = false
            if (stream.match(Regex("^&H[0-9a-f]+", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            } else if (stream.match(Regex("^&O[0-7]+", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            } else if (stream.match(Regex("^[1-9]\\d*F?")) != null) {
                stream.eat(Regex("[Jj]", RegexOption.IGNORE_CASE))
                intLiteral = true
            } else if (stream.match(Regex("^0(?![\\dx])", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            }
            if (intLiteral) {
                stream.eat(Regex("[Ll]", RegexOption.IGNORE_CASE))
                return "number"
            }
        }

        if (stream.match("\"")) {
            state.tokenize = tokenStringFactory(stream.current())
            return state.tokenize(stream, state)
        }

        if (stream.match(vbTripleDelimiters) != null || stream.match(vbDoubleDelimiters) != null) {
            return null
        }
        if (stream.match(vbDoubleOperators) != null ||
            stream.match(vbSingleOperators) != null ||
            stream.match(vbWordOperators) != null
        ) {
            return "operator"
        }
        if (stream.match(vbSingleDelimiters) != null) {
            return null
        }
        if (stream.match(vbDoOpening) != null) {
            state.currentIndent++
            state.doInCurrentLine = true
            return "keyword"
        }
        if (stream.match(vbOpening) != null) {
            if (!state.doInCurrentLine) {
                state.currentIndent++
            } else {
                state.doInCurrentLine = false
            }
            return "keyword"
        }
        if (stream.match(vbMiddle) != null) return "keyword"
        if (stream.match(vbDoubleClosing) != null) {
            state.currentIndent--
            state.currentIndent--
            return "keyword"
        }
        if (stream.match(vbClosing) != null) {
            state.currentIndent--
            return "keyword"
        }
        if (stream.match(vbTypesRe) != null) return "keyword"
        if (stream.match(vbKeywordsRe) != null) return "keyword"
        if (stream.match(vbIdentifiers) != null) return "variable"

        stream.next()
        return "error"
    }

    private fun tokenLexer(stream: StringStream, state: VbState): String? {
        var style = state.tokenize(stream, state)
        val current = stream.current()

        if (current == ".") {
            style = state.tokenize(stream, state)
            return if (style == "variable") "variable" else "error"
        }

        val delimiterIndex1 = "[({".indexOf(current)
        if (delimiterIndex1 != -1) {
            state.currentIndent++
        }
        val delimiterIndex2 = "])}".indexOf(current)
        if (delimiterIndex2 != -1) {
            state.currentIndent--
        }

        return style
    }

    override fun startState(indentUnit: Int) = VbState(tokenize = ::tokenBase)

    override fun copyState(state: VbState) = state.copy()

    override fun token(stream: StringStream, state: VbState): String? {
        if (stream.sol()) {
            state.currentIndent += state.nextLineIndent
            state.nextLineIndent = 0
            state.doInCurrentLine = false
        }
        val style = tokenLexer(stream, state)
        state.lastToken = mapOf("style" to style, "content" to stream.current())
        return style
    }

    override fun indent(state: VbState, textAfter: String, cx: IndentContext): Int? {
        val trueText = textAfter.trim()
        if (vbClosing.containsMatchIn(trueText) ||
            vbDoubleClosing.containsMatchIn(trueText) ||
            vbMiddle.containsMatchIn(trueText)
        ) {
            return cx.unit * (state.currentIndent - 1)
        }
        if (state.currentIndent < 0) return 0
        return state.currentIndent * cx.unit
    }
}
