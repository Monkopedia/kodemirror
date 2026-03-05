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

private val schemeIndentWordSkip = 2

@Suppress("ktlint:standard:max-line-length")
private val schemeKeywords = setOf(
    "\u03bb", "case-lambda", "call/cc", "class", "cond-expand", "define-class",
    "define-values", "exit-handler", "field", "import", "inherit", "init-field",
    "interface", "let*-values", "let-values", "let/ec", "mixin", "opt-lambda",
    "override", "protect", "provide", "public", "rename", "require",
    "require-for-syntax", "syntax", "syntax-case", "syntax-error", "unit/sig",
    "unless", "when", "with-syntax", "and", "begin", "call-with-current-continuation",
    "call-with-input-file", "call-with-output-file", "case", "cond", "define",
    "define-syntax", "define-macro", "defmacro", "delay", "do", "dynamic-wind",
    "else", "for-each", "if", "lambda", "let", "let*", "let-syntax", "letrec",
    "letrec-syntax", "map", "or", "syntax-rules", "abs", "acos", "angle", "append",
    "apply", "asin", "assoc", "assq", "assv", "atan", "boolean?", "caar", "cadr",
    "call-with-values", "car", "cdddar", "cddddr", "cdr", "ceiling",
    "char->integer", "char-alphabetic?", "char-ci<=?", "char-ci<?", "char-ci=?",
    "char-ci>=?", "char-ci>?", "char-downcase", "char-lower-case?", "char-numeric?",
    "char-ready?", "char-upcase", "char-upper-case?", "char-whitespace?",
    "char<=?", "char<?", "char=?", "char>=?", "char>?", "char?",
    "close-input-port", "close-output-port", "complex?", "cons", "cos",
    "current-input-port", "current-output-port", "denominator", "display",
    "eof-object?", "eq?", "equal?", "eqv?", "eval", "even?", "exact->inexact",
    "exact?", "exp", "expt", "#f", "floor", "force", "gcd", "imag-part",
    "inexact->exact", "inexact?", "input-port?", "integer->char", "integer?",
    "interaction-environment", "lcm", "length", "list", "list->string",
    "list->vector", "list-ref", "list-tail", "list?", "load", "log", "magnitude",
    "make-polar", "make-rectangular", "make-string", "make-vector", "max", "member",
    "memq", "memv", "min", "modulo", "negative?", "newline", "not",
    "null-environment", "null?", "number->string", "number?", "numerator", "odd?",
    "open-input-file", "open-output-file", "output-port?", "pair?", "peek-char",
    "port?", "positive?", "procedure?", "quasiquote", "quote", "quotient",
    "rational?", "rationalize", "read", "read-char", "real-part", "real?",
    "remainder", "reverse", "round", "scheme-report-environment", "set!",
    "set-car!", "set-cdr!", "sin", "sqrt", "string", "string->list",
    "string->number", "string->symbol", "string-append", "string-ci<=?",
    "string-ci<?", "string-ci=?", "string-ci>=?", "string-ci>?", "string-copy",
    "string-fill!", "string-length", "string-ref", "string-set!", "string<=?",
    "string<?", "string=?", "string>=?", "string>?", "string?", "substring",
    "symbol->string", "symbol?", "#t", "tan", "transcript-off", "transcript-on",
    "truncate", "values", "vector", "vector->list", "vector-fill!", "vector-length",
    "vector-ref", "vector-set!", "with-input-from-file", "with-output-to-file",
    "write", "write-char", "zero?"
)

private val schemeIndentKeys = setOf(
    "define", "let", "letrec", "let*", "lambda", "define-macro", "defmacro",
    "let-syntax", "letrec-syntax", "let-values", "let*-values", "define-syntax",
    "syntax-rules", "define-values", "when", "unless"
)

private val schemeBinaryMatcher = Regex(
    "^(?:[-+]i|[-+][01]+#*(?:/[01]+#*)?i|" +
        "[-+]?[01]+#*(?:/[01]+#*)?@[-+]?[01]+#*(?:/[01]+#*)?|" +
        "[-+]?[01]+#*(?:/[01]+#*)?[-+](?:[01]+#*(?:/[01]+#*)?)?i|" +
        "[-+]?[01]+#*(?:/[01]+#*)?)(?=[()`\\s;\"]|$)",
    RegexOption.IGNORE_CASE
)
private val schemeOctalMatcher = Regex(
    "^(?:[-+]i|[-+][0-7]+#*(?:/[0-7]+#*)?i|" +
        "[-+]?[0-7]+#*(?:/[0-7]+#*)?@[-+]?[0-7]+#*(?:/[0-7]+#*)?|" +
        "[-+]?[0-7]+#*(?:/[0-7]+#*)?[-+](?:[0-7]+#*(?:/[0-7]+#*)?)?i|" +
        "[-+]?[0-7]+#*(?:/[0-7]+#*)?)(?=[()`\\s;\"]|$)",
    RegexOption.IGNORE_CASE
)
private val schemeHexMatcher = Regex(
    "^(?:[-+]i|[-+][\\da-f]+#*(?:/[\\da-f]+#*)?i|" +
        "[-+]?[\\da-f]+#*(?:/[\\da-f]+#*)?@[-+]?[\\da-f]+#*(?:/[\\da-f]+#*)?|" +
        "[-+]?[\\da-f]+#*(?:/[\\da-f]+#*)?[-+](?:[\\da-f]+#*(?:/[\\da-f]+#*)?)?i|" +
        "[-+]?[\\da-f]+#*(?:/[\\da-f]+#*)?)(?=[()`\\s;\"]|$)",
    RegexOption.IGNORE_CASE
)

