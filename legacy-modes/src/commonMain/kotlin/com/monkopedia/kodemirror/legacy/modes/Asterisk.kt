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

private val asteriskAtoms = listOf(
    "exten",
    "same",
    "include",
    "ignorepat",
    "switch"
)
private val asteriskDpcmd = listOf("#include", "#exec")

@Suppress("MaxLineLength")
private val asteriskApps = listOf(
    "addqueuemember", "adsiprog", "aelsub", "agentlogin",
    "agentmonitoroutgoing", "agi", "alarmreceiver", "amd", "answer",
    "authenticate", "background", "backgrounddetect", "bridge",
    "busy", "callcompletioncancel", "callcompletionrequest",
    "celgenuserevent", "changemonitor", "chanisavail",
    "channelredirect", "chanspy", "clearhash", "confbridge",
    "congestion", "continuewhile", "controlplayback",
    "dahdiacceptr2call", "dahdibarge", "dahdiras", "dahdiscan",
    "dahdisendcallreroutingfacility", "dahdisendkeypadfacility",
    "datetime", "dbdel", "dbdeltree", "deadagi", "dial", "dictate",
    "directory", "disa", "dumpchan", "eagi", "echo", "endwhile",
    "exec", "execif", "execiftime", "exitwhile", "extenspy",
    "externalivr", "festival", "flash", "followme", "forkcdr",
    "getcpeid", "gosub", "gosubif", "goto", "gotoif", "gotoiftime",
    "hangup", "iax2provision", "ices", "importvar", "incomplete",
    "ivrdemo", "jabberjoin", "jabberleave", "jabbersend",
    "jabbersendgroup", "jabberstatus", "jack", "log", "macro",
    "macroexclusive", "macroexit", "macroif", "mailboxexists",
    "meetme", "meetmeadmin", "meetmechanneladmin", "meetmecount",
    "milliwatt", "minivmaccmess", "minivmdelete", "minivmgreet",
    "minivmmwi", "minivmnotify", "minivmrecord", "mixmonitor",
    "monitor", "morsecode", "mp3player", "mset", "musiconhold",
    "nbscat", "nocdr", "noop", "odbc", "odbcfinish", "originate",
    "ospauth", "ospfinish", "osplookup", "ospnext", "page", "park",
    "parkandannounce", "parkedcall", "pausemonitor",
    "pausequeuemember", "pickup", "pickupchan", "playback",
    "playtones", "privacymanager", "proceeding", "progress", "queue",
    "queuelog", "raiseexception", "read", "readexten", "readfile",
    "receivefax", "record", "removequeuemember", "resetcdr",
    "retrydial", "return", "ringing", "sayalpha", "saycountedadj",
    "saycountednoun", "saycountpl", "saydigits", "saynumber",
    "sayphonetic", "sayunixtime", "senddtmf", "sendfax", "sendimage",
    "sendtext", "sendurl", "set", "setamaflags", "setcallerpres",
    "setmusiconhold", "sipaddheader", "sipdtmfmode",
    "sipremoveheader", "skel", "slastation", "slatrunk", "sms",
    "softhangup", "speechactivategrammar", "speechbackground",
    "speechcreate", "speechdeactivategrammar", "speechdestroy",
    "speechloadgrammar", "speechprocessingsound", "speechstart",
    "speechunloadgrammar", "stackpop", "startmusiconhold",
    "stopmixmonitor", "stopmonitor", "stopmusiconhold",
    "stopplaytones", "system", "testclient", "testserver", "transfer",
    "tryexec", "trysystem", "unpausemonitor", "unpausequeuemember",
    "userevent", "verbose", "vmauthenticate", "vmsayname",
    "voicemail", "voicemailmain", "wait", "waitexten",
    "waitfornoise", "waitforring", "waitforsilence",
    "waitmusiconhold", "waituntil", "while", "zapateller"
)

