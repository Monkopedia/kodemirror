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

private val eclKeyword = (
    "abs acos allnodes ascii asin asstring atan atan2 ave case choose choosen choosesets " +
        "clustersize combine correlation cos cosh count covariance cron dataset dedup define " +
        "denormalize distribute distributed distribution ebcdic enth error evaluate event " +
        "eventextra eventname exists exp failcode failmessage fetch fromunicode getisvalid " +
        "global graph group hash hash32 hash64 hashcrc hashmd5 having if index intformat " +
        "isvalid iterate join keyunicode length library limit ln local log loop map matched " +
        "matchlength matchposition matchtext matchunicode max merge mergejoin min nolocal " +
        "nonempty normalize parse pipe power preload process project pull random range rank " +
        "ranked realformat recordof regexfind regexreplace regroup rejected rollup round " +
        "roundup row rowdiff sample set sin sinh sizeof soapcall sort sorted sqrt stepped " +
        "stored sum table tan tanh thisnode topn tounicode transfer trim truncate typeof " +
        "ungroup unicodeorder variance which workunit xmldecode xmlencode xmltext xmlunicode"
    ).split(" ").toSet()

private val eclVariable = (
    "apply assert build buildindex evaluate fail keydiff keypatch loadxml nothor notify " +
        "output parallel sequential soapcall wait"
    ).split(" ").toSet()

private val eclVariable2 = (
    "__compressed__ all and any as atmost before beginc++ best between case const counter " +
        "csv descend encrypt end endc++ endmacro except exclusive expire export extend false " +
        "few first flat from full function group header heading hole ifblock import in " +
        "interface joined keep keyed last left limit load local locale lookup macro many " +
        "maxcount maxlength min skew module named nocase noroot noscan nosort not of only " +
        "opt or outer overwrite packed partition penalty physicallength pipe quote record " +
        "relationship repeat return right scan self separator service shared skew skip sql " +
        "store terminator thor threshold token transform trim true type unicodeorder unsorted " +
        "validate virtual whole wild within xml xpath"
    ).split(" ").toSet()

private val eclVariable3 = (
    "ascii big_endian boolean data decimal ebcdic integer pattern qstring real record rule " +
        "set of string token udecimal unicode unsigned varstring varunicode"
    ).split(" ").toSet()

private val eclBuiltin = (
    "checkpoint deprecated failcode failmessage failure global independent onwarning persist " +
        "priority recovery stored success wait when"
    ).split(" ").toSet()

private val eclBlockKeywords =
    setOf("catch", "class", "do", "else", "finally", "for", "if", "switch", "try", "while")
private val eclAtoms = setOf("true", "false", "null")
private val eclIsOperatorChar = Regex("[+\\-*&%=<>!?|/]")

data class EclContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: EclContext?
)

data class EclState(
    var tokenize: ((StringStream, EclState) -> String?)? = null,
    var context: EclContext,
    var indented: Int = 0,
    var startOfLine: Boolean = true,
    var curPunc: String? = null
)

private fun eclPushContext(state: EclState, col: Int, type: String) {
    state.context = EclContext(state.indented, col, type, null, state.context)
}

private fun eclPopContext(state: EclState): EclContext? {
    val t = state.context.type
    if (t == ")" || t == "]" || t == "}") {
        state.indented = state.context.indented
    }
    state.context = state.context.prev ?: return null
    return state.context
}

private fun eclMetaHook(stream: StringStream, state: EclState): String? {
    if (!state.startOfLine) return null
    stream.skipToEnd()
    return "meta"
}

