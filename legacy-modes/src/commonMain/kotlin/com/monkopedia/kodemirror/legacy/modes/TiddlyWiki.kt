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

private val twKeywords = setOf(
    "allTags", "closeAll", "list", "newJournal", "newTiddler",
    "permaview", "saveChanges", "search", "slider", "tabs",
    "tag", "tagging", "tags", "tiddler", "timeline",
    "today", "version", "option", "with", "filter"
)

private val twIsSpaceName = Regex("[\\w_-]", RegexOption.IGNORE_CASE)
private val twReHR = Regex("^----+$")
private val twReWikiCommentStart = Regex("^/\\*\\*\\*$")
private val twReWikiCommentStop = Regex("^\\*\\*\\*/$")
private val twReBlockQuote = Regex("^<<<$")
private val twReJsCodeStart = Regex("^//\\{\\{\\{$")
private val twReJsCodeStop = Regex("^//\\}\\}\\}$")
private val twReXmlCodeStart = Regex("^<!--\\{\\{\\{-->$")
private val twReXmlCodeStop = Regex("^<!--\\}\\}\\}-->$")
private val twReCodeBlockStart = Regex("^\\{\\{\\{$")
private val twReCodeBlockStop = Regex("^\\}\\}\\}$")
private val twReUntilCodeStop = Regex("^.*?\\}\\}\\}")

data class TiddlyWikiState(
    var tokenize: (StringStream, TiddlyWikiState) -> String?,
    var block: Boolean = false
)

