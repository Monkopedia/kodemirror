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

private val cobolAtoms = setOf(
    "TRUE", "FALSE", "ZEROES", "ZEROS", "ZERO", "SPACES", "SPACE",
    "LOW-VALUE", "LOW-VALUES"
)

@Suppress("ktlint:standard:max-line-length")
private val cobolKeywords = setOf(
    "ACCEPT", "ACCESS", "ACQUIRE", "ADD", "ADDRESS",
    "ADVANCING", "AFTER", "ALIAS", "ALL", "ALPHABET",
    "ALPHABETIC", "ALPHABETIC-LOWER", "ALPHABETIC-UPPER", "ALPHANUMERIC", "ALPHANUMERIC-EDITED",
    "ALSO", "ALTER", "ALTERNATE", "AND", "ANY",
    "ARE", "AREA", "AREAS", "ARITHMETIC", "ASCENDING",
    "ASSIGN", "AT", "ATTRIBUTE", "AUTHOR", "AUTO",
    "AUTO-SKIP", "AUTOMATIC", "B-AND", "B-EXOR", "B-LESS",
    "B-NOT", "B-OR", "BACKGROUND-COLOR", "BACKGROUND-COLOUR", "BEEP",
    "BEFORE", "BELL", "BINARY", "BIT", "BITS",
    "BLANK", "BLINK", "BLOCK", "BOOLEAN", "BOTTOM",
    "BY", "CALL", "CANCEL", "CD", "CF",
    "CH", "CHARACTER", "CHARACTERS", "CLASS", "CLOCK-UNITS",
    "CLOSE", "COBOL", "CODE", "CODE-SET", "COL",
    "COLLATING", "COLUMN", "COMMA", "COMMIT", "COMMITMENT",
    "COMMON", "COMMUNICATION", "COMP", "COMP-0", "COMP-1",
    "COMP-2", "COMP-3", "COMP-4", "COMP-5", "COMP-6",
    "COMP-7", "COMP-8", "COMP-9", "COMPUTATIONAL", "COMPUTATIONAL-0",
    "COMPUTATIONAL-1", "COMPUTATIONAL-2", "COMPUTATIONAL-3", "COMPUTATIONAL-4", "COMPUTATIONAL-5",
    "COMPUTATIONAL-6", "COMPUTATIONAL-7", "COMPUTATIONAL-8", "COMPUTATIONAL-9", "COMPUTE",
    "CONFIGURATION", "CONNECT", "CONSOLE", "CONTAINED", "CONTAINS",
    "CONTENT", "CONTINUE", "CONTROL", "CONTROL-AREA", "CONTROLS",
    "CONVERTING", "COPY", "CORR", "CORRESPONDING", "COUNT",
    "CRT", "CRT-UNDER", "CURRENCY", "CURRENT", "CURSOR",
    "DATA", "DATE", "DATE-COMPILED", "DATE-WRITTEN", "DAY",
    "DAY-OF-WEEK", "DB", "DB-ACCESS-CONTROL-KEY", "DB-DATA-NAME", "DB-EXCEPTION",
    "DB-FORMAT-NAME", "DB-RECORD-NAME", "DB-SET-NAME", "DB-STATUS", "DBCS",
    "DBCS-EDITED", "DE", "DEBUG-CONTENTS", "DEBUG-ITEM", "DEBUG-LINE",
    "DEBUG-NAME", "DEBUG-SUB-1", "DEBUG-SUB-2", "DEBUG-SUB-3", "DEBUGGING",
    "DECIMAL-POINT", "DECLARATIVES", "DEFAULT", "DELETE", "DELIMITED",
    "DELIMITER", "DEPENDING", "DESCENDING", "DESCRIBED", "DESTINATION",
    "DETAIL", "DISABLE", "DISCONNECT", "DISPLAY", "DISPLAY-1",
    "DISPLAY-2", "DISPLAY-3", "DISPLAY-4", "DISPLAY-5", "DISPLAY-6",
    "DISPLAY-7", "DISPLAY-8", "DISPLAY-9", "DIVIDE", "DIVISION",
    "DOWN", "DROP", "DUPLICATE", "DUPLICATES", "DYNAMIC",
    "EBCDIC", "EGI", "EJECT", "ELSE", "EMI",
    "EMPTY", "EMPTY-CHECK", "ENABLE", "END", "END.", "END-ACCEPT", "END-ACCEPT.",
    "END-ADD", "END-CALL", "END-COMPUTE", "END-DELETE", "END-DISPLAY",
    "END-DIVIDE", "END-EVALUATE", "END-IF", "END-INVOKE", "END-MULTIPLY",
    "END-OF-PAGE", "END-PERFORM", "END-READ", "END-RECEIVE", "END-RETURN",
    "END-REWRITE", "END-SEARCH", "END-START", "END-STRING", "END-SUBTRACT",
    "END-UNSTRING", "END-WRITE", "END-XML", "ENTER", "ENTRY",
    "ENVIRONMENT", "EOP", "EQUAL", "EQUALS", "ERASE",
    "ERROR", "ESI", "EVALUATE", "EVERY", "EXCEEDS",
    "EXCEPTION", "EXCLUSIVE", "EXIT", "EXTEND", "EXTERNAL",
    "EXTERNALLY-DESCRIBED-KEY", "FD", "FETCH", "FILE", "FILE-CONTROL",
    "FILE-STREAM", "FILES", "FILLER", "FINAL", "FIND",
    "FINISH", "FIRST", "FOOTING", "FOR", "FOREGROUND-COLOR",
    "FOREGROUND-COLOUR", "FORMAT", "FREE", "FROM", "FULL",
    "FUNCTION", "GENERATE", "GET", "GIVING", "GLOBAL",
    "GO", "GOBACK", "GREATER", "GROUP", "HEADING",
    "HIGH-VALUE", "HIGH-VALUES", "HIGHLIGHT", "I-O", "I-O-CONTROL",
    "ID", "IDENTIFICATION", "IF", "IN", "INDEX",
    "INDEX-1", "INDEX-2", "INDEX-3", "INDEX-4", "INDEX-5",
    "INDEX-6", "INDEX-7", "INDEX-8", "INDEX-9", "INDEXED",
    "INDIC", "INDICATE", "INDICATOR", "INDICATORS", "INITIAL",
    "INITIALIZE", "INITIATE", "INPUT", "INPUT-OUTPUT", "INSPECT",
    "INSTALLATION", "INTO", "INVALID", "INVOKE", "IS",
    "JUST", "JUSTIFIED", "KANJI", "KEEP", "KEY",
    "LABEL", "LAST", "LD", "LEADING", "LEFT",
    "LEFT-JUSTIFY", "LENGTH", "LENGTH-CHECK", "LESS", "LIBRARY",
    "LIKE", "LIMIT", "LIMITS", "LINAGE", "LINAGE-COUNTER",
    "LINE", "LINE-COUNTER", "LINES", "LINKAGE", "LOCAL-STORAGE",
    "LOCALE", "LOCALLY", "LOCK",
    "MEMBER", "MEMORY", "MERGE", "MESSAGE", "METACLASS",
    "MODE", "MODIFIED", "MODIFY", "MODULES", "MOVE",
    "MULTIPLE", "MULTIPLY", "NATIONAL", "NATIVE", "NEGATIVE",
    "NEXT", "NO", "NO-ECHO", "NONE", "NOT",
    "NULL", "NULL-KEY-MAP", "NULL-MAP", "NULLS", "NUMBER",
    "NUMERIC", "NUMERIC-EDITED", "OBJECT", "OBJECT-COMPUTER", "OCCURS",
    "OF", "OFF", "OMITTED", "ON", "ONLY",
    "OPEN", "OPTIONAL", "OR", "ORDER", "ORGANIZATION",
    "OTHER", "OUTPUT", "OVERFLOW", "OWNER", "PACKED-DECIMAL",
    "PADDING", "PAGE", "PAGE-COUNTER", "PARSE", "PERFORM",
    "PF", "PH", "PIC", "PICTURE", "PLUS",
    "POINTER", "POSITION", "POSITIVE", "PREFIX", "PRESENT",
    "PRINTING", "PRIOR", "PROCEDURE", "PROCEDURE-POINTER", "PROCEDURES",
    "PROCEED", "PROCESS", "PROCESSING", "PROGRAM", "PROGRAM-ID",
    "PROMPT", "PROTECTED", "PURGE", "QUEUE", "QUOTE",
    "QUOTES", "RANDOM", "RD", "READ", "READY",
    "REALM", "RECEIVE", "RECONNECT", "RECORD", "RECORD-NAME",
    "RECORDS", "RECURSIVE", "REDEFINES", "REEL", "REFERENCE",
    "REFERENCE-MONITOR", "REFERENCES", "RELATION", "RELATIVE", "RELEASE",
    "REMAINDER", "REMOVAL", "RENAMES", "REPEATED", "REPLACE",
    "REPLACING", "REPORT", "REPORTING", "REPORTS", "REPOSITORY",
    "REQUIRED", "RERUN", "RESERVE", "RESET", "RETAINING",
    "RETRIEVAL", "RETURN", "RETURN-CODE", "RETURNING", "REVERSE-VIDEO",
    "REVERSED", "REWIND", "REWRITE", "RF", "RH",
    "RIGHT", "RIGHT-JUSTIFY", "ROLLBACK", "ROLLING", "ROUNDED",
    "RUN", "SAME", "SCREEN", "SD", "SEARCH",
    "SECTION", "SECURE", "SECURITY", "SEGMENT", "SEGMENT-LIMIT",
    "SELECT", "SEND", "SENTENCE", "SEPARATE", "SEQUENCE",
    "SEQUENTIAL", "SET", "SHARED", "SIGN", "SIZE",
    "SORT", "SORT-MERGE", "SOURCE", "SOURCE-COMPUTER", "SPACE",
    "SPECIAL-NAMES", "STANDARD", "STANDARD-1", "STANDARD-2", "START",
    "STOP", "STRING", "SUB-QUEUE-1", "SUB-QUEUE-2", "SUB-QUEUE-3",
    "SUB-SCHEMA", "SUBTRACT", "SUM", "SUPPRESS", "SYMBOLIC",
    "SYNC", "SYNCHRONIZED", "TABLE", "TALLYING", "TAPE",
    "TERMINAL", "TERMINATE", "TEST", "TEXT", "THAN",
    "THEN", "THROUGH", "THRU", "TIME", "TIMES",
    "TO", "TOP", "TRAILING", "TRAILING-SIGN", "TRANSFORM",
    "TYPE", "UNDERLINE", "UNSTRING", "UNTIL", "UP",
    "UPDATE", "UPON", "USAGE", "USE", "USING",
    "VALUE", "VALUES", "VARYING", "WHEN", "WITH",
    "WORDS", "WORKING-STORAGE", "WRITE", "XML", "XML-CODE",
    "XML-EVENT", "XML-NTEXT", "XML-TEXT", "ZERO", "ZERO-FILL"
)

