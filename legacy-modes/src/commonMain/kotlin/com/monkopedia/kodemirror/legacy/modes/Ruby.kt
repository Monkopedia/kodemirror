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

private val rubyKeywordList = listOf(
    "alias", "and", "BEGIN", "begin", "break", "case", "class", "def", "defined?", "do", "else",
    "elsif", "END", "end", "ensure", "false", "for", "if", "in", "module", "next", "not", "or",
    "redo", "rescue", "retry", "return", "self", "super", "then", "true", "undef", "unless",
    "until", "when", "while", "yield", "nil", "raise", "throw", "catch", "fail", "loop", "callcc",
    "caller", "lambda", "proc", "public", "protected", "private", "require", "load",
    "require_relative", "extend", "autoload", "__END__", "__FILE__", "__LINE__", "__dir__"
)
private val rubyKeywords = rubyKeywordList.toSet()
private val rubyIndentWords = setOf(
    "def", "class", "case", "for", "while", "until", "module", "catch", "loop", "proc", "begin"
)
private val rubyDedentWords = setOf("end", "until")
private val rubyOpening = mapOf("[" to "]", "{" to "}", "(" to ")")
private val rubyClosing = mapOf("]" to "[", "}" to "{", ")" to "(")

data class RubyContext(
    val type: String,
    val indented: Int,
    val prev: RubyContext?
)

data class RubyState(
    var tokenize: MutableList<(StringStream, RubyState) -> String?> = mutableListOf(),
    var indented: Int = 0,
    var context: RubyContext = RubyContext("top", 0, null),
    var continuedLine: Boolean = false,
    var lastTok: String? = null,
    var varList: Boolean = false,
    var curPunc: String? = null
)

private fun rubyChain(
    newtok: (StringStream, RubyState) -> String?,
    stream: StringStream,
    state: RubyState
): String? {
    state.tokenize.add(newtok)
    return newtok(stream, state)
}

private fun rubyRegexpAhead(stream: StringStream): Boolean {
    val start = stream.pos
    var depth = 0
    var found = false
    var escaped = false
    while (true) {
        val next = stream.next() ?: break
        if (!escaped) {
            if ("[{(".indexOf(next) > -1) {
                depth++
            } else if ("]})".indexOf(next) > -1) {
                depth--
                if (depth < 0) break
            } else if (next == "/" && depth == 0) {
                found = true
                break
            }
            escaped = next == "\\"
        } else {
            escaped = false
        }
    }
    stream.backUp(stream.pos - start)
    return found
}

private fun rubyReadBlockComment(stream: StringStream, state: RubyState): String? {
    if (stream.sol() && stream.match("=end") != null && stream.eol()) {
        state.tokenize.removeLastOrNull()
    }
    stream.skipToEnd()
    return "comment"
}

private fun rubyTokenBaseUntilBrace(depth: Int = 1): (StringStream, RubyState) -> String? {
    return fn@{ stream, state ->
        if (stream.peek() == "}") {
            if (depth == 1) {
                state.tokenize.removeLastOrNull()
                return@fn state.tokenize.lastOrNull()?.invoke(stream, state)
            } else {
                state.tokenize[state.tokenize.size - 1] = rubyTokenBaseUntilBrace(depth - 1)
            }
        } else if (stream.peek() == "{") {
            state.tokenize[state.tokenize.size - 1] = rubyTokenBaseUntilBrace(depth + 1)
        }
        rubyTokenBase(stream, state)
    }
}

private fun rubyTokenBaseOnce(): (StringStream, RubyState) -> String? {
    var alreadyCalled = false
    return fn@{ stream, state ->
        if (alreadyCalled) {
            state.tokenize.removeLastOrNull()
            return@fn state.tokenize.lastOrNull()?.invoke(stream, state)
        }
        alreadyCalled = true
        rubyTokenBase(stream, state)
    }
}

private fun rubyReadQuoted(
    quote: String,
    style: String,
    embed: Boolean,
    unescaped: Boolean = false
): (StringStream, RubyState) -> String? {
    return fn@{ stream, state ->
        var escaped = false
        var ch: String?
        while (true) {
            ch = stream.next() ?: break
            if (ch == quote && (unescaped || !escaped)) {
                state.tokenize.removeLastOrNull()
                break
            }
            if (embed && ch == "#" && !escaped) {
                if (stream.eat("{") != null) {
                    state.tokenize.add(rubyTokenBaseUntilBrace())
                    break
                } else if (stream.peek()?.let { Regex("[@\$]").containsMatchIn(it) } == true) {
                    state.tokenize.add(rubyTokenBaseOnce())
                    break
                }
            }
            escaped = !escaped && ch == "\\"
        }
        style
    }
}