val tiddlyWiki: StreamParser<TiddlyWikiState> = object : StreamParser<TiddlyWikiState> {
    override val name: String get() = "tiddlywiki"

    private fun twTokenComment(stream: StringStream, state: TiddlyWikiState): String {
        var maybeEnd = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "/" && maybeEnd) {
                state.tokenize = ::tokenBase
                break
            }
            maybeEnd = ch == "%"
        }
        return "comment"
    }

    private fun twTokenStrong(stream: StringStream, state: TiddlyWikiState): String {
        var maybeEnd = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "'" && maybeEnd) {
                state.tokenize = ::tokenBase
                break
            }
            maybeEnd = ch == "'"
        }
        return "strong"
    }

    private fun twTokenCode(stream: StringStream, state: TiddlyWikiState): String {
        val sb = state.block

        if (sb && stream.current().isNotEmpty()) {
            return "comment"
        }

        if (!sb && stream.match(twReUntilCodeStop) != null) {
            state.tokenize = ::tokenBase
            return "comment"
        }

        if (sb && stream.sol() && stream.match(twReCodeBlockStop) != null) {
            state.tokenize = ::tokenBase
            return "comment"
        }

        stream.next()
        return "comment"
    }

    private fun twTokenEm(stream: StringStream, state: TiddlyWikiState): String {
        var maybeEnd = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "/" && maybeEnd) {
                state.tokenize = ::tokenBase
                break
            }
            maybeEnd = ch == "/"
        }
        return "emphasis"
    }

    private fun twTokenUnderline(stream: StringStream, state: TiddlyWikiState): String {
        var maybeEnd = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "_" && maybeEnd) {
                state.tokenize = ::tokenBase
                break
            }
            maybeEnd = ch == "_"
        }
        return "link"
    }

    private fun twTokenStrike(stream: StringStream, state: TiddlyWikiState): String {
        var maybeEnd = false
        var ch: String?
        while (true) {
            ch = stream.next()
            if (ch == null) break
            if (ch == "-" && maybeEnd) {
                state.tokenize = ::tokenBase
                break
            }
            maybeEnd = ch == "-"
        }
        return "deleted"
    }

    private fun twTokenMacro(stream: StringStream, state: TiddlyWikiState): String? {
        if (stream.current() == "<<") {
            return "meta"
        }

        val ch = stream.next()
        if (ch == null) {
            state.tokenize = ::tokenBase
            return null
        }
        if (ch == ">") {
            if (stream.peek() == ">") {
                stream.next()
                state.tokenize = ::tokenBase
                return "meta"
            }
        }

        stream.eatWhile(Regex("[\\w\$_]"))
        return if (twKeywords.contains(stream.current())) "keyword" else null
    }

    private fun chain(
        stream: StringStream,
        state: TiddlyWikiState,
        f: (StringStream, TiddlyWikiState) -> String?
    ): String? {
        state.tokenize = f
        return f(stream, state)
    }

    private fun tokenBase(stream: StringStream, state: TiddlyWikiState): String? {
        val sol = stream.sol()
        val ch = stream.peek()

        state.block = false

        if (sol && ch != null && Regex("[</\\*{}\\-]").containsMatchIn(ch)) {
            if (stream.match(twReCodeBlockStart) != null) {
                state.block = true
                return chain(stream, state, ::twTokenCode)
            }
            if (stream.match(twReBlockQuote) != null) return "quote"
            if (stream.match(twReWikiCommentStart) != null ||
                stream.match(twReWikiCommentStop) != null
            ) {
                return "comment"
            }
            if (stream.match(twReJsCodeStart) != null ||
                stream.match(twReJsCodeStop) != null ||
                stream.match(twReXmlCodeStart) != null ||
                stream.match(twReXmlCodeStop) != null
            ) {
                return "comment"
            }
            if (stream.match(twReHR) != null) return "contentSeparator"
        }

        stream.next()
        if (sol && ch != null && Regex("[/\\*!#;:>|]").containsMatchIn(ch)) {
            if (ch == "!") {
                stream.skipToEnd()
                return "header"
            }
            if (ch == "*") {
                stream.eatWhile("*")
                return "comment"
            }
            if (ch == "#") {
                stream.eatWhile("#")
                return "comment"
            }
            if (ch == ";") {
                stream.eatWhile(";")
                return "comment"
            }
            if (ch == ":") {
                stream.eatWhile(":")
                return "comment"
            }
            if (ch == ">") {
                stream.eatWhile(">")
                return "quote"
            }
            if (ch == "|") return "header"
        }

        if (ch == "{" && stream.match("{{")) {
            return chain(stream, state, ::twTokenCode)
        }

        if (ch != null && Regex("[hf]", RegexOption.IGNORE_CASE).containsMatchIn(ch) &&
            stream.peek() != null &&
            Regex("[ti]", RegexOption.IGNORE_CASE).containsMatchIn(stream.peek()!!) &&
            stream.match(
                Regex(
                    "\\b(ttps?|tp|ile)://[-A-Z0-9+&@#/%?=~_|\$!:,.;]*[A-Z0-9+&@#/%=~_|\$]",
                    RegexOption.IGNORE_CASE
                )
            ) != null
        ) {
            return "link"
        }

        if (ch == "\"") return "string"

        if (ch == "~") return "brace"

        if (ch != null && Regex("[\\[\\]]").containsMatchIn(ch) && stream.match(ch)) {
            return "brace"
        }

        if (ch == "@") {
            stream.eatWhile(twIsSpaceName)
            return "link"
        }

        if (ch != null && Regex("\\d").containsMatchIn(ch)) {
            stream.eatWhile(Regex("\\d"))
            return "number"
        }

        if (ch == "/") {
            if (stream.eat("%") != null) {
                return chain(stream, state, ::twTokenComment)
            } else if (stream.eat("/") != null) {
                return chain(stream, state, ::twTokenEm)
            }
        }

        if (ch == "_" && stream.eat("_") != null) {
            return chain(stream, state, ::twTokenUnderline)
        }

        if (ch == "-" && stream.eat("-") != null) {
            if (stream.peek() != " ") {
                return chain(stream, state, ::twTokenStrike)
            }
            if (stream.peek() == " ") return "brace"
        }

        if (ch == "'" && stream.eat("'") != null) {
            return chain(stream, state, ::twTokenStrong)
        }

        if (ch == "<" && stream.eat("<") != null) {
            return chain(stream, state, ::twTokenMacro)
        }

        stream.eatWhile(Regex("[\\w\$_]"))
        return null
    }

    override fun startState(indentUnit: Int) = TiddlyWikiState(tokenize = ::tokenBase)

    override fun copyState(state: TiddlyWikiState) = state.copy()

    override fun token(stream: StringStream, state: TiddlyWikiState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }
}