private val cobolBuiltins = setOf("-", "*", "**", "/", "+", "<", "<=", "=", ">", ">=")

private val cobolDigit = Regex("\\d")
private val cobolDigitOrColon = Regex("[\\d:]")
private val cobolHex = Regex("[0-9a-fA-F]")
private val cobolSign = Regex("[+\\-]")
private val cobolExponent = Regex("[eE]")
private val cobolSymbol = Regex("[\\w*+\\-]")

private fun cobolIsNumber(ch: String, stream: StringStream): Boolean {
    if (ch == "0" && stream.eat("x") != null) {
        stream.eatWhile(cobolHex)
        return true
    }
    if ((ch == "+" || ch == "-") && cobolDigit.containsMatchIn(stream.peek() ?: "")) {
        stream.eat(cobolSign)
        val next = stream.next() ?: return false
        if (!cobolDigit.containsMatchIn(next)) {
            stream.backUp(1)
            return false
        }
        stream.eatWhile(cobolDigit)
        if (stream.peek() == ".") {
            stream.eat(".")
            stream.eatWhile(cobolDigit)
        }
        if (stream.eat(cobolExponent) != null) {
            stream.eat(cobolSign)
            stream.eatWhile(cobolDigit)
        }
        return true
    }
    if (cobolDigit.containsMatchIn(ch)) {
        stream.eatWhile(cobolDigit)
        if (stream.peek() == ".") {
            stream.eat(".")
            stream.eatWhile(cobolDigit)
        }
        if (stream.eat(cobolExponent) != null) {
            stream.eat(cobolSign)
            stream.eatWhile(cobolDigit)
        }
        return true
    }
    return false
}

