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

private fun vhdlWords(str: String): Set<String> {
    val obj = mutableSetOf<String>()
    for (w in str.split(",")) {
        obj.add(w)
        obj.add(w.uppercase())
        obj.add(w.replaceFirstChar { it.uppercase() })
    }
    return obj
}

private val vhdlAtoms = vhdlWords("null")
private val vhdlKeywords = vhdlWords(
    "abs,access,after,alias,all,and,architecture,array,assert,attribute,begin,block," +
        "body,buffer,bus,case,component,configuration,constant,disconnect,downto,else," +
        "elsif,end,end block,end case,end component,end for,end generate,end if,end loop," +
        "end process,end record,end units,entity,exit,file,for,function,generate,generic," +
        "generic map,group,guarded,if,impure,in,inertial,inout,is,label,library,linkage," +
        "literal,loop,map,mod,nand,new,next,nor,null,of,on,open,or,others,out,package," +
        "package body,port,port map,postponed,procedure,process,pure,range,record," +
        "register,reject,rem,report,return,rol,ror,select,severity,signal,sla,sll,sra," +
        "srl,subtype,then,to,transport,type,unaffected,units,until,use,variable,wait," +
        "when,while,with,xnor,xor"
)
private val vhdlBlockKeywords = vhdlWords(
    "architecture,entity,begin,case,port,else,elsif,end,for,function,if"
)
private val vhdlIsOperatorChar = Regex("[&|~><!)(*#%@+/=?:;}{,.^\\-\\[\\]]")

data class VhdlContext(
    val indented: Int,
    val column: Int,
    val type: String,
    var align: Boolean?,
    val prev: VhdlContext?
)

data class VhdlState(
    var tokenize: ((StringStream, VhdlState) -> String?)? = null,
    var context: VhdlContext,
    var indented: Int = 0,
    var startOfLine: Boolean = true
)

val vhdl: StreamParser<VhdlState> = object : StreamParser<VhdlState> {
    override val name: String get() = "vhdl"
    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "--"))

    private var curPunc: String? = null

    private fun metaHook(stream: StringStream): String {
        stream.eatWhile(Regex("[\\w\$_]"))
        return "meta"
    }

    private fun tokenString(quote: String): (StringStream, VhdlState) -> String? {
        return fun(stream: StringStream, state: VhdlState): String? {
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
                escaped = !escaped && next == "--"
            }
            if (end) state.tokenize = null
            return "string"
        }
    }

    private fun tokenString2(quote: String): (StringStream, VhdlState) -> String? {
        return fun(stream: StringStream, state: VhdlState): String? {
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
                escaped = !escaped && next == "--"
            }
            if (end) state.tokenize = null
            return "string.special"
        }
    }

    private fun tokenBase(stream: StringStream, state: VhdlState): String? {
        val ch = stream.next() ?: return null
        if (ch == "`" || ch == "$") {
            return metaHook(stream)
        }
        if (ch == "\"") {
            state.tokenize = tokenString2(ch)
            return state.tokenize!!(stream, state)
        }
        if (ch == "'") {
            state.tokenize = tokenString(ch)
            return state.tokenize!!(stream, state)
        }
        if (Regex("[\\[\\]{}(),;:.]").containsMatchIn(ch)) {
            curPunc = ch
            return null
        }
        if (Regex("[\\d']").containsMatchIn(ch)) {
            stream.eatWhile(Regex("[\\w.']"))
            return "number"
        }
        if (ch == "-") {
            if (stream.eat("-") != null) {
                stream.skipToEnd()
                return "comment"
            }
        }
        if (vhdlIsOperatorChar.containsMatchIn(ch)) {
            stream.eatWhile(vhdlIsOperatorChar)
            return "operator"
        }
        stream.eatWhile(Regex("[\\w\$_]"))
        val cur = stream.current()
        if (vhdlKeywords.contains(cur.lowercase())) {
            if (vhdlBlockKeywords.contains(cur)) curPunc = "newstatement"
            return "keyword"
        }
        if (vhdlAtoms.contains(cur)) return "atom"
        return "variable"
    }

    private fun pushContext(state: VhdlState, col: Int, type: String) {
        state.context = VhdlContext(state.indented, col, type, null, state.context)
    }

    private fun popContext(state: VhdlState): VhdlContext {
        val t = state.context.type
        if (t == ")" || t == "]" || t == "}") {
            state.indented = state.context.indented
        }
        val prev = state.context.prev ?: state.context
        state.context = prev
        return prev
    }

    override fun startState(indentUnit: Int) = VhdlState(
        context = VhdlContext(-indentUnit, 0, "top", false, null)
    )

    override fun copyState(state: VhdlState) = VhdlState(
        tokenize = state.tokenize,
        context = state.context.copy(),
        indented = state.indented,
        startOfLine = state.startOfLine
    )

    override fun token(stream: StringStream, state: VhdlState): String? {
        var ctx = state.context
        if (stream.sol()) {
            if (ctx.align == null) ctx.align = false
            state.indented = stream.indentation()
            state.startOfLine = true
        }
        if (stream.eatSpace()) return null
        curPunc = null
        val style = (state.tokenize ?: ::tokenBase)(stream, state)
        if (style == "comment" || style == "meta") return style
        if (ctx.align == null) ctx.align = true

        if ((curPunc == ";" || curPunc == ":") && ctx.type == "statement") {
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
        } else if (ctx.type == "}" || ctx.type == "top" ||
            (ctx.type == "statement" && curPunc == "newstatement")
        ) {
            pushContext(state, stream.column(), "statement")
        }
        state.startOfLine = false
        return style
    }

    override fun indent(state: VhdlState, textAfter: String, cx: IndentContext): Int? {
        if (state.tokenize != null) return 0
        val firstChar = if (textAfter.isNotEmpty()) textAfter[0].toString() else ""
        val ctx = state.context
        val closing = firstChar == ctx.type
        if (ctx.type == "statement") {
            return ctx.indented + (if (firstChar == "{") 0 else cx.unit)
        }
        if (ctx.align == true) return ctx.column + (if (closing) 0 else 1)
        return ctx.indented + (if (closing) 0 else cx.unit)
    }
}