private fun rubyReadHereDoc(
    phrase: String,
    mayIndent: String
): (StringStream, RubyState) -> String? = { stream, state ->
    if (mayIndent.isNotEmpty()) stream.eatSpace()
    if (stream.match(phrase) != null) {
        state.tokenize.removeLastOrNull()
    } else {
        stream.skipToEnd()
    }
    "string"
}

private fun rubyTokenBase(stream: StringStream, state: RubyState): String? {
    state.curPunc = null
    if (stream.sol() && stream.match("=begin") != null && stream.eol()) {
        state.tokenize.add(::rubyReadBlockComment)
        return "comment"
    }
    if (stream.eatSpace()) return null
    val ch = stream.next() ?: return null
    val m: MatchResult?
    if (ch == "`" || ch == "'" || ch == "\"") {
        return rubyChain(rubyReadQuoted(ch, "string", ch == "\"" || ch == "`"), stream, state)
    } else if (ch == "/") {
        return if (rubyRegexpAhead(stream)) {
            rubyChain(rubyReadQuoted(ch, "string.special", true), stream, state)
        } else {
            "operator"
        }
    } else if (ch == "%") {
        var style = "string"
        var embed = true
        if (stream.eat("s") != null) {
            style = "atom"
        } else if (stream.eat(Regex("[WQ]")) != null) {
            style = "string"
        } else if (stream.eat(Regex("[r]")) != null) {
            style = "string.special"
        } else if (stream.eat(Regex("[wxq]")) != null) {
            style = "string"
            embed = false
        }
        val delim = stream.eat(Regex("[^\\w\\s=]")) ?: return "operator"
        val end = rubyOpening[delim] ?: delim
        return rubyChain(
            rubyReadQuoted(end, style, embed, rubyOpening.containsKey(delim)),
            stream,
            state
        )
    } else if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    } else if (ch == "<") {
        m = stream.match(Regex("^<<([-~])[\\\\`\"']?([a-zA-Z_?]\\w*)[\\\\`\"']?(?:;|\$)"))
        if (m != null) {
            return rubyChain(rubyReadHereDoc(m.groupValues[2], m.groupValues[1]), stream, state)
        }
    } else if (ch == "0") {
        if (stream.eat("x") != null) {
            stream.eatWhile(Regex("[\\da-fA-F]"))
        } else if (stream.eat("b") != null) {
            stream.eatWhile(Regex("[01]"))
        } else {
            stream.eatWhile(Regex("[0-7]"))
        }
        return "number"
    } else if (Regex("\\d").containsMatchIn(ch)) {
        stream.match(Regex("^[\\d_]*(?:\\.[\\d_]+)?(?:[eE][+\\-]?[\\d_]+)?"))
        return "number"
    } else if (ch == "?") {
        while (stream.match(Regex("^\\\\[CM]-")) != null) {}
        if (stream.eat("\\") != null) {
            stream.eatWhile(Regex("\\w"))
        } else {
            stream.next()
        }
        return "string"
    } else if (ch == ":") {
        if (stream.eat("'") != null) {
            return rubyChain(
                rubyReadQuoted("'", "atom", false),
                stream,
                state
            )
        }
        if (stream.eat("\"") != null) {
            return rubyChain(
                rubyReadQuoted("\"", "atom", true),
                stream,
                state
            )
        }
        if (stream.eat(Regex("[<>]")) != null) {
            stream.eat(Regex("[<>]"))
            return "atom"
        }
        if (stream.eat(Regex("[+\\-*/&|\\\\:!]")) != null) return "atom"
        if (stream.eat(Regex("[a-zA-Z\$@_\\xa1-\\uffff]")) != null) {
            stream.eatWhile(Regex("[\\w\$\\xa1-\\uffff]"))
            stream.eat(Regex("[?!=]"))
            return "atom"
        }
        return "operator"
    } else if (ch == "@" && stream.match(Regex("^@?[a-zA-Z_\\xa1-\\uffff]")) != null) {
        stream.eat("@")
        stream.eatWhile(Regex("[\\w\\xa1-\\uffff]"))
        return "propertyName"
    } else if (ch == "\$") {
        if (stream.eat(Regex("[a-zA-Z_]")) != null) {
            stream.eatWhile(Regex("[\\w]"))
        } else if (stream.eat(Regex("\\d")) != null) {
            stream.eat(Regex("\\d"))
        } else {
            stream.next()
        }
        return "variableName.special"
    } else if (Regex("[a-zA-Z_\\xa1-\\uffff]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w\\xa1-\\uffff]"))
        stream.eat(Regex("[?!]"))
        if (stream.eat(":") != null) return "atom"
        return "variable"
    } else if (ch == "|" && (state.varList || state.lastTok == "{" || state.lastTok == "do")) {
        state.curPunc = "|"
        return null
    } else if (Regex("[()\\[\\]{};\\\\]").containsMatchIn(ch)) {
        state.curPunc = ch
        return null
    } else if (ch == "-" && stream.eat(">") != null) {
        return "operator"
    } else if (Regex("[=+\\-/*:.^%<>~|]").containsMatchIn(ch)) {
        val more = stream.eatWhile(Regex("[=+\\-/*:.^%<>~|]"))
        if (ch == "." && more == null) state.curPunc = "."
        return "operator"
    }
    return null
}

