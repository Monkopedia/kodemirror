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

data class VbScriptState(
    var tokenize: (StringStream, VbScriptState) -> String?,
    var lastToken: Any? = null,
    var currentIndent: Int = 0,
    var nextLineIndent: Int = 0,
    var doInCurrentLine: Boolean = false,
    var ignoreKeyword: Boolean = false
)

private fun vbsWordRegexp(words: List<String>): Regex {
    return Regex("^((" + words.joinToString(")|(") + "))\\b", RegexOption.IGNORE_CASE)
}

@Suppress("LongMethod")
private fun mkVbScript(isASP: Boolean): StreamParser<VbScriptState> {
    val singleOperators = Regex("^[+\\-*/&\\\\^<>=]")
    val doubleOperators = Regex("^((<>)|(<=)|(>=))")
    val singleDelimiters = Regex("^[.,]")
    val brackets = Regex("^[()]")
    val identifiers = Regex("^[A-Za-z][_A-Za-z0-9]*")

    val openingKeywords = listOf(
        "class", "sub", "select", "while", "if", "function", "property", "with", "for"
    )
    val middleKeywords = listOf("else", "elseif", "case")
    val endKeywords = listOf("next", "loop", "wend")

    val wordOperators = vbsWordRegexp(listOf("and", "or", "not", "xor", "is", "mod", "eqv", "imp"))
    val commonkeywords = listOf(
        "dim", "redim", "then", "until", "randomize", "byval", "byref", "new", "property",
        "exit", "in", "const", "private", "public", "get", "set", "let", "stop",
        "on error resume next", "on error goto 0", "option explicit", "call", "me"
    )

    val atomWords = listOf("true", "false", "nothing", "empty", "null")
    val builtinFuncsWords = listOf(
        "abs", "array", "asc", "atn", "cbool", "cbyte", "ccur", "cdate", "cdbl", "chr",
        "cint", "clng", "cos", "csng", "cstr", "date", "dateadd", "datediff", "datepart",
        "dateserial", "datevalue", "day", "escape", "eval", "execute", "exp", "filter",
        "formatcurrency", "formatdatetime", "formatnumber", "formatpercent", "getlocale",
        "getobject", "getref", "hex", "hour", "inputbox", "instr", "instrrev", "int", "fix",
        "isarray", "isdate", "isempty", "isnull", "isnumeric", "isobject", "join", "lbound",
        "lcase", "left", "len", "loadpicture", "log", "ltrim", "rtrim", "trim", "maths",
        "mid", "minute", "month", "monthname", "msgbox", "now", "oct", "replace", "rgb",
        "right", "rnd", "round", "scriptengine", "scriptenginebuildversion",
        "scriptenginemajorversion", "scriptengineminorversion", "second", "setlocale", "sgn",
        "sin", "space", "split", "sqr", "strcomp", "string", "strreverse", "tan", "time",
        "timer", "timeserial", "timevalue", "typename", "ubound", "ucase", "unescape",
        "vartype", "weekday", "weekdayname", "year"
    )

    val builtinConsts = listOf(
        "vbBlack", "vbRed", "vbGreen", "vbYellow", "vbBlue", "vbMagenta", "vbCyan",
        "vbWhite", "vbBinaryCompare", "vbTextCompare", "vbSunday", "vbMonday", "vbTuesday",
        "vbWednesday", "vbThursday", "vbFriday", "vbSaturday", "vbUseSystemDayOfWeek",
        "vbFirstJan1", "vbFirstFourDays", "vbFirstFullWeek", "vbGeneralDate", "vbLongDate",
        "vbShortDate", "vbLongTime", "vbShortTime", "vbObjectError", "vbOKOnly", "vbOKCancel",
        "vbAbortRetryIgnore", "vbYesNoCancel", "vbYesNo", "vbRetryCancel", "vbCritical",
        "vbQuestion", "vbExclamation", "vbInformation", "vbDefaultButton1",
        "vbDefaultButton2", "vbDefaultButton3", "vbDefaultButton4", "vbApplicationModal",
        "vbSystemModal", "vbOK", "vbCancel", "vbAbort", "vbRetry", "vbIgnore", "vbYes",
        "vbNo", "vbCr", "VbCrLf", "vbFormFeed", "vbLf", "vbNewLine", "vbNullChar",
        "vbNullString", "vbTab", "vbVerticalTab", "vbUseDefault", "vbTrue", "vbFalse",
        "vbEmpty", "vbNull", "vbInteger", "vbLong", "vbSingle", "vbDouble", "vbCurrency",
        "vbDate", "vbString", "vbObject", "vbError", "vbBoolean", "vbVariant",
        "vbDataObject", "vbDecimal", "vbByte", "vbArray"
    )

    var builtinObjsWords = listOf("WScript", "err", "debug", "RegExp") + builtinConsts
    val knownProperties = listOf(
        "description", "firstindex", "global", "helpcontext", "helpfile",
        "ignorecase", "length", "number", "pattern", "source", "value", "count"
    )
    val knownMethods = listOf(
        "clear", "execute", "raise", "replace", "test", "write", "writeline", "close",
        "open", "state", "eof", "update", "addnew", "end", "createobject", "quit"
    )

    val aspBuiltinObjsWords = listOf("server", "response", "request", "session", "application")
    val aspKnownProperties = listOf(
        "buffer", "cachecontrol", "charset", "contenttype", "expires", "expiresabsolute",
        "isclientconnected", "pics", "status", "clientcertificate", "cookies", "form",
        "querystring", "servervariables", "totalbytes", "contents", "staticobjects",
        "codepage", "lcid", "sessionid", "timeout", "scripttimeout"
    )
    val aspKnownMethods = listOf(
        "addheader", "appendtolog", "binarywrite", "end", "flush", "redirect",
        "binaryread", "remove", "removeall", "lock", "unlock", "abandon",
        "getlasterror", "htmlencode", "mappath", "transfer", "urlencode"
    )

    var knownWords = (knownMethods + knownProperties).toMutableList()

    if (isASP) {
        builtinObjsWords = builtinObjsWords + aspBuiltinObjsWords
        knownWords = (knownWords + aspKnownMethods + aspKnownProperties).toMutableList()
    }

    val keywords = vbsWordRegexp(commonkeywords)
    val atoms = vbsWordRegexp(atomWords)
    val builtinFuncs = vbsWordRegexp(builtinFuncsWords)
    val builtinObjs = vbsWordRegexp(builtinObjsWords)
    val known = vbsWordRegexp(knownWords)
    val opening = vbsWordRegexp(openingKeywords)
    val middle = vbsWordRegexp(middleKeywords)
    val closing = vbsWordRegexp(endKeywords)
    val doubleClosing = vbsWordRegexp(listOf("end"))
    val doOpening = vbsWordRegexp(listOf("do"))
    val noIndentWords = vbsWordRegexp(listOf("on error resume next", "exit"))
    val comment = vbsWordRegexp(listOf("rem"))

    // Forward reference for tokenBase (used in tokenStringFactory before tokenBase is defined)
    var tokenBaseRef: (StringStream, VbScriptState) -> String? = { _, _ -> null }

    fun tokenStringFactory(delimiter: String): (StringStream, VbScriptState) -> String? {
        val singleline = delimiter.length == 1
        return fun(stream: StringStream, state: VbScriptState): String? {
            while (!stream.eol()) {
                stream.eatWhile(Regex("[^'\"]"))
                if (stream.match(delimiter)) {
                    state.tokenize = tokenBaseRef
                    return "string"
                } else {
                    stream.eat(Regex("['\"]"))
                }
            }
            if (singleline) state.tokenize = tokenBaseRef
            return "string"
        }
    }

    fun tokenBase(stream: StringStream, state: VbScriptState): String? {
        if (stream.eatSpace()) return null

        val ch = stream.peek()

        if (ch == "'") {
            stream.skipToEnd()
            return "comment"
        }
        if (stream.match(comment) != null) {
            stream.skipToEnd()
            return "comment"
        }

        val isNumStart = stream.match(
            Regex("^((&H)|(&O))?[0-9.]", RegexOption.IGNORE_CASE), false
        ) != null
        val isNumIdent = stream.match(
            Regex("^((&H)|(&O))?[0-9.]+[a-z_]", RegexOption.IGNORE_CASE), false
        ) != null
        if (isNumStart && !isNumIdent) {
            var floatLiteral = false
            if (stream.match(Regex("^\\d*\\.\\d+", RegexOption.IGNORE_CASE)) != null) {
                floatLiteral = true
            } else if (stream.match(Regex("^\\d+\\.\\d*")) != null) {
                floatLiteral = true
            } else if (stream.match(Regex("^\\.\\d+")) != null) {
                floatLiteral = true
            }
            if (floatLiteral) {
                stream.eat(Regex("[Jj]", RegexOption.IGNORE_CASE))
                return "number"
            }
            var intLiteral = false
            if (stream.match(Regex("^&H[0-9a-f]+", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            } else if (stream.match(Regex("^&O[0-7]+", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            } else if (stream.match(Regex("^[1-9]\\d*F?")) != null) {
                stream.eat(Regex("[Jj]", RegexOption.IGNORE_CASE))
                intLiteral = true
            } else if (stream.match(Regex("^0(?![\\dx])", RegexOption.IGNORE_CASE)) != null) {
                intLiteral = true
            }
            if (intLiteral) {
                stream.eat(Regex("[Ll]", RegexOption.IGNORE_CASE))
                return "number"
            }
        }

        if (stream.match("\"")) {
            state.tokenize = tokenStringFactory(stream.current())
            return state.tokenize(stream, state)
        }

        if (stream.match(doubleOperators) != null ||
            stream.match(singleOperators) != null ||
            stream.match(wordOperators) != null
        ) {
            return "operator"
        }
        if (stream.match(singleDelimiters) != null) return null
        if (stream.match(brackets) != null) return "bracket"

        if (stream.match(noIndentWords) != null) {
            state.doInCurrentLine = true
            return "keyword"
        }
        if (stream.match(doOpening) != null) {
            state.currentIndent++
            state.doInCurrentLine = true
            return "keyword"
        }
        if (stream.match(opening) != null) {
            if (!state.doInCurrentLine) {
                state.currentIndent++
            } else {
                state.doInCurrentLine = false
            }
            return "keyword"
        }
        if (stream.match(middle) != null) return "keyword"
        if (stream.match(doubleClosing) != null) {
            state.currentIndent--
            state.currentIndent--
            return "keyword"
        }
        if (stream.match(closing) != null) {
            if (!state.doInCurrentLine) {
                state.currentIndent--
            } else {
                state.doInCurrentLine = false
            }
            return "keyword"
        }
        if (stream.match(keywords) != null) return "keyword"
        if (stream.match(atoms) != null) return "atom"
        if (stream.match(known) != null) return "variableName.special"
        if (stream.match(builtinFuncs) != null) return "builtin"
        if (stream.match(builtinObjs) != null) return "builtin"
        if (stream.match(identifiers) != null) return "variable"

        stream.next()
        return "error"
    }

    tokenBaseRef = ::tokenBase

    fun tokenLexer(stream: StringStream, state: VbScriptState): String? {
        var style = state.tokenize(stream, state)
        val current = stream.current()

        if (current == ".") {
            style = state.tokenize(stream, state)
            val cur2 = stream.current()
            if (style != null &&
                (style.startsWith("variable") || style == "builtin" || style == "keyword")
            ) {
                if (style == "builtin" || style == "keyword") style = "variable"
                if (knownWords.any { it.equals(cur2.substring(1), ignoreCase = true) }) {
                    style = "keyword"
                }
                return style
            } else {
                return "error"
            }
        }

        return style
    }

    return object : StreamParser<VbScriptState> {
        override val name: String get() = "vbscript"

        override fun startState(indentUnit: Int) = VbScriptState(tokenize = ::tokenBase)

        override fun copyState(state: VbScriptState) = state.copy()

        override fun token(stream: StringStream, state: VbScriptState): String? {
            if (stream.sol()) {
                state.currentIndent += state.nextLineIndent
                state.nextLineIndent = 0
                state.doInCurrentLine = false
            }
            val style = tokenLexer(stream, state)
            state.lastToken = mapOf("style" to style, "content" to stream.current())
            return style
        }

        override fun indent(state: VbScriptState, textAfter: String, cx: IndentContext): Int? {
            val trueText = textAfter.trim()
            if (closing.containsMatchIn(trueText) ||
                doubleClosing.containsMatchIn(trueText) ||
                middle.containsMatchIn(trueText)
            ) {
                return cx.unit * (state.currentIndent - 1)
            }
            if (state.currentIndent < 0) return 0
            return state.currentIndent * cx.unit
        }
    }
}

/** Stream parser for VBScript. */
val vbScript: StreamParser<VbScriptState> = mkVbScript(false)

/** Stream parser for VBScript (ASP). */
val vbScriptASP: StreamParser<VbScriptState> = mkVbScript(true)