data class AsteriskState(
    var blockComment: Boolean = false,
    var extenStart: Boolean = false,
    var extenSame: Boolean = false,
    var extenInclude: Boolean = false,
    var extenExten: Boolean = false,
    var extenPriority: Boolean = false,
    var extenApplication: Boolean = false
)

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun asteriskBasicToken(stream: StringStream, state: AsteriskState): String? {
    val ch = stream.next() ?: return null

    if (state.blockComment) {
        if (ch == "-" && stream.match("--;", consume = true)) {
            state.blockComment = false
        } else if (stream.skipTo("--;")) {
            stream.next()
            stream.next()
            stream.next()
            state.blockComment = false
        } else {
            stream.skipToEnd()
        }
        return "comment"
    }
    if (ch == ";") {
        if (stream.match("--", consume = true)) {
            if (!stream.match("-", consume = false)) {
                state.blockComment = true
                return "comment"
            }
        }
        stream.skipToEnd()
        return "comment"
    }
    if (ch == "[") {
        stream.skipTo("]")
        stream.eat("]")
        return "header"
    }
    if (ch == "\"") {
        stream.skipTo("\"")
        return "string"
    }
    if (ch == "'") {
        stream.skipTo("'")
        return "string.special"
    }
    if (ch == "#") {
        stream.eatWhile(Regex("\\w"))
        val cur = stream.current()
        if (cur in asteriskDpcmd) {
            stream.skipToEnd()
            return "strong"
        }
    }
    if (ch == "$") {
        val ch1 = stream.peek()
        if (ch1 == "{") {
            stream.skipTo("}")
            stream.eat("}")
            return "variableName.special"
        }
    }
    stream.eatWhile(Regex("\\w"))
    val cur = stream.current()
    if (cur in asteriskAtoms) {
        state.extenStart = true
        when (cur) {
            "same" -> state.extenSame = true
            "include", "switch", "ignorepat" ->
                state.extenInclude = true
        }
        return "atom"
    }
    return null
}

/** Stream parser for Asterisk dialplan. */
val asterisk: StreamParser<AsteriskState> =
    object : StreamParser<AsteriskState> {
        override val name: String get() = "asterisk"

        override val languageData: Map<String, Any>
            get() = mapOf(
                "commentTokens" to mapOf(
                    "line" to ";",
                    "block" to mapOf("open" to ";--", "close" to "--;")
                )
            )

        override fun startState(indentUnit: Int) = AsteriskState()
        override fun copyState(state: AsteriskState) = state.copy()

        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        override fun token(stream: StringStream, state: AsteriskState): String? {
            if (stream.eatSpace()) return null

            if (state.extenStart) {
                stream.eatWhile(Regex("[^\\s]"))
                val cur = stream.current()
                if (Regex("^=>?$").matches(cur)) {
                    state.extenExten = true
                    state.extenStart = false
                    return "strong"
                } else {
                    state.extenStart = false
                    stream.skipToEnd()
                    return "error"
                }
            } else if (state.extenExten) {
                state.extenExten = false
                state.extenPriority = true
                stream.eatWhile(Regex("[^,]"))
                if (state.extenInclude) {
                    stream.skipToEnd()
                    state.extenPriority = false
                    state.extenInclude = false
                }
                if (state.extenSame) {
                    state.extenPriority = false
                    state.extenSame = false
                    state.extenApplication = true
                }
                return "tag"
            } else if (state.extenPriority) {
                state.extenPriority = false
                state.extenApplication = true
                stream.next()
                if (state.extenSame) return null
                stream.eatWhile(Regex("[^,]"))
                return "number"
            } else if (state.extenApplication) {
                stream.eatWhile(Regex(","))
                val cur = stream.current()
                if (cur == ",") return null
                stream.eatWhile(Regex("\\w"))
                val appName = stream.current().lowercase()
                state.extenApplication = false
                if (appName in asteriskApps) {
                    return "def"
                }
            } else {
                return asteriskBasicToken(stream, state)
            }
            return null
        }
    }
