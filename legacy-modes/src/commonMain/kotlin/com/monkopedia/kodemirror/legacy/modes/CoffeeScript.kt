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

private val csOperators =
    Regex(
        "^(?:->|=>|\\+[+=]?|-[\\-=]?|\\*[\\*=]?|/[/=]?|[=!]=|<[><]?=?|>>?=?|%=?|&=?|" +
            "\\|=?|\\^=?|~|!|\\?|(or|and|\\|\\|||&&|\\?)=)"
    )
private val csDelimiters = Regex("^(?:[(\\[\\]{},:`=;]|\\.\\.\\.?)")
private val csIdentifiers = Regex("^[_A-Za-z\$][_A-Za-z\$0-9]*")
private val csAtProp = Regex("^@[_A-Za-z\$][_A-Za-z\$0-9]*")

private val csWordOperators = Regex("^(?:and|or|not|is|isnt|in|instanceof|typeof)\\b")
private val csIndentKeywordsRaw = listOf(
    "for", "while", "loop", "if", "unless", "else",
    "switch", "try", "catch", "finally", "class"
)
private val csCommonKeywords = listOf(
    "break", "by", "continue", "debugger", "delete",
    "do", "in", "of", "new", "return", "then",
    "this", "@", "throw", "when", "until", "extends"
)

private fun wordRegexp(words: List<String>): Regex {
    return Regex("^((" + words.joinToString(")|(") + "))\\b")
}

private val csKeywords = wordRegexp(csIndentKeywordsRaw + csCommonKeywords)
private val csIndentKeywords = wordRegexp(csIndentKeywordsRaw)
private val csStringPrefixes = Regex("^('{3}|\"{3}|['\"])")
private val csRegexPrefixes = Regex("^(/{3}|/)")
private val csConstants = wordRegexp(
    listOf("Infinity", "NaN", "undefined", "null", "true", "false", "on", "off", "yes", "no")
)

data class CsScope(
    var offset: Int,
    var type: String,
    var prev: CsScope?,
    var align: Boolean?,
    var alignOffset: Int?
)

data class CoffeeScriptState(
    var tokenize: (StringStream, CoffeeScriptState) -> String? = ::csTokenBase,
    var scope: CsScope =
        CsScope(offset = 0, type = "coffee", prev = null, align = false, alignOffset = null),
    var prop: Boolean = false,
    var dedent: Int = 0
)

private fun csTokenFactory(
    delimiter: String,
    singleline: Boolean,
    outclass: String
): (StringStream, CoffeeScriptState) -> String? {
    return fn@{ stream, state ->
        while (!stream.eol()) {
            stream.eatWhile(Regex("[^'\"/\\\\]"))
            if (stream.eat("\\") != null) {
                stream.next()
                if (singleline && stream.eol()) {
                    state.tokenize = ::csTokenBase
                    return@fn outclass
                }
            } else if (stream.match(delimiter) != null) {
                state.tokenize = ::csTokenBase
                return@fn outclass
            } else {
                stream.eat(Regex("['\"/]"))
            }
        }
        if (singleline) {
            state.tokenize = ::csTokenBase
        }
        outclass
    }
}

private fun csLongComment(stream: StringStream, state: CoffeeScriptState): String {
    while (!stream.eol()) {
        stream.eatWhile(Regex("[^#]"))
        if (stream.match("###") != null) {
            state.tokenize = ::csTokenBase
            break
        }
        stream.eatWhile(Regex("#"))
    }
    return "comment"
}

private fun csTokenBase(stream: StringStream, state: CoffeeScriptState): String? {
    if (stream.sol()) {
        if (state.scope.align == null) state.scope.align = false
        val scopeOffset = state.scope.offset
        if (stream.eatSpace()) {
            val lineOffset = stream.indentation()
            if (lineOffset > scopeOffset && state.scope.type == "coffee") {
                return "indent"
            } else if (lineOffset < scopeOffset) {
                return "dedent"
            }
            return null
        } else {
            if (scopeOffset > 0) {
                csDedent(stream, state)
            }
        }
    }
    if (stream.eatSpace()) return null

    if (stream.match("####") != null) {
        stream.skipToEnd()
        return "comment"
    }
    if (stream.match("###") != null) {
        state.tokenize = ::csLongComment
        return state.tokenize(stream, state)
    }
    val ch = stream.peek()
    if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    }

    if (stream.match(Regex("^-?[0-9\\.]"), consume = false) != null) {
        var floatLiteral = false
        if (stream.match(Regex("^-?\\d*\\.\\d+([eE][\\+\\-]?\\d+)?"), consume = true) != null) {
            floatLiteral = true
        }
        if (!floatLiteral && stream.match(Regex("^-?\\d+\\.\\d*"), consume = true) != null) {
            floatLiteral = true
        }
        if (!floatLiteral && stream.match(Regex("^-?\\.\\d+"), consume = true) != null) {
            floatLiteral = true
        }
        if (floatLiteral) {
            if (stream.peek() == ".") stream.backUp(1)
            return "number"
        }
        var intLiteral = false
        if (stream.match(Regex("^-?0x[0-9a-fA-F]+"), consume = true) != null) intLiteral = true
        if (!intLiteral &&
            stream.match(Regex("^-?[1-9]\\d*([eE][\\+\\-]?\\d+)?"), consume = true) != null
        ) {
            intLiteral = true
        }
        if (!intLiteral &&
            stream.match(Regex("^-?0(?![\\dx])"), consume = true) != null
        ) {
            intLiteral = true
        }
        if (intLiteral) return "number"
    }

    if (stream.match(csStringPrefixes) != null) {
        state.tokenize = csTokenFactory(stream.current(), false, "string")
        return state.tokenize(stream, state)
    }
    if (stream.match(csRegexPrefixes) != null) {
        if (stream.current() != "/" || stream.match(Regex("^.*/"), consume = false) != null) {
            state.tokenize = csTokenFactory(stream.current(), true, "string.special")
            return state.tokenize(stream, state)
        } else {
            stream.backUp(1)
        }
    }

    if (stream.match(csOperators) != null || stream.match(csWordOperators) != null) {
        return "operator"
    }
    if (stream.match(csDelimiters) != null) return "punctuation"
    if (stream.match(csConstants) != null) return "atom"
    if (stream.match(csAtProp) != null ||
        (state.prop && stream.match(csIdentifiers) != null)
    ) {
        return "property"
    }
    if (stream.match(csKeywords) != null) return "keyword"
    if (stream.match(csIdentifiers) != null) return "variable"

    stream.next()
    return "error"
}

