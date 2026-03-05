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

private val lsIdentifier =
    "(?![\\d\\s])[\$\\w\\xAA-\\uFFDC](?:(?!\\s)[\$\\w\\xAA-\\uFFDC]|-[A-Za-z])*"

private val lsIndenter = Regex(
    "(?:[({\\[=:]|[-~]>|\\b(?:e(?:lse|xport)|d(?:o|efault)|t(?:ry|hen)|finally|" +
        "import(?:\\s*all)?|const|var|let|new|catch(?:\\s*$lsIdentifier)?))\\s*\$"
)

private val lsKeywordend = "(?![\$\\w]|-[A-Za-z]|\\s*:(?![:=]))"

private data class LsRule(
    val token: String,
    val regex: Regex,
    val next: String? = null
)

private val lsStringfill = LsRule(token = "string", regex = Regex("^.+"))

@Suppress("LongMethod")
private fun buildLsRules(): Map<String, List<LsRule>> {
    val rules = mutableMapOf<String, List<LsRule>>()

    rules["start"] = listOf(
        LsRule(token = "docComment", regex = Regex("^/\\*"), next = "comment"),
        LsRule(token = "comment", regex = Regex("^#.*")),
        LsRule(
            token = "keyword",
            regex = Regex(
                "^(?:t(?:h(?:is|row|en)|ry|ypeof!?)|c(?:on(?:tinue|st)|a(?:se|tch)|lass)|" +
                    "i(?:n(?:stanceof)?|mp(?:ort(?:\\s+all)?|lements)|[fs])|" +
                    "d(?:e(?:fault|lete|bugger)|o)|f(?:or(?:\\s+own)?|inally|unction)|" +
                    "s(?:uper|witch)|e(?:lse|x(?:tends|port)|val)|a(?:nd|rguments)|" +
                    "n(?:ew|ot)|un(?:less|til)|w(?:hile|ith)|o[fr]|return|break|let|var|loop)" +
                    lsKeywordend
            )
        ),
        LsRule(
            token = "atom",
            regex = Regex("^(?:true|false|yes|no|on|off|null|void|undefined)$lsKeywordend")
        ),
        LsRule(
            token = "invalid",
            regex = Regex(
                "^(?:p(?:ackage|r(?:ivate|otected)|ublic)|i(?:mplements|nterface)|" +
                    "enum|static|yield)$lsKeywordend"
            )
        ),
        LsRule(
            token = "className standard",
            regex = Regex(
                "^(?:R(?:e(?:gExp|ferenceError)|angeError)|S(?:tring|yntaxError)|" +
                    "E(?:rror|valError)|Array|Boolean|Date|Function|Number|Object|" +
                    "TypeError|URIError)$lsKeywordend"
            )
        ),
        LsRule(
            token = "variableName function standard",
            regex = Regex(
                "^(?:is(?:NaN|Finite)|parse(?:Int|Float)|Math|JSON|" +
                    "(?:en|de)codeURI(?:Component)?)$lsKeywordend"
            )
        ),
        LsRule(
            token = "variableName standard",
            regex = Regex("^(?:t(?:hat|il|o)|f(?:rom|allthrough)|it|by|e)$lsKeywordend")
        ),
        LsRule(token = "variableName", regex = Regex("^${lsIdentifier}\\s*:(?![:=])")),
        LsRule(token = "variableName", regex = Regex("^$lsIdentifier")),
        LsRule(token = "operatorKeyword", regex = Regex("^(?:\\.{3}|\\s+\\?)")),
        LsRule(token = "keyword", regex = Regex("^(?:@+|::|\\.\\.)"), next = "key"),
        LsRule(token = "operatorKeyword", regex = Regex("^\\.\\s*"), next = "key"),
        LsRule(token = "string", regex = Regex("^\\\\\\S[^\\s,;)}\\]]*")),
        LsRule(token = "docString", regex = Regex("^'''"), next = "qdoc"),
        LsRule(token = "docString", regex = Regex("^\"\"\""), next = "qqdoc"),
        LsRule(token = "string", regex = Regex("^'"), next = "qstring"),
        LsRule(token = "string", regex = Regex("^\""), next = "qqstring"),
        LsRule(token = "string", regex = Regex("^`"), next = "js"),
        LsRule(token = "string", regex = Regex("^<\\["), next = "words"),
        LsRule(token = "regexp", regex = Regex("^//"), next = "heregex"),
        LsRule(
            token = "regexp",
            regex = Regex(
                "^\\/(?:[^\\[/\\n\\\\]*(?:(?:\\\\.|\\[[^\\]\\n\\\\]*" +
                    "(?:\\\\.[^\\]\\n\\\\]*)*\\])[^\\[/\\n\\\\]*)*)\\/[gimy\$]{0,4}"
            ),
            next = "key"
        ),
        LsRule(
            token = "number",
            regex = Regex(
                "^(?:0x[\\da-fA-F][\\da-fA-F_]*|" +
                    "(?:[2-9]|[12]\\d|3[0-6])r[\\da-zA-Z][\\da-zA-Z_]*|" +
                    "(?:\\d[\\d_]*(?:\\.\\d[\\d_]*)?|\\.\\d[\\d_]*)" +
                    "(?:e[+-]?\\d[\\d_]*)?[\\w\$]*)"
            )
        ),
        LsRule(token = "paren", regex = Regex("^[({\\[]")),
        LsRule(token = "paren", regex = Regex("^[)}\\]]"), next = "key"),
        LsRule(token = "operatorKeyword", regex = Regex("^\\S+")),
        LsRule(token = "content", regex = Regex("^\\s+"))
    )

    rules["heregex"] = listOf(
        LsRule(token = "regexp", regex = Regex("^.*?//[gimy\$?]{0,4}"), next = "start"),
        LsRule(token = "regexp", regex = Regex("^\\s*#\\{")),
        LsRule(token = "comment", regex = Regex("^\\s+(?:#.*)?")),
        LsRule(token = "regexp", regex = Regex("^\\S+"))
    )

    rules["key"] = listOf(
        LsRule(token = "operatorKeyword", regex = Regex("^[.?@!]+")),
        LsRule(token = "variableName", regex = Regex("^$lsIdentifier"), next = "start"),
        LsRule(token = "content", regex = Regex("^"), next = "start")
    )

    rules["comment"] = listOf(
        LsRule(token = "docComment", regex = Regex("^.*?\\*/"), next = "start"),
        LsRule(token = "docComment", regex = Regex("^.+"))
    )

    rules["qdoc"] = listOf(
        LsRule(token = "string", regex = Regex("^.*?'''"), next = "key"),
        lsStringfill
    )

    rules["qqdoc"] = listOf(
        LsRule(token = "string", regex = Regex("^.*?\"\"\""), next = "key"),
        lsStringfill
    )

    rules["qstring"] = listOf(
        LsRule(
            token = "string",
            regex = Regex("^[^\\\\']*(?:\\\\.[^\\\\']*)*'"),
            next = "key"
        ),
        lsStringfill
    )

    rules["qqstring"] = listOf(
        LsRule(
            token = "string",
            regex = Regex("^[^\\\\\"]*(?:\\\\.[^\\\\\"]*)*\""),
            next = "key"
        ),
        lsStringfill
    )

    rules["js"] = listOf(
        LsRule(
            token = "string",
            regex = Regex("^[^\\\\`]*(?:\\\\.[^\\\\`]*)*`"),
            next = "key"
        ),
        lsStringfill
    )

    rules["words"] = listOf(
        LsRule(token = "string", regex = Regex("^.*?\\]>"), next = "key"),
        lsStringfill
    )

    return rules
}