private fun eclTokenBase(stream: StringStream, state: EclState): String? {
    val ch = stream.next() ?: return null
    if (ch == "#") {
        val result = eclMetaHook(stream, state)
        if (result != null) return result
    }
    if (ch == "\"" || ch == "'") {
        state.tokenize = eclTokenString(ch)
        return state.tokenize!!(stream, state)
    }
    if (Regex("[\\[\\]{}(),;:.]").containsMatchIn(ch)) {
        state.curPunc = ch
        return null
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    }
    if (ch == "/") {
        if (stream.eat("*") != null) {
            state.tokenize = ::eclTokenComment
            return eclTokenComment(stream, state)
        }
        if (stream.eat("/") != null) {
            stream.skipToEnd()
            return "comment"
        }
    }
    if (eclIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(eclIsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w\$_]"))
    val cur = stream.current().lowercase()
    if (cur in eclKeyword) {
        if (cur in eclBlockKeywords) state.curPunc = "newstatement"
        return "keyword"
    }
    if (cur in eclVariable) {
        if (cur in eclBlockKeywords) state.curPunc = "newstatement"
        return "variable"
    }
    if (cur in eclVariable2) {
        if (cur in eclBlockKeywords) state.curPunc = "newstatement"
        return "modifier"
    }
    if (cur in eclVariable3) {
        if (cur in eclBlockKeywords) state.curPunc = "newstatement"
        return "type"
    }
    if (cur in eclBuiltin) {
        if (cur in eclBlockKeywords) state.curPunc = "newstatement"
        return "builtin"
    }
    // Check for type with trailing digits/underscores
    var i = cur.length - 1
    while (i >= 0 && (cur[i].isDigit() || cur[i] == '_')) i--
    if (i > 0) {
        val cur2 = cur.substring(0, i + 1)
        if (cur2 in eclVariable3) {
            if (cur2 in eclBlockKeywords) state.curPunc = "newstatement"
            return "type"
        }
    }
    if (cur in eclAtoms) return "atom"
    return null
}

private fun eclTokenString(quote: String): (StringStream, EclState) -> String? = { stream, state ->
    var escaped = false
    var end = false
    while (true) {
        val next = stream.next() ?: break
        if (next == quote && !escaped) {
            end = true
            break
        }
        escaped = !escaped && next == "\\"
    }
    if (end || !escaped) state.tokenize = null
    "string"
}

private fun eclTokenComment(stream: StringStream, state: EclState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "/" && maybeEnd) {
            state.tokenize = null
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

/** Stream parser for ECL. */
val ecl: StreamParser<EclState> = object : StreamParser<EclState> {
    override val name: String get() = "ecl"

    override fun startState(indentUnit: Int) = EclState(
        context = EclContext(-indentUnit, 0, "top", false, null)
    )

    override fun copyState(state: EclState) = state.copy(
        context = state.context.copy()
    )

    override fun token(stream: StringStream, state: EclState): String? {
        val ctx = state.context
        if (stream.sol()) {
            if (ctx.align == null) ctx.align = false
            state.indented = stream.indentation()
            state.startOfLine = true
        }
        if (stream.eatSpace()) return null
        state.curPunc = null
        val style = (state.tokenize ?: ::eclTokenBase)(stream, state)
        if (style == "comment" || style == "meta") return style
        if (ctx.align == null) ctx.align = true

        val punc = state.curPunc
        if ((punc == ";" || punc == ":") && ctx.type == "statement") {
            eclPopContext(state)
        } else if (punc == "{") {
            eclPushContext(state, stream.column(), "}")
        } else if (punc == "[") {
            eclPushContext(state, stream.column(), "]")
        } else if (punc == "(") {
            eclPushContext(state, stream.column(), ")")
        } else if (punc == "}") {
            var c = state.context
            while (c.type == "statement") {
                eclPopContext(state)
                c = state.context
            }
            if (c.type == "}") eclPopContext(state)
            c = state.context
            while (c.type == "statement") {
                eclPopContext(state)
                c = state.context
            }
        } else if (punc == ctx.type) {
            eclPopContext(state)
        } else if (ctx.type == "}" || ctx.type == "top" ||
            (ctx.type == "statement" && punc == "newstatement")
        ) {
            eclPushContext(state, stream.column(), "statement")
        }
        state.startOfLine = false
        return style
    }

    override fun indent(state: EclState, textAfter: String, context: IndentContext): Int? {
        if (state.tokenize != null && state.tokenize != ::eclTokenBase) return 0
        var ctx = state.context
        val firstChar = textAfter.firstOrNull()?.toString() ?: ""
        if (ctx.type == "statement" && firstChar == "}") ctx = ctx.prev ?: ctx
        val closing = firstChar == ctx.type
        return when {
            ctx.type == "statement" -> ctx.indented + if (firstChar == "{") 0 else context.unit
            ctx.align == true -> ctx.column + if (closing) 0 else 1
            else -> ctx.indented + if (closing) 0 else context.unit
        }
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s*[{}]$")
        )
}
