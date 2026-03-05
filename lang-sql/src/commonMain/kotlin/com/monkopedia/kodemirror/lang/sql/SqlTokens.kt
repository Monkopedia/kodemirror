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
@file:Suppress("ktlint:standard:max-line-length")

package com.monkopedia.kodemirror.lang.sql

import com.monkopedia.kodemirror.lezer.lr.ExternalTokenizer
import com.monkopedia.kodemirror.lezer.lr.InputStream

// Token type constants (from generated grammar)
internal const val WHITESPACE = 36
internal const val LINE_COMMENT = 1
internal const val BLOCK_COMMENT = 2
internal const val SQL_STRING = 3
internal const val SQL_NUMBER = 4
internal const val BOOL = 5
internal const val NULL = 6
internal const val PAREN_L = 7
internal const val PAREN_R = 8
internal const val BRACE_L = 9
internal const val BRACE_R = 10
internal const val BRACKET_L = 11
internal const val BRACKET_R = 12
internal const val SEMI = 13
internal const val DOT = 14
internal const val OPERATOR = 15
internal const val PUNCTUATION = 16
internal const val SPECIAL_VAR = 17
internal const val IDENTIFIER = 18
internal const val QUOTED_IDENTIFIER = 19
internal const val KEYWORD = 20
internal const val TYPE = 21
internal const val BITS = 22
internal const val BYTES = 23
internal const val BUILTIN = 24

// SQL type and keyword strings
internal const val SQL_TYPES =
    "array binary bit boolean char character clob date decimal double float int integer " +
        "interval large national nchar nclob numeric object precision real smallint time " +
        "timestamp varchar varying "

internal const val SQL_KEYWORDS =
    "absolute action add after all allocate alter and any are as asc assertion at " +
        "authorization before begin between both breadth by call cascade cascaded case cast " +
        "catalog check close collate collation column commit condition connect connection " +
        "constraint constraints constructor continue corresponding count create cross cube " +
        "current current_date current_default_transform_group " +
        "current_transform_group_for_type current_path current_role current_time " +
        "current_timestamp current_user cursor cycle data day deallocate declare default " +
        "deferrable deferred delete depth deref desc describe descriptor deterministic " +
        "diagnostics disconnect distinct do domain drop dynamic each else elseif end " +
        "end-exec equals escape except exception exec execute exists exit external fetch " +
        "first for foreign found from free full function general get global go goto grant " +
        "group grouping handle having hold hour identity if immediate in indicator " +
        "initially inner inout input insert intersect into is isolation join key language " +
        "last lateral leading leave left level like limit local localtime localtimestamp " +
        "locator loop map match method minute modifies module month names natural nesting " +
        "new next no none not of old on only open option or order ordinality out outer " +
        "output overlaps pad parameter partial path prepare preserve primary prior " +
        "privileges procedure public read reads recursive redo ref references referencing " +
        "relative release repeat resignal restrict result return returns revoke right role " +
        "rollback rollup routine row rows savepoint schema scroll search second section " +
        "select session session_user set sets signal similar size some space specific " +
        "specifictype sql sqlexception sqlstate sqlwarning start state static system_user " +
        "table temporary then timezone_hour timezone_minute to trailing transaction " +
        "translation treat trigger under undo union unique unnest until update usage user " +
        "using value values view when whenever where while with without work write year zone "

/**
 * Spec for an SQL dialect tokenizer configuration.
 */
data class DialectSpec(
    val backslashEscapes: Boolean = false,
    val hashComments: Boolean = false,
    val spaceAfterDashes: Boolean = false,
    val slashComments: Boolean = false,
    val doubleQuotedStrings: Boolean = false,
    val doubleDollarQuotedStrings: Boolean = false,
    val unquotedBitLiterals: Boolean = false,
    val treatBitsAsBytes: Boolean = false,
    val charSetCasts: Boolean = false,
    val plsqlQuotingMechanism: Boolean = false,
    val operatorChars: String = "*+-%<>!=&|~^/",
    val specialVar: String = "?",
    val identifierQuotes: String = "\"",
    val caseInsensitiveIdentifiers: Boolean = false,
    val words: Map<String, Int> = emptyMap()
)

