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

data class PythonConfig(
    val version: Int = 3,
    val singleLineStringErrors: Boolean = false,
    val hangingIndent: Int? = null,
    val extra_keywords: List<String> = emptyList(),
    val extra_builtins: List<String> = emptyList()
)

data class PythonScope(
    val offset: Int,
    val type: String,
    val align: Int?
)

class PythonState(
    var tokenize: Int = 0, // 0=tokenBase, 1+=string tokenizers
    var scopes: MutableList<PythonScope> =
        mutableListOf(PythonScope(0, "py", null)),
    var indent: Int = 0,
    var lastToken: String? = null,
    var lambda: Boolean = false,
    var dedent: Boolean = false,
    var errorToken: Boolean = false,
    var beginningOfLine: Boolean = true,
    // String tokenizer state
    var stringDelimiter: String = "",
    var stringIsSingle: Boolean = true,
    var stringIsFmt: Boolean = false,
    var stringNestDepth: Int = 0,
    // Outer tokenizer for nested strings
    var outerTokenize: Int = 0
)

private fun wordRegexp(words: List<String>): Regex {
    return Regex("^((" + words.joinToString(")|(") + "))\\b")
}

private val wordOperators = wordRegexp(listOf("and", "or", "not", "is"))

private val commonKeywords = listOf(
    "as", "assert", "break", "class", "continue",
    "def", "del", "elif", "else", "except", "finally",
    "for", "from", "global", "if", "import",
    "lambda", "pass", "raise", "return",
    "try", "while", "with", "yield", "in", "False", "True"
)

