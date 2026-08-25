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

import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for a partially typed character literal crashing the Clojure
 * parse (issue #269).
 *
 * `qualifiedSymbol` can succeed with a *zero-length* match — its trailing
 * `(?:…)*` may match nothing while the lookahead is satisfied by a backslash —
 * and a backslash that `characterLiteral` rejected is exactly the input that
 * reaches it unconsumed. CodeMirror 5 v5.65.16 guards with `if (!symbol)`,
 * which is true for `""` as well as `null`, and its `symbol.charAt(0)` returns
 * `""` rather than throwing. The port narrowed the guard to `symbol == null`
 * and used `symbol[0]`, so `\ab`, `\abc`, `\newlin` — every intermediate state
 * of typing `\newline` — threw out of the parse.
 *
 * The upstream oracle for these inputs is the error branch: advance one
 * character, eat up to the next delimiter, and emit `[null, "error"]`. Every
 * assertion below therefore checks the tokens produced, not merely that the
 * parse completed: "did not throw" would also hold for a fix that returned the
 * wrong token or left the stream in the wrong place.
 */
class ClojurePartialCharacterLiteralTest {

    /** Every token in the tree as `name@from-to`, in document order. */
    private fun <S> tokens(parser: StreamParser<S>, code: String): List<String> {
        val lang = StreamLanguage.define(parser)
        val state = EditorState.create(
            EditorStateConfig(doc = code.asDoc(), extensions = lang.extension)
        )
        val tree = syntaxTree(state)
        assertEquals(code.length, tree.length)
        val result = mutableListOf<String>()
        val cursor = tree.cursor()
        while (cursor.next()) {
            result.add("${cursor.type.name}@${cursor.from}-${cursor.to}")
        }
        return result
    }

    /** Offset every token range by [by], for comparing against a shifted doc. */
    private fun shift(tokens: List<String>, by: Int): List<String> = tokens.map { token ->
        val (name, range) = token.split("@")
        val (from, to) = range.split("-").map(String::toInt)
        "$name@${from + by}-${to + by}"
    }

    /**
     * The minimal reproducer from the issue: a backslash followed by two
     * characters that do not form a character literal. The whole run is one
     * `invalid` token — `stream.next()` takes the `\`, then `eatWhile` takes
     * `ab` because neither is a delimiter.
     */
    @Test
    fun twoCharacterPartialLiteralIsOneErrorToken() {
        assertEquals(listOf("invalid@0-3"), tokens(clojure, "\\ab"))
    }

    /** Same shape, one character longer. */
    @Test
    fun threeCharacterPartialLiteralIsOneErrorToken() {
        assertEquals(listOf("invalid@0-4"), tokens(clojure, "\\abc"))
    }

    /** The named-literal case: `\newline` mistyped, or caught mid-keystroke. */
    @Test
    fun partialNamedLiteralIsOneErrorToken() {
        assertEquals(listOf("invalid@0-7"), tokens(clojure, "\\newlin"))
    }

    /**
     * Typing `\newline` one keystroke at a time. `\` and `\n` are complete
     * character literals so they highlight as such; every longer prefix short
     * of the full name is the crashing input, and the full name is a literal
     * again. None of these may throw.
     */
    @Test
    fun everyPrefixOfNewlineLiteralParses() {
        val full = "\\newline"
        val actual = (1..full.length).associate { n ->
            val prefix = full.substring(0, n)
            prefix to tokens(clojure, prefix)
        }
        assertEquals(
            mapOf(
                "\\" to listOf("string.special@0-1"),
                "\\n" to listOf("string.special@0-2"),
                "\\ne" to listOf("invalid@0-3"),
                "\\new" to listOf("invalid@0-4"),
                "\\newl" to listOf("invalid@0-5"),
                "\\newli" to listOf("invalid@0-6"),
                "\\newlin" to listOf("invalid@0-7"),
                "\\newline" to listOf("string.special@0-8")
            ),
            actual
        )
    }

    /**
     * The error branch must consume the backslash and stop at the delimiter, so
     * whatever follows tokenizes exactly as it does on its own. This is the
     * upstream oracle stated as an invariant rather than a literal expectation:
     * a fix that mis-consumed the run would shift or swallow the tail.
     */
    @Test
    fun partialLiteralDoesNotDisturbTheRestOfTheForm() {
        val tail = " (def x 1)"
        assertEquals(
            shift(tokens(clojure, tail), "\\ab".length),
            tokens(clojure, "\\ab$tail").drop(1)
        )
    }

    /**
     * The bad literal inside a real form: the surrounding brackets and symbols
     * still tokenize, which they cannot do if the parse threw partway.
     */
    @Test
    fun partialLiteralInsideAFormLeavesTheFormIntact() {
        assertEquals(
            listOf(
                "bracket@0-1",
                "keyword@1-4",
                "variableName@5-6",
                "invalid@7-10",
                "bracket@10-11"
            ),
            tokens(clojure, "(def x \\ab)")
        )
    }
}
