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

@Suppress("MaxLineLength")
private val aplBuiltInFuncs = setOf(
    "+", "\u2212", "\u00d7", "\u00f7", "\u2308", "\u230a", "\u2223",
    "\u2373", "?", "\u22c6", "\u235f", "\u25cb", "!", "\u2339",
    "<", "\u2264", "=", ">", "\u2265", "\u2260", "\u2261", "\u2262",
    "\u2208", "\u2377", "\u222a", "\u2229", "\u223c", "\u2228",
    "\u2227", "\u2371", "\u2372", "\u2374", ",", "\u236a", "\u233d",
    "\u2296", "\u2349", "\u2191", "\u2193", "\u2282", "\u2283",
    "\u2337", "\u234b", "\u2352", "\u22a4", "\u22a5", "\u2355",
    "\u234e", "\u22a3", "\u22a2"
)

private val aplIsOperator = Regex("[./\u233f\u2340\u00a8\u2363]")
private val aplIsNiladic = Regex("\u236c")

@Suppress("MaxLineLength")
private val aplIsFunction = Regex(
    "[+\u2212\u00d7\u00f7\u2308\u230a\u2223\u2373?\\u22c6\u235f" +
        "\u25cb!\u2339<\u2264=>\u2265\u2260\u2261\u2262\u2208\u2377" +
        "\u222a\u2229\u223c\u2228\u2227\u2371\u2372\u2374,\u236a" +
        "\u233d\u2296\u2349\u2191\u2193\u2282\u2283\u2337\u234b" +
        "\u2352\u22a4\u22a5\u2355\u234e\u22a3\u22a2]"
)
private val aplIsArrow = Regex("\u2190")
private val aplIsComment = Regex("[\u235d#].*$")

data class AplState(
    var prev: Boolean = false,
    var func: Boolean = false,
    var op: Boolean = false,
    var string: Boolean = false,
    var escape: Boolean = false
)

/** Stream parser for APL. */
val apl: StreamParser<AplState> = object : StreamParser<AplState> {
    override val name: String get() = "apl"

    override fun startState(indentUnit: Int) = AplState()
    override fun copyState(state: AplState) = state.copy()

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun token(stream: StringStream, state: AplState): String? {
        if (stream.eatSpace()) return null
        val ch = stream.next() ?: return null

        if (ch == "\"" || ch == "'") {
            stream.eatWhile(aplStringEater(ch))
            stream.next()
            state.prev = true
            return "string"
        }
        if (Regex("[\\[{(]").containsMatchIn(ch)) {
            state.prev = false
            return null
        }
        if (Regex("[\\]})]").containsMatchIn(ch)) {
            state.prev = true
            return null
        }
        if (aplIsNiladic.containsMatchIn(ch)) {
            state.prev = false
            return "atom"
        }
        if (Regex("[\u00af\\d]").containsMatchIn(ch)) {
            if (state.func) {
                state.func = false
                state.prev = false
            } else {
                state.prev = true
            }
            stream.eatWhile(Regex("[\\w.]"))
            return "number"
        }
        if (aplIsOperator.containsMatchIn(ch)) {
            return "operator"
        }
        if (aplIsArrow.containsMatchIn(ch)) {
            return "operator"
        }
        if (aplIsFunction.containsMatchIn(ch)) {
            state.func = true
            state.prev = false
            return if (ch in aplBuiltInFuncs) {
                "variableName.function.standard"
            } else {
                "variableName.function"
            }
        }
        if (aplIsComment.containsMatchIn(ch)) {
            stream.skipToEnd()
            return "comment"
        }
        if (ch == "\u2218" && stream.peek() == ".") {
            stream.next()
            return "variableName.function"
        }
        stream.eatWhile(Regex("[\\w\$_]"))
        state.prev = true
        return "keyword"
    }
}

private fun aplStringEater(type: String): (String) -> Boolean {
    return { c ->
        c != type
    }
}
