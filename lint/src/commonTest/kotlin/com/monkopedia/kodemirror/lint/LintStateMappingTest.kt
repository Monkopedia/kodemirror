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
package com.monkopedia.kodemirror.lint

import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Clamping and mapping of diagnostics (#382). Every expected value was produced by running
 * `@codemirror/lint` 6.9.5 on the same input (`setDiagnostics`, then one change, then
 * `forEachDiagnostic`'s reported `from`/`to`).
 */
class LintStateMappingTest {

    private fun d(from: Int, to: Int, message: String) =
        Diagnostic(DocPos(from), DocPos(to), Severity.ERROR, message)

    private fun insert(at: Int, text: String) =
        ChangeSpec.Single(DocPos(at), insert = InsertContent.StringContent(text))

    private fun delete(from: Int, to: Int) = ChangeSpec.Single(DocPos(from), DocPos(to))

    private fun dump(state: EditorState): String {
        val parts = mutableListOf<String>()
        forEachDiagnostic(state) { parts.add("${it.message}@${it.from.value}-${it.to.value}") }
        return "count=${diagnosticCount(state)} [${parts.joinToString(",")}]"
    }

    /** Returns the dump after setting [diagnostics] and the dump after applying [change]. */
    private fun run(
        doc: String,
        diagnostics: List<Diagnostic>,
        change: ChangeSpec
    ): Pair<String, String> {
        val start = EditorState.create(EditorStateConfig(doc = doc.asDoc(), extensions = lintState))
        val set = start.update(
            TransactionSpec(effects = listOf(setDiagnosticsEffect.of(diagnostics)))
        ).state
        val changed = set.update(TransactionSpec(changes = change)).state
        return dump(set) to dump(changed)
    }

    @Test
    fun diagnosticEndingPastEofIsClampedAndSurvivesEdits() {
        assertEquals(
            "count=1 [a@2-5]" to "count=1 [a@3-6]",
            run("hello", listOf(d(2, 8, "a")), insert(0, "x"))
        )
    }

    @Test
    fun diagnosticStartingPastEofIsDropped() {
        assertEquals(
            "count=0 []" to "count=0 []",
            run("hello", listOf(d(7, 9, "a")), insert(0, "x"))
        )
    }

    @Test
    fun diagnosticStartingAtEofIsClampedToAPoint() {
        assertEquals(
            "count=1 [a@5-5]" to "count=1 [a@6-6]",
            run("hello", listOf(d(5, 8, "a")), insert(0, "x"))
        )
    }

    @Test
    fun inRangeDiagnosticSurvivesWhenAnotherIsPastEof() {
        assertEquals(
            "count=1 [in@0-2]" to "count=1 [in@0-2]",
            run("hello", listOf(d(0, 2, "in"), d(6, 9, "out")), insert(5, "!"))
        )
    }

    @Test
    fun pointDiagnosticSurvivesUnrelatedEdit() {
        assertEquals(
            "count=2 [pt@3-3,rng@6-11]" to "count=2 [pt@3-3,rng@6-11]",
            run("hello world", listOf(d(3, 3, "pt"), d(6, 11, "rng")), insert(11, "!"))
        )
    }

    @Test
    fun pointDiagnosticMovesWithInsertBefore() {
        assertEquals(
            "count=1 [pt@3-3]" to "count=1 [pt@5-5]",
            run("hello world", listOf(d(3, 3, "pt")), insert(0, "xx"))
        )
    }

    @Test
    fun pointDiagnosticStaysBeforeInsertAtItsPosition() {
        assertEquals(
            "count=1 [pt@3-3]" to "count=1 [pt@3-3]",
            run("hello world", listOf(d(3, 3, "pt")), insert(3, "xx"))
        )
    }

    @Test
    fun pointDiagnosticIsDroppedWhenADeletionSpansIt() {
        assertEquals(
            "count=1 [pt@3-3]" to "count=0 []",
            run("hello world", listOf(d(3, 3, "pt")), delete(2, 4))
        )
    }

    @Test
    fun pointDiagnosticSurvivesDeletionEndingAtIt() {
        assertEquals(
            "count=1 [pt@3-3]" to "count=1 [pt@2-2]",
            run("hello world", listOf(d(3, 3, "pt")), delete(2, 3))
        )
        assertEquals(
            "count=1 [pt@3-3]" to "count=1 [pt@0-0]",
            run("hello world", listOf(d(3, 3, "pt")), delete(0, 3))
        )
    }

    @Test
    fun pointDiagnosticSurvivesDeletionStartingAtIt() {
        assertEquals(
            "count=1 [pt@3-3]" to "count=1 [pt@3-3]",
            run("hello world", listOf(d(3, 3, "pt")), delete(3, 4))
        )
    }

    @Test
    fun pointDiagnosticAtEofSurvivesEdit() {
        assertEquals(
            "count=1 [pt@5-5]" to "count=1 [pt@6-6]",
            run("hello", listOf(d(5, 5, "pt")), insert(0, "x"))
        )
    }

    @Test
    fun rangeDiagnosticMapping() {
        // Controls: these already agreed with upstream before #382.
        assertEquals(
            "count=1 [r@2-5]" to "count=0 []",
            run("hello world", listOf(d(2, 5, "r")), delete(1, 6))
        )
        assertEquals(
            "count=1 [r@2-5]" to "count=1 [r@3-6]",
            run("hello world", listOf(d(2, 5, "r")), insert(2, "x"))
        )
        assertEquals(
            "count=1 [r@2-5]" to "count=1 [r@2-5]",
            run("hello world", listOf(d(2, 5, "r")), insert(5, "x"))
        )
        assertEquals(
            "count=1 [r@2-5]" to "count=1 [r@2-4]",
            run("hello world", listOf(d(2, 5, "r")), delete(4, 8))
        )
    }
}
