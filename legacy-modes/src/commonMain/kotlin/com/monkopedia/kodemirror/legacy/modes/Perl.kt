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

class PerlState(
    var tokenize: (StringStream, PerlState) -> String? = ::perlTokenPerl,
    var chain: String? = null,
    var style: String? = null,
    var tail: Regex? = null
)

// Perl keyword/operator/variable map: value is either:
//   1 = keyword, 2 = def, 3 = atom, 4 = operator, 5 = builtin
//   null = magic touch (regex/quote initiators)
//   [x, 1] = x with y=must be defined
private val PERL: Map<String, Any?> = buildMap {
    // operators
    for (op in listOf(
        "->", "++", "--", "**", "=~", "!~", "*", "/", "%", "x",
        "+", "-", ".", "<<", ">>", "<", ">", "<=", ">=", "lt", "gt",
        "le", "ge", "==", "!=", "<=>", "eq", "ne", "cmp", "~~",
        "&", "|", "^", "&&", "||", "//", "..", "...", "?", ":", "=",
        "+=", "-=", "*=", ",", "=>", "::", "not", "and", "or", "xor"
    )) put(op, 4)
    // predefined variables with [5,1]
    for (v in listOf(
        "BEGIN", "END", "PRINT", "PRINTF", "GETC", "READ", "READLINE",
        "DESTROY", "TIE", "TIEHANDLE", "UNTIE"
    )) put(v, listOf(5, 1))
    // predefined variables = 5
    for (v in listOf(
        "STDIN", "STDIN_TOP", "STDOUT", "STDOUT_TOP", "STDERR", "STDERR_TOP",
        "\$ARG", "\$_", "@ARG", "@_", "\$LIST_SEPARATOR", "\$\"",
        "\$PROCESS_ID", "\$PID", "\$\$", "\$REAL_GROUP_ID", "\$GID", "\$(",
        "\$EFFECTIVE_GROUP_ID", "\$EGID", "\$)", "\$PROGRAM_NAME", "\$0",
        "\$SUBSCRIPT_SEPARATOR", "\$SUBSEP", "\$;", "\$REAL_USER_ID", "\$UID",
        "\$<", "\$EFFECTIVE_USER_ID", "\$EUID", "\$>", "\$a", "\$b",
        "\$COMPILING", "\$^C", "\$DEBUGGING", "\$^D", "\${^ENCODING}",
        "\$ENV", "%ENV", "\$SYSTEM_FD_MAX", "\$^F", "@F",
        "\${^GLOBAL_PHASE}", "\$^H", "%^H", "@INC", "%INC",
        "\$INPLACE_EDIT", "\$^I", "\$^M", "\$OSNAME", "\$^O",
        "\${^OPEN}", "\$PERLDB", "\$^P", "\$SIG", "%SIG", "\$BASETIME",
        "\$^T", "\${^TAINT}", "\${^UNICODE}", "\${^UTF8CACHE}",
        "\${^UTF8LOCALE}", "\$PERL_VERSION", "\$^V",
        "\${^WIN32_SLOPPY_STAT}", "\$EXECUTABLE_NAME", "\$^X",
        "\$1", "\$MATCH", "\$&", "\${^MATCH}", "\$PREMATCH", "\$`",
        "\${^PREMATCH}", "\$POSTMATCH", "\$'", "\${^POSTMATCH}",
        "\$LAST_PAREN_MATCH", "\$+", "\$LAST_SUBMATCH_RESULT", "\$^N",
        "@LAST_MATCH_END", "@+", "%LAST_PAREN_MATCH", "%+",
        "@LAST_MATCH_START", "@-", "%LAST_MATCH_START", "%-",
        "\$LAST_REGEXP_CODE_RESULT", "\$^R", "\${^RE_DEBUG_FLAGS}",
        "\${^RE_TRIE_MAXBUF}", "\$ARGV", "@ARGV", "ARGV", "ARGVOUT",
        "\$OUTPUT_FIELD_SEPARATOR", "\$OFS", "\$,",
        "\$INPUT_LINE_NUMBER", "\$NR", "\$.", "\$INPUT_RECORD_SEPARATOR",
        "\$RS", "\$/", "\$OUTPUT_RECORD_SEPARATOR", "\$ORS", "\$\\",
        "\$OUTPUT_AUTOFLUSH", "\$|", "\$ACCUMULATOR", "\$^A",
        "\$FORMAT_FORMFEED", "\$^L", "\$FORMAT_PAGE_NUMBER", "\$%",
        "\$FORMAT_LINES_LEFT", "\$-", "\$FORMAT_LINE_BREAK_CHARACTERS",
        "\$:", "\$FORMAT_LINES_PER_PAGE", "\$=", "\$FORMAT_TOP_NAME",
        "\$^", "\$FORMAT_NAME", "\$~", "\${^CHILD_ERROR_NATIVE}",
        "\$EXTENDED_OS_ERROR", "\$^E", "\$EXCEPTIONS_BEING_CAUGHT",
        "\$^S", "\$WARNING", "\$^W", "\${^WARNING_BITS}",
        "\$OS_ERROR", "\$ERRNO", "\$!", "%OS_ERROR", "%ERRNO", "%!",
        "\$CHILD_ERROR", "\$?", "\$EVAL_ERROR", "\$@", "\$OFMT",
        "\$#", "\$*", "\$ARRAY_BASE", "\$[", "\$OLD_PERL_VERSION", "\$]"
    )) put(v, 5)
    // blocks with [1,1]
    for (v in listOf("if", "elsif", "else", "while", "unless", "for", "foreach")) {
        put(v, listOf(1, 1))
    }
    // continue with [1,1]
    put("continue", listOf(1, 1))
    // functions = 1
    for (v in listOf(
        "abs", "accept", "alarm", "atan2", "bind", "binmode", "bless",
        "bootstrap", "break", "caller", "chdir", "chmod", "chomp", "chop",
        "chown", "chr", "chroot", "close", "closedir", "connect", "cos",
        "crypt", "dbmclose", "dbmopen", "default", "defined", "delete",
        "die", "do", "dump", "each", "endgrent", "endhostent", "endnetent",
        "endprotoent", "endpwent", "endservent", "eof", "eval", "exec",
        "exists", "exit", "exp", "fcntl", "fileno", "flock", "fork",
        "format", "formline", "getc", "getgrent", "getgrgid", "getgrnam",
        "gethostbyaddr", "gethostbyname", "gethostent", "getlogin",
        "getnetbyaddr", "getnetbyname", "getnetent", "getpeername",
        "getpgrp", "getppid", "getpriority", "getprotobyname",
        "getprotobynumber", "getprotoent", "getpwent", "getpwnam",
        "getpwuid", "getservbyname", "getservbyport", "getservent",
        "getsockname", "getsockopt", "given", "glob", "gmtime", "goto",
        "grep", "hex", "import", "index", "int", "ioctl", "join", "keys",
        "kill", "last", "lc", "lcfirst", "length", "link", "listen",
        "localtime", "lock", "log", "lstat", "map", "mkdir", "msgctl",
        "msgget", "msgrcv", "msgsnd", "new", "next", "no", "oct", "open",
        "opendir", "ord", "pack", "package", "pipe", "pop", "pos",
        "print", "printf", "prototype", "push", "rand", "read", "readdir",
        "readline", "readlink", "readpipe", "recv", "redo", "ref",
        "rename", "require", "reset", "return", "reverse", "rewinddir",
        "rindex", "rmdir", "say", "scalar", "seek", "seekdir", "select",
        "semctl", "semget", "semop", "send", "setgrent", "sethostent",
        "setnetent", "setpgrp", "setpriority", "setprotoent", "setpwent",
        "setservent", "setsockopt", "shift", "shmctl", "shmget", "shmread",
        "shmwrite", "shutdown", "sin", "sleep", "socket", "socketpair",
        "sort", "splice", "split", "sprintf", "sqrt", "srand", "stat",
        "state", "study", "sub", "substr", "symlink", "syscall", "sysopen",
        "sysread", "sysseek", "system", "syswrite", "tell", "telldir",
        "tie", "tied", "time", "times", "truncate", "uc", "ucfirst",
        "umask", "undef", "unlink", "unpack", "unshift", "untie", "use",
        "utime", "values", "vec", "wait", "waitpid", "wantarray", "warn",
        "when", "write"
    )) put(v, 1)
    // def = 2
    for (v in listOf("local", "my", "our")) put(v, 2)
    // null (magic touch)
    for (v in listOf("m", "q", "qq", "qr", "quotemeta", "qw", "qx", "s", "tr", "y")) {
        put(v, null)
    }
}