private fun isAlpha(ch: Int): Boolean = (ch in 65..90) || (ch in 97..122) || (ch in 48..57)

private fun isHexDigit(ch: Int): Boolean = (ch in 48..57) || (ch in 97..102) || (ch in 65..70)

private fun readLiteral(input: InputStream, endQuote: Int, backslashEscapes: Boolean) {
    var escaped = false
    while (true) {
        if (input.next < 0) return
        if (input.next == endQuote && !escaped) {
            input.advance()
            return
        }
        escaped = backslashEscapes && !escaped && input.next == 92 // Backslash
        input.advance()
    }
}

private fun readDoubleDollarLiteral(input: InputStream, tag: String) {
    scan@ while (true) {
        if (input.next < 0) return
        if (input.next == 36) { // Dollar
            input.advance()
            for (i in tag.indices) {
                if (input.next != tag[i].code) continue@scan
                input.advance()
            }
            if (input.next == 36) { // Dollar
                input.advance()
                return
            }
        } else {
            input.advance()
        }
    }
}

private fun readPLSQLQuotedLiteral(input: InputStream, openDelim: Int) {
    val matchingDelim = "[{<(".indexOf(openDelim.toChar())
    val closeDelim = if (matchingDelim < 0) openDelim else "]}>)"[matchingDelim].code
    while (true) {
        if (input.next < 0) return
        if (input.next == closeDelim && input.peek(1) == 39) { // SingleQuote
            input.advance(2)
            return
        }
        input.advance()
    }
}

private fun readWord(input: InputStream, result: String?): String? {
    var r = result
    while (true) {
        if (input.next != 95 && !isAlpha(input.next)) break // Underscore
        if (r != null) r += input.next.toChar()
        input.advance()
    }
    return r
}

private fun readWordOrQuoted(input: InputStream) {
    val n = input.next
    if (n == 39 || n == 34 || n == 96) { // SingleQuote, DoubleQuote, Backtick
        val quote = n
        input.advance()
        readLiteral(input, quote, false)
    } else {
        readWord(input, null)
    }
}

private fun readBits(input: InputStream, endQuote: Int = -1) {
    while (input.next == 48 || input.next == 49) // '0' or '1'
        input.advance()
    if (endQuote >= 0 && input.next == endQuote) input.advance()
}

private fun readNumber(input: InputStream, sawDot: Boolean) {
    var seenDot = sawDot
    while (true) {
        if (input.next == 46) { // Dot
            if (seenDot) break
            seenDot = true
        } else if (input.next < 48 || input.next > 57) { // not 0-9
            break
        }
        input.advance()
    }
    if (input.next == 69 || input.next == 101) { // E or e
        input.advance()
        if (input.next == 43 || input.next == 45) input.advance() // Plus or Dash
        while (input.next in 48..57) input.advance()
    }
}

private fun eol(input: InputStream) {
    while (!(input.next < 0 || input.next == 10)) // Newline
        input.advance()
}

private fun inString(ch: Int, str: String): Boolean {
    for (c in str) if (c.code == ch) return true
    return false
}

private val space = " \t\r\n"

/**
 * Build a keywords lookup map for a dialect.
 */
fun buildKeywords(keywords: String, types: String, builtin: String? = null): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    result["true"] = BOOL
    result["false"] = BOOL
    result["null"] = NULL
    result["unknown"] = NULL
    for (kw in keywords.split(" ")) if (kw.isNotEmpty()) result[kw] = KEYWORD
    for (tp in types.split(" ")) if (tp.isNotEmpty()) result[tp] = TYPE
    for (kw in (builtin ?: "").split(" ")) if (kw.isNotEmpty()) result[kw] = BUILTIN
    return result
}

internal val defaultWords: Map<String, Int> = buildKeywords(SQL_KEYWORDS, SQL_TYPES)

internal val defaults = DialectSpec(words = defaultWords)

