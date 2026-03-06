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

private val luaBuiltins = setOf(
    "_G", "_VERSION", "assert", "collectgarbage", "dofile", "error",
    "getfenv", "getmetatable", "ipairs", "load", "loadfile", "loadstring",
    "module", "next", "pairs", "pcall", "print", "rawequal", "rawget",
    "rawset", "require", "select", "setfenv", "setmetatable", "tonumber",
    "tostring", "type", "unpack", "xpcall",
    "coroutine.create", "coroutine.resume", "coroutine.running",
    "coroutine.status", "coroutine.wrap", "coroutine.yield",
    "debug.debug", "debug.getfenv", "debug.gethook", "debug.getinfo",
    "debug.getlocal", "debug.getmetatable", "debug.getregistry",
    "debug.getupvalue", "debug.setfenv", "debug.sethook",
    "debug.setlocal", "debug.setmetatable", "debug.setupvalue",
    "debug.traceback",
    "close", "flush", "lines", "read", "seek", "setvbuf", "write",
    "io.close", "io.flush", "io.input", "io.lines", "io.open",
    "io.output", "io.popen", "io.read", "io.stderr", "io.stdin",
    "io.stdout", "io.tmpfile", "io.type", "io.write",
    "math.abs", "math.acos", "math.asin", "math.atan", "math.atan2",
    "math.ceil", "math.cos", "math.cosh", "math.deg", "math.exp",
    "math.floor", "math.fmod", "math.frexp", "math.huge", "math.ldexp",
    "math.log", "math.log10", "math.max", "math.min", "math.modf",
    "math.pi", "math.pow", "math.rad", "math.random", "math.randomseed",
    "math.sin", "math.sinh", "math.sqrt", "math.tan", "math.tanh",
    "os.clock", "os.date", "os.difftime", "os.execute", "os.exit",
    "os.getenv", "os.remove", "os.rename", "os.setlocale", "os.time",
    "os.tmpname",
    "package.cpath", "package.loaded", "package.loaders",
    "package.loadlib", "package.path", "package.preload",
    "package.seeall",
    "string.byte", "string.char", "string.dump", "string.find",
    "string.format", "string.gmatch", "string.gsub", "string.len",
    "string.lower", "string.match", "string.rep", "string.reverse",
    "string.sub", "string.upper",
    "table.concat", "table.insert", "table.maxn", "table.remove",
    "table.sort"
)

private val luaKeywords = setOf(
    "and", "break", "elseif", "false", "nil", "not", "or", "return",
    "true", "function", "end", "if", "then", "else", "do",
    "while", "repeat", "until", "for", "in", "local"
)

private val indentTokens = Regex(
    "^(?:function|if|repeat|do|\\(|\\{)$",
    RegexOption.IGNORE_CASE
)
private val dedentTokens = Regex(
    "^(?:end|until|\\)|\\})$",
    RegexOption.IGNORE_CASE
)
private val dedentPartial = Regex(
    "^(?:end|until|\\)|\\}|else|elseif)",
    RegexOption.IGNORE_CASE
)

data class LuaState(
    var basecol: Int = 0,
    var indentDepth: Int = 0,
    var cur: (StringStream, LuaState) -> String? = ::luaNormal
)

private fun readBracket(stream: StringStream): Int {
    var level = 0
    while (stream.eat("=") != null) ++level
    stream.eat("[")
    return level
}

private fun luaNormal(stream: StringStream, state: LuaState): String? {
    val ch = stream.next() ?: return null
    if (ch == "-" && stream.eat("-") != null) {
        if (stream.eat("[") != null && stream.eat("[") != null) {
            state.cur = luaBracketed(readBracket(stream), "comment")
            return state.cur(stream, state)
        }
        stream.skipToEnd()
        return "comment"
    }
    if (ch == "\"" || ch == "'") {
        state.cur = luaString(ch)
        return state.cur(stream, state)
    }
    if (ch == "[") {
        val pk = stream.peek()
        if (pk != null && Regex("[\\[=]").containsMatchIn(pk)) {
            state.cur = luaBracketed(readBracket(stream), "string")
            return state.cur(stream, state)
        }
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.%]"))
        return "number"
    }
    if (Regex("[\\w_]").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w\\\\\\-_.]"))
        return "variable"
    }
    return null
}

private fun luaBracketed(level: Int, style: String): (StringStream, LuaState) -> String? =
    { stream, state ->
        var curlev: Int? = null
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (curlev == null) {
                if (ch == "]") curlev = 0
            } else if (ch == "=") {
                curlev++
            } else if (ch == "]" && curlev == level) {
                state.cur = ::luaNormal
                break
            } else {
                curlev = null
            }
        }
        style
    }

private fun luaString(quote: String): (StringStream, LuaState) -> String? = { stream, state ->
    var escaped = false
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (ch == quote && !escaped) break
        escaped = !escaped && ch == "\\"
    }
    if (!escaped) state.cur = ::luaNormal
    "string"
}

/** Stream parser for Lua. */
val lua: StreamParser<LuaState> = object : StreamParser<LuaState> {
    override val name: String get() = "lua"

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("""^\s*(?:end|until|else|\)|\})$"""),
            "commentTokens" to mapOf(
                "line" to "--",
                "block" to mapOf("open" to "--[[", "close" to "]]--")
            )
        )

    override fun startState(indentUnit: Int) = LuaState()
    override fun copyState(state: LuaState) = state.copy()

    override fun token(stream: StringStream, state: LuaState): String? {
        if (stream.eatSpace()) return null
        var style = state.cur(stream, state)
        val word = stream.current()
        if (style == "variable") {
            if (word in luaKeywords) {
                style = "keyword"
            } else if (word in luaBuiltins) style = "builtin"
        }
        if (style != "comment" && style != "string") {
            if (indentTokens.matches(word)) {
                ++state.indentDepth
            } else if (dedentTokens.matches(word)) --state.indentDepth
        }
        return style
    }

    override fun indent(state: LuaState, textAfter: String, context: IndentContext): Int {
        val closing = dedentPartial.containsMatchIn(textAfter)
        return state.basecol + context.unit * (
            state.indentDepth - (if (closing) 1 else 0)
            )
    }
}
