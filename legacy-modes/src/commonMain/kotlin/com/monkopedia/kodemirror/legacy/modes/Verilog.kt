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

private val verilogKeywords = (
    "accept_on alias always always_comb always_ff always_latch and assert assign assume " +
        "automatic before begin bind bins binsof bit break buf bufif0 bufif1 byte case casex " +
        "casez cell chandle checker class clocking cmos config const constraint context " +
        "continue cover covergroup coverpoint cross deassign default defparam design disable " +
        "dist do edge else end endcase endchecker endclass endclocking endconfig endfunction " +
        "endgenerate endgroup endinterface endmodule endpackage endprimitive endprogram " +
        "endproperty endspecify endsequence endtable endtask enum event eventually expect " +
        "export extends extern final first_match for force foreach forever fork forkjoin " +
        "function generate genvar global highz0 highz1 if iff ifnone ignore_bins " +
        "illegal_bins implements implies import incdir include initial inout input inside " +
        "instance int integer interconnect interface intersect join join_any join_none large " +
        "let liblist library local localparam logic longint macromodule matches medium " +
        "modport module nand negedge nettype new nexttime nmos nor noshowcancelled not " +
        "notif0 notif1 null or output package packed parameter pmos posedge primitive " +
        "priority program property protected pull0 pull1 pulldown pullup " +
        "pulsestyle_ondetect pulsestyle_onevent pure rand randc randcase randsequence rcmos " +
        "real realtime ref reg reject_on release repeat restrict return rnmos rpmos rtran " +
        "rtranif0 rtranif1 s_always s_eventually s_nexttime s_until s_until_with scalared " +
        "sequence shortint shortreal showcancelled signed small soft solve specify specparam " +
        "static string strong strong0 strong1 struct super supply0 supply1 sync_accept_on " +
        "sync_reject_on table tagged task this throughout time timeprecision timeunit tran " +
        "tranif0 tranif1 tri tri0 tri1 triand trior trireg type typedef union unique unique0 " +
        "unsigned until until_with untyped use uwire var vectored virtual void wait " +
        "wait_order wand weak weak0 weak1 while wildcard wire with within wor xnor xor"
    ).split(" ").toSet()

private val verilogIsOperatorChar = Regex("[+\\-*/!~&|^%=?:]")
private val verilogIsBracketChar = Regex("[\\[\\]{}()]")
private val verilogUnsignedNumber = Regex("^\\d[0-9_]*")
private val verilogDecimalLiteral = Regex("^\\d*\\s*'s?d\\s*\\d[0-9_]*", RegexOption.IGNORE_CASE)
private val verilogBinaryLiteral = Regex("^\\d*\\s*'s?b\\s*[xz01][xz01_]*", RegexOption.IGNORE_CASE)
private val verilogOctLiteral = Regex("^\\d*\\s*'s?o\\s*[xz0-7][xz0-7_]*", RegexOption.IGNORE_CASE)
private val verilogHexLiteral = Regex(
    "^\\d*\\s*'s?h\\s*[0-9a-fxz?][0-9a-fxz?_]*",
    RegexOption.IGNORE_CASE
)
private val verilogRealLiteral = Regex(
    "^(\\d[\\d_]*(\\.[\\d_]*)?E-?[\\d_]+)|(\\d[\\d_]*\\.[\\d_]*)",
    RegexOption.IGNORE_CASE
)

private val verilogBlockKeywords = (
    "case checker class clocking config function generate interface module package " +
        "primitive program property specify sequence table task"
    ).split(" ").toSet()

private val verilogStatementKeywords = (
    "always always_comb always_ff always_latch assert assign assume else export for " +
        "foreach forever if import initial repeat while"
    ).split(" ").toSet()

data class VerilogContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: VerilogContext?
)

data class VerilogState(
    var tokenize: ((StringStream, VerilogState) -> String?)? = null,
    var context: VerilogContext,
    var indented: Int = 0,
    var startOfLine: Boolean = true
)

