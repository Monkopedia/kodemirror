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

private val tclKeywords = (
    "Tcl safe after append array auto_execok auto_import auto_load " +
        "auto_mkindex auto_mkindex_old auto_qualify auto_reset bgerror " +
        "binary break catch cd close concat continue dde eof encoding " +
        "error eval exec exit expr fblocked fconfigure fcopy file " +
        "fileevent filename filename flush for foreach format gets " +
        "glob global history http if incr info interp join lappend " +
        "lindex linsert list llength load lrange lreplace lsearch lset " +
        "lsort memory msgcat namespace open package parray pid " +
        "pkg::create pkg_mkIndex proc puts pwd re_syntax read regex " +
        "regexp registry regsub rename resource return scan seek set " +
        "socket source split string subst switch tcl_endOfWord " +
        "tcl_findLibrary tcl_startOfNextWord tcl_wordBreakAfter " +
        "tcl_startOfPreviousWord tcl_wordBreakBefore tcltest tclvars " +
        "tell time trace unknown unset update uplevel upvar variable " +
        "vwait"
    ).split(" ").toSet()

private val tclFunctions = setOf(
    "if", "elseif", "else", "and", "not", "or", "eq", "ne", "in",
    "ni", "for", "foreach", "while", "switch"
)

private val tclIsOperatorChar = Regex("[+\\-*&%=<>!?^/|]")

data class TclState(
    var tokenize: (StringStream, TclState) -> String? = ::tclTokenBase,
    var beforeParams: Boolean = false,
    var inParams: Boolean = false
)

private fun tclChain(
    stream: StringStream,
    state: TclState,
    f: (StringStream, TclState) -> String?
): String? {
    state.tokenize = f
    return f(stream, state)
}

private fun tclTokenBase(stream: StringStream, state: TclState): String? {
    val beforeParams = state.beforeParams
    state.beforeParams = false
    val ch = stream.next() ?: return null

    if ((ch == "\"" || ch == "'") && state.inParams) {
        return tclChain(stream, state, tclTokenString(ch))
    } else if (Regex("[\\[\\]{}(),;.]").containsMatchIn(ch)) {
        if (ch == "(" && beforeParams) {
            state.inParams = true
        } else if (ch == ")") state.inParams = false
        return null
    } else if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    } else if (ch == "#") {
        if (stream.eat("*") != null) {
            return tclChain(stream, state, ::tclTokenComment)
        }
        if (ch == "#" && stream.match(Regex("^ *\\[ *\\[")) != null) {
            return tclChain(stream, state, ::tclTokenUnparsed)
        }
        stream.skipToEnd()
        return "comment"
    } else if (ch == "$") {
        stream.eatWhile(Regex("[\$_a-z0-9A-Z.{:]"))
        stream.eatWhile(Regex("}"))
        state.beforeParams = true
        return "builtin"
    } else if (tclIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(tclIsOperatorChar)
        return "comment"
    } else {
        stream.eatWhile(Regex("[\\w\$_{}\\u00a1-\\uffff]"))
        val word = stream.current().lowercase()
        if (word in tclKeywords) return "keyword"
        if (word in tclFunctions) {
            state.beforeParams = true
            return "keyword"
        }
        return null
    }
}

private fun tclTokenString(quote: String): (StringStream, TclState) -> String? = { stream, state ->
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
    if (end) state.tokenize = ::tclTokenBase
    "string"
}

private fun tclTokenComment(stream: StringStream, state: TclState): String {
    var maybeEnd = false
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (ch == "#" && maybeEnd) {
            state.tokenize = ::tclTokenBase
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun tclTokenUnparsed(stream: StringStream, state: TclState): String {
    var maybeEnd = 0
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (ch == "#" && maybeEnd == 2) {
            state.tokenize = ::tclTokenBase
            break
        }
        if (ch == "]") {
            maybeEnd++
        } else if (ch != " ") maybeEnd = 0
    }
    return "meta"
}

/** Stream parser for Tcl. */
val tcl: StreamParser<TclState> = object : StreamParser<TclState> {
    override val name: String get() = "tcl"

    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "#"))

    override fun startState(indentUnit: Int) = TclState()
    override fun copyState(state: TclState) = state.copy()

    override fun token(stream: StringStream, state: TclState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }
}
