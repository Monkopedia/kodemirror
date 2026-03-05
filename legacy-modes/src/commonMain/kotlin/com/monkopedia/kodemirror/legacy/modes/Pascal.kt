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

private val pascalKeywords = (
    "absolute and array asm begin case const constructor destructor " +
        "div do downto else end file for function goto if " +
        "implementation in inherited inline interface label mod nil " +
        "not object of operator or packed procedure program record " +
        "reintroduce repeat self set shl shr string then to type " +
        "unit until uses var while with xor as class dispinterface " +
        "except exports finalization finally initialization inline " +
        "is library on out packed property raise resourcestring " +
        "threadvar try absolute abstract alias assembler bitpacked " +
        "break cdecl continue cppdecl cvar default deprecated " +
        "dynamic enumerator experimental export external far far16 " +
        "forward generic helper implements index interrupt iocheck " +
        "local message name near nodefault noreturn nostackframe " +
        "oldfpccall otherwise overload override pascal platform " +
        "private protected public published read register " +
        "reintroduce result safecall saveregisters softfloat " +
        "specialize static stdcall stored strict unaligned " +
        "unimplemented varargs virtual write"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val pascalAtoms = setOf("null")

private val pascalIsOperatorChar = Regex("[+\\-*&%=<>!?|/]")

data class PascalState(
    var tokenize: ((StringStream, PascalState) -> String?)? = null
)

private fun pascalTokenBase(stream: StringStream, state: PascalState): String? {
    val ch = stream.next() ?: return null
    if (ch == "#") {
        stream.skipToEnd()
        return "meta"
    }
    if (ch == "\"" || ch == "'") {
        state.tokenize = pascalTokenString(ch)
        return state.tokenize!!(stream, state)
    }
    if (ch == "(" && stream.eat("*") != null) {
        state.tokenize = ::pascalTokenComment
        return pascalTokenComment(stream, state)
    }
    if (ch == "{") {
        state.tokenize = ::pascalTokenCommentBraces
        return pascalTokenCommentBraces(stream, state)
    }
    if (Regex("[\\[\\](),;:.]").containsMatchIn(ch)) {
        return null
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    }
    if (ch == "/") {
        if (stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
    }
    if (pascalIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(pascalIsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w\$_]"))
    val cur = stream.current().lowercase()
    if (cur in pascalKeywords) return "keyword"
    if (cur in pascalAtoms) return "atom"
    return "variable"
}

private fun pascalTokenString(quote: String): (StringStream, PascalState) -> String? =
    { stream, state ->
        var escaped = false
        var end = false
        var next: String?
        while (true) {
            next = stream.next()
            if (next == null) break
            if (next == quote && !escaped) {
                end = true
                break
            }
            escaped = !escaped && next == "\\"
        }
        if (end || !escaped) state.tokenize = null
        "string"
    }

private fun pascalTokenComment(stream: StringStream, state: PascalState): String {
    var maybeEnd = false
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (ch == ")" && maybeEnd) {
            state.tokenize = null
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun pascalTokenCommentBraces(stream: StringStream, state: PascalState): String {
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (ch == "}") {
            state.tokenize = null
            break
        }
    }
    return "comment"
}

val pascal: StreamParser<PascalState> =
    object : StreamParser<PascalState> {
        override val name: String get() = "pascal"

        override val languageData: Map<String, Any>
            get() = mapOf(
                "indentOnInput" to Regex("""^\s*[{}]$"""),
                "commentTokens" to mapOf(
                    "block" to mapOf("open" to "(*", "close" to "*)")
                )
            )

        override fun startState(indentUnit: Int) = PascalState()
        override fun copyState(state: PascalState) = state.copy()

        override fun token(stream: StringStream, state: PascalState): String? {
            if (stream.eatSpace()) return null
            return (state.tokenize ?: ::pascalTokenBase)(stream, state)
        }
    }
