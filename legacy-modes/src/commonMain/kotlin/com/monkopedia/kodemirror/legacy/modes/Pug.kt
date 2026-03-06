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

private val ATTRS_NEST = mapOf('{' to '}', '(' to ')', '[' to ']')

// Use the JS mode defined in JavaScriptLegacy.kt
// Pug depends on the javascript StreamParser

class PugState(
    var indentUnit: Int = 2,
    var javaScriptLine: Boolean = false,
    var javaScriptLineExcludesColon: Boolean = false,
    var javaScriptArguments: Boolean = false,
    var javaScriptArgumentsDepth: Int = 0,
    var isInterpolating: Boolean = false,
    var interpolationNesting: Int = 0,
    // JavaScript state
    var jsState: Any? = null,
    var restOfLine: String = "",
    var isIncludeFiltered: Boolean = false,
    var isEach: Boolean = false,
    var lastTag: String = "",
    var isAttrs: Boolean = false,
    var attrsNest: MutableList<Char> = mutableListOf(),
    var inAttributeName: Boolean = true,
    var attributeIsType: Boolean = false,
    var attrValue: String = "",
    var indentOf: Int = Int.MAX_VALUE,
    var indentToken: String = "",
    var mixinCallAfter: Boolean = false
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun pugNextToken(stream: StringStream, state: PugState): String? {
    // restOfLine
    if (stream.sol()) state.restOfLine = ""
    if (state.restOfLine.isNotEmpty()) {
        stream.skipToEnd()
        val tok = state.restOfLine
        state.restOfLine = ""
        return tok
    }

    // interpolationContinued
    if (state.isInterpolating) {
        if (stream.peek() == "}") {
            state.interpolationNesting--
            if (state.interpolationNesting < 0) {
                stream.next()
                state.isInterpolating = false
                return "punctuation"
            }
        } else if (stream.peek() == "{") {
            state.interpolationNesting++
        }
        @Suppress("UNCHECKED_CAST")
        val jsParser = javaScriptLegacy as StreamParser<Any>
        val tok = jsParser.token(stream, state.jsState!!)
        return tok ?: return null
    }

    // includeFilteredContinued
    if (state.isIncludeFiltered) {
        state.isIncludeFiltered = false
        if (stream.match(Regex("^:[\\w\\-]+")) != null) {
            state.restOfLine = "string"
            // setStringMode
            state.indentOf = stream.indentation()
            state.indentToken = "string"
            return "atom"
        }
    }

    // eachContinued
    if (state.isEach) {
        if (stream.match(Regex("^ in\\b")) != null) {
            state.javaScriptLine = true
            state.isEach = false
            return "keyword"
        } else if (stream.sol() || stream.eol()) {
            state.isEach = false
        } else if (stream.next() != null) {
            while (stream.match(Regex("^ in\\b"), false) == null && stream.next() != null) {
                // eat chars
            }
            return "variable"
        }
    }

    // attrsContinued
    if (state.isAttrs) {
        val peek = stream.peek()
        if (peek != null) {
            val pChar = peek[0]
            if (ATTRS_NEST.containsKey(pChar)) {
                state.attrsNest.add(ATTRS_NEST[pChar]!!)
            }
            if (state.attrsNest.isNotEmpty() &&
                state.attrsNest.last() == pChar
            ) {
                state.attrsNest.removeAt(state.attrsNest.lastIndex)
            } else if (stream.eat(")") != null) {
                state.isAttrs = false
                return "punctuation"
            }
        }
        if (state.inAttributeName && stream.match(Regex("^[^=,)!]+")) != null) {
            if (stream.peek() == "=" || stream.peek() == "!") {
                state.inAttributeName = false
                @Suppress("UNCHECKED_CAST")
                val jsParser = javaScriptLegacy as StreamParser<Any>
                state.jsState = jsParser.startState(2)
                if (state.lastTag == "script" &&
                    stream.current().trim().lowercase() == "type"
                ) {
                    state.attributeIsType = true
                } else {
                    state.attributeIsType = false
                }
            }
            return "attribute"
        }

        @Suppress("UNCHECKED_CAST")
        val jsParser = javaScriptLegacy as StreamParser<Any>
        val tok = jsParser.token(stream, state.jsState!!)
        if (state.attrsNest.isEmpty() &&
            (tok == "string" || tok == "variable" || tok == "keyword")
        ) {
            state.inAttributeName = true
            state.attrValue = ""
            stream.backUp(stream.current().length)
            return pugNextToken(stream, state)
        }
        state.attrValue += stream.current()
        return tok ?: return null
    }

    // javaScript
    if (stream.sol()) {
        state.javaScriptLine = false
        state.javaScriptLineExcludesColon = false
    }
    if (state.javaScriptLine) {
        if (state.javaScriptLineExcludesColon && stream.peek() == ":") {
            state.javaScriptLine = false
            state.javaScriptLineExcludesColon = false
        } else {
            @Suppress("UNCHECKED_CAST")
            val jsParser = javaScriptLegacy as StreamParser<Any>
            val tok = jsParser.token(stream, state.jsState!!)
            if (stream.eol()) state.javaScriptLine = false
            return tok ?: return null
        }
    }

    // javaScriptArguments
    if (state.javaScriptArguments) {
        if (state.javaScriptArgumentsDepth == 0 && stream.peek() != "(") {
            state.javaScriptArguments = false
        } else {
            if (stream.peek() == "(") {
                state.javaScriptArgumentsDepth++
            } else if (stream.peek() == ")") state.javaScriptArgumentsDepth--
            if (state.javaScriptArgumentsDepth == 0) {
                state.javaScriptArguments = false
            } else {
                @Suppress("UNCHECKED_CAST")
                val jsParser = javaScriptLegacy as StreamParser<Any>
                val tok = jsParser.token(stream, state.jsState!!)
                return tok ?: return null
            }
        }
    }

    // callArguments
    if (state.mixinCallAfter) {
        state.mixinCallAfter = false
        if (stream.match(Regex("^\\( *[-\\w]+ *="), false) == null) {
            state.javaScriptArguments = true
            state.javaScriptArgumentsDepth = 0
        }
        return null
    }

    // Indented content
    if (stream.sol() && state.indentOf != Int.MAX_VALUE) {
        if (stream.indentation() > state.indentOf) {
            stream.skipToEnd()
            return state.indentToken
        } else {
            state.indentOf = Int.MAX_VALUE
            state.indentToken = ""
        }
    }

    // yield
    if (stream.match(Regex("^yield\\b")) != null) return "keyword"
    // doctype
    if (stream.match(Regex("^(?:doctype) *([^\\n]+)?")) != null) return "meta"
    // interpolation
    if (stream.match("#{")) {
        state.isInterpolating = true
        state.interpolationNesting = 0
        return "punctuation"
    }
    // case
    if (stream.match(Regex("^case\\b")) != null) {
        state.javaScriptLine = true
        return "keyword"
    }
    // when
    if (stream.match(Regex("^when\\b")) != null) {
        state.javaScriptLine = true
        state.javaScriptLineExcludesColon = true
        return "keyword"
    }
    // default
    if (stream.match(Regex("^default\\b")) != null) return "keyword"
    // extends
    if (stream.match(Regex("^extends?\\b")) != null) {
        state.restOfLine = "string"
        return "keyword"
    }
    // append / prepend
    if (stream.match(Regex("^append\\b")) != null) {
        state.restOfLine = "variable"
        return "keyword"
    }
    if (stream.match(Regex("^prepend\\b")) != null) {
        state.restOfLine = "variable"
        return "keyword"
    }
    // block
    if (stream.match(Regex("^block\\b *(?:(prepend|append)\\b)?")) != null) {
        state.restOfLine = "variable"
        return "keyword"
    }
    // include
    if (stream.match(Regex("^include\\b")) != null) {
        state.restOfLine = "string"
        return "keyword"
    }
    // includeFiltered
    if (stream.match(Regex("^include:([a-zA-Z0-9\\-]+)"), false) != null &&
        stream.match("include")
    ) {
        state.isIncludeFiltered = true
        return "keyword"
    }
    // mixin
    if (stream.match(Regex("^mixin\\b")) != null) {
        state.javaScriptLine = true
        return "keyword"
    }
    // call
    if (stream.match(Regex("^\\+([-\\w]+)")) != null) {
        if (stream.match(Regex("^\\( *[-\\w]+ *="), false) == null) {
            state.javaScriptArguments = true
            state.javaScriptArgumentsDepth = 0
        }
        return "variable"
    }
    if (stream.match("+#{", false)) {
        stream.next()
        state.mixinCallAfter = true
        if (stream.match("#{")) {
            state.isInterpolating = true
            state.interpolationNesting = 0
            return "punctuation"
        }
    }
    // conditional
    if (stream.match(Regex("^(if|unless|else if|else)\\b")) != null) {
        state.javaScriptLine = true
        return "keyword"
    }
    // each
    if (stream.match(Regex("^(- *)?(each|for)\\b")) != null) {
        state.isEach = true
        return "keyword"
    }
    // while
    if (stream.match(Regex("^while\\b")) != null) {
        state.javaScriptLine = true
        return "keyword"
    }
    // tag
    val tagMatch = stream.match(Regex("^(\\w(?:[-:\\w]*\\w)?)/?"))
    if (tagMatch != null) {
        state.lastTag = tagMatch.groupValues[1].lowercase()
        return "tag"
    }
    // filter
    if (stream.match(Regex("^:([\\w\\-]+)")) != null) {
        state.indentOf = stream.indentation()
        state.indentToken = "string"
        return "atom"
    }
    // code
    if (stream.match(Regex("^(!?=|-)")) != null) {
        state.javaScriptLine = true
        return "punctuation"
    }
    // id
    if (stream.match(Regex("^#([\\w-]+)")) != null) return "builtin"
    // className
    if (stream.match(Regex("^\\.([\\w-]+)")) != null) return "className"
    // attrs
    if (stream.peek() == "(") {
        stream.next()
        state.isAttrs = true
        state.attrsNest = mutableListOf()
        state.inAttributeName = true
        state.attrValue = ""
        state.attributeIsType = false
        return "punctuation"
    }
    // attributesBlock
    if (stream.match(Regex("^&attributes\\b")) != null) {
        state.javaScriptArguments = true
        state.javaScriptArgumentsDepth = 0
        return "keyword"
    }
    // indent
    if (stream.sol() && stream.eatSpace()) return "indent"
    // comment
    if (stream.match(Regex("^ *\\/\\/(-)?([^\\n]*)")) != null) {
        state.indentOf = stream.indentation()
        state.indentToken = "comment"
        return "comment"
    }
    // colon
    if (stream.match(Regex("^: *")) != null) return "colon"
    // text
    if (stream.match(Regex("^(?:\\| ?| )([^\\n]+)")) != null) return "string"
    if (stream.match(Regex("^(<[^\\n]*)"), false) != null) {
        state.indentOf = stream.indentation()
        state.indentToken = "string"
        stream.skipToEnd()
        return state.indentToken
    }
    // dot
    if (stream.eat(".") != null) {
        state.indentOf = stream.indentation()
        state.indentToken = "string"
        return "dot"
    }

    stream.next()
    return null
}

@Suppress("UNCHECKED_CAST")
val pug: StreamParser<PugState> = object : StreamParser<PugState> {
    override val name: String get() = "pug"

    override fun startState(indentUnit: Int): PugState {
        val jsParser = javaScriptLegacy as StreamParser<Any>
        return PugState(
            indentUnit = indentUnit,
            jsState = jsParser.startState(indentUnit)
        )
    }

    override fun copyState(state: PugState): PugState {
        val jsParser = javaScriptLegacy as StreamParser<Any>
        return PugState(
            indentUnit = state.indentUnit,
            javaScriptLine = state.javaScriptLine,
            javaScriptLineExcludesColon = state.javaScriptLineExcludesColon,
            javaScriptArguments = state.javaScriptArguments,
            javaScriptArgumentsDepth = state.javaScriptArgumentsDepth,
            isInterpolating = state.isInterpolating,
            interpolationNesting = state.interpolationNesting,
            jsState = state.jsState?.let { jsParser.copyState(it) },
            restOfLine = state.restOfLine,
            isIncludeFiltered = state.isIncludeFiltered,
            isEach = state.isEach,
            lastTag = state.lastTag,
            isAttrs = state.isAttrs,
            attrsNest = state.attrsNest.toMutableList(),
            inAttributeName = state.inAttributeName,
            attributeIsType = state.attributeIsType,
            attrValue = state.attrValue,
            indentOf = state.indentOf,
            indentToken = state.indentToken,
            mixinCallAfter = state.mixinCallAfter
        )
    }

    override fun token(stream: StringStream, state: PugState): String? {
        return pugNextToken(stream, state)
    }
}
