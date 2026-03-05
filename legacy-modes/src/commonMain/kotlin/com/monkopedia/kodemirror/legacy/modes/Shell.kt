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

private val shellWords: Map<String, String> = buildMap {
    val commonAtoms = listOf("true", "false")
    val commonKeywords = listOf(
        "if", "then", "do", "else", "elif", "while", "until", "for",
        "in", "esac", "fi", "fin", "fil", "done", "exit", "set",
        "unset", "export", "function"
    )
    val commonCommands = listOf(
        "ab", "awk", "bash", "beep", "cat", "cc", "cd", "chown",
        "chmod", "chroot", "clear", "cp", "curl", "cut", "diff",
        "echo", "find", "gawk", "gcc", "get", "git", "grep", "hg",
        "kill", "killall", "ln", "ls", "make", "mkdir", "openssl",
        "mv", "nc", "nl", "node", "npm", "ping", "ps", "restart",
        "rm", "rmdir", "sed", "service", "sh", "shopt", "shred",
        "source", "sort", "sleep", "ssh", "start", "stop", "su",
        "sudo", "svn", "tee", "telnet", "top", "touch", "vi", "vim",
        "wall", "wc", "wget", "who", "write", "yes", "zsh"
    )
    commonAtoms.forEach { put(it, "atom") }
    commonKeywords.forEach { put(it, "keyword") }
    commonCommands.forEach { put(it, "builtin") }
}

data class ShellState(
    var tokens: MutableList<(StringStream, ShellState) -> String?> =
        mutableListOf()
)

private fun shellTokenize(stream: StringStream, state: ShellState): String? {
    val handler = state.tokens.firstOrNull() ?: ::shellTokenBase
    return handler(stream, state)
}

private fun shellTokenBase(stream: StringStream, state: ShellState): String? {
    if (stream.eatSpace()) return null

    val sol = stream.sol()
    val ch = stream.next() ?: return null

    if (ch == "\\") {
        stream.next()
        return null
    }
    if (ch == "'" || ch == "\"" || ch == "`") {
        state.tokens.add(
            0,
            shellTokenString(ch, if (ch == "`") "quote" else "string")
        )
        return shellTokenize(stream, state)
    }
    if (ch == "#") {
        if (sol && stream.eat("!") != null) {
            stream.skipToEnd()
            return "meta"
        }
        stream.skipToEnd()
        return "comment"
    }
    if (ch == "$") {
        state.tokens.add(0, ::shellTokenDollar)
        return shellTokenize(stream, state)
    }
    if (ch == "+" || ch == "=") {
        return "operator"
    }
    if (ch == "-") {
        stream.eat("-")
        stream.eatWhile(Regex("\\w"))
        return "attribute"
    }
    if (ch == "<") {
        if (stream.match("<<")) return "operator"
        val heredoc = stream.match(
            Regex("^<-?\\s*(?:['\"]([^'\"]*)['\"]|([^'\"\\s]*))")
        )
        if (heredoc != null) {
            val delim = if (heredoc.groupValues[1].isNotEmpty()) {
                heredoc.groupValues[1]
            } else {
                heredoc.groupValues[2]
            }
            state.tokens.add(0, shellTokenHeredoc(delim))
            return "string.special"
        }
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("\\d"))
        if (stream.eol() || stream.peek()?.let {
                !Regex("\\w").containsMatchIn(it)
            } == true
        ) {
            return "number"
        }
    }
    stream.eatWhile(Regex("[\\w-]"))
    val cur = stream.current()
    if (stream.peek() == "=" && Regex("\\w+").matches(cur)) return "def"
    return shellWords[cur]
}

private fun shellTokenString(quote: String, style: String): (StringStream, ShellState) -> String? {
    val close = when (quote) {
        "(" -> ")"
        "{" -> "}"
        else -> quote
    }
    return fn@{ stream, state ->
        var escaped = false
        while (true) {
            val next = stream.next() ?: break
            if (next == close && !escaped) {
                state.tokens.removeFirst()
                break
            } else if (next == "$" && !escaped && quote != "'" &&
                stream.peek() != close
            ) {
                stream.backUp(1)
                state.tokens.add(0, ::shellTokenDollar)
                break
            } else if (!escaped && quote != close && next == quote) {
                state.tokens.add(
                    0,
                    shellTokenString(quote, style)
                )
                return@fn shellTokenize(stream, state)
            } else if (!escaped && Regex("['\"]").containsMatchIn(next) &&
                !Regex("['\"]").containsMatchIn(quote)
            ) {
                state.tokens.add(
                    0,
                    shellTokenStringStart(next, "string")
                )
                stream.backUp(1)
                break
            }
            escaped = !escaped && next == "\\"
        }
        style
    }
}

private fun shellTokenStringStart(
    quote: String,
    style: String
): (StringStream, ShellState) -> String? = { stream, state ->
    state.tokens[0] = shellTokenString(quote, style)
    stream.next()
    shellTokenize(stream, state)
}

private fun shellTokenDollar(stream: StringStream, state: ShellState): String? {
    if (state.tokens.size > 1) stream.eat("$")
    val ch = stream.next()
    if (ch != null && Regex("['\"{(]").containsMatchIn(ch)) {
        state.tokens[0] = shellTokenString(
            ch,
            when (ch) {
                "(" -> "quote"
                "{" -> "def"
                else -> "string"
            }
        )
        return shellTokenize(stream, state)
    }
    if (ch != null && !Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("\\w"))
    }
    state.tokens.removeFirst()
    return "def"
}

private fun shellTokenHeredoc(delim: String): (StringStream, ShellState) -> String? =
    { stream, state ->
        if (stream.sol() && stream.string == delim) state.tokens.removeFirst()
        stream.skipToEnd()
        "string.special"
    }

val shell: StreamParser<ShellState> = object : StreamParser<ShellState> {
    override val name: String get() = "shell"

    override val languageData: Map<String, Any>
        get() = mapOf(
            "closeBrackets" to mapOf(
                "brackets" to listOf("(", "[", "{", "'", "\"", "`")
            ),
            "commentTokens" to mapOf("line" to "#")
        )

    override fun startState(indentUnit: Int) = ShellState()
    override fun copyState(state: ShellState) = ShellState(tokens = state.tokens.toMutableList())

    override fun token(stream: StringStream, state: ShellState): String? {
        return shellTokenize(stream, state)
    }
}