@Suppress("ktlint:standard:max-line-length")
private val schemeDecimalMatcher = Regex(
    "^(?:[-+]i|[-+](?:(?:(?:\\d+#+\\.?#*|\\d+\\.\\d*#*|\\.\\d+#*|\\d+)(?:[esfdl][-+]?\\d+)?)|\\d+#*/\\d+#*)i|" +
        "[-+]?(?:(?:(?:\\d+#+\\.?#*|\\d+\\.\\d*#*|\\.\\d+#*|\\d+)(?:[esfdl][-+]?\\d+)?)|\\d+#*/\\d+#*)@[-+]?(?:(?:(?:\\d+#+\\.?#*|\\d+\\.\\d*#*|\\.\\d+#*|\\d+)(?:[esfdl][-+]?\\d+)?)|\\d+#*/\\d+#*)|" +
        "[-+]?(?:(?:(?:\\d+#+\\.?#*|\\d+\\.\\d*#*|\\.\\d+#*|\\d+)(?:[esfdl][-+]?\\d+)?)|\\d+#*/\\d+#*)[-+](?:(?:(?:\\d+#+\\.?#*|\\d+\\.\\d*#*|\\.\\d+#*|\\d+)(?:[esfdl][-+]?\\d+)?)|\\d+#*/\\d+#*)?i|" +
        "(?:(?:(?:\\d+#+\\.?#*|\\d+\\.\\d*#*|\\.\\d+#*|\\d+)(?:[esfdl][-+]?\\d+)?)|\\d+#*/\\d+#*))(?=[()`\\s;\"]|$)",
    RegexOption.IGNORE_CASE
)

data class SchemeIndentStack(
    val indent: Int,
    val type: String,
    val prev: SchemeIndentStack?
)

data class SchemeState(
    var indentStack: SchemeIndentStack? = null,
    var indentation: Int = 0,
    var mode: String? = null,
    var sExprComment: Any = false,
    var sExprQuote: Any = false
)

private fun schemeIsBinaryNumber(stream: StringStream): Boolean =
    stream.match(schemeBinaryMatcher) != null

private fun schemeIsOctalNumber(stream: StringStream): Boolean =
    stream.match(schemeOctalMatcher) != null

private fun schemeIsHexNumber(stream: StringStream): Boolean =
    stream.match(schemeHexMatcher) != null

private fun schemeIsDecimalNumber(stream: StringStream, backup: Boolean = false): Boolean {
    if (backup) stream.backUp(1)
    return stream.match(schemeDecimalMatcher) != null
}

private fun schemeProcessEscapedSequence(stream: StringStream, token: String, state: SchemeState) {
    var next: String?
    var escaped = false
    while (stream.next().also { next = it } != null) {
        if (next == token && !escaped) {
            state.mode = null
            break
        }
        escaped = !escaped && next == "\\"
    }
}