val ruby: StreamParser<RubyState> = object : StreamParser<RubyState> {
    override val name: String get() = "ruby"

    override fun startState(indentUnit: Int) = RubyState(
        tokenize = mutableListOf(::rubyTokenBase),
        context = RubyContext("top", -indentUnit, null)
    )

    override fun copyState(state: RubyState) = state.copy(
        tokenize = state.tokenize.toMutableList(),
        context = state.context.copy()
    )

    override fun token(stream: StringStream, state: RubyState): String? {
        state.curPunc = null
        if (stream.sol()) state.indented = stream.indentation()
        var style = state.tokenize.lastOrNull()?.invoke(stream, state)
        var kwtype: String? = null
        var thisTok = state.curPunc

        if (style == "variable") {
            val word = stream.current()
            style = when {
                state.lastTok == "." -> "property"
                word in rubyKeywords -> "keyword"
                Regex("^[A-Z]").containsMatchIn(word) -> "tag"
                state.lastTok == "def" || state.lastTok == "class" || state.varList -> "def"
                else -> "variable"
            }
            if (style == "keyword") {
                thisTok = word
                when {
                    word in rubyIndentWords -> kwtype = "indent"
                    word in rubyDedentWords -> kwtype = "dedent"
                    (word == "if" || word == "unless") && stream.column() == stream.indentation() ->
                        kwtype = "indent"
                    word == "do" && state.context.indented < state.indented ->
                        kwtype = "indent"
                }
            }
        }
        if (state.curPunc != null || (style != null && style != "comment")) {
            state.lastTok = thisTok
        }
        if (state.curPunc == "|") state.varList = !state.varList

        val curPunc = state.curPunc
        if (kwtype == "indent" || curPunc != null && Regex("[({\\[]").containsMatchIn(curPunc)) {
            state.context = RubyContext(curPunc ?: style ?: "", state.indented, state.context)
        } else if (
            (kwtype == "dedent" || curPunc != null && Regex("[)\\]}]").containsMatchIn(curPunc)) &&
            state.context.prev != null
        ) {
            state.context = state.context.prev!!
        }

        if (stream.eol()) {
            state.continuedLine = (curPunc == "\\" || style == "operator")
        }
        return style
    }

    override fun indent(state: RubyState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize.lastOrNull() != ::rubyTokenBase) return null
        val firstChar = if (textAfter.isNotEmpty()) textAfter[0].toString() else ""
        val ct = state.context
        val closed = ct.type == rubyClosing[firstChar] ||
            ct.type == "keyword" &&
            Regex("^(?:end|until|else|elsif|when|rescue)\\b").containsMatchIn(textAfter)
        return ct.indented + (if (closed) 0 else context.unit) +
            (if (state.continuedLine) context.unit else 0)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s*(?:end|rescue|elsif|else|})$"),
            "commentTokens" to mapOf("line" to "#"),
            "autocomplete" to rubyKeywordList
        )
}
