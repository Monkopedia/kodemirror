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

// Haxe mode

private data class HaxeKw(val type: String, val style: String)

private val haxeKeywords: Map<String, HaxeKw> = run {
    fun kw(type: String) = HaxeKw(type, "keyword")
    val a = kw("keyword a")
    val b = kw("keyword b")
    val c = kw("keyword c")
    val operator = kw("operator")
    val atom = HaxeKw("atom", "atom")
    val attribute = HaxeKw("attribute", "attribute")
    val typedef = kw("typedef")
    mapOf(
        "if" to a, "while" to a, "else" to b, "do" to b, "try" to b,
        "return" to c, "break" to c, "continue" to c, "new" to c, "throw" to c,
        "var" to kw("var"), "inline" to attribute, "static" to attribute,
        "using" to kw("import"),
        "public" to attribute, "private" to attribute,
        "cast" to kw("cast"), "import" to kw("import"), "macro" to kw("macro"),
        "function" to kw("function"), "catch" to kw("catch"),
        "untyped" to kw("untyped"), "callback" to kw("cb"),
        "for" to kw("for"), "switch" to kw("switch"),
        "case" to kw("case"), "default" to kw("default"),
        "in" to operator, "never" to kw("property_access"),
        "trace" to kw("trace"),
        "class" to typedef, "abstract" to typedef, "enum" to typedef,
        "interface" to typedef, "typedef" to typedef, "extends" to typedef,
        "implements" to typedef, "dynamic" to typedef,
        "true" to atom, "false" to atom, "null" to atom
    )
}

private val haxeIsOperatorChar = Regex("[+\\-*&%=<>!?|]")

data class HxmlState(
    var define: Boolean = false,
    var inString: Boolean = false
)

val hxml: StreamParser<HxmlState> = object : StreamParser<HxmlState> {
    override val name: String get() = "hxml"
    override fun startState(indentUnit: Int) = HxmlState()
    override fun copyState(state: HxmlState) = state.copy()

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun token(stream: StringStream, state: HxmlState): String? {
        val ch = stream.peek()

        if (ch == "#") {
            stream.skipToEnd()
            return "comment"
        }
        if (stream.sol() && ch == "-") {
            var style = "variable-2"
            stream.eat(Regex("-"))
            if (stream.peek() == "-") {
                stream.eat(Regex("-"))
                style = "keyword a"
            }
            if (stream.peek() == "D") {
                stream.eat(Regex("[D]"))
                style = "keyword c"
                state.define = true
            }
            stream.eatWhile(Regex("[A-Za-z]"))
            return style
        }

        val ch2 = stream.peek()
        if (!state.inString && ch2 == "'") {
            state.inString = true
            stream.next()
        }
        if (state.inString) {
            if (stream.skipTo("'")) {
                // found
            } else {
                stream.skipToEnd()
            }
            if (stream.peek() == "'") {
                stream.next()
                state.inString = false
            }
            return "string"
        }

        stream.next()
        return null
    }

    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "#"))
}

// The haxe mode is complex with combinator-based parsing.
// We simplify by just doing tokenization and basic indent tracking.
data class HaxeState(
    // 0=base, 1=string, 2=comment
    var tokenize: Int = 0,
    var stringQuote: String = "",
    var indented: Int = 0,
    var reAllowed: Boolean = true,
    var kwAllowed: Boolean = true,
    var lastType: String = "",
    var lastContent: String = "",
    var lexicalType: String = "block",
    var lexicalIndented: Int = 0,
    var lexicalColumn: Int = 0,
    var lexicalAlign: Boolean? = null,
    var lexicalInfo: String? = null,
    var lexicalPrevType: String? = null,
    var lexicalPrevIndented: Int = 0
)

