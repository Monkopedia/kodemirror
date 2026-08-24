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
 * Regression tests for a `dedent = true` rule that fires with an empty indent
 * stack (issue #262).
 *
 * CodeMirror 5's `simple-mode` runs `state.indent.pop()` unguarded, and
 * `Array.prototype.pop()` on an empty array is a silent no-op returning
 * `undefined`. The Kotlin port used `removeAt(lastIndex)`, which throws, so a
 * document that *opens* with a closing token crashed the parse.
 *
 * Every assertion below therefore checks the tokens the parse produces, not
 * merely that it completes: the no-op semantics mean the unmatched closer must
 * leave parser state untouched, so the rest of the document has to tokenize
 * exactly as it does without the closer in front of it.
 */
class SimpleModeDedentTest {

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
     * A Rust document that begins with `}` — a stray brace typed into an empty
     * buffer. `rustLegacy` puts the `[\}\]\)]` dedent rule in its `start` state,
     * so this pops an empty indent stack.
     */
    @Test
    fun rustLeadingCloseBraceTokenizesLikeUpstream() {
        // `}` and the space are `null` tokens; `let x` matches the
        // ("keyword", null, "def") rule, whose first group ends at 5, and whose
        // `x` is replayed from `pending` as `def` -> `variableName.definition`.
        assertEquals(
            listOf(
                "keyword@2-5",
                "variableName.definition@6-7",
                "operator@8-9",
                "number@10-12"
            ),
            tokens(rustLegacy, "} let x = 42;")
        )
    }

    /**
     * The upstream oracle, expressed directly: because `[].pop()` is a no-op,
     * the unmatched `}` must not perturb parser state, so the tokens after it
     * are exactly those of the same text parsed on its own, shifted by one.
     */
    @Test
    fun rustLeadingCloseBraceDoesNotPerturbState() {
        val tail = " let x = 42;"
        assertEquals(
            shift(tokens(rustLegacy, tail), 1),
            tokens(rustLegacy, "}$tail")
        )
    }

    /**
     * Three unmatched closers in a row: the stack is already empty and each pop
     * must stay a no-op rather than driving it negative.
     */
    @Test
    fun rustRepeatedUnmatchedClosersAreNoOps() {
        // `}])` is three `null` tokens, so `fn` starts at 4 and `main` at 7.
        assertEquals(
            listOf("keyword@4-6", "variableName.definition@7-11"),
            tokens(rustLegacy, "}]) fn main() {}")
        )
    }

    /**
     * An unmatched closer followed by a balanced pair: the no-op pop must leave
     * the later `{ … }` tokenizing exactly as it does on its own.
     */
    @Test
    fun rustUnmatchedCloserDoesNotDisturbLaterBalancedPair() {
        assertEquals(
            shift(tokens(rustLegacy, "{ let x = 42; }"), 1),
            tokens(rustLegacy, "}{ let x = 42; }")
        )
    }

    /**
     * `nsis` has nine `dedent = true` rules; `!endif` is one of them and is
     * matched at start-of-line in the `start` state.
     */
    @Test
    fun nsisLeadingDedentDirectiveTokenizes() {
        assertEquals(
            listOf("keyword@0-6", "keyword@7-11"),
            tokens(nsis, "!endif\nName \"Test\"").take(2)
        )
    }

    /** The same upstream oracle for `nsis`. */
    @Test
    fun nsisLeadingDedentDirectiveDoesNotPerturbState() {
        val tail = "Name \"Test\"\nOutFile \"test.exe\""
        assertEquals(
            shift(tokens(nsis, tail), "!endif\n".length),
            tokens(nsis, "!endif\n$tail").drop(1)
        )
    }
}