private fun schemeHandleDefault(stream: StringStream, state: SchemeState): String? {
    val ch = stream.next() ?: return null

    return when {
        ch == "\"" -> {
            state.mode = "string"
            "string"
        }
        ch == "'" -> {
            if (stream.peek() == "(" || stream.peek() == "[") {
                if (state.sExprQuote !is Int) state.sExprQuote = 0
                "atom"
            } else {
                stream.eatWhile(Regex("[\\w_\\-!\$%&*+./:<=>?@^~]"))
                "atom"
            }
        }
        ch == "|" -> {
            state.mode = "symbol"
            "symbol"
        }
        ch == "#" -> {
            when {
                stream.eat("|") != null -> {
                    state.mode = "comment"
                    "comment"
                }
                stream.eat(Regex("[tf]")) != null -> "atom"
                stream.eat(";") != null -> {
                    state.mode = "s-expr-comment"
                    "comment"
                }
                else -> {
                    var numTest: ((StringStream) -> Boolean)? = null
                    var hasExactness = false
                    var hasRadix = true
                    if (stream.eat(Regex("[ei]")) != null) {
                        hasExactness = true
                    } else {
                        stream.backUp(1)
                    }
                    when {
                        stream.match(Regex("^#b")) != null -> numTest = ::schemeIsBinaryNumber
                        stream.match(Regex("^#o")) != null -> numTest = ::schemeIsOctalNumber
                        stream.match(Regex("^#x")) != null -> numTest = ::schemeIsHexNumber
                        stream.match(Regex("^#d")) != null -> numTest = ::schemeIsDecimalNumber
                        stream.match(Regex("^[-+0-9.]"), consume = false) != null -> {
                            hasRadix = false
                            numTest = ::schemeIsDecimalNumber
                        }
                        !hasExactness -> stream.eat("#")
                    }
                    if (numTest != null) {
                        if (hasRadix && !hasExactness) stream.match(Regex("^#[ei]"))
                        if (numTest(stream)) "number" else null
                    } else {
                        null
                    }
                }
            }
        }
        Regex("^[-+0-9.]").containsMatchIn(ch) && schemeIsDecimalNumber(stream, backup = true) -> "number"
        ch == ";" -> {
            stream.skipToEnd()
            "comment"
        }
        ch == "(" || ch == "[" -> {
            var keyWord = ""
            val indentTemp = stream.column()
            var letter: String?
            while (stream.eat(Regex("[^\\s()\\[;\\])]")).also { letter = it } != null) {
                keyWord += letter
            }
            if (keyWord.isNotEmpty() && keyWord in schemeIndentKeys) {
                state.indentStack = SchemeIndentStack(
                    indent = indentTemp + schemeIndentWordSkip,
                    type = ch,
                    prev = state.indentStack
                )
            } else {
                stream.eatSpace()
                if (stream.eol() || stream.peek() == ";") {
                    state.indentStack = SchemeIndentStack(
                        indent = indentTemp + 1,
                        type = ch,
                        prev = state.indentStack
                    )
                } else {
                    state.indentStack = SchemeIndentStack(
                        indent = indentTemp + stream.current().length,
                        type = ch,
                        prev = state.indentStack
                    )
                }
            }
            stream.backUp(stream.current().length - 1)
            if (state.sExprComment is Int) state.sExprComment = (state.sExprComment as Int) + 1
            if (state.sExprQuote is Int) state.sExprQuote = (state.sExprQuote as Int) + 1
            "bracket"
        }
        ch == ")" || ch == "]" -> {
            val expectedOpen = if (ch == ")") "(" else "["
            if (state.indentStack != null && state.indentStack!!.type == expectedOpen) {
                state.indentStack = state.indentStack!!.prev
                if (state.sExprComment is Int) {
                    val newCount = (state.sExprComment as Int) - 1
                    if (newCount == 0) {
                        state.sExprComment = false
                        return "comment"
                    }
                    state.sExprComment = newCount
                }
                if (state.sExprQuote is Int) {
                    val newCount = (state.sExprQuote as Int) - 1
                    if (newCount == 0) {
                        state.sExprQuote = false
                        return "atom"
                    }
                    state.sExprQuote = newCount
                }
            }
            "bracket"
        }
        else -> {
            stream.eatWhile(Regex("[\\w_\\-!\$%&*+./:<=>?@^~]"))
            if (stream.current() in schemeKeywords) "builtin" else "variable"
        }
    }
}

val scheme: StreamParser<SchemeState> = object : StreamParser<SchemeState> {
    override val name: String get() = "scheme"

    override fun startState(indentUnit: Int) = SchemeState()

    override fun copyState(state: SchemeState): SchemeState {
        fun copyStack(s: SchemeIndentStack?): SchemeIndentStack? = s?.let {
            SchemeIndentStack(indent = it.indent, type = it.type, prev = copyStack(it.prev))
        }
        return SchemeState(
            indentStack = copyStack(state.indentStack),
            indentation = state.indentation,
            mode = state.mode,
            sExprComment = state.sExprComment,
            sExprQuote = state.sExprQuote
        )
    }

    override fun token(stream: StringStream, state: SchemeState): String? {
        if (state.indentStack == null && stream.sol()) {
            state.indentation = stream.indentation()
        }
        if (stream.eatSpace()) return null

        var returnType: String? = null

        when (state.mode) {
            "string" -> {
                schemeProcessEscapedSequence(stream, "\"", state)
                returnType = "string"
            }
            "symbol" -> {
                schemeProcessEscapedSequence(stream, "|", state)
                returnType = "symbol"
            }
            "comment" -> {
                var next: String?
                var maybeEnd = false
                while (stream.next().also { next = it } != null) {
                    if (next == "#" && maybeEnd) {
                        state.mode = null
                        break
                    }
                    maybeEnd = next == "|"
                }
                returnType = "comment"
            }
            "s-expr-comment" -> {
                state.mode = null
                if (stream.peek() == "(" || stream.peek() == "[") {
                    state.sExprComment = 0
                    returnType = schemeHandleDefault(stream, state)
                } else {
                    stream.eatWhile(Regex("[^\\s()\\[\\]]"))
                    returnType = "comment"
                }
            }
            else -> {
                returnType = schemeHandleDefault(stream, state)
            }
        }

        return if (state.sExprComment is Int) {
            "comment"
        } else if (state.sExprQuote is Int) {
            "atom"
        } else {
            returnType
        }
    }

    override fun indent(state: SchemeState, textAfter: String, context: IndentContext): Int {
        return state.indentStack?.indent ?: state.indentation
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "closeBrackets" to mapOf("brackets" to listOf("(", "[", "{", "\"")),
            "commentTokens" to mapOf("line" to ";;")
        )
}
