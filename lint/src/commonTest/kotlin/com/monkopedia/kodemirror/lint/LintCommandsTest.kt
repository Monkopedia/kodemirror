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

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LintCommandsTest {

    private fun createView(
        doc: String,
        diagnostics: List<Diagnostic>,
        anchor: Int = 0,
        head: Int = anchor
    ): EditorSession {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.EditorSelectionSpec(
                    EditorSelection.single(DocPos(anchor), DocPos(head))
                ),
                extensions = ExtensionList(listOf(lintState, lintPanelOpen))
            )
        )
        val view = EditorSession(state)
        view.dispatch(
            TransactionSpec(
                effects = listOf(setDiagnosticsEffect.of(diagnostics))
            )
        )
        return view
    }

    private fun d(from: Int, to: Int, message: String) =
        Diagnostic(DocPos(from), DocPos(to), Severity.ERROR, message)

    private val alphabet = "abcdefghijklmnopqrstuvwxyz"
    private val twoDiagnostics = listOf(d(5, 8, "first"), d(15, 18, "second"))

    /** Runs [command] [times] times and reports its last result plus "anchor/head". */
    private fun run(
        view: EditorSession,
        command: (EditorSession) -> Boolean,
        times: Int = 1
    ): String {
        var result = false
        repeat(times) { result = command(view) }
        val sel = view.state.selection.main
        return "$result sel=${sel.anchor.value}/${sel.head.value}"
    }

    // Expected values below were produced by @codemirror/lint 6.9.5's nextDiagnostic and
    // previousDiagnostic on the same documents, diagnostics and selections (#382).

    @Test
    fun nextDiagnosticSelectsTheNextRange() {
        assertEquals("true sel=5/8", run(createView(alphabet, twoDiagnostics), nextDiagnostic))
        assertEquals(
            "true sel=15/18",
            run(createView(alphabet, twoDiagnostics), nextDiagnostic, times = 2)
        )
        assertEquals("true sel=5/8", run(createView(alphabet, twoDiagnostics, 4), nextDiagnostic))
    }

    @Test
    fun nextDiagnosticFromInsideADiagnosticSelectsIt() {
        assertEquals("true sel=5/8", run(createView(alphabet, twoDiagnostics, 6), nextDiagnostic))
    }

    @Test
    fun nextDiagnosticFromTheEndOfADiagnosticMovesOn() {
        assertEquals("true sel=15/18", run(createView(alphabet, twoDiagnostics, 8), nextDiagnostic))
    }

    @Test
    fun nextDiagnosticWrapsAround() {
        assertEquals(
            "true sel=2/5",
            run(createView("abcdefghij", listOf(d(2, 5, "only")), 8), nextDiagnostic)
        )
    }

    @Test
    fun nextDiagnosticReturnsFalseWhenAlreadyOnTheOnlyDiagnostic() {
        assertEquals(
            "false sel=2/5",
            run(createView("abcdefghij", listOf(d(2, 5, "only")), 2, 5), nextDiagnostic)
        )
    }

    @Test
    fun nextDiagnosticReturnsFalseWithoutDiagnostics() {
        assertEquals("false sel=3/3", run(createView(alphabet, emptyList(), 3), nextDiagnostic))
    }

    @Test
    fun nextDiagnosticSelectsAPointDiagnostic() {
        assertEquals(
            "true sel=10/10",
            run(createView(alphabet, listOf(d(10, 10, "pt"))), nextDiagnostic)
        )
    }

    @Test
    fun previousDiagnosticSelectsThePreviousRange() {
        assertEquals(
            "true sel=15/18",
            run(createView(alphabet, twoDiagnostics, 20), previousDiagnostic)
        )
        assertEquals(
            "true sel=5/8",
            run(createView(alphabet, twoDiagnostics, 20), previousDiagnostic, times = 2)
        )
        assertEquals(
            "true sel=5/8",
            run(createView(alphabet, twoDiagnostics, 9), previousDiagnostic)
        )
    }

    @Test
    fun previousDiagnosticFromInsideADiagnosticSkipsIt() {
        assertEquals(
            "true sel=5/8",
            run(createView(alphabet, twoDiagnostics, 16), previousDiagnostic)
        )
    }

    @Test
    fun previousDiagnosticWraps() {
        assertEquals(
            "true sel=15/18",
            run(createView(alphabet, twoDiagnostics, 2), previousDiagnostic)
        )
        assertEquals(
            "true sel=15/18",
            run(createView(alphabet, twoDiagnostics, 8), previousDiagnostic)
        )
    }

    @Test
    fun previousDiagnosticReturnsFalseWhenNothingToMoveTo() {
        assertEquals(
            "false sel=16/16",
            run(createView(alphabet, emptyList(), 16), previousDiagnostic)
        )
        assertEquals(
            "false sel=2/2",
            run(createView("abcdefghij", listOf(d(2, 5, "only")), 2), previousDiagnostic)
        )
        assertEquals(
            "false sel=2/5",
            run(createView("abcdefghij", listOf(d(2, 5, "only")), 2, 5), previousDiagnostic)
        )
    }

    @Test
    fun commandsReturnFalseWithoutTheLintState() {
        val view = EditorSession(EditorState.create(EditorStateConfig(doc = alphabet.asDoc())))
        assertFalse(nextDiagnostic(view))
        assertFalse(previousDiagnostic(view))
    }

    @Test
    fun openAndCloseLintPanelToggle() {
        val view = createView("hello", emptyList())
        assertTrue(openLintPanel(view))
        val openState = view.state.field(lintPanelOpen, require = false)
        assertEquals(true, openState)
        assertTrue(closeLintPanel(view))
        val closedState = view.state.field(lintPanelOpen, require = false)
        assertEquals(false, closedState)
        assertFalse(closeLintPanel(view))
    }
}
