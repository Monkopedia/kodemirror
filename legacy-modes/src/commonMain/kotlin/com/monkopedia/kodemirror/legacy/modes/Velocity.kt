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

private val velocityKeywords = run {
    val w = "#end #else #break #stop #[[ #]] #{end} #{else} #{break} #{stop}"
    w.split(" ").toSet()
}

private val velocityFunctions = run {
    val w = "#if #elseif #foreach #set #include #parse #macro #define #evaluate " +
        "#{if} #{elseif} #{foreach} #{set} #{include} #{parse} #{macro} #{define} #{evaluate}"
    w.split(" ").toSet()
}

private val velocitySpecials = run {
    val w = "\$foreach.count \$foreach.hasNext \$foreach.first \$foreach.last " +
        "\$foreach.topmost \$foreach.parent.count \$foreach.parent.hasNext " +
        "\$foreach.parent.first \$foreach.parent.last \$foreach.parent " +
        "\$velocityCount \$!bodyContent \$bodyContent"
    w.split(" ").toSet()
}

private val velocityOperatorChar = Regex("[+\\-*&%=<>!?:/|]")

data class VelocityState(
    var tokenize: (StringStream, VelocityState) -> String?,
    var beforeParams: Boolean = false,
    var inParams: Boolean = false,
    var inString: Boolean = false,
    var lastTokenWasBuiltin: Boolean = false
)

val velocity: StreamParser<VelocityState> = object : StreamParser<VelocityState> {
    override val name: String get() = "velocity"
    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "##",
                "block" to mapOf("open" to "#*", "close" to "*#")
            )
        )

    private fun tokenString(quote: String): (StringStream, VelocityState) -> String? {
        return fun(stream: StringStream, state: VelocityState): String? {
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
                if (quote == "\"" && stream.peek() == "$" && !escaped) {
                    state.inString = true
                    end = true
                    break
                }
                escaped = !escaped && next == "\\"
            }
            if (end) state.tokenize = ::tokenBase
            return "string"
        }
    }

    private fun tokenComment(stream: StringStream, state: VelocityState): String? {
        var maybeEnd = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "#" && maybeEnd) {
                state.tokenize = ::tokenBase
                break
            }
            maybeEnd = ch == "*"
        }
        return "comment"
    }

    private fun tokenUnparsed(stream: StringStream, state: VelocityState): String? {
        var maybeEnd = 0
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "#" && maybeEnd == 2) {
                state.tokenize = ::tokenBase
                break
            }
            if (ch == "]") {
                maybeEnd++
            } else if (ch != " ") {
                maybeEnd = 0
            }
        }
        return "meta"
    }

    private fun tokenBase(stream: StringStream, state: VelocityState): String? {
        val beforeParams = state.beforeParams
        state.beforeParams = false
        val ch = stream.next() ?: return null

        if (ch == "'" && !state.inString && state.inParams) {
            state.lastTokenWasBuiltin = false
            state.tokenize = tokenString(ch)
            return state.tokenize(stream, state)
        } else if (ch == "\"") {
            state.lastTokenWasBuiltin = false
            if (state.inString) {
                state.inString = false
                return "string"
            } else if (state.inParams) {
                state.tokenize = tokenString(ch)
                return state.tokenize(stream, state)
            }
        } else if (Regex("[\\[\\]{}(),;.]").containsMatchIn(ch)) {
            if (ch == "(" && beforeParams) {
                state.inParams = true
            } else if (ch == ")") {
                state.inParams = false
                state.lastTokenWasBuiltin = true
            }
            return null
        } else if (Regex("\\d").containsMatchIn(ch)) {
            state.lastTokenWasBuiltin = false
            stream.eatWhile(Regex("[\\w.]"))
            return "number"
        } else if (ch == "#" && stream.eat("*") != null) {
            state.lastTokenWasBuiltin = false
            state.tokenize = ::tokenComment
            return tokenComment(stream, state)
        } else if (ch == "#" && stream.match(Regex("^ *\\[ *\\[")) != null) {
            state.lastTokenWasBuiltin = false
            state.tokenize = ::tokenUnparsed
            return tokenUnparsed(stream, state)
        } else if (ch == "#" && stream.eat("#") != null) {
            state.lastTokenWasBuiltin = false
            stream.skipToEnd()
            return "comment"
        } else if (ch == "$") {
            stream.eat("!")
            stream.eatWhile(Regex("[\\w\\d\$_.{}-]"))
            if (velocitySpecials.contains(stream.current())) {
                return "keyword"
            } else {
                state.lastTokenWasBuiltin = true
                state.beforeParams = true
                return "builtin"
            }
        } else if (velocityOperatorChar.containsMatchIn(ch)) {
            state.lastTokenWasBuiltin = false
            stream.eatWhile(velocityOperatorChar)
            return "operator"
        } else {
            stream.eatWhile(Regex("[\\w\$_{}@]"))
            val word = stream.current()
            if (velocityKeywords.contains(word)) {
                return "keyword"
            }
            if (velocityFunctions.contains(word) ||
                (
                    stream.current().matches(Regex("^#@?[a-z0-9_]+ *$", RegexOption.IGNORE_CASE)) &&
                        stream.peek() == "(" &&
                        !velocityFunctions.contains(word.lowercase())
                    )
            ) {
                state.beforeParams = true
                state.lastTokenWasBuiltin = false
                return "keyword"
            }
            if (state.inString) {
                state.lastTokenWasBuiltin = false
                return "string"
            }
            if (stream.pos > word.length &&
                stream.string[stream.pos - word.length - 1] == '.' &&
                state.lastTokenWasBuiltin
            ) {
                return "builtin"
            }
            state.lastTokenWasBuiltin = false
            return null
        }
        return null
    }

    override fun startState(indentUnit: Int) = VelocityState(
        tokenize = ::tokenBase
    )

    override fun copyState(state: VelocityState) = state.copy()

    override fun token(stream: StringStream, state: VelocityState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }
}