/**
 * Create a [DialectSpec] merging the given [spec] fields with [defaults], optionally
 * overriding the keyword/type/builtin word set.
 */
internal fun buildDialect(
    spec: SqlDialectDef,
    kws: String? = null,
    types: String? = null,
    builtin: String? = null
): DialectSpec {
    val words = if (kws != null) buildKeywords(kws, types ?: "", builtin) else defaults.words
    return DialectSpec(
        backslashEscapes = spec.backslashEscapes ?: defaults.backslashEscapes,
        hashComments = spec.hashComments ?: defaults.hashComments,
        spaceAfterDashes = spec.spaceAfterDashes ?: defaults.spaceAfterDashes,
        slashComments = spec.slashComments ?: defaults.slashComments,
        doubleQuotedStrings = spec.doubleQuotedStrings ?: defaults.doubleQuotedStrings,
        doubleDollarQuotedStrings =
        spec.doubleDollarQuotedStrings ?: defaults.doubleDollarQuotedStrings,
        unquotedBitLiterals = spec.unquotedBitLiterals ?: defaults.unquotedBitLiterals,
        treatBitsAsBytes = spec.treatBitsAsBytes ?: defaults.treatBitsAsBytes,
        charSetCasts = spec.charSetCasts ?: defaults.charSetCasts,
        plsqlQuotingMechanism = spec.plsqlQuotingMechanism ?: defaults.plsqlQuotingMechanism,
        operatorChars = spec.operatorChars ?: defaults.operatorChars,
        specialVar = spec.specialVar ?: defaults.specialVar,
        identifierQuotes = spec.identifierQuotes ?: defaults.identifierQuotes,
        caseInsensitiveIdentifiers =
        spec.caseInsensitiveIdentifiers ?: defaults.caseInsensitiveIdentifiers,
        words = words
    )
}

/**
 * Create an [ExternalTokenizer] for the given [DialectSpec].
 */
