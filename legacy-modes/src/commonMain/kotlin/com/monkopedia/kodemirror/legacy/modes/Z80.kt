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

data class Z80State(
    var context: Int = 0
)

private fun mkZ80(ez80: Boolean): StreamParser<Z80State> {
    val keywords1: Regex
    val keywords2: Regex
    if (ez80) {
        keywords1 = Regex(
            "^(exx?|(ld|cp)([di]r?)?|[lp]ea|pop|push|ad[cd]|cpl|daa|dec|inc|neg|sbc|sub" +
                "|and|bit|[cs]cf|x?or|res|set|r[lr]c?a?|r[lr]d|s[lr]a|srl|djnz|nop|[de]i" +
                "|halt|im|in([di]mr?|ir?|irx|2r?)|ot(dmr?|[id]rx|imr?)|out(0?|[di]r?|[di]2r?)" +
                "|tst(io)?|slp)(\\.([sl]?i)?[sl])?\\b",
            RegexOption.IGNORE_CASE
        )
        keywords2 = Regex(
            "^(((call|j[pr]|rst|ret[in]?)(\\.([sl]?i)?[sl])?)|(rs|st)mix)\\b",
            RegexOption.IGNORE_CASE
        )
    } else {
        keywords1 = Regex(
            "^(exx?|(ld|cp|in)([di]r?)?|pop|push|ad[cd]|cpl|daa|dec|inc|neg|sbc|sub" +
                "|and|bit|[cs]cf|x?or|res|set|r[lr]c?a?|r[lr]d|s[lr]a|srl|djnz|nop|rst" +
                "|[de]i|halt|im|ot[di]r|out[di]?)\\b",
            RegexOption.IGNORE_CASE
        )
        keywords2 = Regex(
            "^(call|j[pr]|ret[in]?|b_?(call|jump))\\b",
            RegexOption.IGNORE_CASE
        )
    }

    val variables1 = Regex("^(af?|bc?|c|de?|e|hl?|l|i[xy]?|r|sp)\\b", RegexOption.IGNORE_CASE)
    val variables2 = Regex("^(n?[zc]|p[oe]?|m)\\b", RegexOption.IGNORE_CASE)
    val errors = Regex("^([hl][xy]|i[xy][hl]|slia|sll)\\b", RegexOption.IGNORE_CASE)
    val numbers = Regex("^([\\da-f]+h|[0-7]+o|[01]+b|\\d+d?)\\b", RegexOption.IGNORE_CASE)

    return object : StreamParser<Z80State> {
        override val name: String get() = "z80"

        override fun startState(indentUnit: Int) = Z80State()
        override fun copyState(state: Z80State) = state.copy()

        override fun token(stream: StringStream, state: Z80State): String? {
            if (stream.column() == 0) {
                state.context = 0
            }

            if (stream.eatSpace()) return null

            if (stream.eatWhile(Regex("""\w"""))) {
                if (ez80 && stream.eat(".") != null) {
                    stream.eatWhile(Regex("""\w"""))
                }
                val w = stream.current()

                if (stream.indentation() > 0) {
                    if ((state.context == 1 || state.context == 4) &&
                        variables1.containsMatchIn(w)
                    ) {
                        state.context = 4
                        return "variable"
                    }

                    if (state.context == 2 && variables2.containsMatchIn(w)) {
                        state.context = 4
                        return "variableName.special"
                    }

                    if (keywords1.containsMatchIn(w)) {
                        state.context = 1
                        return "keyword"
                    } else if (keywords2.containsMatchIn(w)) {
                        state.context = 2
                        return "keyword"
                    } else if (state.context == 4 && numbers.containsMatchIn(w)) {
                        return "number"
                    }

                    if (errors.containsMatchIn(w)) return "error"
                } else if (stream.match(numbers) != null) {
                    return "number"
                } else {
                    return null
                }
            } else if (stream.eat(";") != null) {
                stream.skipToEnd()
                return "comment"
            } else if (stream.eat("\"") != null) {
                var w = stream.next()
                while (w != null) {
                    if (w == "\"") break
                    if (w == "\\") stream.next()
                    w = stream.next()
                }
                return "string"
            } else if (stream.eat("'") != null) {
                if (stream.match(Regex("^\\\\?.'")) != null) {
                    return "number"
                }
            } else if (stream.eat(".") != null || (stream.sol() && stream.eat("#") != null)) {
                state.context = 5
                if (stream.eatWhile(Regex("""\w"""))) {
                    return "def"
                }
            } else if (stream.eat("$") != null) {
                if (stream.eatWhile(Regex("^[\\da-f]", RegexOption.IGNORE_CASE))) {
                    return "number"
                }
            } else if (stream.eat("%") != null) {
                if (stream.eatWhile(Regex("^[01]"))) {
                    return "number"
                }
            } else {
                stream.next()
            }
            return null
        }
    }
}

val z80: StreamParser<Z80State> = mkZ80(false)
val ez80: StreamParser<Z80State> = mkZ80(true)