private val rxStyle = "string.special"
private val rxModifiers = Regex("[goseximacplud]")

private fun perlLook(stream: StringStream, c: Int): String {
    val pos = stream.pos + c
    return if (pos >= 0 && pos < stream.string.length) {
        stream.string[pos].toString()
    } else {
        ""
    }
}

private fun perlPrefix(stream: StringStream, c: Int = 0): String {
    return if (c > 0) {
        val x = stream.pos - c
        stream.string.substring(if (x >= 0) x else 0, stream.pos)
    } else {
        stream.string.substring(0, stream.pos - 1)
    }
}

private fun perlSuffix(stream: StringStream, c: Int = 0): String {
    val y = stream.string.length
    val x = y - stream.pos + 1
    return stream.string.substring(stream.pos, stream.pos + (if (c > 0 && c < y) c else x))
}

private fun perlEatSuffix(stream: StringStream, c: Int) {
    val x = stream.pos + c
    stream.pos = when {
        x <= 0 -> 0
        x >= stream.string.length - 1 -> stream.string.length - 1
        else -> x
    }
}

@Suppress("NestedBlockDepth")
private fun perlTokenChain(
    stream: StringStream,
    state: PerlState,
    chain: List<String>,
    style: String,
    tail: Regex? = null
): String {
    state.chain = null
    state.style = null
    state.tail = null
    state.tokenize = fun(s: StringStream, st: PerlState): String? {
        var e = false
        var c: String?
        var i = 0
        while (true) {
            c = s.next()
            if (c == null) break
            if (c == chain[i] && !e) {
                i++
                if (i >= chain.size) {
                    if (tail != null) s.eatWhile(tail)
                    st.tokenize = ::perlTokenPerl
                    return style
                } else {
                    st.chain = chain[i]
                    st.style = style
                    st.tail = tail
                }
                st.tokenize = ::perlTokenPerl
                return style
            }
            e = !e && c == "\\"
        }
        return style
    }
    return state.tokenize(stream, state) ?: style
}