data class CobolState(
    var indentStack: CobolIndent? = null,
    var indentation: Int = 0,
    var mode: String? = null
)

data class CobolIndent(
    val prev: CobolIndent?,
    val indent: Int
)

/** Stream parser for COBOL. */
val cobol: StreamParser<CobolState> = object : StreamParser<CobolState> {
    override val name: String get() = "cobol"

    override fun startState(indentUnit: Int) = CobolState()
    override fun copyState(state: CobolState) = state.copy()

    override fun token(stream: StringStream, state: CobolState): String? {
        if (state.indentStack == null && stream.sol()) {
            state.indentation = 6
        }
        if (stream.eatSpace()) return null

        when (state.mode) {
            "string" -> {
                var next: String?
                while (stream.next().also { next = it } != null) {
                    if ((next == "\"" || next == "'") &&
                        stream.match(Regex("['\"]"), consume = false) == null
                    ) {
                        state.mode = null
                        break
                    }
                }
                return "string"
            }
            else -> {
                val ch = stream.next() ?: return null
                val col = stream.column()
                return when {
                    col in 0..5 -> "def"
                    col in 72..79 -> {
                        stream.skipToEnd()
                        "header"
                    }
                    ch == "*" && col == 6 -> {
                        stream.skipToEnd()
                        "comment"
                    }
                    ch == "\"" || ch == "'" -> {
                        state.mode = "string"
                        "string"
                    }
                    ch == "'" && !cobolDigitOrColon.containsMatchIn(stream.peek() ?: "") -> {
                        "atom"
                    }
                    ch == "." -> "link"
                    cobolIsNumber(ch, stream) -> "number"
                    else -> {
                        if (cobolSymbol.containsMatchIn(ch)) {
                            var c = col
                            while (c < 71) {
                                if (stream.eat(cobolSymbol) == null) break else c++
                            }
                        }
                        val word = stream.current().uppercase()
                        when {
                            word in cobolKeywords -> "keyword"
                            word in cobolBuiltins -> "builtin"
                            word in cobolAtoms -> "atom"
                            else -> null
                        }
                    }
                }
            }
        }
    }

    override fun indent(
        state: CobolState,
        textAfter: String,
        context: com.monkopedia.kodemirror.language.IndentContext
    ): Int {
        return state.indentStack?.indent ?: state.indentation
    }
}
