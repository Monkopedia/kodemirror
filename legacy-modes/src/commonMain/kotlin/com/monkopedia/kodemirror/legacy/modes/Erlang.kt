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

data class ErlangToken(
    val token: String,
    val column: Int,
    val indent: Int,
    val type: String?
)

class ErlangState(
    var tokenStack: MutableList<ErlangToken> = mutableListOf(),
    var in_string: Boolean = false,
    var in_atom: Boolean = false
)

private val erlTypeWords = listOf("-type", "-spec", "-export_type", "-opaque")
private val erlKeywordWords = listOf(
    "after", "begin", "catch", "case", "cond", "end", "fun", "if",
    "let", "of", "query", "receive", "try", "when"
)
private val erlSeparatorRE = Regex("[\\->,;]")
private val erlSeparatorWords = listOf("->", ";", ",")
private val erlOperatorAtomWords = listOf(
    "and", "andalso", "band", "bnot", "bor", "bsl", "bsr", "bxor",
    "div", "not", "or", "orelse", "rem", "xor"
)
private val erlOperatorSymbolRE = Regex("[+\\-*/<>=|:!]")
private val erlOperatorSymbolWords = listOf(
    "=", "+", "-", "*", "/", ">", ">=", "<", "=<", "=:=", "==",
    "=/=", "/=", "||", "<-", "!"
)
private val erlOpenParenRE = Regex("[<(\\[{]")
private val erlOpenParenWords = listOf("<<", "(", "[", "{")
private val erlCloseParenRE = Regex("[>)\\]}]")
private val erlCloseParenWords = listOf("}", "]", ")", ">>")
private val erlGuardWords = listOf(
    "is_atom", "is_binary", "is_bitstring", "is_boolean", "is_float",
    "is_function", "is_integer", "is_list", "is_number", "is_pid",
    "is_port", "is_record", "is_reference", "is_tuple",
    "atom", "binary", "bitstring", "boolean", "function", "integer", "list",
    "number", "pid", "port", "record", "reference", "tuple"
)
private val erlBifWords = listOf(
    "abs", "adler32", "adler32_combine", "alive", "apply",
    "atom_to_binary", "atom_to_list", "binary_to_atom",
    "binary_to_existing_atom", "binary_to_list", "binary_to_term",
    "bit_size", "bitstring_to_list", "byte_size", "check_process_code",
    "contact_binary", "crc32", "crc32_combine", "date", "decode_packet",
    "delete_module", "disconnect_node", "element", "erase", "exit",
    "float", "float_to_list", "garbage_collect", "get", "get_keys",
    "group_leader", "halt", "hd", "integer_to_list", "internal_bif",
    "iolist_size", "iolist_to_binary", "is_alive", "is_atom", "is_binary",
    "is_bitstring", "is_boolean", "is_float", "is_function", "is_integer",
    "is_list", "is_number", "is_pid", "is_port", "is_process_alive",
    "is_record", "is_reference", "is_tuple", "length", "link",
    "list_to_atom", "list_to_binary", "list_to_bitstring",
    "list_to_existing_atom", "list_to_float", "list_to_integer",
    "list_to_pid", "list_to_tuple", "load_module", "make_ref",
    "module_loaded", "monitor_node", "node", "node_link", "node_unlink",
    "nodes", "notalive", "now", "open_port", "pid_to_list", "port_close",
    "port_command", "port_connect", "port_control", "pre_loaded",
    "process_flag", "process_info", "processes", "purge_module", "put",
    "register", "registered", "round", "self", "setelement", "size",
    "spawn", "spawn_link", "spawn_monitor", "spawn_opt", "split_binary",
    "statistics", "term_to_binary", "time", "throw", "tl", "trunc",
    "tuple_size", "tuple_to_list", "unlink", "unregister", "whereis"
)
private val erlAnumRE = Regex("[\\w@\u00D8-\u00DE\u00C0-\u00D6\u00DF-\u00F6\u00F8-\u00FF]")
private val erlEscapesRE = Regex(
    "[0-7]{1,3}|[bdefnrstv\\\\\"']|\\^[a-zA-Z]|x[0-9a-zA-Z]{2}|x\\{[0-9a-zA-Z]+\\}"
)