/** Stream parser for Verilog/SystemVerilog. */
val verilog: StreamParser<VerilogState> = run {
    val openClose = mutableMapOf<String, String?>()
    for (keyword in verilogBlockKeywords) {
        openClose[keyword] = "end$keyword"
    }
    openClose["begin"] = "end"
    openClose["casex"] = "endcase"
    openClose["casez"] = "endcase"
    openClose["do"] = "while"
    openClose["fork"] = "join;join_any;join_none"
    openClose["covergroup"] = "endgroup"

    var curPunc: String? = null
    var curKeyword: String? = null

    fun isClosing(text: String?, contextClosing: String?): Boolean {
        if (text == null || contextClosing == null) return false
        if (text == contextClosing) return true
        return contextClosing.split(";").any { it == text }
    }

    fun tokenString(quote: String): (StringStream, VerilogState) -> String? {
        return fun(stream: StringStream, state: VerilogState): String? {
            var escaped = false
            var next: String?
            var end = false
            while (true) {
                next = stream.next()
                if (next == null) break
                if (next == quote && !escaped) {
                    end = true
                    break
                }
                escaped = !escaped && next == "\\"
            }
            if (end) state.tokenize = null
            return "string"
        }
    }

    fun tokenComment(stream: StringStream, state: VerilogState): String {
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

    fun tokenBase(stream: StringStream, state: VerilogState): String? {
        val ch = stream.peek() ?: run {
            stream.next()
            return null
        }

        if (Regex("[,;:.]").containsMatchIn(ch)) {
            curPunc = stream.next()
            return null
        }
        if (verilogIsBracketChar.containsMatchIn(ch)) {
            curPunc = stream.next()
            return "bracket"
        }
        if (ch == "`") {
            stream.next()
            if (stream.eatWhile(Regex("[\\w\$_]"))) {
                return "def"
            }
            return null
        }
        if (ch == "$") {
            stream.next()
            if (stream.eatWhile(Regex("[\\w\$_]"))) {
                return "meta"
            }
            return null
        }
        if (ch == "#") {
            stream.next()
            stream.eatWhile(Regex("[\\d_.]"))
            return "def"
        }
        if (ch == "\"") {
            stream.next()
            state.tokenize = tokenString(ch)
            return state.tokenize!!(stream, state)
        }
        if (ch == "/") {
            stream.next()
            if (stream.eat("*") != null) {
                state.tokenize = ::tokenComment
                return tokenComment(stream, state)
            }
            if (stream.eat("/") != null) {
                stream.skipToEnd()
                return "comment"
            }
            stream.backUp(1)
        }

        if (stream.match(verilogRealLiteral) != null ||
            stream.match(verilogDecimalLiteral) != null ||
            stream.match(verilogBinaryLiteral) != null ||
            stream.match(verilogOctLiteral) != null ||
            stream.match(verilogHexLiteral) != null ||
            stream.match(verilogUnsignedNumber) != null
        ) {
            return "number"
        }

        if (stream.eatWhile(verilogIsOperatorChar)) {
            return "meta"
        }

        if (stream.eatWhile(Regex("[\\w\$_]"))) {
            val cur = stream.current()
            if (verilogKeywords.contains(cur)) {
                if (openClose.containsKey(cur) && openClose[cur] != null) {
                    curPunc = "newblock"
                }
                if (verilogStatementKeywords.contains(cur)) {
                    curPunc = "newstatement"
                }
                curKeyword = cur
                return "keyword"
            }
            return "variable"
        }

        stream.next()
        return null
    }

    fun pushContext(state: VerilogState, col: Int, type: String) {
        state.context = VerilogContext(state.indented, col, type, null, state.context)
    }

    fun popContext(state: VerilogState): VerilogContext {
        val t = state.context.type
        if (t == ")" || t == "]" || t == "}") {
            state.indented = state.context.indented
        }
        val prev = state.context.prev ?: state.context
        state.context = prev
        return prev
    }

    object : StreamParser<VerilogState> {
        override val name: String get() = "verilog"
        override val languageData: Map<String, Any>
            get() = mapOf(
                "commentTokens" to mapOf(
                    "line" to "//",
                    "block" to mapOf("open" to "/*", "close" to "*/")
                )
            )

        override fun startState(indentUnit: Int) = VerilogState(
            context = VerilogContext(-indentUnit, 0, "top", false, null)
        )

        override fun copyState(state: VerilogState) = VerilogState(
            tokenize = state.tokenize,
            context = state.context.copy(),
            indented = state.indented,
            startOfLine = state.startOfLine
        )

        override fun token(stream: StringStream, state: VerilogState): String? {
            var ctx = state.context
            if (stream.sol()) {
                if (ctx.align == null) ctx.align = false
                state.indented = stream.indentation()
                state.startOfLine = true
            }
            if (stream.eatSpace()) return null
            curPunc = null
            curKeyword = null
            val style = (state.tokenize ?: ::tokenBase)(stream, state)
            if (style == "comment" || style == "meta" || style == "variable") return style
            if (ctx.align == null) ctx.align = true

            if (curPunc == ctx.type) {
                popContext(state)
            } else if ((curPunc == ";" && ctx.type == "statement") ||
                (ctx.type.isNotEmpty() && isClosing(curKeyword, ctx.type))
            ) {
                ctx = popContext(state)
                while (ctx.type == "statement") ctx = popContext(state)
            } else if (curPunc == "{") {
                pushContext(state, stream.column(), "}")
            } else if (curPunc == "[") {
                pushContext(state, stream.column(), "]")
            } else if (curPunc == "(") {
                pushContext(state, stream.column(), ")")
            } else if (ctx.type == "endcase" && curPunc == ":") {
                pushContext(state, stream.column(), "statement")
            } else if (curPunc == "newstatement") {
                pushContext(state, stream.column(), "statement")
            } else if (curPunc == "newblock") {
                if (curKeyword == "function" && ctx.type in listOf("statement", "endgroup")) {
                    // do nothing
                } else if (curKeyword == "task" && ctx.type == "statement") {
                    // do nothing
                } else {
                    val close = openClose[curKeyword]
                    if (close != null) {
                        pushContext(state, stream.column(), close)
                    }
                }
            }

            state.startOfLine = false
            return style
        }

        override fun indent(state: VerilogState, textAfter: String, context: IndentContext): Int? {
            if (state.tokenize != null) return null
            var ctx = state.context
            val firstChar = if (textAfter.isNotEmpty()) textAfter[0].toString() else ""
            if (ctx.type == "statement" && firstChar == "}") ctx = ctx.prev ?: ctx
            var closing = false
            val possibleClosing = Regex("^((\\w+)|[)}\\]])").find(textAfter)
            if (possibleClosing != null) {
                closing = isClosing(possibleClosing.value, ctx.type)
            }
            if (ctx.type == "statement") {
                return ctx.indented + (if (firstChar == "{") 0 else context.unit)
            }
            if (Regex("[)\\]}]").containsMatchIn(ctx.type) && ctx.align == true) {
                return ctx.column + (if (closing) 0 else 1)
            }
            if (ctx.type == ")" && !closing) {
                return ctx.indented + context.unit
            }
            return ctx.indented + (if (closing) 0 else context.unit)
        }
    }
}