private fun csIndent(stream: StringStream, state: CoffeeScriptState, type: String = "coffee") {
    var offset = 0
    var align: Boolean? = false
    var alignOffset: Int? = null
    var scope: CsScope? = state.scope
    while (scope != null) {
        if (scope.type == "coffee" || scope.type == "}") {
            offset = scope.offset + stream.indentUnit
            break
        }
        scope = scope.prev
    }
    if (type != "coffee") {
        align = null
        alignOffset = stream.column() + stream.current().length
    } else if (state.scope.align == true) {
        state.scope.align = false
    }
    state.scope = CsScope(
        offset = offset,
        type = type,
        prev = state.scope,
        align = align,
        alignOffset = alignOffset
    )
}

private fun csDedent(stream: StringStream, state: CoffeeScriptState): Boolean {
    if (state.scope.prev == null) return false
    if (state.scope.type == "coffee") {
        val indent = stream.indentation()
        var matched = false
        var scope: CsScope? = state.scope
        while (scope != null) {
            if (indent == scope.offset) {
                matched = true
                break
            }
            scope = scope.prev
        }
        if (!matched) return true
        while (state.scope.prev != null && state.scope.offset != indent) {
            state.scope = state.scope.prev!!
        }
        return false
    } else {
        state.scope = state.scope.prev!!
        return false
    }
}

private fun csTokenLexer(stream: StringStream, state: CoffeeScriptState): String? {
    val style = state.tokenize(stream, state)
    val current = stream.current()

    if (current == "return") {
        state.dedent = 1
    }
    if (((current == "->" || current == "=>") && stream.eol()) || style == "indent") {
        csIndent(stream, state)
    }
    val delimIdx = "[({".indexOf(current)
    if (delimIdx != -1) {
        csIndent(stream, state, "])}".substring(delimIdx, delimIdx + 1))
    }
    if (csIndentKeywords.containsMatchIn(current)) {
        csIndent(stream, state)
    }
    if (current == "then") {
        csDedent(stream, state)
    }

    if (style == "dedent") {
        if (csDedent(stream, state)) return "error"
    }
    val closingDelimIdx = "])}".indexOf(current)
    if (closingDelimIdx != -1) {
        while (state.scope.type == "coffee" && state.scope.prev != null) {
            state.scope = state.scope.prev!!
        }
        if (state.scope.type == current) {
            state.scope = state.scope.prev ?: state.scope
        }
    }
    if (state.dedent > 0 && stream.eol()) {
        if (state.scope.type == "coffee" && state.scope.prev != null) {
            state.scope = state.scope.prev!!
        }
        state.dedent = 0
    }

    return if (style == "indent" || style == "dedent") null else style
}

/** Stream parser for CoffeeScript. */
val coffeeScript: StreamParser<CoffeeScriptState> = object : StreamParser<CoffeeScriptState> {
    override val name: String get() = "coffeescript"

    override fun startState(indentUnit: Int) = CoffeeScriptState()

    override fun copyState(state: CoffeeScriptState): CoffeeScriptState {
        fun copyScope(s: CsScope): CsScope = CsScope(
            offset = s.offset,
            type = s.type,
            prev = s.prev?.let { copyScope(it) },
            align = s.align,
            alignOffset = s.alignOffset
        )
        return CoffeeScriptState(
            tokenize = state.tokenize,
            scope = copyScope(state.scope),
            prop = state.prop,
            dedent = state.dedent
        )
    }

    override fun token(stream: StringStream, state: CoffeeScriptState): String? {
        val fillAlign = if (state.scope.align == null) state.scope else null
        if (fillAlign != null && stream.sol()) fillAlign.align = false

        val style = csTokenLexer(stream, state)
        if (style != null && style != "comment") {
            fillAlign?.align = true
            state.prop = style == "punctuation" && stream.current() == "."
        }
        return style
    }

    override fun indent(state: CoffeeScriptState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != ::csTokenBase) return 0
        val scope = state.scope
        val closer = textAfter.isNotEmpty() && "])}".indexOf(textAfter[0]) > -1
        var effectiveScope = scope
        if (closer) {
            var s: CsScope? = effectiveScope
            while (s != null && s.type == "coffee") s = s.prev
            effectiveScope = s ?: effectiveScope
        }
        val closes = closer && effectiveScope.type == textAfter[0].toString()
        return if (effectiveScope.align == true) {
            (effectiveScope.alignOffset ?: 0) - (if (closes) 1 else 0)
        } else {
            (if (closes) effectiveScope.prev else effectiveScope)?.offset ?: 0
        }
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf("line" to "#")
        )
}
