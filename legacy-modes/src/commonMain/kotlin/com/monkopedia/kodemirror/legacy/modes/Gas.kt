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

data class GasState(
    var tokenize: ((StringStream, GasState) -> String?)? = null
)

private fun mkGas(arch: String): StreamParser<GasState> {
    val custom = mutableListOf<(String, StringStream) -> String?>()
    var lineCommentStartSymbol = ""

    val directives = mutableMapOf(
        ".abort" to "builtin", ".align" to "builtin",
        ".altmacro" to "builtin", ".ascii" to "builtin",
        ".asciz" to "builtin", ".balign" to "builtin",
        ".balignw" to "builtin", ".balignl" to "builtin",
        ".bundle_align_mode" to "builtin", ".bundle_lock" to "builtin",
        ".bundle_unlock" to "builtin", ".byte" to "builtin",
        ".cfi_startproc" to "builtin", ".comm" to "builtin",
        ".data" to "builtin", ".def" to "builtin",
        ".desc" to "builtin", ".dim" to "builtin",
        ".double" to "builtin", ".eject" to "builtin",
        ".else" to "builtin", ".elseif" to "builtin",
        ".end" to "builtin", ".endef" to "builtin",
        ".endfunc" to "builtin", ".endif" to "builtin",
        ".equ" to "builtin", ".equiv" to "builtin",
        ".eqv" to "builtin", ".err" to "builtin",
        ".error" to "builtin", ".exitm" to "builtin",
        ".extern" to "builtin", ".fail" to "builtin",
        ".file" to "builtin", ".fill" to "builtin",
        ".float" to "builtin", ".func" to "builtin",
        ".global" to "builtin", ".gnu_attribute" to "builtin",
        ".hidden" to "builtin", ".hword" to "builtin",
        ".ident" to "builtin", ".if" to "builtin",
        ".incbin" to "builtin", ".include" to "builtin",
        ".int" to "builtin", ".internal" to "builtin",
        ".irp" to "builtin", ".irpc" to "builtin",
        ".lcomm" to "builtin", ".lflags" to "builtin",
        ".line" to "builtin", ".linkonce" to "builtin",
        ".list" to "builtin", ".ln" to "builtin",
        ".loc" to "builtin", ".loc_mark_labels" to "builtin",
        ".local" to "builtin", ".long" to "builtin",
        ".macro" to "builtin", ".mri" to "builtin",
        ".noaltmacro" to "builtin", ".nolist" to "builtin",
        ".octa" to "builtin", ".offset" to "builtin",
        ".org" to "builtin", ".p2align" to "builtin",
        ".popsection" to "builtin", ".previous" to "builtin",
        ".print" to "builtin", ".protected" to "builtin",
        ".psize" to "builtin", ".purgem" to "builtin",
        ".pushsection" to "builtin", ".quad" to "builtin",
        ".reloc" to "builtin", ".rept" to "builtin",
        ".sbttl" to "builtin", ".scl" to "builtin",
        ".section" to "builtin", ".set" to "builtin",
        ".short" to "builtin", ".single" to "builtin",
        ".size" to "builtin", ".skip" to "builtin",
        ".sleb128" to "builtin", ".space" to "builtin",
        ".stab" to "builtin", ".string" to "builtin",
        ".struct" to "builtin", ".subsection" to "builtin",
        ".symver" to "builtin", ".tag" to "builtin",
        ".text" to "builtin", ".title" to "builtin",
        ".type" to "builtin", ".uleb128" to "builtin",
        ".val" to "builtin", ".version" to "builtin",
        ".vtable_entry" to "builtin", ".vtable_inherit" to "builtin",
        ".warning" to "builtin", ".weak" to "builtin",
        ".weakref" to "builtin", ".word" to "builtin"
    )

    val registers = mutableMapOf<String, String>()

    when (arch) {
        "x86" -> {
            lineCommentStartSymbol = "#"
            registers["al"] = "variable"
            registers["ah"] = "variable"
            registers["ax"] = "variable"
            registers["eax"] = "variableName.special"
            registers["rax"] = "variableName.special"
            registers["bl"] = "variable"
            registers["bh"] = "variable"
            registers["bx"] = "variable"
            registers["ebx"] = "variableName.special"
            registers["rbx"] = "variableName.special"
            registers["cl"] = "variable"
            registers["ch"] = "variable"
            registers["cx"] = "variable"
            registers["ecx"] = "variableName.special"
            registers["rcx"] = "variableName.special"
            registers["dl"] = "variable"
            registers["dh"] = "variable"
            registers["dx"] = "variable"
            registers["edx"] = "variableName.special"
            registers["rdx"] = "variableName.special"
            registers["si"] = "variable"
            registers["esi"] = "variableName.special"
            registers["rsi"] = "variableName.special"
            registers["di"] = "variable"
            registers["edi"] = "variableName.special"
            registers["rdi"] = "variableName.special"
            registers["sp"] = "variable"
            registers["esp"] = "variableName.special"
            registers["rsp"] = "variableName.special"
            registers["bp"] = "variable"
            registers["ebp"] = "variableName.special"
            registers["rbp"] = "variableName.special"
            registers["ip"] = "variable"
            registers["eip"] = "variableName.special"
            registers["rip"] = "variableName.special"
            registers["cs"] = "keyword"
            registers["ds"] = "keyword"
            registers["ss"] = "keyword"
            registers["es"] = "keyword"
            registers["fs"] = "keyword"
            registers["gs"] = "keyword"
        }
        "arm", "armv6" -> {
            lineCommentStartSymbol = "@"
            directives["syntax"] = "builtin"
            for (i in 0..12) registers["r$i"] = "variable"
            registers["sp"] = "variableName.special"
            registers["lr"] = "variableName.special"
            registers["pc"] = "variableName.special"
            registers["r13"] = "variableName.special"
            registers["r14"] = "variableName.special"
            registers["r15"] = "variableName.special"
            custom.add { ch, stream ->
                if (ch == "#") {
                    stream.eatWhile(Regex("\\w"))
                    "number"
                } else {
                    null
                }
            }
        }
    }

    fun nextUntilUnescaped(stream: StringStream, end: String): Boolean {
        var escaped = false
        while (true) {
            val next = stream.next() ?: return escaped
            if (next == end && !escaped) return false
            escaped = !escaped && next == "\\"
        }
    }

    fun gasClikeComment(stream: StringStream, state: GasState): String {
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

    return object : StreamParser<GasState> {
        override val name: String get() = "gas"

        override fun startState(indentUnit: Int) = GasState()
        override fun copyState(state: GasState) = state.copy()

        override fun token(stream: StringStream, state: GasState): String? {
            if (state.tokenize != null) {
                return state.tokenize!!(stream, state)
            }
            if (stream.eatSpace()) return null

            val ch = stream.next() ?: return null

            if (ch == "/") {
                if (stream.eat("*") != null) {
                    state.tokenize = ::gasClikeComment
                    return gasClikeComment(stream, state)
                }
            }

            if (lineCommentStartSymbol.isNotEmpty() && ch == lineCommentStartSymbol) {
                stream.skipToEnd()
                return "comment"
            }

            if (ch == "\"") {
                nextUntilUnescaped(stream, "\"")
                return "string"
            }

            if (ch == ".") {
                stream.eatWhile(Regex("\\w"))
                val cur = stream.current().lowercase()
                return directives[cur]
            }

            if (ch == "=") {
                stream.eatWhile(Regex("\\w"))
                return "tag"
            }

            if (ch == "{" || ch == "}") return "bracket"

            if (Regex("\\d").containsMatchIn(ch)) {
                if (ch == "0" && stream.eat("x") != null) {
                    stream.eatWhile(Regex("[0-9a-fA-F]"))
                    return "number"
                }
                stream.eatWhile(Regex("\\d"))
                return "number"
            }

            if (Regex("\\w").containsMatchIn(ch)) {
                stream.eatWhile(Regex("\\w"))
                if (stream.eat(":") != null) return "tag"
                val cur = stream.current().lowercase()
                return registers[cur]
            }

            for (fn in custom) {
                val style = fn(ch, stream)
                if (style != null) return style
            }

            return null
        }

        override val languageData: Map<String, Any>
            get() = mapOf(
                "commentTokens" to buildMap {
                    if (lineCommentStartSymbol.isNotEmpty()) {
                        put("line", lineCommentStartSymbol)
                    }
                    put("block", mapOf("open" to "/*", "close" to "*/"))
                }
            )
    }
}

val gas: StreamParser<GasState> = mkGas("x86")
val gasArm: StreamParser<GasState> = mkGas("arm")
