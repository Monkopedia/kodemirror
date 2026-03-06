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

private val ttcnKeywords = (
    "activate address alive all alt altstep and and4b any break case component const " +
        "continue control deactivate display do else encode enumerated except exception " +
        "execute extends extension external for from function goto group if import in " +
        "infinity inout interleave label language length log match message mixed mod " +
        "modifies module modulepar mtc noblock not not4b nowait of on optional or or4b " +
        "out override param pattern port procedure record recursive rem repeat return " +
        "runs select self sender set signature system template testcase to type union " +
        "value valueof var variant while with xor xor4b"
    ).split(" ").toSet()

private val ttcnBuiltin = (
    "bit2hex bit2int bit2oct bit2str char2int char2oct encvalue decomp decvalue " +
        "float2int float2str hex2bit hex2int hex2oct hex2str int2bit int2char int2float " +
        "int2hex int2oct int2str int2unichar isbound ischosen ispresent isvalue lengthof " +
        "log2str oct2bit oct2char oct2hex oct2int oct2str regexp replace rnd sizeof " +
        "str2bit str2float str2hex str2int str2oct substr unichar2int unichar2char enum2int"
    ).split(" ").toSet()

private val ttcnTypes = (
    "anytype bitstring boolean char charstring default float hexstring integer objid " +
        "octetstring universal verdicttype timer"
    ).split(" ").toSet()

private val ttcnTimerOps = "read running start stop timeout".split(" ").toSet()
private val ttcnPortOps = (
    "call catch check clear getcall getreply halt raise receive reply send trigger"
    ).split(" ").toSet()
private val ttcnConfigOps = "create connect disconnect done kill killed map unmap".split(
    " "
).toSet()
private val ttcnVerdictOps = "getverdict setverdict".split(" ").toSet()
private val ttcnSutOps = setOf("action")
private val ttcnFunctionOps = "apply derefers refers".split(" ").toSet()
private val ttcnVerdictConsts = "error fail inconc none pass".split(" ").toSet()
private val ttcnBooleanConsts = "true false".split(" ").toSet()
private val ttcnOtherConsts = "null NULL omit".split(" ").toSet()
private val ttcnVisibilityModifiers = "private public friend".split(" ").toSet()
private val ttcnTemplateMatch = "complement ifpresent subset superset permutation".split(
    " "
).toSet()

private val ttcnIsOperatorChar = Regex("[+\\-*&@=<>!/]")

data class TtcnContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: TtcnContext?
)

data class TtcnState(
    var tokenize: ((StringStream, TtcnState) -> String?)? = null,
    var context: TtcnContext = TtcnContext(0, 0, "top", false, null),
    var indented: Int = 0,
    var startOfLine: Boolean = true
)