val haxe: StreamParser<HaxeState> = object : StreamParser<HaxeState> {
    override val name: String get() = "haxe"

    override fun startState(indentUnit: Int): HaxeState {
        return HaxeState(
            lexicalIndented = -indentUnit
        )
    }

    override fun copyState(state: HaxeState) = state.copy()

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
    override fun token(stream: StringStream, state: HaxeState): String? {
        if (stream.sol()) {
            state.indented = stream.indentation()
        }
        if (stream.eatSpace()) return null

        var hxType = ""
        var hxContent = ""

        fun ret(tp: String, style: String? = null, cont: String? = null): String? {
            hxType = tp
            hxContent = cont ?: ""
            return style
        }

        val style: String? = when (state.tokenize) {
            1 -> {
                // string tokenizer
                var escaped = false
                var next: String?
                var done = false
                while (true) {
                    next = stream.next()
                    if (next == null) break
                    if (next == state.stringQuote && !escaped) {
                        done = true
                        break
                    }
                    escaped = !escaped && next == "\\"
                }
                if (done) state.tokenize = 0
                ret("string", "string")
            }
            2 -> {
                // block comment tokenizer
                var maybeEnd = false
                var c: String?
                while (true) {
                    c = stream.next()
                    if (c == null) break
                    if (c == "/" && maybeEnd) {
                        state.tokenize = 0
                        break
                    }
                    maybeEnd = c == "*"
                }
                ret("comment", "comment")
            }
            else -> {
                // base tokenizer
                val ch = stream.next() ?: return null
                if (ch == "\"" || ch == "'") {
                    state.stringQuote = ch
                    state.tokenize = 1
                    val r = ret("string", "string")
                    // Continue tokenizing the string
                    var escaped = false
                    var n: String?
                    while (true) {
                        n = stream.next()
                        if (n == null) break
                        if (n == ch && !escaped) break
                        escaped = !escaped && n == "\\"
                    }
                    if (n == ch) state.tokenize = 0
                    r
                } else if (Regex("[\\[\\]{}(),;:.]").containsMatchIn(ch)) {
                    ret(ch)
                } else if (ch == "0" && stream.eat(Regex("[xX]")) != null) {
                    stream.eatWhile(Regex("[\\da-fA-F]"))
                    ret("number", "number")
                } else if (Regex("\\d").containsMatchIn(ch) ||
                    (ch == "-" && stream.eat(Regex("\\d")) != null)
                ) {
                    stream.match(Regex("^\\d*(?:\\.\\d*(?!\\.)?)?(?:[eE][+\\-]?\\d+)?"))
                    ret("number", "number")
                } else if (state.reAllowed && ch == "~" && stream.eat(Regex("/")) != null) {
                    // regexp
                    var esc = false
                    var nx: String?
                    while (true) {
                        nx = stream.next()
                        if (nx == null) break
                        if (nx == "/" && !esc) break
                        esc = !esc && nx == "\\"
                    }
                    stream.eatWhile(Regex("[gimsu]"))
                    ret("regexp", "string.special")
                } else if (ch == "/") {
                    if (stream.eat("*") != null) {
                        state.tokenize = 2
                        var maybeEnd = false
                        var c2: String?
                        while (true) {
                            c2 = stream.next()
                            if (c2 == null) break
                            if (c2 == "/" && maybeEnd) {
                                state.tokenize = 0
                                break
                            }
                            maybeEnd = c2 == "*"
                        }
                        ret("comment", "comment")
                    } else if (stream.eat("/") != null) {
                        stream.skipToEnd()
                        ret("comment", "comment")
                    } else {
                        stream.eatWhile(haxeIsOperatorChar)
                        ret("operator", null, stream.current())
                    }
                } else if (ch == "#") {
                    stream.skipToEnd()
                    ret("conditional", "meta")
                } else if (ch == "@") {
                    stream.eat(Regex(":"))
                    stream.eatWhile(Regex("[\\w_]"))
                    ret("metadata", "meta")
                } else if (haxeIsOperatorChar.containsMatchIn(ch)) {
                    stream.eatWhile(haxeIsOperatorChar)
                    ret("operator", null, stream.current())
                } else {
                    if (Regex("[A-Z]").containsMatchIn(ch)) {
                        stream.eatWhile(Regex("[\\w_<>]"))
                        ret("type", "type", stream.current())
                    } else {
                        stream.eatWhile(Regex("[\\w_]"))
                        val word = stream.current()
                        val known = haxeKeywords[word]
                        if (known != null && state.kwAllowed) {
                            ret(known.type, known.style, word)
                        } else {
                            ret("variable", "variable", word)
                        }
                    }
                }
            }
        }

        if (hxType == "comment") return style

        state.reAllowed = hxType == "operator" || hxType == "keyword c" ||
            Regex("^[\\[{}(,;:]$").containsMatchIn(hxType)
        state.kwAllowed = hxType != "."
        state.lastType = hxType
        state.lastContent = hxContent

        return style
    }

    override fun indent(state: HaxeState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != 0) return 0
        return state.indented
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
