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

private val groovyKeywords = (
    "abstract as assert boolean break byte case catch char class const continue def default " +
        "do double else enum extends final finally float for goto if implements import in " +
        "instanceof int interface long native new package private protected public return " +
        "short static strictfp super switch synchronized threadsafe throw throws trait " +
        "transient try void volatile while"
    ).split(" ").toSet()

private val groovyBlockKeywords = (
    "catch class def do else enum finally for if interface switch trait try while"
    ).split(" ").toSet()

private val groovyStandaloneKeywords = setOf("return", "break", "continue")
private val groovyAtoms = setOf("null", "true", "false", "this")

data class GroovyContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: GroovyContext?
)

data class GroovyState(
    var tokenize: MutableList<(StringStream, GroovyState) -> String?>,
    var context: GroovyContext,
    var indented: Int = 0,
    var startOfLine: Boolean = true,
    var lastToken: String? = null,
    var curPunc: String? = null
)

private fun groovyExpectExpression(last: String?, newline: Boolean): Boolean {
    return last == null || last == "operator" || last == "->" ||
        (last.length == 1 && Regex("[.\\[{(,;:]").containsMatchIn(last)) ||
        last == "newstatement" || last == "keyword" || last == "proplabel" ||
        (last == "standalone" && !newline)
}

private fun groovyTokenBase(stream: StringStream, state: GroovyState): String? {
    val ch = stream.next() ?: return null
    if (ch == "\"" || ch == "'") {
        return groovyStartString(ch, stream, state)
    }
    if (Regex("[\\[\\]{}(),;:.]").containsMatchIn(ch)) {
        state.curPunc = ch
        return null
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        if (stream.eat(Regex("[eE]")) != null) {
            stream.eat(Regex("[+-]"))
            stream.eatWhile(Regex("\\d"))
        }
        return "number"
    }
    if (ch == "/") {
        if (stream.eat("*") != null) {
            state.tokenize.add(::groovyTokenComment)
            return groovyTokenComment(stream, state)
        }
        if (stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
        if (groovyExpectExpression(state.lastToken, false)) {
            return groovyStartString(ch, stream, state)
        }
    }
    if (ch == "-" && stream.eat(">") != null) {
        state.curPunc = "->"
        return null
    }
    if (Regex("[+\\-*&%=<>!?|/~]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[+\\-*&%=<>|~]"))
        return "operator"
    }
    stream.eatWhile(Regex("[\\w\$_]"))
    if (ch == "@") {
        stream.eatWhile(Regex("[\\w\$_.]"))
        return "meta"
    }
    if (state.lastToken == ".") return "property"
    if (stream.eat(":") != null) {
        state.curPunc = "proplabel"
        return "property"
    }
    val cur = stream.current()
    if (cur in groovyAtoms) return "atom"
    if (cur in groovyKeywords) {
        if (cur in groovyBlockKeywords) {
            state.curPunc = "newstatement"
        } else if (cur in groovyStandaloneKeywords) state.curPunc = "standalone"
        return "keyword"
    }
    return "variable"
}

private fun groovyStartString(quote: String, stream: StringStream, state: GroovyState): String {
    var tripleQuoted = false
    if (quote != "/" && stream.eat(quote) != null) {
        if (stream.eat(quote) != null) {
            tripleQuoted = true
        } else {
            return "string"
        }
    }
    val tokenFn = groovyMakeStringTokenizer(quote, tripleQuoted, state)
    state.tokenize.add(tokenFn)
    return tokenFn(stream, state)
}

private fun groovyMakeStringTokenizer(
    quote: String,
    tripleQuoted: Boolean,
    outerState: GroovyState
): (StringStream, GroovyState) -> String {
    val fn: (StringStream, GroovyState) -> String = fn@{ stream, state ->
        var escaped = false
        var end = !tripleQuoted
        while (true) {
            val next = stream.next() ?: break
            if (next == quote && !escaped) {
                if (!tripleQuoted) {
                    end = true
                    break
                }
                if (stream.match(quote + quote)) {
                    end = true
                    break
                }
            }
            if (quote == "\"" && next == "$" && !escaped) {
                if (stream.eat("{") != null) {
                    state.tokenize.add(groovyTokenBaseUntilBrace())
                    return@fn "string"
                } else if (stream.match(Regex("^\\w"), consume = false) != null) {
                    state.tokenize.add(groovyVariableDerefTokenizer())
                    return@fn "string"
                }
            }
            escaped = !escaped && next == "\\"
        }
        if (end) state.tokenize.removeLast()
        "string"
    }
    return fn
}