private val lsRules = buildLsRules()

data class LsLastToken(
    var style: String? = null,
    var indent: Int = 0,
    var content: String = ""
)

data class LiveScriptState(
    var next: String = "start",
    var lastToken: LsLastToken = LsLastToken()
)

private fun lsTokenBase(stream: StringStream, state: LiveScriptState): String {
    val nextRule = state.next
    val rules = lsRules[nextRule] ?: run {
        stream.next()
        return "error"
    }
    for (r in rules) {
        if (stream.match(r.regex) != null) {
            if (r.next != null) state.next = r.next
            return r.token
        }
    }
    stream.next()
    return "error"
}

val liveScript: StreamParser<LiveScriptState> = object : StreamParser<LiveScriptState> {
    override val name: String get() = "livescript"

    override fun startState(indentUnit: Int) = LiveScriptState()
    override fun copyState(state: LiveScriptState) = state.copy(
        lastToken = state.lastToken.copy()
    )

    override fun token(stream: StringStream, state: LiveScriptState): String? {
        var style = "error"
        while (stream.pos == stream.start) {
            style = lsTokenBase(stream, state)
        }
        state.lastToken = LsLastToken(
            style = style,
            indent = stream.indentation(),
            content = stream.current()
        )
        return style.replace(".", " ")
    }

    override fun indent(state: LiveScriptState, textAfter: String, context: IndentContext): Int? {
        var indentation = state.lastToken.indent
        if (lsIndenter.containsMatchIn(state.lastToken.content)) {
            indentation += 2
        }
        return indentation
    }
}