fun tokensFor(d: DialectSpec): ExternalTokenizer = ExternalTokenizer({ input, _ ->
    val next = input.next
    input.advance()
    when {
        inString(next, space) -> {
            while (inString(input.next, space)) input.advance()
            input.acceptToken(WHITESPACE)
        }
        next == 36 && d.doubleDollarQuotedStrings -> {
            // Dollar sign: double-dollar quoted string
            val tag = readWord(input, "") ?: ""
            if (input.next == 36) {
                input.advance()
                readDoubleDollarLiteral(input, tag)
                input.acceptToken(SQL_STRING)
            }
        }
        next == 39 || (next == 34 && d.doubleQuotedStrings) -> {
            // SingleQuote or DoubleQuote (with flag)
            readLiteral(input, next, d.backslashEscapes)
            input.acceptToken(SQL_STRING)
        }
        (next == 35 && d.hashComments) ||
            (next == 47 && input.next == 47 && d.slashComments) -> {
            // Hash comment or slash-slash comment
            eol(input)
            input.acceptToken(LINE_COMMENT)
        }
        next == 45 && input.next == 45 &&
            (!d.spaceAfterDashes || input.peek(1) == 32) -> {
            // Dash-dash comment
            eol(input)
            input.acceptToken(LINE_COMMENT)
        }
        next == 47 && input.next == 42 -> {
            // Slash-star block comment (nested)
            input.advance()
            var depth = 1
            while (true) {
                val cur = input.next
                if (input.next < 0) break
                input.advance()
                if (cur == 42 && input.next == 47) {
                    depth--
                    input.advance()
                    if (depth == 0) break
                } else if (cur == 47 && input.next == 42) {
                    depth++
                    input.advance()
                }
            }
            input.acceptToken(BLOCK_COMMENT)
        }
        (next == 101 || next == 69) && input.next == 39 -> {
            // e' or E' escape string
            input.advance()
            readLiteral(input, 39, true)
            input.acceptToken(SQL_STRING)
        }
        (next == 110 || next == 78) && input.next == 39 && d.charSetCasts -> {
            // n' or N' national string
            input.advance()
            readLiteral(input, 39, d.backslashEscapes)
            input.acceptToken(SQL_STRING)
        }
        next == 95 && d.charSetCasts -> {
            // _charset string cast
            var i = 0
            while (true) {
                if (input.next == 39 && i > 1) {
                    input.advance()
                    readLiteral(input, 39, d.backslashEscapes)
                    input.acceptToken(SQL_STRING)
                    break
                }
                if (!isAlpha(input.next)) break
                input.advance()
                i++
            }
        }
        d.plsqlQuotingMechanism &&
            (next == 113 || next == 81) && input.next == 39 &&
            input.peek(1) > 0 && !inString(input.peek(1), space) -> {
            // q' or Q' PL/SQL quoting mechanism
            val openDelim = input.peek(1)
            input.advance(2)
            readPLSQLQuotedLiteral(input, openDelim)
            input.acceptToken(SQL_STRING)
        }
        inString(next, d.identifierQuotes) -> {
            // Quoted identifier
            val endQuote = if (next == 91) 93 else next // BracketL -> BracketR
            readLiteral(input, endQuote, false)
            input.acceptToken(QUOTED_IDENTIFIER)
        }
        next == 40 -> input.acceptToken(PAREN_L)
        next == 41 -> input.acceptToken(PAREN_R)
        next == 123 -> input.acceptToken(BRACE_L)
        next == 125 -> input.acceptToken(BRACE_R)
        next == 91 -> input.acceptToken(BRACKET_L)
        next == 93 -> input.acceptToken(BRACKET_R)
        next == 59 -> input.acceptToken(SEMI)
        d.unquotedBitLiterals && next == 48 && input.next == 98 -> {
            // 0b... unquoted bit literal
            input.advance()
            readBits(input)
            input.acceptToken(BITS)
        }
        (next == 98 || next == 66) && (input.next == 39 || input.next == 34) -> {
            // b' or B' bit literal
            val quoteStyle = input.next
            input.advance()
            if (d.treatBitsAsBytes) {
                readLiteral(input, quoteStyle, d.backslashEscapes)
                input.acceptToken(BYTES)
            } else {
                readBits(input, quoteStyle)
                input.acceptToken(BITS)
            }
        }
        (next == 48 && (input.next == 120 || input.next == 88)) ||
            ((next == 120 || next == 88) && input.next == 39) -> {
            // 0x... or x'...' hex literal
            val quoted = input.next == 39
            input.advance()
            while (isHexDigit(input.next)) input.advance()
            if (quoted && input.next == 39) input.advance()
            input.acceptToken(SQL_NUMBER)
        }
        next == 46 && input.next >= 48 && input.next <= 57 -> {
            // .NNN number starting with dot
            readNumber(input, true)
            input.acceptToken(SQL_NUMBER)
        }
        next == 46 -> input.acceptToken(DOT)
        next >= 48 && next <= 57 -> {
            // Regular number
            readNumber(input, false)
            input.acceptToken(SQL_NUMBER)
        }
        inString(next, d.operatorChars) -> {
            while (inString(input.next, d.operatorChars)) input.advance()
            input.acceptToken(OPERATOR)
        }
        inString(next, d.specialVar) -> {
            if (input.next == next) input.advance()
            readWordOrQuoted(input)
            input.acceptToken(SPECIAL_VAR)
        }
        next == 58 || next == 44 -> input.acceptToken(PUNCTUATION) // Colon or Comma
        isAlpha(next) -> {
            var word = next.toChar().toString()
            val wordResult = readWord(input, word)
            if (wordResult != null) word = wordResult
            val tokenType = if (input.next == 46 ||
                input.peek(-word.length - 1) == 46
            ) {
                IDENTIFIER
            } else {
                d.words[word.lowercase()] ?: IDENTIFIER
            }
            input.acceptToken(tokenType)
        }
        else -> { /* no token */ }
    }
})

/** The default tokens tokenizer (uses defaults dialect). */
val tokens: ExternalTokenizer = tokensFor(defaults)
