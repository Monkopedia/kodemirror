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

private data class JsKw(val type: String, val style: String)

private val jsKeywords: Map<String, JsKw> = run {
    fun kw(type: String) = JsKw(type, "keyword")
    val a = kw("keyword a")
    val b = kw("keyword b")
    val c = kw("keyword c")
    val d = kw("keyword d")
    val operator = kw("operator")
    val atom = JsKw("atom", "atom")
    mapOf(
        "if" to kw("if"), "while" to a, "with" to a, "else" to b, "do" to b,
        "try" to b, "finally" to b,
        "return" to d, "break" to d, "continue" to d, "new" to kw("new"),
        "delete" to c, "void" to c, "throw" to c,
        "debugger" to kw("debugger"), "var" to kw("var"), "const" to kw("var"),
        "let" to kw("var"),
        "function" to kw("function"), "catch" to kw("catch"),
        "for" to kw("for"), "switch" to kw("switch"), "case" to kw("case"),
        "default" to kw("default"),
        "in" to operator, "typeof" to operator, "instanceof" to operator,
        "true" to atom, "false" to atom, "null" to atom, "undefined" to atom,
        "NaN" to atom, "Infinity" to atom,
        "this" to kw("this"), "class" to kw("class"), "super" to kw("atom"),
        "yield" to c, "export" to kw("export"), "import" to kw("import"),
        "extends" to c, "await" to c
    )
}

private val jsIsOperatorChar = Regex("[+\\-*&%=<>!?|~^@]")
private const val JS_BRACKETS = "([{}])"
private val jsStringDelimiter = Regex("[\"'/`]")
private val jsTsReturnType =
    Regex(":\\s*(?:\\w+(?:<[^>]*>|\\[\\])?|\\{[^}]*\\})\\s*\$")
private val jsElseAhead = Regex("^\\s*else\\b")
private val jsNoPopAfter = Regex("^[,\\.=+\\-*:?\\[\\(]")
private val jsCaseAhead = Regex("^(?:case|default)\\b")
private val jsIsJsonldKeyword =
    Regex("^@(context|id|value|language|type|container|list|set|reverse|index|base|vocab|graph)\"")

data class JSLexical(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean? = null,
    val prev: JSLexical? = null,
    val info: String? = null
)

data class JavaScriptConfig(
    val name: String = "javascript",
    val json: Boolean = false,
    val jsonld: Boolean = false,
    val typescript: Boolean = false,
    val statementIndent: Int? = null,
    val wordCharacters: Regex = Regex("[\\w\$\\u00a1-\\uffff]"),
    /**
     * Whether the body of a `switch` is indented by two units, with `case`
     * and `default` labels at one. Upstream's `doubleIndentSwitch`, which
     * defaults to on.
     */
    val doubleIndentSwitch: Boolean = true
)