private fun groovyTokenBaseUntilBrace(): (StringStream, GroovyState) -> String? {
    var depth = 1
    val fn: (StringStream, GroovyState) -> String? = fn@{ stream, state ->
        if (stream.peek() == "}") {
            depth--
            if (depth == 0) {
                state.tokenize.removeLast()
                return@fn state.tokenize.last()(stream, state)
            }
        } else if (stream.peek() == "{") {
            depth++
        }
        return@fn groovyTokenBase(stream, state)
    }
    return fn
}

private fun groovyVariableDerefTokenizer(): (StringStream, GroovyState) -> String? {
    val fn: (StringStream, GroovyState) -> String? = fn@{ stream, state ->
        val next = stream.match(Regex("^(\\.|[\\w\$_]+)"))
        if (next == null || (
                if (next.value[0] == '.') {
                    stream.match(Regex("^[\\w\$_]"), consume = false)
                } else {
                    stream.match(Regex("^\\."), consume = false)
                }
                ) == null
        ) {
            state.tokenize.removeLast()
        }
        if (next == null) return@fn state.tokenize.last()(stream, state)
        return@fn if (next.value[0] == '.') null else "variable"
    }
    return fn
}

private fun groovyTokenComment(stream: StringStream, state: GroovyState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "/" && maybeEnd) {
            state.tokenize.removeLast()
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun groovyPushContext(state: GroovyState, col: Int, type: String) {
    state.context = GroovyContext(
        indented = state.indented,
        column = col,
        type = type,
        align = null,
        prev = state.context
    )
}

private fun groovyPopContext(state: GroovyState): GroovyContext {
    val t = state.context.type
    if (t == ")" || t == "]" || t == "}") {
        state.indented = state.context.indented
    }
    state.context = state.context.prev ?: state.context
    return state.context
}

/** Stream parser for Groovy. */
val groovy: StreamParser<GroovyState> = object : StreamParser<GroovyState> {
    override val name: String get() = "groovy"

    override fun startState(indentUnit: Int) = GroovyState(
        tokenize = mutableListOf(::groovyTokenBase),
        context = GroovyContext(
            indented = -indentUnit,
            column = 0,
            type = "top",
            align = false,
            prev = null
        )
    )

    override fun copyState(state: GroovyState) = state.copy(
        tokenize = state.tokenize.toMutableList(),
        context = state.context.copy()
    )

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun token(stream: StringStream, state: GroovyState): String? {
        var ctx = state.context
        if (stream.sol()) {
            if (ctx.align == null) ctx.align = false
            state.indented = stream.indentation()
            state.startOfLine = true
            if (ctx.type == "statement" &&
                !groovyExpectExpression(state.lastToken, true)
            ) {
                groovyPopContext(state)
                ctx = state.context
            }
        }
        if (stream.eatSpace()) return null
        state.curPunc = null
        val style = state.tokenize.last()(stream, state)
        if (style == "comment") return style
        if (ctx.align == null) ctx.align = true

        val curPunc = state.curPunc
        if ((curPunc == ";" || curPunc == ":") && ctx.type == "statement") {
            groovyPopContext(state)
        } else if (
            curPunc == "->" && ctx.type == "statement" &&
            ctx.prev?.type == "}"
        ) {
            groovyPopContext(state)
            state.context.align = false
        } else if (curPunc == "{") {
            groovyPushContext(state, stream.column(), "}")
        } else if (curPunc == "[") {
            groovyPushContext(state, stream.column(), "]")
        } else if (curPunc == "(") {
            groovyPushContext(state, stream.column(), ")")
        } else if (curPunc == "}") {
            while (state.context.type == "statement") groovyPopContext(state)
            if (state.context.type == "}") groovyPopContext(state)
            while (state.context.type == "statement") groovyPopContext(state)
        } else if (curPunc == state.context.type) {
            groovyPopContext(state)
        } else if (
            state.context.type == "}" || state.context.type == "top" ||
            (state.context.type == "statement" && curPunc == "newstatement")
        ) {
            groovyPushContext(state, stream.column(), "statement")
        }
        state.startOfLine = false
        state.lastToken = curPunc ?: style
        return style
    }

    override fun indent(state: GroovyState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize.last() !== ::groovyTokenBase) return null
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        var ctx = state.context
        if (ctx.type == "statement" &&
            !groovyExpectExpression(state.lastToken, true)
        ) {
            ctx = ctx.prev ?: ctx
        }
        val closing = firstChar == ctx.type
        if (ctx.type == "statement") {
            return ctx.indented + if (firstChar == "{") 0 else context.unit
        }
        if (ctx.align == true) return ctx.column + if (closing) 0 else 1
        return ctx.indented + if (closing) 0 else context.unit
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s*[{}]$"),
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
