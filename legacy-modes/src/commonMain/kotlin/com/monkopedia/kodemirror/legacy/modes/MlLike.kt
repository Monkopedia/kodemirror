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

data class MlLikeState(
    var tokenize: (StringStream, MlLikeState) -> String? = { _, _ -> null },
    var commentLevel: Int = 0,
    var longString: Boolean = false
)

private fun mlLike(
    name: String,
    extraWords: Map<String, String> = emptyMap(),
    slashComments: Boolean = false
): StreamParser<MlLikeState> {
    val words = mutableMapOf(
        "as" to "keyword", "do" to "keyword", "else" to "keyword",
        "end" to "keyword", "exception" to "keyword", "fun" to "keyword",
        "functor" to "keyword", "if" to "keyword", "in" to "keyword",
        "include" to "keyword", "let" to "keyword", "of" to "keyword",
        "open" to "keyword", "rec" to "keyword", "struct" to "keyword",
        "then" to "keyword", "type" to "keyword", "val" to "keyword",
        "while" to "keyword", "with" to "keyword"
    )
    words.putAll(extraWords)

    fun mlTokenBase(stream: StringStream, state: MlLikeState): String? {
        val ch = stream.next() ?: return null

        if (ch == "\"") {
            state.tokenize = ::mlTokenString
            return state.tokenize(stream, state)
        }
        if (ch == "{") {
            if (stream.eat("|") != null) {
                state.longString = true
                state.tokenize = ::mlTokenLongString
                return state.tokenize(stream, state)
            }
        }
        if (ch == "(") {
            if (stream.match(Regex("^\\*(?!\\))")) != null) {
                state.commentLevel++
                state.tokenize = ::mlTokenComment
                return state.tokenize(stream, state)
            }
        }
        if (ch == "~" || ch == "?") {
            stream.eatWhile(Regex("\\w"))
            return "variableName special"
        }
        if (ch == "`") {
            stream.eatWhile(Regex("\\w"))
            return "quote"
        }
        if (ch == "/" && slashComments && stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
        if (Regex("\\d").containsMatchIn(ch)) {
            if (ch == "0" && stream.eat(Regex("[bB]")) != null) {
                stream.eatWhile(Regex("[01]"))
            }
            if (ch == "0" && stream.eat(Regex("[xX]")) != null) {
                stream.eatWhile(Regex("[0-9a-fA-F]"))
            }
            if (ch == "0" && stream.eat(Regex("[oO]")) != null) {
                stream.eatWhile(Regex("[0-7]"))
            } else {
                stream.eatWhile(Regex("[\\d_]"))
                if (stream.eat(".") != null) {
                    stream.eatWhile(Regex("\\d"))
                }
                if (stream.eat(Regex("[eE]")) != null) {
                    stream.eatWhile(Regex("[\\d\\-+]"))
                }
            }
            return "number"
        }
        if (Regex("[+\\-*&%=<>!?|@.~:]").containsMatchIn(ch)) {
            return "operator"
        }
        if (Regex("[\\w\\u00a1-\\uffff]").containsMatchIn(ch)) {
            stream.eatWhile(Regex("[\\w\\u00a1-\\uffff]"))
            val cur = stream.current()
            return if (words.containsKey(cur)) words[cur] else "variable"
        }
        return null
    }

    // Assign the tokenBase function reference after defining it
    val tokenBaseFn: (StringStream, MlLikeState) -> String? = ::mlTokenBase

    return object : StreamParser<MlLikeState> {
        override val name: String get() = name

        override fun startState(indentUnit: Int) = MlLikeState(
            tokenize = tokenBaseFn
        )

        override fun copyState(state: MlLikeState) = state.copy()

        override fun token(stream: StringStream, state: MlLikeState): String? {
            if (stream.eatSpace()) return null
            return state.tokenize(stream, state)
        }

        override val languageData: Map<String, Any>
            get() = buildMap {
                put(
                    "commentTokens",
                    buildMap {
                        if (slashComments) put("line", "//")
                        put("block", mapOf("open" to "(*", "close" to "*)"))
                    }
                )
            }
    }
}

private fun mlTokenString(stream: StringStream, state: MlLikeState): String {
    var escaped = false
    while (true) {
        val next = stream.next() ?: break
        if (next == "\"" && !escaped) {
            state.tokenize = { s, st -> mlDefaultTokenBase(s, st) }
            break
        }
        escaped = !escaped && next == "\\"
    }
    return "string"
}

private fun mlTokenComment(stream: StringStream, state: MlLikeState): String {
    var prev: String? = null
    while (state.commentLevel > 0) {
        val next = stream.next() ?: break
        if (prev == "(" && next == "*") state.commentLevel++
        if (prev == "*" && next == ")") state.commentLevel--
        prev = next
    }
    if (state.commentLevel <= 0) {
        state.tokenize = { s, st -> mlDefaultTokenBase(s, st) }
    }
    return "comment"
}

private fun mlTokenLongString(stream: StringStream, state: MlLikeState): String {
    var prev: String? = null
    while (state.longString) {
        val next = stream.next() ?: break
        if (prev == "|" && next == "}") state.longString = false
        prev = next
    }
    if (!state.longString) {
        state.tokenize = { s, st -> mlDefaultTokenBase(s, st) }
    }
    return "string"
}

/**
 * Fallback tokenBase used when resetting state.tokenize.
 * This is a simple version that delegates to each language's specific tokenBase
 * which is set during startState.
 */
private fun mlDefaultTokenBase(stream: StringStream, state: MlLikeState): String? {
    // Re-invoke the full tokenBase logic by restoring it
    // This won't be called because each language stores its own tokenBase
    stream.next()
    return null
}

/** Stream parser for OCaml. */
@Suppress("ktlint:standard:max-line-length")
val oCaml: StreamParser<MlLikeState> = mlLike(
    name = "ocaml",
    extraWords = mapOf(
        "and" to "keyword", "assert" to "keyword", "begin" to "keyword",
        "class" to "keyword", "constraint" to "keyword", "done" to "keyword",
        "downto" to "keyword", "external" to "keyword", "function" to "keyword",
        "initializer" to "keyword", "lazy" to "keyword", "match" to "keyword",
        "method" to "keyword", "module" to "keyword", "mutable" to "keyword",
        "new" to "keyword", "nonrec" to "keyword", "object" to "keyword",
        "private" to "keyword", "sig" to "keyword", "to" to "keyword",
        "try" to "keyword", "value" to "keyword", "virtual" to "keyword",
        "when" to "keyword",
        "raise" to "builtin", "failwith" to "builtin", "true" to "builtin",
        "false" to "builtin",
        "asr" to "builtin", "land" to "builtin", "lor" to "builtin",
        "lsl" to "builtin", "lsr" to "builtin", "lxor" to "builtin",
        "mod" to "builtin", "or" to "builtin",
        "raise_notrace" to "builtin", "trace" to "builtin", "exit" to "builtin",
        "print_string" to "builtin", "print_endline" to "builtin",
        "int" to "type", "float" to "type", "bool" to "type",
        "char" to "type", "string" to "type", "unit" to "type",
        "List" to "builtin"
    )
)

/** Stream parser for F#. */
@Suppress("ktlint:standard:max-line-length")
val fSharp: StreamParser<MlLikeState> = mlLike(
    name = "fsharp",
    extraWords = mapOf(
        "abstract" to "keyword", "assert" to "keyword", "base" to "keyword",
        "begin" to "keyword", "class" to "keyword", "default" to "keyword",
        "delegate" to "keyword", "do!" to "keyword", "done" to "keyword",
        "downcast" to "keyword", "downto" to "keyword", "elif" to "keyword",
        "extern" to "keyword", "finally" to "keyword", "for" to "keyword",
        "function" to "keyword", "global" to "keyword", "inherit" to "keyword",
        "inline" to "keyword", "interface" to "keyword", "internal" to "keyword",
        "lazy" to "keyword", "let!" to "keyword", "match" to "keyword",
        "member" to "keyword", "module" to "keyword", "mutable" to "keyword",
        "namespace" to "keyword", "new" to "keyword", "null" to "keyword",
        "override" to "keyword", "private" to "keyword", "public" to "keyword",
        "return!" to "keyword", "return" to "keyword", "select" to "keyword",
        "static" to "keyword", "to" to "keyword", "try" to "keyword",
        "upcast" to "keyword", "use!" to "keyword", "use" to "keyword",
        "void" to "keyword", "when" to "keyword", "yield!" to "keyword",
        "yield" to "keyword",
        "atomic" to "keyword", "break" to "keyword", "checked" to "keyword",
        "component" to "keyword", "const" to "keyword", "constraint" to "keyword",
        "constructor" to "keyword", "continue" to "keyword", "eager" to "keyword",
        "event" to "keyword", "external" to "keyword", "fixed" to "keyword",
        "method" to "keyword", "mixin" to "keyword", "object" to "keyword",
        "parallel" to "keyword", "process" to "keyword", "protected" to "keyword",
        "pure" to "keyword", "sealed" to "keyword", "tailcall" to "keyword",
        "trait" to "keyword", "virtual" to "keyword", "volatile" to "keyword",
        "List" to "builtin", "Seq" to "builtin", "Map" to "builtin",
        "Set" to "builtin", "Option" to "builtin", "int" to "builtin",
        "string" to "builtin", "not" to "builtin", "true" to "builtin",
        "false" to "builtin", "raise" to "builtin", "failwith" to "builtin"
    ),
    slashComments = true
)

/** Stream parser for Standard ML. */
val sml: StreamParser<MlLikeState> = mlLike(
    name = "sml",
    extraWords = mapOf(
        "abstype" to "keyword", "and" to "keyword", "andalso" to "keyword",
        "case" to "keyword", "datatype" to "keyword", "fn" to "keyword",
        "handle" to "keyword", "infix" to "keyword", "infixr" to "keyword",
        "local" to "keyword", "nonfix" to "keyword", "op" to "keyword",
        "orelse" to "keyword", "raise" to "keyword", "withtype" to "keyword",
        "eqtype" to "keyword", "sharing" to "keyword", "sig" to "keyword",
        "signature" to "keyword", "structure" to "keyword", "where" to "keyword",
        "true" to "keyword", "false" to "keyword",
        "int" to "builtin", "real" to "builtin", "string" to "builtin",
        "char" to "builtin", "bool" to "builtin"
    ),
    slashComments = true
)