private fun erlQuote(stream: StringStream, quoteChar: String, escapeChar: String): Boolean {
    while (!stream.eol()) {
        val ch = stream.next()
        if (ch == quoteChar) {
            return true
        } else if (ch == escapeChar) stream.next()
    }
    return false
}

private fun erlDoubleQuote(stream: StringStream) = erlQuote(stream, "\"", "\\")
private fun erlSingleQuote(stream: StringStream) = erlQuote(stream, "'", "\\")

private fun erlLookahead(stream: StringStream): String {
    val m = stream.match(Regex("^\\s*([^\\s%])"), false)
    return if (m != null) m.groupValues[1] else ""
}

private fun erlIsMember(element: String, list: List<String>) = element in list

private fun erlPeekToken(state: ErlangState, depth: Int = 1): ErlangToken? {
    val len = state.tokenStack.size
    return if (len < depth) null else state.tokenStack[len - depth]
}

private fun erlFakeToken(type: String) = ErlangToken(type, 0, 0, type)

@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun erlMaybeDropPre(
    s: MutableList<ErlangToken>,
    token: ErlangToken
): MutableList<ErlangToken> {
    val last = s.size - 1
    if (last > 0 && s[last].type == "record" && token.type == "dot") {
        s.removeAt(last)
    } else if (last > 0 && s[last].type == "group") {
        s.removeAt(last)
        s.add(token)
    } else {
        s.add(token)
    }
    return s
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
private fun erlMaybeDropPost(s: MutableList<ErlangToken>): MutableList<ErlangToken> {
    if (s.isEmpty()) return s
    val last = s.size - 1

    if (s[last].type == "dot") return mutableListOf()
    if (last > 1 && s[last].type == "fun" && s[last - 1].token == "fun") {
        return s.subList(0, last - 1).toMutableList()
    }

    val result = when (s[last].token) {
        "}" -> erlD(s, mapOf("g" to listOf("{")))
        "]" -> erlD(s, mapOf("i" to listOf("[")))
        ")" -> erlD(s, mapOf("i" to listOf("(")))
        ">>" -> erlD(s, mapOf("i" to listOf("<<")))
        "end" -> erlD(
            s,
            mapOf("i" to listOf("begin", "case", "fun", "if", "receive", "try"))
        )
        "," -> erlD(
            s,
            mapOf(
                "e" to listOf(
                    "begin", "try", "when", "->", ",", "(", "[", "{", "<<"
                )
            )
        )
        "->" -> erlD(
            s,
            mapOf(
                "r" to listOf("when"),
                "m" to listOf("try", "if", "case", "receive")
            )
        )
        ";" -> erlD(
            s,
            mapOf("E" to listOf("case", "fun", "if", "receive", "try", "when"))
        )
        "catch" -> erlD(s, mapOf("e" to listOf("try")))
        "of" -> erlD(s, mapOf("e" to listOf("case")))
        "after" -> erlD(s, mapOf("e" to listOf("receive", "try")))
        else -> null
    }
    return result ?: s
}

@Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
private fun erlD(
    stack: MutableList<ErlangToken>,
    tt: Map<String, List<String>>
): MutableList<ErlangToken>? {
    var lastType: String? = null
    for ((type, tokens) in tt) {
        lastType = type
        val len = stack.size - 1
        for (i in (len - 1) downTo 0) {
            if (erlIsMember(stack[i].token, tokens)) {
                val ss = stack.subList(0, i).toMutableList()
                return when (type) {
                    "m" -> {
                        ss.add(stack[i])
                        ss.add(stack[len])
                        ss
                    }
                    "r" -> {
                        ss.add(stack[len])
                        ss
                    }
                    "i" -> ss
                    "g" -> {
                        ss.add(erlFakeToken("group"))
                        ss
                    }
                    "E", "e" -> {
                        ss.add(stack[i])
                        ss
                    }
                    else -> ss
                }
            }
        }
    }
    return if (lastType == "E") mutableListOf() else null
}

private fun erlPushToken(state: ErlangState, token: ErlangToken) {
    if (token.type != "comment" && token.type != "whitespace") {
        state.tokenStack = erlMaybeDropPre(state.tokenStack, token)
        state.tokenStack = erlMaybeDropPost(state.tokenStack)
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun erlRval(state: ErlangState, stream: StringStream, type: String?): String? {
    erlPushToken(
        state,
        ErlangToken(stream.current(), stream.column(), stream.indentation(), type)
    )
    return when (type) {
        "atom" -> "atom"
        "attribute" -> "attribute"
        "boolean" -> "atom"
        "builtin" -> "builtin"
        "close_paren" -> null
        "colon" -> null
        "comment" -> "comment"
        "dot" -> null
        "error" -> "error"
        "fun" -> "meta"
        "function" -> "tag"
        "guard" -> "property"
        "keyword" -> "keyword"
        "macro" -> "macroName"
        "number" -> "number"
        "open_paren" -> null
        "operator" -> "operator"
        "record" -> "bracket"
        "separator" -> null
        "string" -> "string"
        "type" -> "def"
        "variable" -> "variable"
        else -> null
    }
}

private fun erlNongreedy(stream: StringStream, re: Regex, words: List<String>): Boolean {
    if (stream.current().length == 1 && re.containsMatchIn(stream.current())) {
        stream.backUp(1)
        while (stream.peek()?.let { re.containsMatchIn(it) } == true) {
            stream.next()
            if (erlIsMember(stream.current(), words)) return true
        }
        stream.backUp(stream.current().length - 1)
    }
    return false
}

private fun erlGreedy(stream: StringStream, re: Regex, words: List<String>): Boolean {
    if (stream.current().length == 1 && re.containsMatchIn(stream.current())) {
        while (stream.peek()?.let { re.containsMatchIn(it) } == true) {
            stream.next()
        }
        while (stream.current().isNotEmpty()) {
            if (erlIsMember(stream.current(), words)) {
                return true
            } else {
                stream.backUp(1)
            }
        }
        stream.next()
    }
    return false
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun erlTokenizer(stream: StringStream, state: ErlangState): String? {
    if (state.in_string) {
        state.in_string = !erlDoubleQuote(stream)
        return erlRval(state, stream, "string")
    }
    if (state.in_atom) {
        state.in_atom = !erlSingleQuote(stream)
        return erlRval(state, stream, "atom")
    }
    if (stream.eatSpace()) return erlRval(state, stream, "whitespace")

    // attributes and type specs
    if (erlPeekToken(state) == null &&
        stream.match(
            Regex("-\\s*[a-z\u00DF-\u00F6\u00F8-\u00FF][\\w\u00D8-\u00DE\u00C0-\u00D6\u00DF-\u00F6\u00F8-\u00FF]*")
        ) != null
    ) {
        return if (erlIsMember(stream.current(), erlTypeWords)) {
            erlRval(state, stream, "type")
        } else {
            erlRval(state, stream, "attribute")
        }
    }

    val ch = stream.next() ?: return null

    if (ch == "%") {
        stream.skipToEnd()
        return erlRval(state, stream, "comment")
    }
    if (ch == ":") return erlRval(state, stream, "colon")
    if (ch == "?") {
        stream.eatSpace()
        stream.eatWhile(erlAnumRE)
        return erlRval(state, stream, "macro")
    }
    if (ch == "#") {
        stream.eatSpace()
        stream.eatWhile(erlAnumRE)
        return erlRval(state, stream, "record")
    }
    if (ch == "$") {
        if (stream.next() == "\\" && stream.match(erlEscapesRE) == null) {
            return erlRval(state, stream, "error")
        }
        return erlRval(state, stream, "number")
    }
    if (ch == ".") return erlRval(state, stream, "dot")

    if (ch == "'") {
        val finished = erlSingleQuote(stream)
        if (finished) {
            state.in_atom = false
            if (stream.match(Regex("\\s*/\\s*[0-9]"), false) != null) {
                stream.match(Regex("\\s*/\\s*[0-9]"), true)
                return erlRval(state, stream, "fun")
            }
            if (stream.match(Regex("\\s*\\("), false) != null ||
                stream.match(Regex("\\s*:"), false) != null
            ) {
                return erlRval(state, stream, "function")
            }
        } else {
            state.in_atom = true
        }
        return erlRval(state, stream, "atom")
    }

    if (ch == "\"") {
        state.in_string = !erlDoubleQuote(stream)
        return erlRval(state, stream, "string")
    }

    if (Regex("[A-Z_\u00D8-\u00DE\u00C0-\u00D6]").containsMatchIn(ch)) {
        stream.eatWhile(erlAnumRE)
        return erlRval(state, stream, "variable")
    }

    if (Regex("[a-z_\u00DF-\u00F6\u00F8-\u00FF]").containsMatchIn(ch)) {
        stream.eatWhile(erlAnumRE)
        if (stream.match(Regex("\\s*/\\s*[0-9]"), false) != null) {
            stream.match(Regex("\\s*/\\s*[0-9]"), true)
            return erlRval(state, stream, "fun")
        }
        val w = stream.current()
        if (erlIsMember(w, erlKeywordWords)) {
            return erlRval(state, stream, "keyword")
        } else if (erlIsMember(w, erlOperatorAtomWords)) {
            return erlRval(state, stream, "operator")
        } else if (stream.match(Regex("\\s*\\("), false) != null) {
            if (erlIsMember(w, erlBifWords) &&
                (
                    erlPeekToken(state)?.token != ":" ||
                        erlPeekToken(state, 2)?.token == "erlang"
                    )
            ) {
                return erlRval(state, stream, "builtin")
            } else if (erlIsMember(w, erlGuardWords)) {
                return erlRval(state, stream, "guard")
            } else {
                return erlRval(state, stream, "function")
            }
        } else if (erlLookahead(stream) == ":") {
            return if (w == "erlang") {
                erlRval(state, stream, "builtin")
            } else {
                erlRval(state, stream, "function")
            }
        } else if (erlIsMember(w, listOf("true", "false"))) {
            return erlRval(state, stream, "boolean")
        } else {
            return erlRval(state, stream, "atom")
        }
    }

    val digitRE = Regex("[0-9]")
    val radixRE = Regex("[0-9a-zA-Z]")
    if (digitRE.containsMatchIn(ch)) {
        stream.eatWhile(digitRE)
        if (stream.eat("#") != null) {
            if (!stream.eatWhile(radixRE)) stream.backUp(1)
        } else if (stream.eat(".") != null) {
            if (!stream.eatWhile(digitRE)) {
                stream.backUp(1)
            } else {
                if (stream.eat(Regex("[eE]")) != null) {
                    if (stream.eat(Regex("[-+]")) != null) {
                        if (!stream.eatWhile(digitRE)) stream.backUp(2)
                    } else {
                        if (!stream.eatWhile(digitRE)) stream.backUp(1)
                    }
                }
            }
        }
        return erlRval(state, stream, "number")
    }

    if (erlNongreedy(stream, erlOpenParenRE, erlOpenParenWords)) {
        return erlRval(state, stream, "open_paren")
    }
    if (erlNongreedy(stream, erlCloseParenRE, erlCloseParenWords)) {
        return erlRval(state, stream, "close_paren")
    }
    if (erlGreedy(stream, erlSeparatorRE, erlSeparatorWords)) {
        return erlRval(state, stream, "separator")
    }
    if (erlGreedy(stream, erlOperatorSymbolRE, erlOperatorSymbolWords)) {
        return erlRval(state, stream, "operator")
    }
    return erlRval(state, stream, null)
}

private fun erlWordAfter(str: String): String {
    val m = Regex(",|[a-z]+|\\}|\\]|\\)|>>|\\|+|\\(").find(str)
    return if (m != null && m.range.first == 0) m.value else ""
}

private fun erlGetTokenIndex(
    objs: List<ErlangToken>,
    propname: String,
    propvals: List<String>
): Int? {
    for (i in objs.indices.reversed()) {
        val value = when (propname) {
            "token" -> objs[i].token
            "type" -> objs[i].type
            else -> null
        }
        if (value != null && erlIsMember(value, propvals)) return i
    }
    return null
}

private fun erlGetToken(state: ErlangState, tokens: List<String>): ErlangToken? {
    val i = erlGetTokenIndex(state.tokenStack, "token", tokens)
    return if (i != null) state.tokenStack[i] else null
}

private fun erlPostcommaToken(state: ErlangState): ErlangToken? {
    val objs = state.tokenStack.dropLast(1)
    val i = erlGetTokenIndex(objs, "type", listOf("open_paren"))
    return if (i != null) objs[i] else null
}

private fun erlDefaultToken(state: ErlangState): ErlangToken? {
    val objs = state.tokenStack
    val stop = erlGetTokenIndex(objs, "type", listOf("open_paren", "separator", "keyword"))
    val oper = erlGetTokenIndex(objs, "type", listOf("operator"))
    return if (stop != null && oper != null && stop < oper) {
        if (stop + 1 < objs.size) objs[stop + 1] else null
    } else if (stop != null) {
        objs[stop]
    } else {
        null
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun erlIndenter(state: ErlangState, textAfter: String, cx: IndentContext): Int? {
    val wordAfter = erlWordAfter(textAfter)
    val currT = erlPeekToken(state, 1) ?: return null
    val prevT = erlPeekToken(state, 2) ?: return 0

    if (state.in_string || state.in_atom) return null

    if (currT.token == "when") {
        return currT.column + cx.unit
    } else if (wordAfter == "when" && prevT.type == "function") {
        return prevT.indent + cx.unit
    } else if (wordAfter == "(" && currT.token == "fun") {
        return currT.column + 3
    } else if (wordAfter == "catch") {
        val t = erlGetToken(state, listOf("try"))
        if (t != null) return t.column
    } else if (erlIsMember(wordAfter, listOf("end", "after", "of"))) {
        val t = erlGetToken(
            state,
            listOf("begin", "case", "fun", "if", "receive", "try")
        )
        return t?.column
    } else if (erlIsMember(wordAfter, erlCloseParenWords)) {
        val t = erlGetToken(state, erlOpenParenWords)
        return t?.column
    } else if (erlIsMember(currT.token, listOf(",", "|", "||")) ||
        erlIsMember(wordAfter, listOf(",", "|", "||"))
    ) {
        val t = erlPostcommaToken(state)
        return if (t != null) t.column + t.token.length else cx.unit
    } else if (currT.token == "->") {
        return if (erlIsMember(prevT.token, listOf("receive", "case", "if", "try"))) {
            prevT.column + cx.unit + cx.unit
        } else {
            prevT.column + cx.unit
        }
    } else if (erlIsMember(currT.token, erlOpenParenWords)) {
        return currT.column + currT.token.length
    } else {
        val t = erlDefaultToken(state)
        return if (t != null) t.column + cx.unit else 0
    }
    return null
}

val erlang: StreamParser<ErlangState> = object : StreamParser<ErlangState> {
    override val name: String get() = "erlang"

    override fun startState(indentUnit: Int) = ErlangState()

    override fun copyState(state: ErlangState): ErlangState {
        return ErlangState(
            tokenStack = state.tokenStack.toMutableList(),
            in_string = state.in_string,
            in_atom = state.in_atom
        )
    }

    override fun token(stream: StringStream, state: ErlangState): String? {
        return erlTokenizer(stream, state)
    }

    override fun indent(state: ErlangState, textAfter: String, context: IndentContext): Int? {
        return erlIndenter(state, textAfter, context)
    }

    override val languageData: Map<String, Any>
        get() = mapOf("commentTokens" to mapOf("line" to "%"))
}
