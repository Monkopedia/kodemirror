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

data class Jinja2State(
    var tokenize: (StringStream, Jinja2State) -> String? = ::jinja2TokenBase,
    var incomment: Boolean = false,
    // false, true, or a String like "}" or "%"
    var intag: Any = false,
    var operator: Boolean = false,
    var sign: Boolean = false,
    // false or a String like "'" or "\""
    var instring: Any = false,
    var inbraces: Int = 0,
    var inbrackets: Int = 0,
    var lineTag: Boolean = false
)

private val jinja2Keywords = Regex(
    "((" + listOf(
        "and", "as", "block", "endblock", "by", "cycle", "debug", "else",
        "elif", "extends", "filter", "endfilter", "firstof", "do", "for",
        "endfor", "if", "endif", "ifchanged", "endifchanged", "ifequal",
        "endifequal", "ifnotequal", "set", "raw", "endraw",
        "endifnotequal", "in", "include", "load", "not", "now", "or",
        "parsed", "regroup", "reversed", "spaceless", "call", "endcall",
        "macro", "endmacro", "endspaceless", "ssi", "templatetag",
        "openblock", "closeblock", "openvariable", "closevariable",
        "without", "context", "openbrace", "closebrace", "opencomment",
        "closecomment", "widthratio", "url", "with", "endwith",
        "get_current_language", "trans", "endtrans", "noop", "blocktrans",
        "endblocktrans", "get_available_languages",
        "get_current_language_bidi", "pluralize", "autoescape",
        "endautoescape"
    ).joinToString(")|(") + "))\\b"
)

private val jinja2Operator = Regex("^[+\\-*&%=<>!?|~^]")
private val jinja2Sign = Regex("^[:\\[\\(\\{]")
private val jinja2Atom = Regex("((true)|(false))\\b")
private val jinja2Number = Regex("^(\\d[+\\-\\*/])?\\d+(\\.\\d+)?")

private fun jinja2TokenBase(stream: StringStream, state: Jinja2State): String? {
    val ch = stream.peek()

    // Comment
    if (state.incomment) {
        if (!stream.skipTo("#}")) {
            stream.skipToEnd()
        } else {
            stream.eatWhile(Regex("[#}]"))
            state.incomment = false
        }
        return "comment"
    } else if (state.intag != false) {
        // After operator
        if (state.operator) {
            state.operator = false
            if (stream.match(jinja2Atom) != null) return "atom"
            if (stream.match(jinja2Number) != null) return "number"
        }
        // After sign
        if (state.sign) {
            state.sign = false
            if (stream.match(jinja2Atom) != null) return "atom"
            if (stream.match(jinja2Number) != null) return "number"
        }

        if (state.instring != false) {
            if (ch == (state.instring as String)) {
                state.instring = false
            }
            stream.next()
            return "string"
        } else if (ch == "'" || ch == "\"") {
            state.instring = ch
            stream.next()
            return "string"
        } else if (state.inbraces > 0 && ch == ")") {
            stream.next()
            state.inbraces--
        } else if (ch == "(") {
            stream.next()
            state.inbraces++
        } else if (state.inbrackets > 0 && ch == "]") {
            stream.next()
            state.inbrackets--
        } else if (ch == "[") {
            stream.next()
            state.inbrackets++
        } else if (state.lineTag == false && state.intag is String) {
            val tag = state.intag as String
            if (stream.match(tag + "}") ||
                (stream.eat("-") != null && stream.match(tag + "}"))
            ) {
                state.intag = false
                return "tag"
            } else if (stream.match(jinja2Operator) != null) {
                state.operator = true
                return "operator"
            } else if (stream.match(jinja2Sign) != null) {
                state.sign = true
            } else {
                if (stream.column() == 1 && state.lineTag &&
                    stream.match(jinja2Keywords) != null
                ) {
                    return "keyword"
                }
                if (stream.eat(" ") != null || stream.sol()) {
                    if (stream.match(jinja2Keywords) != null) return "keyword"
                    if (stream.match(jinja2Atom) != null) return "atom"
                    if (stream.match(jinja2Number) != null) return "number"
                    if (stream.sol()) stream.next()
                } else {
                    stream.next()
                }
            }
        } else if (state.intag == true) {
            // lineTag mode
            if (stream.match(jinja2Operator) != null) {
                state.operator = true
                return "operator"
            } else if (stream.match(jinja2Sign) != null) {
                state.sign = true
            } else {
                if (stream.column() == 1 && state.lineTag &&
                    stream.match(jinja2Keywords) != null
                ) {
                    return "keyword"
                }
                if (stream.eat(" ") != null || stream.sol()) {
                    if (stream.match(jinja2Keywords) != null) return "keyword"
                    if (stream.match(jinja2Atom) != null) return "atom"
                    if (stream.match(jinja2Number) != null) return "number"
                    if (stream.sol()) stream.next()
                } else {
                    stream.next()
                }
            }
        }
        return "variable"
    } else if (stream.eat("{") != null) {
        if (stream.eat("#") != null) {
            state.incomment = true
            if (!stream.skipTo("#}")) {
                stream.skipToEnd()
            } else {
                stream.eatWhile(Regex("[#}]"))
                state.incomment = false
            }
            return "comment"
        } else {
            val tagCh = stream.eat(Regex("[{%]"))
            if (tagCh != null) {
                state.intag = if (tagCh == "{") "}" else tagCh
                state.inbraces = 0
                state.inbrackets = 0
                stream.eat("-")
                return "tag"
            }
        }
    } else if (stream.eat("#") != null) {
        if (stream.peek() == "#") {
            stream.skipToEnd()
            return "comment"
        } else if (!stream.eol()) {
            state.intag = true
            state.lineTag = true
            state.inbraces = 0
            state.inbrackets = 0
            return "tag"
        }
    }
    stream.next()
    return null
}

/** Stream parser for Jinja2. */
val jinja2Legacy: StreamParser<Jinja2State> = object : StreamParser<Jinja2State> {
    override val name: String get() = "jinja2"
    override fun startState(indentUnit: Int) = Jinja2State()

    override fun copyState(state: Jinja2State) = state.copy()

    override fun token(stream: StringStream, state: Jinja2State): String? {
        val style = state.tokenize(stream, state)
        if (stream.eol() && state.lineTag &&
            state.instring == false &&
            state.inbraces == 0 && state.inbrackets == 0
        ) {
            state.intag = false
            state.lineTag = false
        }
        return style
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "block" to mapOf("open" to "{#", "close" to "#}"),
                "line" to "##"
            )
        )
}
