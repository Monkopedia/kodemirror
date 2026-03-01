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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.view.EditorView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LintCommandsTest {

    private fun createView(
        doc: String,
        diagnostics: List<Diagnostic>,
        cursor: Int = 0
    ): EditorView {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(cursor),
                extensions = ExtensionList(listOf(lintState, lintPanelOpen))
            )
        )
        val view = EditorView(state)
        view.dispatch(
            TransactionSpec(
                effects = listOf(setDiagnosticsEffect.of(diagnostics))
            )
        )
        return view
    }

    @Test
    fun nextDiagnosticJumpsToNext() {
        val diags = listOf(
            Diagnostic(5, 8, Severity.ERROR, "first"),
            Diagnostic(15, 18, Severity.WARNING, "second")
        )
        val view = createView("abcdefghijklmnopqrstuvwxyz", diags, cursor = 0)
        assertTrue(nextDiagnostic(view))
        assertEquals(5, view.state.selection.main.head)
    }

    @Test
    fun previousDiagnosticJumpsToPrevious() {
        val diags = listOf(
            Diagnostic(5, 8, Severity.ERROR, "first"),
            Diagnostic(15, 18, Severity.WARNING, "second")
        )
        val view = createView("abcdefghijklmnopqrstuvwxyz", diags, cursor = 20)
        assertTrue(previousDiagnostic(view))
        assertEquals(15, view.state.selection.main.head)
    }

    @Test
    fun nextDiagnosticWrapsAround() {
        val diags = listOf(
            Diagnostic(2, 5, Severity.ERROR, "only")
        )
        val view = createView("abcdefghij", diags, cursor = 8)
        assertTrue(nextDiagnostic(view))
        assertEquals(2, view.state.selection.main.head)
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
