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

private val ttcnCfgKeywords = (
    "Yes No LogFile FileMask ConsoleMask AppendFile TimeStampFormat " +
        "LogEventTypes SourceInfoFormat LogEntityName LogSourceInfo " +
        "DiskFullAction LogFileNumber LogFileSize MatchingHints Detailed " +
        "Compact SubCategories Stack Single None Seconds DateTime Time " +
        "Stop Error Retry Delete TCPPort KillTimer NumHCs " +
        "UnixSocketsEnabled LocalAddress"
    ).split(" ").toSet()

private val ttcnCfgFileNCtrlMaskOptions = (
    "TTCN_EXECUTOR TTCN_ERROR TTCN_WARNING TTCN_PORTEVENT TTCN_TIMEROP " +
        "TTCN_VERDICTOP TTCN_DEFAULTOP TTCN_TESTCASE TTCN_ACTION TTCN_USER " +
        "TTCN_FUNCTION TTCN_STATISTICS TTCN_PARALLEL TTCN_MATCHING TTCN_DEBUG " +
        "EXECUTOR ERROR WARNING PORTEVENT TIMEROP VERDICTOP DEFAULTOP TESTCASE " +
        "ACTION USER FUNCTION STATISTICS PARALLEL MATCHING DEBUG LOG_ALL " +
        "LOG_NOTHING ACTION_UNQUALIFIED DEBUG_ENCDEC DEBUG_TESTPORT " +
        "DEBUG_UNQUALIFIED DEFAULTOP_ACTIVATE DEFAULTOP_DEACTIVATE DEFAULTOP_EXIT " +
        "DEFAULTOP_UNQUALIFIED ERROR_UNQUALIFIED EXECUTOR_COMPONENT " +
        "EXECUTOR_CONFIGDATA EXECUTOR_EXTCOMMAND EXECUTOR_LOGOPTIONS " +
        "EXECUTOR_RUNTIME EXECUTOR_UNQUALIFIED FUNCTION_RND FUNCTION_UNQUALIFIED " +
        "MATCHING_DONE MATCHING_MCSUCCESS MATCHING_MCUNSUCC MATCHING_MMSUCCESS " +
        "MATCHING_MMUNSUCC MATCHING_PCSUCCESS MATCHING_PCUNSUCC MATCHING_PMSUCCESS " +
        "MATCHING_PMUNSUCC MATCHING_PROBLEM MATCHING_TIMEOUT MATCHING_UNQUALIFIED " +
        "PARALLEL_PORTCONN PARALLEL_PORTMAP PARALLEL_PTC PARALLEL_UNQUALIFIED " +
        "PORTEVENT_DUALRECV PORTEVENT_DUALSEND PORTEVENT_MCRECV PORTEVENT_MCSEND " +
        "PORTEVENT_MMRECV PORTEVENT_MMSEND PORTEVENT_MQUEUE PORTEVENT_PCIN " +
        "PORTEVENT_PCOUT PORTEVENT_PMIN PORTEVENT_PMOUT PORTEVENT_PQUEUE " +
        "PORTEVENT_STATE PORTEVENT_UNQUALIFIED STATISTICS_UNQUALIFIED " +
        "STATISTICS_VERDICT TESTCASE_FINISH TESTCASE_START TESTCASE_UNQUALIFIED " +
        "TIMEROP_GUARD TIMEROP_READ TIMEROP_START TIMEROP_STOP TIMEROP_TIMEOUT " +
        "TIMEROP_UNQUALIFIED USER_UNQUALIFIED VERDICTOP_FINAL " +
        "VERDICTOP_GETVERDICT VERDICTOP_SETVERDICT VERDICTOP_UNQUALIFIED " +
        "WARNING_UNQUALIFIED"
    ).split(" ").toSet()

private val ttcnCfgExternalCommands = (
    "BeginControlPart EndControlPart BeginTestCase EndTestCase"
    ).split(" ").toSet()

private val ttcnCfgIsOperatorChar = Regex("[|]")

data class TtcnCfgContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: TtcnCfgContext?
)

data class TtcnCfgState(
    var tokenize: ((StringStream, TtcnCfgState) -> String?)? = null,
    var context: TtcnCfgContext = TtcnCfgContext(0, 0, "top", false, null),
    var indented: Int = 0,
    var startOfLine: Boolean = true
)

val ttcnCfg: StreamParser<TtcnCfgState> = object : StreamParser<TtcnCfgState> {
    override val name: String get() = "ttcn"
    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "#"))

    private var curPunc: String? = null

    private fun tokenString(quote: String): (StringStream, TtcnCfgState) -> String? {
        return fun(stream: StringStream, state: TtcnCfgState): String? {
            var escaped = false
            var next: String?
            var end = false
            while (true) {
                next = stream.next()
                if (next == null) break
                if (next == quote && !escaped) {
                    val afterNext = stream.peek()
                    if (afterNext != null) {
                        val aq = afterNext.lowercase()
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

    private fun tokenBase(stream: StringStream, state: TtcnCfgState): String? {
        val ch = stream.next() ?: return null
        if (ch == "\"" || ch == "'") {
            state.tokenize = tokenString(ch)
            return state.tokenize!!(stream, state)
        }
        if (Regex("[:=]").containsMatchIn(ch)) {
            curPunc = ch
            return "punctuation"
        }
        if (ch == "#") {
            stream.skipToEnd()
            return "comment"
        }
        if (Regex("\\d").containsMatchIn(ch)) {
            stream.eatWhile(Regex("[\\w.]"))
            return "number"
        }
        if (ttcnCfgIsOperatorChar.containsMatchIn(ch)) {
            stream.eatWhile(ttcnCfgIsOperatorChar)
            return "operator"
        }
        if (ch == "[") {
            stream.eatWhile(Regex("[\\w_\\]]"))
            return "number"
        }

        stream.eatWhile(Regex("[\\w\$_]"))
        val cur = stream.current()
        if (ttcnCfgKeywords.contains(cur)) return "keyword"
        if (ttcnCfgFileNCtrlMaskOptions.contains(cur)) return "atom"
        if (ttcnCfgExternalCommands.contains(cur)) return "deleted"

        return "variable"
    }

    private fun pushContext(state: TtcnCfgState, col: Int, type: String) {
        var indent = state.indented
        if (state.context.type == "statement") {
            indent = state.context.indented
        }
        state.context = TtcnCfgContext(indent, col, type, null, state.context)
    }

    private fun popContext(state: TtcnCfgState): TtcnCfgContext {
        val t = state.context.type
        if (t == ")" || t == "]" || t == "}") {
            state.indented = state.context.indented
        }
        val prev = state.context.prev ?: state.context
        state.context = prev
        return prev
    }

    override fun startState(indentUnit: Int) = TtcnCfgState()

    override fun copyState(state: TtcnCfgState) = TtcnCfgState(
        tokenize = state.tokenize,
        context = state.context.copy(),
        indented = state.indented,
        startOfLine = state.startOfLine
    )

    override fun token(stream: StringStream, state: TtcnCfgState): String? {
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