class JavaScriptState(
    // 0=base, 1=string, 2=comment, 3=quasi
    var tokenize: Int = 0,
    var stringQuote: String = "",
    var lastType: String = "sof",
    var indented: Int = 0,
    var lexical: JSLexical = JSLexical(-2, 0, "block"),
    var fatArrowAt: Int? = null
) {
    /**
     * The continuation stack (`state.cc` upstream): the grammar's pending
     * combinators, top of stack last. Internal because the combinator type is
     * an implementation detail of the mode.
     */
    internal var cc: MutableList<JsComb> = mutableListOf()

    /** Names bound in the innermost variable scope. */
    internal var localVars: JsVar? = null

    /** The enclosing variable scopes. */
    internal var context: JsContext? = null
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun mkJavaScript(config: JavaScriptConfig): StreamParser<JavaScriptState> {
    val jsonMode = config.json || config.jsonld
    val jsonldMode = config.jsonld
    val isTS = config.typescript
    val wordRE = config.wordCharacters
    val statementIndent = config.statementIndent

    // scratch variables for token type communication
    var type = ""
    var content = ""

    fun ret(tp: String, style: String? = null, cont: String? = null): String? {
        type = tp
        content = cont ?: ""
        return style
    }

    fun readRegexp(stream: StringStream) {
        var escaped = false
        var inSet = false
        while (true) {
            val next = stream.next() ?: break
            if (!escaped) {
                if (next == "/" && !inSet) return
                if (next == "[") {
                    inSet = true
                } else if (inSet && next == "]") {
                    inSet = false
                }
            }
            escaped = !escaped && next == "\\"
        }
    }

    fun expressionAllowed(stream: StringStream, state: JavaScriptState, backUp: Int): Boolean =
        state.tokenize == 0 &&
            Regex(
                "^(?:operator|sof|keyword [bcd]|case|new|export|default|spread|[\\[{}(,;:]|=>)\$"
            ).containsMatchIn(state.lastType) ||
            (
                state.lastType == "quasi" &&
                    Regex("\\{\\s*\$").containsMatchIn(
                        stream.string.substring(0, stream.pos - backUp)
                    )
                )

    fun tokenBase(stream: StringStream, state: JavaScriptState): String? {
        val ch = stream.next() ?: return null

        if (ch == "\"" || ch == "'") {
            state.stringQuote = ch
            state.tokenize = 1
            // inline string tokenizing
            if (jsonldMode && stream.peek() == "@" && stream.match(jsIsJsonldKeyword) != null) {
                state.tokenize = 0
                return ret("jsonld-keyword", "meta")
            }
            var escaped = false
            while (true) {
                val next = stream.next() ?: break
                if (next == ch && !escaped) break
                escaped = !escaped && next == "\\"
            }
            state.tokenize = 0
            return ret("string", "string")
        } else if (ch == "." && stream.match(Regex("^\\d[\\d_]*(?:[eE][+\\-]?[\\d_]+)?")) != null
        ) {
            return ret("number", "number")
        } else if (ch == "." && stream.match("..")) {
            return ret("spread", "meta")
        } else if (Regex("[\\[\\]{}(),;:.]").containsMatchIn(ch)) {
            return ret(ch)
        } else if (ch == "=" && stream.eat(">") != null) {
            return ret("=>", "operator")
        } else if (ch == "0" &&
            stream.match(Regex("^(?:x[\\dA-Fa-f_]+|o[0-7_]+|b[01_]+)n?")) != null
        ) {
            return ret("number", "number")
        } else if (Regex("\\d").containsMatchIn(ch)) {
            stream.match(Regex("^[\\d_]*(?:n|(?:\\.[\\d_]*)?(?:[eE][+\\-]?[\\d_]+)?)?"))
            return ret("number", "number")
        } else if (ch == "/") {
            if (stream.eat("*") != null) {
                state.tokenize = 2
                // inline block comment
                var maybeEnd = false
                while (true) {
                    val c = stream.next() ?: break
                    if (c == "/" && maybeEnd) {
                        state.tokenize = 0
                        break
                    }
                    maybeEnd = c == "*"
                }
                return ret("comment", "comment")
            } else if (stream.eat("/") != null) {
                stream.skipToEnd()
                return ret("comment", "comment")
            } else if (expressionAllowed(stream, state, 1)) {
                readRegexp(stream)
                stream.match(Regex("^\\b(([gimyus])(?![gimyus]*\\2))+\\b"))
                return ret("regexp", "string.special")
            } else {
                stream.eat("=")
                return ret("operator", "operator", stream.current())
            }
        } else if (ch == "`") {
            state.tokenize = 3
            // inline quasi tokenizer
            var escaped = false
            while (true) {
                val next = stream.next() ?: break
                if (!escaped && (next == "`" || (next == "$" && stream.eat("{") != null))) {
                    state.tokenize = 0
                    break
                }
                escaped = !escaped && next == "\\"
            }
            return ret("quasi", "string.special", stream.current())
        } else if (ch == "#" && stream.peek() == "!") {
            stream.skipToEnd()
            return ret("meta", "meta")
        } else if (ch == "#" && stream.eatWhile(wordRE)) {
            return ret("variable", "property")
        } else if (ch == "<" &&
            stream.match("!--") ||
            (
                ch == "-" &&
                    stream.match("->") &&
                    !Regex("\\S").containsMatchIn(
                        stream.string.substring(0, stream.start)
                    )
                )
        ) {
            stream.skipToEnd()
            return ret("comment", "comment")
        } else if (jsIsOperatorChar.containsMatchIn(ch)) {
            if (ch != ">" ||
                state.lexical.type != ">"
            ) {
                if (stream.eat("=") != null) {
                    if (ch == "!" || ch == "=") stream.eat("=")
                } else if (Regex("[<>*+\\-|&?]").containsMatchIn(ch)) {
                    stream.eat(ch)
                    if (ch == ">") stream.eat(ch)
                }
            }
            if (ch == "?" && stream.eat(".") != null) return ret(".")
            return ret("operator", "operator", stream.current())
        } else if (wordRE.containsMatchIn(ch)) {
            stream.eatWhile(wordRE)
            val word = stream.current()
            if (state.lastType != ".") {
                val kw = jsKeywords[word]
                if (kw != null) {
                    return ret(kw.type, kw.style, word)
                }
                if (word == "async" &&
                    stream.match(
                        Regex("^(\\s|\\/\\*([^*]|\\*(?!\\/))*?\\*\\/)*[\\[(\\w]"),
                        false
                    ) != null
                ) {
                    return ret("async", "keyword", word)
                }
            }
            return ret("variable", "variable", word)
        }
        return null
    }

    fun isContinuedStatement(state: JavaScriptState, textAfter: String): Boolean {
        // Upstream reads `textAfter.charAt(0)`, which yields `""` -- not an
        // error -- for an empty line, and neither test matches `""`. Reading
        // `textAfter[0]` instead threw, so mirror the totality with the same
        // idiom `indent` already uses for its own first character.
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        return state.lastType == "operator" ||
            state.lastType == "," ||
            jsIsOperatorChar.containsMatchIn(firstChar) ||
            Regex("[,.]").containsMatchIn(firstChar)
    }

    // A crude lookahead trick to notice that we are parsing the argument
    // patterns of a fat-arrow function before hitting the arrow token. It only
    // works if the arrow is on the same line as the arguments with no comments
    // in between; the fallback is to only notice at the arrow itself.
    @Suppress(
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "ReturnCount",
        "LoopWithTooManyJumpStatements"
    )
    fun findFatArrow(stream: StringStream, state: JavaScriptState) {
        if (state.fatArrowAt != null) state.fatArrowAt = null
        var arrow = stream.string.indexOf("=>", stream.start)
        if (arrow < 0) return

        if (isTS) {
            // Try to skip TypeScript return type declarations after the arguments.
            // Upstream assigns the match index of the *sliced* string straight
            // to `arrow`, which is only the same as an absolute offset when
            // `stream.start` is 0 -- which it is on the `sol()` call path. Kept
            // as-is so the port answers what upstream answers.
            val m = jsTsReturnType.find(stream.string.substring(stream.start, arrow))
            if (m != null) arrow = m.range.first
        }

        var depth = 0
        var sawSomething = false
        var pos = arrow - 1
        while (pos >= 0) {
            val ch = stream.string[pos].toString()
            val bracket = JS_BRACKETS.indexOf(ch)
            if (bracket in 0..2) {
                if (depth == 0) {
                    pos++
                    break
                }
                depth--
                if (depth == 0) {
                    if (ch == "(") sawSomething = true
                    break
                }
            } else if (bracket in 3..5) {
                depth++
            } else if (wordRE.containsMatchIn(ch)) {
                sawSomething = true
            } else if (jsStringDelimiter.containsMatchIn(ch)) {
                while (true) {
                    if (pos == 0) return
                    val next = stream.string[pos - 1].toString()
                    val beforeNext = if (pos >= 2) stream.string[pos - 2].toString() else ""
                    if (next == ch && beforeNext != "\\") {
                        pos--
                        break
                    }
                    pos--
                }
            } else if (sawSomething && depth == 0) {
                pos++
                break
            }
            pos--
        }
        if (sawSomething && depth == 0) state.fatArrowAt = pos
    }

    val grammar = JsGrammar(isTS, jsonMode, jsonldMode) { s, st -> findFatArrow(s, st) }

    return object : StreamParser<JavaScriptState> {
        override val name: String get() = config.name

        override fun startState(indentUnit: Int): JavaScriptState = JavaScriptState(
            lexical = JSLexical(-indentUnit, 0, "block", false)
        )

        override fun copyState(state: JavaScriptState): JavaScriptState = JavaScriptState(
            tokenize = state.tokenize,
            stringQuote = state.stringQuote,
            lastType = state.lastType,
            indented = state.indented,
            // The lexical chain is shared by reference, exactly as upstream's
            // default `copyState` shares it: `align` is filled in once per
            // scope and both copies must observe the same answer.
            lexical = state.lexical,
            fatArrowAt = state.fatArrowAt
        ).also {
            // The continuation stack is copied (upstream slices the array);
            // the scope lists are immutable and shared.
            it.cc = state.cc.toMutableList()
            it.localVars = state.localVars
            it.context = state.context
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
        override fun token(stream: StringStream, state: JavaScriptState): String? {
            if (stream.sol()) {
                if (state.lexical.align == null) state.lexical.align = false
                state.indented = stream.indentation()
                findFatArrow(stream, state)
            }
            if (state.tokenize != 2 && stream.eatSpace()) return null

            val style: String?
            when (state.tokenize) {
                1 -> {
                    // string
                    if (jsonldMode &&
                        stream.peek() == "@" &&
                        stream.match(jsIsJsonldKeyword) != null
                    ) {
                        state.tokenize = 0
                        style = ret("jsonld-keyword", "meta")
                    } else {
                        var escaped = false
                        while (true) {
                            val next = stream.next() ?: break
                            if (next == state.stringQuote && !escaped) break
                            escaped = !escaped && next == "\\"
                        }
                        state.tokenize = 0
                        style = ret("string", "string")
                    }
                }
                2 -> {
                    // comment
                    var maybeEnd = false
                    while (true) {
                        val c = stream.next() ?: break
                        if (c == "/" && maybeEnd) {
                            state.tokenize = 0
                            break
                        }
                        maybeEnd = c == "*"
                    }
                    style = ret("comment", "comment")
                }
                3 -> {
                    // quasi/template literal
                    var escaped = false
                    while (true) {
                        val next = stream.next() ?: break
                        if (!escaped &&
                            (next == "`" || (next == "$" && stream.eat("{") != null))
                        ) {
                            state.tokenize = 0
                            break
                        }
                        escaped = !escaped && next == "\\"
                    }
                    style = ret("quasi", "string.special", stream.current())
                }
                else -> {
                    style = tokenBase(stream, state)
                }
            }

            if (type == "comment") return style

            state.lastType = if (type == "operator" &&
                (content == "++" || content == "--")
            ) {
                "incdec"
            } else {
                type
            }

            return grammar.parseJS(state, style, type, content, stream)
        }

        @Suppress("CyclomaticComplexMethod", "ReturnCount", "LoopWithTooManyJumpStatements")
        override fun indent(
            state: JavaScriptState,
            textAfter: String,
            context: IndentContext
        ): Int? {
            if (state.tokenize == 2 || state.tokenize == JS_TOKENIZE_QUASI) return null
            if (state.tokenize != 0) return 0
            val firstChar = textAfter.firstOrNull()?.toString() ?: ""
            var lexical = state.lexical

            // Kludge to prevent 'maybeelse' from blocking lexical scope pops.
            if (!jsElseAhead.containsMatchIn(textAfter)) {
                for (i in state.cc.indices.reversed()) {
                    val c = state.cc[i]
                    if (c === grammar.poplex) {
                        lexical = lexical.prev ?: break
                    } else if (c !== grammar.maybeelse && c !== grammar.popcontext) {
                        break
                    }
                }
            }

            // Walk up stat/form lexical scopes. The `maybeoperator*` conjunct
            // is what keeps a `stat` scope alive for a genuinely continued
            // expression; without it every `stat` and `form` scope is popped.
            while (lexical.type == "stat" || lexical.type == "form") {
                val top = state.cc.lastOrNull()
                val topIsOperator = top === grammar.maybeoperatorComma ||
                    top === grammar.maybeoperatorNoComma
                val continues = firstChar == "}" ||
                    (topIsOperator && !jsNoPopAfter.containsMatchIn(textAfter))
                if (!continues) break
                lexical = lexical.prev ?: break
            }

            if (statementIndent != null &&
                lexical.type == ")" &&
                lexical.prev?.type == "stat"
            ) {
                lexical = lexical.prev
            }

            val closing = firstChar == lexical.type

            return when {
                lexical.type == "vardef" ->
                    lexical.indented + (
                        if (state.lastType == "operator" || state.lastType == ",") {
                            (lexical.info?.length ?: 0) + 1
                        } else {
                            0
                        }
                        )
                lexical.type == "form" && firstChar == "{" -> lexical.indented
                lexical.type == "form" -> lexical.indented + context.unit
                lexical.type == "stat" ->
                    lexical.indented + (
                        if (isContinuedStatement(state, textAfter)) {
                            statementIndent ?: context.unit
                        } else {
                            0
                        }
                        )
                lexical.info == "switch" && !closing && config.doubleIndentSwitch ->
                    lexical.indented + (
                        if (jsCaseAhead.containsMatchIn(textAfter)) {
                            context.unit
                        } else {
                            2 * context.unit
                        }
                        )
                lexical.align == true -> lexical.column + (if (closing) 0 else 1)
                else -> lexical.indented + (if (closing) 0 else context.unit)
            }
        }

        override val languageData: Map<String, Any>
            get() {
                val commentTokens = if (jsonMode) {
                    emptyMap()
                } else {
                    mapOf(
                        "line" to "//",
                        "block" to mapOf("open" to "/*", "close" to "*/")
                    )
                }
                return mapOf("commentTokens" to commentTokens)
            }
    }
}

/** Stream parser for JavaScript. */
val javaScriptLegacy: StreamParser<JavaScriptState> =
    mkJavaScript(JavaScriptConfig(name = "javascript"))

/** Stream parser for JSON. */
val json: StreamParser<JavaScriptState> =
    mkJavaScript(JavaScriptConfig(name = "json", json = true))

/** Stream parser for JSON-LD. */
val jsonld: StreamParser<JavaScriptState> =
    mkJavaScript(JavaScriptConfig(name = "json", jsonld = true))

/** Stream parser for TypeScript. */
val typescript: StreamParser<JavaScriptState> =
    mkJavaScript(JavaScriptConfig(name = "typescript", typescript = true))