private fun perlTokenSOMETHING(stream: StringStream, state: PerlState, string: String): String {
    state.tokenize = fun(s: StringStream, st: PerlState): String? {
        if (s.string == string) st.tokenize = ::perlTokenPerl
        s.skipToEnd()
        return "string"
    }
    return state.tokenize(stream, state) ?: "string"
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun perlTokenPerl(stream: StringStream, state: PerlState): String? {
    if (stream.eatSpace()) return null
    if (state.chain != null) {
        return perlTokenChain(
            stream,
            state,
            listOf(state.chain!!),
            state.style ?: "string",
            state.tail
        )
    }
    if (stream.match(
            Regex(
                "^(-?((\\d[\\d_]*)?\\.[0-9]+(e[+-]?\\d+)?|\\d+\\.\\d*)" +
                    "|0x[\\da-fA-F_]+|0b[01_]+|\\d[\\d_]*(e[+-]?\\d+)?)"
            )
        ) != null
    ) {
        return "number"
    }
    if (stream.match(Regex("^<<(?=[_a-zA-Z])")) != null) {
        stream.eatWhile(Regex("\\w"))
        return perlTokenSOMETHING(stream, state, stream.current().substring(2))
    }
    if (stream.sol() && stream.match(Regex("^=item(?!\\w)")) != null) {
        return perlTokenSOMETHING(stream, state, "=cut")
    }

    val ch = stream.next() ?: return null

    if (ch == "\"" || ch == "'") {
        if (perlPrefix(stream, 3) == "<<$ch") {
            val p = stream.pos
            stream.eatWhile(Regex("\\w"))
            val n = stream.current().substring(1)
            if (n.isNotEmpty() && stream.eat(ch) != null) {
                return perlTokenSOMETHING(stream, state, n)
            }
            stream.pos = p
        }
        return perlTokenChain(stream, state, listOf(ch), "string")
    }

    if (ch == "q") {
        val c2 = perlLook(stream, -2)
        if (c2.isEmpty() || !Regex("\\w").containsMatchIn(c2)) {
            val c = perlLook(stream, 0)
            val delims = mapOf("(" to ")", "[" to "]", "{" to "}", "<" to ">")
            when (c) {
                "x" -> {
                    val c1 = perlLook(stream, 1)
                    val end = delims[c1]
                    if (end != null) {
                        perlEatSuffix(stream, 2)
                        return perlTokenChain(stream, state, listOf(end), rxStyle, rxModifiers)
                    }
                    if (Regex("[\\^'\"!~/]").containsMatchIn(c1)) {
                        perlEatSuffix(stream, 1)
                        return perlTokenChain(
                            stream,
                            state,
                            listOf(stream.eat(c1) ?: c1),
                            rxStyle,
                            rxModifiers
                        )
                    }
                }
                "q" -> {
                    val c1 = perlLook(stream, 1)
                    val end = delims[c1]
                    if (end != null) {
                        perlEatSuffix(stream, 2)
                        return perlTokenChain(stream, state, listOf(end), "string")
                    }
                    if (Regex("[\\^'\"!~/]").containsMatchIn(c1)) {
                        perlEatSuffix(stream, 1)
                        return perlTokenChain(stream, state, listOf(stream.eat(c1) ?: c1), "string")
                    }
                }
                "w" -> {
                    val c1 = perlLook(stream, 1)
                    val end = delims[c1]
                    if (end != null) {
                        perlEatSuffix(stream, 2)
                        return perlTokenChain(stream, state, listOf(end), "bracket")
                    }
                    if (Regex("[\\^'\"!~/]").containsMatchIn(c1)) {
                        perlEatSuffix(stream, 1)
                        return perlTokenChain(
                            stream,
                            state,
                            listOf(stream.eat(c1) ?: c1),
                            "bracket"
                        )
                    }
                }
                "r" -> {
                    val c1 = perlLook(stream, 1)
                    val end = delims[c1]
                    if (end != null) {
                        perlEatSuffix(stream, 2)
                        return perlTokenChain(stream, state, listOf(end), rxStyle, rxModifiers)
                    }
                    if (Regex("[\\^'\"!~/]").containsMatchIn(c1)) {
                        perlEatSuffix(stream, 1)
                        return perlTokenChain(
                            stream,
                            state,
                            listOf(stream.eat(c1) ?: c1),
                            rxStyle,
                            rxModifiers
                        )
                    }
                }
                else -> {
                    if (Regex("[\\^'\"!~/(\\[{<]").containsMatchIn(c)) {
                        val end = delims[c]
                        if (end != null) {
                            perlEatSuffix(stream, 1)
                            return perlTokenChain(stream, state, listOf(end), "string")
                        }
                        if (Regex("[\\^'\"!~/]").containsMatchIn(c)) {
                            return perlTokenChain(
                                stream,
                                state,
                                listOf(stream.eat(c) ?: c),
                                "string"
                            )
                        }
                    }
                }
            }
        }
    }

    // m// regexp
    if (ch == "m") {
        val c2 = perlLook(stream, -2)
        if (c2.isEmpty() || !Regex("\\w").containsMatchIn(c2)) {
            val c = stream.eat(Regex("[(\\[{<\\^'\"!~/]"))
            if (c != null) {
                val delims = mapOf("(" to ")", "[" to "]", "{" to "}", "<" to ">")
                val end = delims[c]
                return if (end != null) {
                    perlTokenChain(stream, state, listOf(end), rxStyle, rxModifiers)
                } else {
                    perlTokenChain(stream, state, listOf(c), rxStyle, rxModifiers)
                }
            }
        }
    }

    // s/// y/// tr///
    for (prefix in listOf("s", "y")) {
        if (ch == prefix) {
            val c2 = Regex("[/}>\\])\\w]").containsMatchIn(perlLook(stream, -2))
            if (!c2) {
                val c = stream.eat(Regex("[(\\[{<\\^'\"!~/]"))
                if (c != null) {
                    val delims = mapOf("(" to ")", "[" to "]", "{" to "}", "<" to ">")
                    val end = delims[c]
                    return if (end != null) {
                        perlTokenChain(stream, state, listOf(end, end), rxStyle, rxModifiers)
                    } else {
                        perlTokenChain(stream, state, listOf(c, c), rxStyle, rxModifiers)
                    }
                }
            }
        }
    }
    if (ch == "t") {
        val c2 = Regex("[/}>\\])\\w]").containsMatchIn(perlLook(stream, -2))
        if (!c2) {
            val r = stream.eat("r")
            if (r != null) {
                val c = stream.eat(Regex("[(\\[{<\\^'\"!~/]"))
                if (c != null) {
                    val delims = mapOf("(" to ")", "[" to "]", "{" to "}", "<" to ">")
                    val end = delims[c]
                    return if (end != null) {
                        perlTokenChain(stream, state, listOf(end, end), rxStyle, rxModifiers)
                    } else {
                        perlTokenChain(stream, state, listOf(c, c), rxStyle, rxModifiers)
                    }
                }
            }
        }
    }

    if (ch == "`") return perlTokenChain(stream, state, listOf(ch), "builtin")

    if (ch == "/") {
        return if (!Regex("~\\s*$").containsMatchIn(perlPrefix(stream))) {
            "operator"
        } else {
            perlTokenChain(stream, state, listOf(ch), rxStyle, rxModifiers)
        }
    }

    if (ch == "$") {
        val p = stream.pos
        if (stream.eatWhile(Regex("\\d")) ||
            (stream.eat("{") != null && stream.eatWhile(Regex("\\d")) && stream.eat("}") != null)
        ) {
            return "builtin"
        } else {
            stream.pos = p
        }
    }

    if (Regex("[\$@%]").containsMatchIn(ch)) {
        val p = stream.pos
        if ((stream.eat("^") != null && stream.eat(Regex("[A-Z]")) != null) ||
            (
                !Regex("[@\$%&]").containsMatchIn(perlLook(stream, -2)) &&
                    stream.eat(Regex("[=|\\\\\\-#?@;&`~^!\\[\\]*'\"$+.,/<>()]")) != null
                )
        ) {
            val c = stream.current()
            if (PERL.containsKey(c)) return "builtin"
        }
        stream.pos = p
    }

    if (Regex("[\$@%&]").containsMatchIn(ch)) {
        if (stream.eatWhile(Regex("[\\w$]")) ||
            (stream.eat("{") != null && stream.eatWhile(Regex("[\\w$]")) && stream.eat("}") != null)
        ) {
            val c = stream.current()
            return if (PERL.containsKey(c)) "builtin" else "variable"
        }
    }

    if (ch == "#") {
        if (perlLook(stream, -2) != "$") {
            stream.skipToEnd()
            return "comment"
        }
    }

    if (Regex("[:+\\-^*\$&%@=<>!?|/~.]").containsMatchIn(ch)) {
        val p = stream.pos
        stream.eatWhile(Regex("[:+\\-^*\$&%@=<>!?|/~.]"))
        if (PERL.containsKey(stream.current())) {
            return "operator"
        } else {
            stream.pos = p
        }
    }

    if (ch == "_") {
        if (stream.pos == 1) {
            val suf = perlSuffix(stream, 6)
            if (suf == "_END__") {
                return perlTokenChain(stream, state, listOf("\u0000"), "comment")
            } else {
                val suf7 = perlSuffix(stream, 7)
                if (suf7 == "_DATA__") {
                    return perlTokenChain(stream, state, listOf("\u0000"), "builtin")
                } else if (suf7 == "_C__") {
                    return perlTokenChain(stream, state, listOf("\u0000"), "string")
                }
            }
        }
    }

    if (Regex("\\w").containsMatchIn(ch)) {
        val p = stream.pos
        if (perlLook(stream, -2) == "{" &&
            (
                perlLook(stream, 0) == "}" ||
                    (stream.eatWhile(Regex("\\w")) && perlLook(stream, 0) == "}")
                )
        ) {
            return "string"
        } else {
            stream.pos = p
        }
    }

    if (Regex("[A-Z]").containsMatchIn(ch)) {
        val l = perlLook(stream, -2)
        val p = stream.pos
        stream.eatWhile(Regex("[A-Z_]"))
        if (Regex("[\\da-z]").containsMatchIn(perlLook(stream, 0))) {
            stream.pos = p
        } else {
            val c = PERL[stream.current()]
            if (c == null) return "meta"
            val cv = if (c is List<*>) c[0] as Int else c as Int
            if (l != ":") {
                return when (cv) {
                    1 -> "keyword"
                    2 -> "def"
                    3 -> "atom"
                    4 -> "operator"
                    5 -> "builtin"
                    else -> "meta"
                }
            } else {
                return "meta"
            }
        }
    }

    if (Regex("[a-zA-Z_]").containsMatchIn(ch)) {
        val l = perlLook(stream, -2)
        stream.eatWhile(Regex("\\w"))
        val c = PERL[stream.current()]
        if (c == null) return "meta"
        val cv = if (c is List<*>) c[0] as Int else c as Int
        if (l != ":") {
            return when (cv) {
                1 -> "keyword"
                2 -> "def"
                3 -> "atom"
                4 -> "operator"
                5 -> "builtin"
                else -> "meta"
            }
        } else {
            return "meta"
        }
    }

    return null
}

val perl: StreamParser<PerlState> = object : StreamParser<PerlState> {
    override val name: String get() = "perl"
    override fun startState(indentUnit: Int) = PerlState()

    override fun copyState(state: PerlState): PerlState {
        return PerlState(
            tokenize = state.tokenize,
            chain = state.chain,
            style = state.style,
            tail = state.tail
        )
    }

    override fun token(stream: StringStream, state: PerlState): String? {
        return state.tokenize(stream, state)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf("line" to "#"),
            "wordChars" to "$"
        )
}
