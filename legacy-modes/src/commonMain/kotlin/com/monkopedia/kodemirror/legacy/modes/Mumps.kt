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

private val mumpsCommandKeywords = listOf(
    "break", "close", "do", "else", "for", "goto", "halt", "hang", "if",
    "job", "kill", "lock", "merge", "new", "open", "quit", "read", "set",
    "tcommit", "trollback", "tstart", "use", "view", "write", "xecute",
    "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n",
    "o", "q", "r", "s", "tc", "tro", "ts", "u", "v", "w", "x"
)

@Suppress("ktlint:standard:max-line-length")
private val mumpsIntrinsicFuncsWords = listOf(
    "\\\$ascii", "\\\$char", "\\\$data", "\\\$ecode", "\\\$estack", "\\\$etrap",
    "\\\$extract", "\\\$find", "\\\$fnumber", "\\\$get", "\\\$horolog", "\\\$io",
    "\\\$increment", "\\\$job", "\\\$justify", "\\\$length", "\\\$name", "\\\$next",
    "\\\$order", "\\\$piece", "\\\$qlength", "\\\$qsubscript", "\\\$query", "\\\$quit",
    "\\\$random", "\\\$reverse", "\\\$select", "\\\$stack", "\\\$test", "\\\$text",
    "\\\$translate", "\\\$view", "\\\$x", "\\\$y",
    "\\\$a", "\\\$c", "\\\$d", "\\\$e", "\\\$ec", "\\\$es", "\\\$et",
    "\\\$f", "\\\$fn", "\\\$g", "\\\$h", "\\\$i", "\\\$j", "\\\$l", "\\\$n",
    "\\\$na", "\\\$o", "\\\$p", "\\\$q", "\\\$ql", "\\\$qs", "\\\$r", "\\\$re",
    "\\\$s", "\\\$st", "\\\$t", "\\\$tr", "\\\$v", "\\\$z"
)

private val mumpsIntrinsicFuncs = Regex(
    "^((" + mumpsIntrinsicFuncsWords.joinToString(")|(") + "))\\b",
    RegexOption.IGNORE_CASE
)
private val mumpsCommand = Regex(
    "^((" + mumpsCommandKeywords.joinToString(")|(") + "))\\b",
    RegexOption.IGNORE_CASE
)

private val mumpsSingleOperators = Regex("^[+\\-*/&#!_?\\\\<>=\\'\\[\\]]")
private val mumpsDoubleOperators = Regex("^(('=)|(<=)|(>=)|('>)|('<)|(\\[\\[)|(\\]\\])|(\\^\\\$))")
private val mumpsSingleDelimiters = Regex("^[.,:]")
private val mumpsBrackets = Regex("[()]")
private val mumpsIdentifiers = Regex("^[%A-Za-z][A-Za-z0-9]*")

data class MumpsState(
    var label: Boolean = false,
    var commandMode: Int = 0
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun mumpsTokenBase(stream: StringStream, state: MumpsState): String? {
    if (stream.sol()) {
        state.label = true
        state.commandMode = 0
    }

    val ch = stream.peek()

    if (ch == " " || ch == "\t") {
        state.label = false
        if (state.commandMode == 0) {
            state.commandMode = 1
        } else if (state.commandMode < 0 || state.commandMode == 2) {
            state.commandMode = 0
        }
    } else if (ch != "." && state.commandMode > 0) {
        if (ch == ":") {
            state.commandMode = -1
        } else {
            state.commandMode = 2
        }
    }

    if (ch == "(" || ch == "\t") state.label = false

    if (ch == ";") {
        stream.skipToEnd()
        return "comment"
    }

    if (stream.match(Regex("^[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?")) != null) {
        return "number"
    }

    if (ch == "\"") {
        if (stream.skipTo("\"")) {
            stream.next()
            return "string"
        } else {
            stream.skipToEnd()
            return "error"
        }
    }

    if (stream.match(mumpsDoubleOperators) != null || stream.match(mumpsSingleOperators) != null) {
        return "operator"
    }

    if (stream.match(mumpsSingleDelimiters) != null) return null

    if (mumpsBrackets.containsMatchIn(ch ?: "")) {
        stream.next()
        return "bracket"
    }

    if (state.commandMode > 0 && stream.match(mumpsCommand) != null) {
        return "controlKeyword"
    }

    if (stream.match(mumpsIntrinsicFuncs) != null) return "builtin"

    if (stream.match(mumpsIdentifiers) != null) return "variable"

    if (ch == "$" || ch == "^") {
        stream.next()
        return "builtin"
    }

    if (ch == "@") {
        stream.next()
        return "string special"
    }

    if (Regex("[\\w%]").containsMatchIn(ch ?: "")) {
        stream.eatWhile(Regex("[\\w%]"))
        return "variable"
    }

    stream.next()
    return "error"
}

/** Stream parser for MUMPS. */
val mumps: StreamParser<MumpsState> = object : StreamParser<MumpsState> {
    override val name: String get() = "mumps"

    override fun startState(indentUnit: Int) = MumpsState()
    override fun copyState(state: MumpsState) = state.copy()

    override fun token(stream: StringStream, state: MumpsState): String? {
        val style = mumpsTokenBase(stream, state)
        if (state.label) return "tag"
        return style
    }
}
