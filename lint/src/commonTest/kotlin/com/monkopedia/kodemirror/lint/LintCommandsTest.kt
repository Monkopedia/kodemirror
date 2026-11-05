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

    /**
     * Runs a sequence of "N" (next) / "P" (previous) commands and reports each result the way the
     * upstream probe prints it: `next:true:5/8`.
     */
    private fun steps(view: EditorSession, keys: String): String = keys.map { key ->
        val name = if (key == 'N') "next" else "prev"
        val result = (if (key == 'N') nextDiagnostic else previousDiagnostic)(view)
        val sel = view.state.selection.main
        "$name:$result:${sel.anchor.value}/${sel.head.value}"
    }.joinToString(" ")

    private fun ranges(vararg pairs: Pair<Int, Int>) = pairs.map { (from, to) ->
        d(from, to, "$from-$to")
    }

    // Overlapping diagnostics: upstream navigates over the non-overlapping pieces LintState.init
    // splits them into, so every diagnostic stays reachable. Expected strings are the output of
    // review-398/probe398.mjs against @codemirror/lint 6.9.5.

    @Test
    fun sameStartTiesCycleThroughBothPieces() {
        assertEquals(
            "next:true:5/7 next:true:7/10 next:true:5/7 next:true:7/10",
            steps(createView(alphabet, ranges(5 to 10, 5 to 7)), "NNNN")
        )
        assertEquals(
            "next:true:5/7 next:true:7/10 next:true:5/7 next:true:7/10",
            steps(createView(alphabet, ranges(5 to 7, 5 to 10)), "NNNN")
        )
        assertEquals(
            "prev:true:7/10 prev:true:5/7 prev:true:7/10 prev:true:5/7",
            steps(createView(alphabet, ranges(5 to 10, 5 to 7), 20), "PPPP")
        )
        assertEquals(
            "prev:true:7/10 prev:true:5/7 prev:true:7/10 prev:true:5/7",
            steps(createView(alphabet, ranges(5 to 7, 5 to 10), 20), "PPPP")
        )
    }

    @Test
    fun pointSharingAStartWithARangeStaysReachable() {
        assertEquals(
            "next:true:5/5 next:true:5/8 next:true:5/5 next:true:5/8",
            steps(createView(alphabet, ranges(5 to 8, 5 to 5)), "NNNN")
        )
        assertEquals(
            "next:true:5/5 next:true:5/8 next:true:5/5 next:true:5/8",
            steps(createView(alphabet, ranges(5 to 5, 5 to 8)), "NNNN")
        )
    }

    @Test
    fun nestedDiagnostics() {
        assertEquals(
            "next:true:0/20 next:false:0/20 next:false:0/20 next:false:0/20",
            steps(createView(alphabet, ranges(0 to 20, 5 to 8)), "NNNN")
        )
        assertEquals(
            "next:true:5/20 next:true:0/20 next:false:0/20 next:false:0/20",
            steps(createView(alphabet, ranges(0 to 20, 5 to 8), 6), "NNNN")
        )
        assertEquals(
            "prev:true:8/20 prev:true:5/8 prev:true:0/5 prev:true:8/20",
            steps(createView(alphabet, ranges(0 to 20, 5 to 8), 25), "PPPP")
        )
    }

    @Test
    fun partlyOverlappingDiagnostics() {
        assertEquals(
            "next:true:0/10 next:true:10/15 next:true:0/10 next:true:10/15",
            steps(createView(alphabet, ranges(0 to 10, 5 to 15)), "NNNN")
        )
        assertEquals(
            "next:true:5/10 next:true:10/15 next:true:0/10 next:true:10/15",
            steps(createView(alphabet, ranges(0 to 10, 5 to 15), 7), "NNNN")
        )
        assertEquals(
            "prev:true:10/15 prev:true:5/10 prev:true:0/5 prev:true:10/15",
            steps(createView(alphabet, ranges(0 to 10, 5 to 15), 20), "PPPP")
        )
    }

    // A point inside a range is a widget piece that starts before the previous piece ends, so
    // upstream's RangeSetBuilder puts it in a second layer, visited after the first. Expected
    // strings from @codemirror/lint 6.9.5 (review-382/layer398-upstream.txt).

    @Test
    fun pointInsideARangeIsVisitedInItsOwnLayer() {
        assertEquals(
            "next:true:5/7 next:true:7/10 next:true:5/7 prev:true:7/10 prev:true:7/7 prev:false:7/7",
            steps(createView(alphabet, ranges(5 to 10, 7 to 7)), "NNNPPP")
        )
        assertEquals(
            "next:true:0/4 next:true:5/7 next:true:7/10 prev:true:7/7 prev:true:0/4 prev:true:7/10",
            steps(createView(alphabet, ranges(0 to 4, 5 to 10, 7 to 7), 20), "NNNPPP")
        )
    }

    @Test
    fun duplicateDiagnosticsAreOnePiece() {
        assertEquals(
            "next:true:5/8 next:false:5/8 next:false:5/8 next:false:5/8",
            steps(createView(alphabet, ranges(5 to 8, 5 to 8)), "NNNN")
        )
    }

    @Test
    fun lineBreakOnlyDiagnosticIsAPoint() {
        assertEquals(
            "next:true:3/3 next:false:3/3 next:false:3/3 next:false:3/3",
            steps(createView("abc\ndef\nghi", ranges(3 to 4)), "NNNN")
        )
    }

    @Test
    fun adjacentAndUnsortedDiagnosticsCycle() {
        assertEquals(
            "next:true:0/5 next:true:5/10 next:true:0/5 next:true:5/10",
            steps(createView(alphabet, ranges(0 to 5, 5 to 10)), "NNNN")
        )
        assertEquals(
            "prev:true:5/10 prev:true:0/5 prev:true:5/10 prev:true:0/5",
            steps(createView(alphabet, ranges(0 to 5, 5 to 10), 12), "PPPP")
        )
        assertEquals(
            "next:true:5/8 next:true:15/18 next:true:5/8 next:true:15/18",
            steps(createView(alphabet, ranges(15 to 18, 5 to 8)), "NNNN")
        )
        assertEquals(
            "next:true:1/2 next:true:6/6 next:true:1/2 next:true:6/6",
            steps(createView("abcdef", ranges(6 to 6, 1 to 2)), "NNNN")
        )
    }

    @Test
    fun selectionDirectionUsesItsRange() {
        val two = ranges(5 to 8, 15 to 18)
        assertEquals("next:true:15/18", steps(createView(alphabet, two, 6, 16), "N"))
        assertEquals("next:true:15/18", steps(createView(alphabet, two, 16, 6), "N"))
        assertEquals("prev:true:5/8", steps(createView(alphabet, two, 6, 16), "P"))
        assertEquals("prev:true:15/18", steps(createView(alphabet, two, 8, 5), "P"))
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
