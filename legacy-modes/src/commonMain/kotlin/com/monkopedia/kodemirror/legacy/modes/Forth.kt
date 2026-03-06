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

private val forthCoreWords = (
    "INVERT AND OR XOR " +
        "2* 2/ LSHIFT RSHIFT " +
        "0= = 0< < > U< MIN MAX " +
        "2DROP 2DUP 2OVER 2SWAP ?DUP DEPTH DROP DUP OVER ROT SWAP " +
        ">R R> R@ " +
        "+ - 1+ 1- ABS NEGATE " +
        "S>D * M* UM* " +
        "FM/MOD SM/REM UM/MOD */ */MOD / /MOD MOD " +
        "HERE , @ ! CELL+ CELLS C, C@ C! CHARS 2@ 2! " +
        "ALIGN ALIGNED +! ALLOT " +
        "CHAR [CHAR] [ ] BL " +
        "FIND EXECUTE IMMEDIATE COUNT LITERAL STATE " +
        "; DOES> >BODY " +
        "EVALUATE " +
        "SOURCE >IN " +
        "<# # #S #> HOLD SIGN BASE >NUMBER HEX DECIMAL " +
        "FILL MOVE " +
        ". CR EMIT SPACE SPACES TYPE U. .R U.R " +
        "ACCEPT " +
        "TRUE FALSE " +
        "<> U> 0<> 0> " +
        "NIP TUCK ROLL PICK " +
        "2>R 2R@ 2R> " +
        "WITHIN UNUSED MARKER " +
        "I J " +
        "TO " +
        "COMPILE, [COMPILE] " +
        "SAVE-INPUT RESTORE-INPUT " +
        "PAD ERASE " +
        "2LITERAL DNEGATE " +
        "D- D+ D0< D0= D2* D2/ D< D= DMAX DMIN D>S DABS " +
        "M+ M*/ D. D.R 2ROT DU< " +
        "CATCH THROW " +
        "FREE RESIZE ALLOCATE " +
        "CS-PICK CS-ROLL " +
        "GET-CURRENT SET-CURRENT FORTH-WORDLIST GET-ORDER SET-ORDER " +
        "PREVIOUS SEARCH-WORDLIST WORDLIST FIND ALSO ONLY FORTH " +
        "DEFINITIONS ORDER " +
        "-TRAILING /STRING SEARCH COMPARE CMOVE CMOVE> BLANK SLITERAL"
    ).split(" ").filter { it.isNotEmpty() }.map { it.uppercase() }.toSet()

private val forthImmediateWords = (
    "IF ELSE THEN BEGIN WHILE REPEAT UNTIL RECURSE [IF] [ELSE] " +
        "[THEN] ?DO DO LOOP +LOOP UNLOOP LEAVE EXIT AGAIN CASE OF " +
        "ENDOF ENDCASE"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

data class ForthState(
    var state: String = "",
    var base: Int = 10,
    var wordList: MutableList<String> = mutableListOf()
)

/** Stream parser for Forth. */
val forth: StreamParser<ForthState> = object : StreamParser<ForthState> {
    override val name: String get() = "forth"

    override fun startState(indentUnit: Int) = ForthState()
    override fun copyState(state: ForthState) =
        state.copy(wordList = state.wordList.toMutableList())

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    override fun token(stream: StringStream, state: ForthState): String? {
        if (stream.eatSpace()) return null

        if (state.state == "") {
            if (stream.match(
                    Regex("^(]|:NONAME)(\\s|$)", RegexOption.IGNORE_CASE)
                ) != null
            ) {
                state.state = " compilation"
                return "builtin"
            }
            val mat = stream.match(
                Regex("^(:)\\s+(\\S+)(\\s|$)+")
            )
            if (mat != null) {
                state.wordList.add(mat.groupValues[2].uppercase())
                state.state = " compilation"
                return "def"
            }
            val mat2 = stream.match(
                Regex(
                    "^(VARIABLE|2VARIABLE|CONSTANT|2CONSTANT|CREATE|" +
                        "POSTPONE|VALUE|WORD)\\s+(\\S+)(\\s|$)+",
                    RegexOption.IGNORE_CASE
                )
            )
            if (mat2 != null) {
                state.wordList.add(mat2.groupValues[2].uppercase())
                return "def"
            }
            if (stream.match(
                    Regex("^('|\\[']|)\\s+(\\S+)(\\s|$)+")
                ) != null
            ) {
                return "builtin"
            }
        } else {
            if (stream.match(Regex("^(;|\\[)(\\s)")) != null) {
                state.state = ""
                stream.backUp(1)
                return "builtin"
            }
            if (stream.match(Regex("^(;|\\[)($)")) != null) {
                state.state = ""
                return "builtin"
            }
            if (stream.match(
                    Regex("^(POSTPONE)\\s+\\S+(\\s|$)+")
                ) != null
            ) {
                return "builtin"
            }
        }

        val mat = stream.match(Regex("^(\\S+)(\\s+|$)"))
        if (mat != null) {
            val word = mat.groupValues[1]
            if (word.uppercase() in state.wordList.map { it.uppercase() }) {
                return "variable"
            }
            if (word == "\\") {
                stream.skipToEnd()
                return "comment"
            }
            if (word.uppercase() in forthCoreWords) {
                return "builtin"
            }
            if (word.uppercase() in forthImmediateWords) {
                return "keyword"
            }
            if (word == "(") {
                stream.eatWhile { it != ")" }
                stream.eat(")")
                return "comment"
            }
            if (word == ".(") {
                stream.eatWhile { it != ")" }
                stream.eat(")")
                return "string"
            }
            if (word == "S\"" || word == ".\"" || word == "C\"") {
                stream.eatWhile { it != "\"" }
                stream.eat("\"")
                return "string"
            }
            val num = word.toDoubleOrNull()
            if (num != null && num != 0.0) {
                return "number"
            }
            // Try as integer
            val intNum = word.toLongOrNull()
            if (intNum != null) {
                return "number"
            }
            // Try hex
            val hexNum = word.toLongOrNull(16)
            if (hexNum != null) {
                return "number"
            }
            return "atom"
        }
        return null
    }
}