val ttcn: StreamParser<TtcnState> = object : StreamParser<TtcnState> {
    override val name: String get() = "ttcn"
    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )

    private var curPunc: String? = null

    private fun tokenString(quote: String): (StringStream, TtcnState) -> String? {
        return fun(stream: StringStream, state: TtcnState): String? {
            var escaped = false
            var next: String?
            var end = false
            while (true) {
                next = stream.next()
                if (next == null) break
                if (next == quote && !escaped) {
                    val afterQuote = stream.peek()
                    if (afterQuote != null) {
                        val aq = afterQuote.lowercase()
                        if (aq == "b" || aq == "h" || aq == "o") stream.next()
                    }
                    end = true
                    break
                }
                escaped = !escaped && next == "\\"
            }
            if (end || !escaped) state.tokenize = null
            return "string"
        }
    }

    private fun tokenComment(stream: StringStream, state: TtcnState): String {
        var maybeEnd = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "/" && maybeEnd) {
                state.tokenize = null
                break
            }
            maybeEnd = ch == "*"
        }
        return "comment"
    }

    private fun tokenBase(stream: StringStream, state: TtcnState): String? {
        val ch = stream.next() ?: return null

        if (ch == "\"" || ch == "'") {
            state.tokenize = tokenString(ch)
            return state.tokenize!!(stream, state)
        }
        if (Regex("[\\[\\]{}(),;\\\\:?.]").containsMatchIn(ch)) {
            curPunc = ch
            return "punctuation"
        }
        if (ch == "#") {
            stream.skipToEnd()
            return "atom"
        }
        if (ch == "%") {
            stream.eatWhile(Regex("\\b"))
            return "atom"
        }
        if (Regex("\\d").containsMatchIn(ch)) {
            stream.eatWhile(Regex("[\\w.]"))
            return "number"
        }
        if (ch == "/") {
            if (stream.eat("*") != null) {
                state.tokenize = ::tokenComment
                return tokenComment(stream, state)
            }
            if (stream.eat("/") != null) {
                stream.skipToEnd()
                return "comment"
            }
        }
        if (ttcnIsOperatorChar.containsMatchIn(ch)) {
            if (ch == "@") {
                if (stream.match("try") || stream.match("catch") || stream.match("lazy")) {
                    return "keyword"
                }
            }
            stream.eatWhile(ttcnIsOperatorChar)
            return "operator"
        }
        stream.eatWhile(Regex("[\\w\$_\\xa1-\\uffff]"))
        val cur = stream.current()

        if (ttcnKeywords.contains(cur)) return "keyword"
        if (ttcnBuiltin.contains(cur)) return "builtin"
        if (ttcnTimerOps.contains(cur)) return "def"
        if (ttcnConfigOps.contains(cur)) return "def"
        if (ttcnVerdictOps.contains(cur)) return "def"
        if (ttcnPortOps.contains(cur)) return "def"
        if (ttcnSutOps.contains(cur)) return "def"
        if (ttcnFunctionOps.contains(cur)) return "def"
        if (ttcnVerdictConsts.contains(cur)) return "string"
        if (ttcnBooleanConsts.contains(cur)) return "string"
        if (ttcnOtherConsts.contains(cur)) return "string"
        if (ttcnTypes.contains(cur)) return "typeName.standard"
        if (ttcnVisibilityModifiers.contains(cur)) return "modifier"
        if (ttcnTemplateMatch.contains(cur)) return "atom"

        return "variable"
    }

    private fun pushContext(state: TtcnState, col: Int, type: String) {
        var indent = state.indented
        if (state.context.type == "statement") {
            indent = state.context.indented
        }
        state.context = TtcnContext(indent, col, type, null, state.context)
    }

    private fun popContext(state: TtcnState): TtcnContext {
        val t = state.context.type
        if (t == ")" || t == "]" || t == "}") {
            state.indented = state.context.indented
        }
        val prev = state.context.prev ?: state.context
        state.context = prev
        return prev
    }

    override fun startState(indentUnit: Int) = TtcnState()

    override fun copyState(state: TtcnState) = TtcnState(
        tokenize = state.tokenize,
        context = state.context.copy(),
        indented = state.indented,
        startOfLine = state.startOfLine
    )

    override fun token(stream: StringStream, state: TtcnState): String? {
        var ctx = state.context
        if (stream.sol()) {
            if (ctx.align == null) ctx.align = false
            state.indented = stream.indentation()
            state.startOfLine = true
        }
        if (stream.eatSpace()) return null
        curPunc = null
        val style = (state.tokenize ?: ::tokenBase)(stream, state)
        if (style == "comment") return style
        if (ctx.align == null) ctx.align = true

        if ((curPunc == ";" || curPunc == ":" || curPunc == ",") &&
            ctx.type == "statement"
        ) {
            popContext(state)
        } else if (curPunc == "{") {
            pushContext(state, stream.column(), "}")
        } else if (curPunc == "[") {
            pushContext(state, stream.column(), "]")
        } else if (curPunc == "(") {
            pushContext(state, stream.column(), ")")
        } else if (curPunc == "}") {
            while (ctx.type == "statement") ctx = popContext(state)
            if (ctx.type == "}") ctx = popContext(state)
            while (ctx.type == "statement") ctx = popContext(state)
        } else if (curPunc == ctx.type) {
            popContext(state)
        } else if (((ctx.type == "}" || ctx.type == "top") && curPunc != ";") ||
            (ctx.type == "statement" && curPunc == "newstatement")
        ) {
            pushContext(state, stream.column(), "statement")
        }

        state.startOfLine = false
        return style
    }
}