private val commonBuiltins = listOf(
    "abs", "all", "any", "bin", "bool", "bytearray", "callable", "chr",
    "classmethod", "compile", "complex", "delattr", "dict", "dir", "divmod",
    "enumerate", "eval", "filter", "float", "format", "frozenset",
    "getattr", "globals", "hasattr", "hash", "help", "hex", "id",
    "input", "int", "isinstance", "issubclass", "iter", "len",
    "list", "locals", "map", "max", "memoryview", "min", "next",
    "object", "oct", "open", "ord", "pow", "property", "range",
    "repr", "reversed", "round", "set", "setattr", "slice",
    "sorted", "staticmethod", "str", "sum", "super", "tuple",
    "type", "vars", "zip", "__import__", "NotImplemented",
    "Ellipsis", "__debug__"
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
fun mkPython(parserConf: PythonConfig = PythonConfig()): StreamParser<PythonState> {
    val errorClass = "error"
    val delimiters = Regex("^[()\\[\\]{}@,:`=;.\\\\]")
    val operators = listOf(
        Regex(
            "^([-+*/%/&|^]=?|[<>=]+|//=?|\\*\\*=?|!=|[~!@]|\\.\\.\\.)"
        )
    )
    val hangingIndent = parserConf.hangingIndent
    // Forward declaration for mutual recursion between tokenBaseInner and tokenString
    var tokenStringFn: ((StringStream, PythonState) -> String?) = { _, _ -> null }

    val py3 = parserConf.version >= 3
    val identifiers = if (py3) {
        Regex("^[_A-Za-z\\u00A1-\\uFFFF][_A-Za-z0-9\\u00A1-\\uFFFF]*")
    } else {
        Regex("^[_A-Za-z][_A-Za-z0-9]*")
    }

    val myKeywords = if (py3) {
        commonKeywords + listOf(
            "nonlocal", "None", "aiter", "anext", "async", "await",
            "breakpoint", "match", "case"
        )
    } else {
        commonKeywords + listOf("exec", "print")
    } + parserConf.extra_keywords

    val myBuiltins = if (py3) {
        commonBuiltins + listOf("ascii", "bytes", "exec", "print")
    } else {
        commonBuiltins + listOf(
            "apply", "basestring", "buffer", "cmp", "coerce", "execfile",
            "file", "intern", "long", "raw_input", "reduce", "reload",
            "unichr", "unicode", "xrange", "None"
        )
    } + parserConf.extra_builtins

    val stringPrefixes = if (py3) {
        Regex(
            "^(([rbuf]|(br)|(rb)|(fr)|(rf))?('{3}|\"{3}|['\"]))",
            RegexOption.IGNORE_CASE
        )
    } else {
        Regex(
            "^(([rubf]|(ur)|(br))?('{3}|\"{3}|['\"]))",
            RegexOption.IGNORE_CASE
        )
    }
    val keywords = wordRegexp(myKeywords)
    val builtins = wordRegexp(myBuiltins)

    fun top(state: PythonState): PythonScope = state.scopes.last()

    fun pushPyScope(stream: StringStream, state: PythonState) {
        while (top(state).type != "py") state.scopes.removeAt(state.scopes.lastIndex)
        state.scopes.add(
            PythonScope(
                top(state).offset + stream.indentUnit,
                "py",
                null
            )
        )
    }

    fun pushBracketScope(stream: StringStream, state: PythonState, type: String) {
        val align = if (stream.match(
                Regex("^[\\s\\[{(]*(?:#|$)"),
                false
            ) != null
        ) {
            null
        } else {
            stream.column() + 1
        }
        state.scopes.add(
            PythonScope(
                state.indent + (hangingIndent ?: stream.indentUnit),
                type,
                align
            )
        )
    }

    fun dedent(stream: StringStream, state: PythonState): Boolean {
        val indented = stream.indentation()
        while (state.scopes.size > 1 && top(state).offset > indented) {
            if (top(state).type != "py") return true
            state.scopes.removeAt(state.scopes.lastIndex)
        }
        return top(state).offset != indented
    }

    @Suppress("ReturnCount")
    fun tokenBaseInner(
        stream: StringStream,
        state: PythonState,
        inFormat: Boolean = false
    ): String? {
        if (stream.eatSpace()) return null
        if (!inFormat && stream.match(Regex("^#.*")) != null) return "comment"

        if (stream.match(Regex("^[0-9.]"), false) != null) {
            var floatLiteral = false
            if (stream.match(Regex("^[\\d_]*\\.\\d+(e[+-]?\\d+)?", RegexOption.IGNORE_CASE)) != null) {
                floatLiteral = true
            }
            if (stream.match(Regex("^[\\d_]+\\.\\d*")) != null) floatLiteral = true
            if (stream.match(Regex("^\\.\\d+")) != null) floatLiteral = true
            if (floatLiteral) {
                stream.eat(Regex("[jJ]"))
                return "number"
            }
            var intLiteral = false
            if (stream.match(Regex("^0x[0-9a-f_]+", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            }
            if (stream.match(Regex("^0b[01_]+", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            }
            if (stream.match(Regex("^0o[0-7_]+", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            }
            if (stream.match(Regex("^[1-9][\\d_]*(e[+-]?[\\d_]+)?")) != null) {
                stream.eat(Regex("[jJ]"))
                intLiteral = true
            }
            if (stream.match(Regex("^0(?![\\dx])", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            }
            if (intLiteral) {
                stream.eat(Regex("[lL]"))
                return "number"
            }
        }

        if (stream.match(stringPrefixes) != null) {
            val isFmtString = stream.current().lowercase().indexOf('f') != -1
            val cur = stream.current()
            var delim = cur
            while (delim.isNotEmpty() && "rubf".contains(delim[0].lowercaseChar())) {
                delim = delim.substring(1)
            }
            val singleLine = delim.length == 1
            state.stringDelimiter = delim
            state.stringIsSingle = singleLine
            state.stringIsFmt = isFmtString
            state.outerTokenize = state.tokenize
            state.tokenize = if (isFmtString) 2 else 1 // 1=string, 2=fmtString
            state.stringNestDepth = 0
            return tokenStringFn(stream, state)
        }

        for (op in operators) {
            if (stream.match(op) != null) return "operator"
        }
        if (stream.match(delimiters) != null) return "punctuation"
        if (state.lastToken == "." && stream.match(identifiers) != null) return "property"
        if (stream.match(keywords) != null || stream.match(wordOperators) != null) {
            return "keyword"
        }
        if (stream.match(builtins) != null) return "builtin"
        if (stream.match(Regex("^(self|cls)\\b")) != null) return "self"
        if (stream.match(identifiers) != null) {
            if (state.lastToken == "def" || state.lastToken == "class") return "def"
            return "variable"
        }

        stream.next()
        return if (inFormat) null else errorClass
    }

    fun tokenString(stream: StringStream, state: PythonState): String? {
        val delim = state.stringDelimiter
        val singleLine = state.stringIsSingle
        val isFmt = state.stringIsFmt
        val outClass = "string"

        if (isFmt) {
            // Format string with interpolation
            if (state.stringNestDepth > 0) {
                // Inside nested expression
                val inner = tokenBaseInner(stream, state, true)
                if (inner == "punctuation") {
                    val cur = stream.current()
                    if (cur == "{") {
                        state.stringNestDepth++
                    } else if (cur == "}") {
                        state.stringNestDepth--
                        if (state.stringNestDepth == 0) {
                            // Back to string mode
                            return inner
                        }
                    }
                }
                return inner
            }

            while (!stream.eol()) {
                stream.eatWhile(Regex("[^'\"{}\\\\]"))
                if (stream.eat("\\") != null) {
                    stream.next()
                    if (singleLine && stream.eol()) return outClass
                } else if (stream.match(delim)) {
                    state.tokenize = state.outerTokenize
                    return outClass
                } else if (stream.match("{{")) {
                    return outClass
                } else if (stream.match("{", false)) {
                    state.stringNestDepth = 1
                    stream.next()
                    val cur = stream.current()
                    return if (cur.isNotEmpty()) outClass else tokenString(stream, state)
                } else if (stream.match("}}")) {
                    return outClass
                } else if (stream.match("}")) {
                    return errorClass
                } else {
                    stream.eat(Regex("['\"]"))
                }
            }
            if (singleLine) {
                if (parserConf.singleLineStringErrors) {
                    return errorClass
                } else {
                    state.tokenize = state.outerTokenize
                }
            }
            return outClass
        } else {
            // Regular string
            while (!stream.eol()) {
                stream.eatWhile(Regex("[^'\"\\\\]"))
                if (stream.eat("\\") != null) {
                    stream.next()
                    if (singleLine && stream.eol()) return outClass
                } else if (stream.match(delim)) {
                    state.tokenize = state.outerTokenize
                    return outClass
                } else {
                    stream.eat(Regex("['\"]"))
                }
            }
            if (singleLine) {
                if (parserConf.singleLineStringErrors) {
                    return errorClass
                } else {
                    state.tokenize = state.outerTokenize
                }
            }
            return outClass
        }
    }

    tokenStringFn = ::tokenString

    fun tokenBase(stream: StringStream, state: PythonState): String? {
        val sol = stream.sol() && state.lastToken != "\\"
        if (sol) state.indent = stream.indentation()
        if (sol && top(state).type == "py") {
            val scopeOffset = top(state).offset
            if (stream.eatSpace()) {
                val lineOffset = stream.indentation()
                if (lineOffset > scopeOffset) {
                    pushPyScope(stream, state)
                } else if (lineOffset < scopeOffset &&
                    dedent(stream, state) && stream.peek() != "#"
                ) {
                    state.errorToken = true
                }
                return null
            } else {
                var style = tokenBaseInner(stream, state)
                if (scopeOffset > 0 && dedent(stream, state)) {
                    style = (style ?: "") + " " + errorClass
                }
                return style
            }
        }
        return tokenBaseInner(stream, state)
    }

    fun tokenLexer(stream: StringStream, state: PythonState): String? {
        if (stream.sol()) {
            state.beginningOfLine = true
            state.dedent = false
        }

        val style = if (state.tokenize == 0) {
            tokenBase(stream, state)
        } else {
            tokenString(stream, state)
        }
        val current = stream.current()

        if (state.beginningOfLine && current == "@") {
            return if (stream.match(identifiers, false) != null) {
                "meta"
            } else {
                if (py3) "operator" else errorClass
            }
        }

        if (Regex("\\S").containsMatchIn(current)) state.beginningOfLine = false

        var resultStyle = style
        if ((resultStyle == "variable" || resultStyle == "builtin") &&
            state.lastToken == "meta"
        ) {
            resultStyle = "meta"
        }

        if (current == "pass" || current == "return") state.dedent = true
        if (current == "lambda") state.lambda = true

        if (current == ":" && !state.lambda && top(state).type == "py" &&
            stream.match(Regex("^\\s*(?:#|$)"), false) != null
        ) {
            pushPyScope(stream, state)
        }

        if (current.length == 1 &&
            !(resultStyle != null && Regex("string|comment").containsMatchIn(resultStyle))
        ) {
            var delimiterIndex = "[({".indexOf(current)
            if (delimiterIndex != -1) {
                pushBracketScope(
                    stream,
                    state,
                    "])}".substring(delimiterIndex, delimiterIndex + 1)
                )
            }
            delimiterIndex = "])}".indexOf(current)
            if (delimiterIndex != -1) {
                if (top(state).type == current) {
                    state.indent =
                        state.scopes.removeAt(state.scopes.lastIndex).offset -
                        (hangingIndent ?: stream.indentUnit)
                } else {
                    return errorClass
                }
            }
        }
        if (state.dedent && stream.eol() && top(state).type == "py" &&
            state.scopes.size > 1
        ) {
            state.scopes.removeAt(state.scopes.lastIndex)
        }

        return resultStyle
    }

    return object : StreamParser<PythonState> {
        override val name: String get() = "python"

        override fun startState(indentUnit: Int) = PythonState()

        override fun copyState(state: PythonState): PythonState {
            return PythonState(
                tokenize = state.tokenize,
                scopes = state.scopes.toMutableList(),
                indent = state.indent,
                lastToken = state.lastToken,
                lambda = state.lambda,
                dedent = state.dedent,
                errorToken = state.errorToken,
                beginningOfLine = state.beginningOfLine,
                stringDelimiter = state.stringDelimiter,
                stringIsSingle = state.stringIsSingle,
                stringIsFmt = state.stringIsFmt,
                stringNestDepth = state.stringNestDepth,
                outerTokenize = state.outerTokenize
            )
        }

        override fun token(stream: StringStream, state: PythonState): String? {
            val addErr = state.errorToken
            if (addErr) state.errorToken = false
            var style = tokenLexer(stream, state)

            if (style != null && style != "comment") {
                state.lastToken = if (style == "keyword" || style == "punctuation") {
                    stream.current()
                } else {
                    style
                }
            }
            if (style == "punctuation") style = null
            if (stream.eol() && state.lambda) state.lambda = false

            return if (addErr) errorClass else style
        }

        override fun indent(state: PythonState, textAfter: String, context: IndentContext): Int? {
            if (state.tokenize != 0) {
                return if (state.stringIsFmt || state.stringIsSingle) null else 0
            }
            val scope = top(state)
            val closing = (scope.type == textAfter.firstOrNull()?.toString()) ||
                (
                    scope.type == "py" && !state.dedent &&
                        Regex("^(else:|elif |except |finally:)").containsMatchIn(textAfter)
                    )
            return if (scope.align != null) {
                scope.align - (if (closing) 1 else 0)
            } else {
                scope.offset - (if (closing) hangingIndent ?: context.unit else 0)
            }
        }

        override val languageData: Map<String, Any>
            get() = mapOf(
                "autocomplete" to (commonKeywords + commonBuiltins + listOf("exec", "print")),
                "commentTokens" to mapOf("line" to "#")
            )
    }
}

val pythonLegacy: StreamParser<PythonState> = mkPython()

val cython: StreamParser<PythonState> = mkPython(
    PythonConfig(
        extra_keywords = (
            "by cdef cimport cpdef ctypedef enum except " +
                "extern gil include nogil property public " +
                "readonly struct union DEF IF ELIF ELSE"
            ).split(" ")
    )
)
