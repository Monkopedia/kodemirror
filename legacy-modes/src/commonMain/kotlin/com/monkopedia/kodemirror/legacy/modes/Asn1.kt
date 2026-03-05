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

private val asn1Keywords = (
    "DEFINITIONS OBJECTS IF DERIVED INFORMATION ACTION REPLY ANY " +
        "NAMED CHARACTERIZED BEHAVIOUR REGISTERED WITH AS IDENTIFIED " +
        "CONSTRAINED BY PRESENT BEGIN IMPORTS FROM UNITS SYNTAX " +
        "MIN-ACCESS MAX-ACCESS MINACCESS MAXACCESS REVISION STATUS " +
        "DESCRIPTION SEQUENCE SET COMPONENTS OF CHOICE " +
        "DistinguishedName ENUMERATED SIZE MODULE END INDEX AUGMENTS " +
        "EXTENSIBILITY IMPLIED EXPORTS"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val asn1CmipVerbs = setOf(
    "ACTIONS",
    "ADD",
    "GET",
    "NOTIFICATIONS",
    "REPLACE",
    "REMOVE"
)

private val asn1CompareTypes = (
    "OPTIONAL DEFAULT MANAGED MODULE-TYPE MODULE_IDENTITY " +
        "MODULE-COMPLIANCE OBJECT-TYPE OBJECT-IDENTITY " +
        "OBJECT-COMPLIANCE MODE CONFIRMED CONDITIONAL SUBORDINATE " +
        "SUPERIOR CLASS TRUE FALSE NULL TEXTUAL-CONVENTION"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val asn1Status = setOf(
    "current",
    "deprecated",
    "mandatory",
    "obsolete"
)

private val asn1Tags = setOf(
    "APPLICATION",
    "AUTOMATIC",
    "EXPLICIT",
    "IMPLICIT",
    "PRIVATE",
    "TAGS",
    "UNIVERSAL"
)

private val asn1Storage = (
    "BOOLEAN INTEGER OBJECT IDENTIFIER BIT OCTET STRING UTCTime " +
        "InterfaceIndex IANAifType CMIP-Attribute REAL PACKAGE " +
        "PACKAGES IpAddress PhysAddress NetworkAddress BITS " +
        "BMPString TimeStamp TimeTicks TruthValue RowStatus " +
        "DisplayString GeneralString GraphicString IA5String " +
        "NumericString PrintableString SnmpAdminString " +
        "TeletexString UTF8String VideotexString VisibleString " +
        "StringStore ISO646String T61String UniversalString " +
        "Unsigned32 Integer32 Gauge Gauge32 Counter Counter32 " +
        "Counter64"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val asn1Modifier = (
    "ATTRIBUTE ATTRIBUTES MANDATORY-GROUP MANDATORY-GROUPS GROUP " +
        "GROUPS ELEMENTS EQUALITY ORDERING SUBSTRINGS DEFINED"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val asn1AccessTypes = (
    "not-accessible accessible-for-notify read-only read-create " +
        "read-write"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val asn1IsOperatorChar = Regex("[|^]")

class Asn1Context(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: Asn1Context?
)

data class Asn1State(
    var tokenize: ((StringStream, Asn1State) -> String?)? = null,
    var context: Asn1Context? = null,
    var indented: Int = 0,
    var startOfLine: Boolean = true,
    var curPunc: String? = null
)

private fun asn1PushContext(state: Asn1State, col: Int, type: String) {
    val indent = state.indented
    state.context = Asn1Context(indent, col, type, null, state.context)
}

private fun asn1PopContext(state: Asn1State) {
    val ctx = state.context ?: return
    val t = ctx.type
    if (t == ")" || t == "]" || t == "}") {
        state.indented = ctx.indented
    }
    state.context = ctx.prev
}

private fun asn1TokenBase(stream: StringStream, state: Asn1State): String? {
    val ch = stream.next() ?: return null
    if (ch == "\"" || ch == "'") {
        state.tokenize = asn1TokenString(ch)
        return state.tokenize!!(stream, state)
    }
    if (Regex("[\\[\\](){}:=,;]").containsMatchIn(ch)) {
        state.curPunc = ch
        return "punctuation"
    }
    if (ch == "-") {
        if (stream.eat("-") != null) {
            stream.skipToEnd()
            return "comment"
        }
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    }
    if (asn1IsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(asn1IsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w-]"))
    val cur = stream.current()
    if (cur in asn1Keywords) return "keyword"
    if (cur in asn1CmipVerbs) return "variableName"
    if (cur in asn1CompareTypes) return "atom"
    if (cur in asn1Status) return "comment"
    if (cur in asn1Tags) return "typeName"
    if (cur in asn1Storage) return "modifier"
    if (cur in asn1Modifier) return "modifier"
    if (cur in asn1AccessTypes) return "modifier"
    return "variableName"
}

private fun asn1TokenString(quote: String): (StringStream, Asn1State) -> String? =
    { stream, state ->
        var escaped = false
        var end = false
        var next: String?
        while (true) {
            next = stream.next()
            if (next == null) break
            if (next == quote && !escaped) {
                val afterNext = stream.peek()
                if (afterNext != null) {
                    val lower = afterNext.lowercase()
                    if (lower == "b" || lower == "h" || lower == "o") {
                        stream.next()
                    }
                }
                end = true
                break
            }
            escaped = !escaped && next == "\\"
        }
        if (end) state.tokenize = null
        "string"
    }

@Suppress("LongMethod")
fun asn1(indentStatements: Boolean = true): StreamParser<Asn1State> =
    object : StreamParser<Asn1State> {
        override val name: String get() = "asn1"

        override val languageData: Map<String, Any>
            get() = mapOf(
                "indentOnInput" to Regex("""^\s*[{}]$"""),
                "commentTokens" to mapOf("line" to "--")
            )

        override fun startState(indentUnit: Int): Asn1State {
            return Asn1State(
                context = Asn1Context(
                    indented = -2,
                    column = 0,
                    type = "top",
                    align = false,
                    prev = null
                )
            )
        }

        override fun copyState(state: Asn1State): Asn1State {
            return state.copy(context = copyAsn1Context(state.context))
        }

        @Suppress("CyclomaticComplexMethod")
        override fun token(stream: StringStream, state: Asn1State): String? {
            var ctx = state.context ?: return null
            if (stream.sol()) {
                if (ctx.align == null) ctx.align = false
                state.indented = stream.indentation()
                state.startOfLine = true
            }
            if (stream.eatSpace()) return null
            state.curPunc = null
            val style = (state.tokenize ?: ::asn1TokenBase)(stream, state)
            if (style == "comment") return style
            if (ctx.align == null) ctx.align = true

            val curPunc = state.curPunc
            if ((curPunc == ";" || curPunc == ":" || curPunc == ",") &&
                ctx.type == "statement"
            ) {
                asn1PopContext(state)
            } else if (curPunc == "{") {
                asn1PushContext(state, stream.column(), "}")
            } else if (curPunc == "[") {
                asn1PushContext(state, stream.column(), "]")
            } else if (curPunc == "(") {
                asn1PushContext(state, stream.column(), ")")
            } else if (curPunc == "}") {
                ctx = state.context ?: return style
                while (ctx.type == "statement") {
                    asn1PopContext(state)
                    ctx = state.context ?: return style
                }
                if (ctx.type == "}") {
                    asn1PopContext(state)
                    ctx = state.context ?: return style
                }
                while (ctx.type == "statement") {
                    asn1PopContext(state)
                    ctx = state.context ?: return style
                }
            } else if (curPunc == ctx.type) {
                asn1PopContext(state)
            } else if (
                indentStatements &&
                (
                    (
                        (ctx.type == "}" || ctx.type == "top") &&
                            curPunc != ";"
                        ) ||
                        (
                            ctx.type == "statement" &&
                                curPunc == "newstatement"
                            )
                    )
            ) {
                asn1PushContext(state, stream.column(), "statement")
            }
            state.startOfLine = false
            return style
        }

        override fun indent(state: Asn1State, textAfter: String, context: IndentContext): Int? {
            val ctx = state.context ?: return null
            if (ctx.type == "top") return null
            val firstChar = textAfter.firstOrNull()?.toString()
            val closing = firstChar == ctx.type
            return if (ctx.align == true) {
                ctx.column + (if (closing) 0 else 1)
            } else {
                ctx.indented + (if (closing) 0 else context.unit)
            }
        }
    }

private fun copyAsn1Context(ctx: Asn1Context?): Asn1Context? {
    if (ctx == null) return null
    return Asn1Context(
        indented = ctx.indented,
        column = ctx.column,
        type = ctx.type,
        align = ctx.align,
        prev = copyAsn1Context(ctx.prev)
    )
}
