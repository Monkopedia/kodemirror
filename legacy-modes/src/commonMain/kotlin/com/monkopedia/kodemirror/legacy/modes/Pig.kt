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

@Suppress("ktlint:standard:max-line-length")
private val pigBuiltinStr =
    "ABS ACOS ARITY ASIN ATAN AVG BAGSIZE BINSTORAGE BLOOM BUILDBLOOM CBRT CEIL " +
        "CONCAT COR COS COSH COUNT COUNT_STAR COV CROSS CSV CUT CONSTANTSIZE CUBEDIMENSIONS DIFF DISTINCT DOUBLEABS " +
        "DOUBLEAVG DOUBLEBASE DOUBLEMAX DOUBLEMIN DOUBLEROUND DOUBLESUM EXP FLOOR FLOATABS FLOATAVG " +
        "FLOATMAX FLOATMIN FLOATROUND FLOATSUM GENERICINVOKER INDEXOF INTABS INTAVG INTMAX INTMIN " +
        "INTSUM INVOKEFORDOUBLE INVOKEFORFLOAT INVOKEFORINT INVOKEFORLONG INVOKEFORSTRING INVOKER " +
        "ISEMPTY JSONLOADER JSONMETADATA JSONSTORAGE LAST_INDEX_OF LCFIRST LOG LOG10 LOWER LONGABS " +
        "LONGAVG LONGMAX LONGMIN LONGSUM MAX MIN MAPSIZE MONITOREDUDF NONDETERMINISTIC OUTPUTSCHEMA " +
        "PIGSTORAGE PIGSTREAMING RANDOM REGEX_EXTRACT REGEX_EXTRACT_ALL REPLACE ROUND SIN SINH SIZE " +
        "SQRT STRSPLIT SUBSTRING SUM STRINGCONCAT STRINGMAX STRINGMIN STRINGSIZE TAN TANH TOBAG " +
        "TOKENIZE TOMAP TOP TOTUPLE TRIM TEXTLOADER TUPLESIZE UCFIRST UPPER UTF8STORAGECONVERTER"

@Suppress("ktlint:standard:max-line-length")
private val pigKeywordStr =
    "VOID IMPORT RETURNS DEFINE LOAD FILTER FOREACH ORDER CUBE DISTINCT COGROUP " +
        "JOIN CROSS UNION SPLIT INTO IF OTHERWISE ALL AS BY USING INNER OUTER ONSCHEMA PARALLEL " +
        "PARTITION GROUP AND OR NOT GENERATE FLATTEN ASC DESC IS STREAM THROUGH STORE MAPREDUCE " +
        "SHIP CACHE INPUT OUTPUT STDERROR STDIN STDOUT LIMIT SAMPLE LEFT RIGHT FULL EQ GT LT GTE LTE " +
        "NEQ MATCHES TRUE FALSE DUMP"

private val pigTypeStr = "BOOLEAN INT LONG FLOAT DOUBLE CHARARRAY BYTEARRAY BAG TUPLE MAP"

private val pigBuiltins = pigBuiltinStr.split(" ").filter { it.isNotEmpty() }.toSet()
private val pigKeywords = pigKeywordStr.split(" ").filter { it.isNotEmpty() }.toSet()
private val pigTypes = pigTypeStr.split(" ").filter { it.isNotEmpty() }.toSet()

private val pigIsOperatorChar = Regex("[*+\\-%<>=&?:/!|]")

data class PigState(
    var tokenize: (StringStream, PigState) -> String?,
    var startOfLine: Boolean = true
)

private fun pigTokenComment(stream: StringStream, state: PigState): String? {
    var isEnd = false
    var ch: String?
    while (true) {
        ch = stream.next()
        if (ch == null) break
        if (ch == "/" && isEnd) {
            state.tokenize = ::pigTokenBase
            break
        }
        isEnd = (ch == "*")
    }
    return "comment"
}

private fun pigTokenString(quote: String): (StringStream, PigState) -> String? {
    return fun(stream: StringStream, state: PigState): String? {
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
            escaped = !escaped && next == "\\"
        }
        if (end || !escaped) {
            state.tokenize = ::pigTokenBase
        }
        return "error"
    }
}

private fun pigChain(
    stream: StringStream,
    state: PigState,
    f: (StringStream, PigState) -> String?
): String? {
    state.tokenize = f
    return f(stream, state)
}

private fun pigTokenBase(stream: StringStream, state: PigState): String? {
    val ch = stream.next() ?: return null

    // is a start of string?
    if (ch == "\"" || ch == "'") {
        return pigChain(stream, state, pigTokenString(ch))
    }
    // is it one of the special chars
    if (Regex("[\\[\\]{}(),;.]").matches(ch)) return null
    // is it a number?
    if (Regex("\\d").matches(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    }
    // multi line comment or operator
    if (ch == "/") {
        if (stream.eat("*") != null) {
            return pigChain(stream, state, ::pigTokenComment)
        } else {
            stream.eatWhile(pigIsOperatorChar)
            return "operator"
        }
    }
    // single line comment or operator
    if (ch == "-") {
        if (stream.eat("-") != null) {
            stream.skipToEnd()
            return "comment"
        } else {
            stream.eatWhile(pigIsOperatorChar)
            return "operator"
        }
    }
    // is it an operator
    if (pigIsOperatorChar.matches(ch)) {
        stream.eatWhile(pigIsOperatorChar)
        return "operator"
    }

    // get the whole word
    stream.eatWhile(Regex("[\\w\$_]"))
    val cur = stream.current().uppercase()

    // is it one of the listed keywords?
    if (cur in pigKeywords) {
        // keywords can be used as variables like flatten(group), group.$0 etc
        if (stream.eat(")") == null && stream.eat(".") == null) {
            return "keyword"
        }
    }
    // is it one of the builtin functions?
    if (cur in pigBuiltins) return "builtin"
    // is it one of the listed types?
    if (cur in pigTypes) return "type"
    // default is a 'variable'
    return "variable"
}

/** Stream parser for Pig Latin (Apache Pig). */
val pig: StreamParser<PigState> = object : StreamParser<PigState> {
    override val name: String get() = "pig"

    override fun startState(indentUnit: Int) = PigState(
        tokenize = ::pigTokenBase
    )

    override fun copyState(state: PigState) = state.copy(
        tokenize = state.tokenize
    )

    override fun token(stream: StringStream, state: PigState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }
}
