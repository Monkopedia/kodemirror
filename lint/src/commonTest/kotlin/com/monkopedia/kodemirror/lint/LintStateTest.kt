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

class LintStateTest {

    private fun createState(doc: String): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            extensions = lintState
        )
    )

    private fun setDiags(state: EditorState, diagnostics: List<Diagnostic>): EditorState {
        val tr = state.update(
            TransactionSpec(
                effects = listOf(setDiagnosticsEffect.of(diagnostics))
            )
        )
        return tr.state
    }

    private fun collectDiags(state: EditorState): List<Diagnostic> {
        val result = mutableListOf<Diagnostic>()
        forEachDiagnostic(state) { result.add(it) }
        return result
    }

    @Test
    fun canStoreDiagnostics() {
        val state = createState("hello world")
        val diags = listOf(
            Diagnostic(DocPos(0), DocPos(5), Severity.ERROR, "bad word"),
            Diagnostic(DocPos(6), DocPos(11), Severity.WARNING, "suspicious")
        )
        val updated = setDiags(state, diags)
        val stored = collectDiags(updated)
        assertEquals(2, stored.size)
        assertEquals("bad word", stored[0].message)
        assertEquals("suspicious", stored[1].message)
    }

    @Test
    fun mapsDiagnosticsThroughDeletions() {
        val state = createState("abcdefghij")
        val withDiags = setDiags(
            state,
            listOf(Diagnostic(DocPos(5), DocPos(8), Severity.ERROR, "err"))
        )
        val tr = withDiags.update(
            TransactionSpec(
                changes = ChangeSpec.Single(DocPos(2), DocPos(5))
            )
        )
        val mapped = collectDiags(tr.state)
        assertEquals(1, mapped.size)
        assertEquals(DocPos(2), mapped[0].from)
        assertEquals(DocPos(5), mapped[0].to)
    }

    @Test
    fun doesNotIncludeNewTextInDiagnostics() {
        val state = createState("abcdefghij")
        val withDiags = setDiags(
            state,
            listOf(Diagnostic(DocPos(3), DocPos(6), Severity.WARNING, "warn"))
        )
        val tr = withDiags.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    DocPos(3),
                    DocPos(3),
                    InsertContent.StringContent("XYZ")
                )
            )
        )
        val mapped = collectDiags(tr.state)
        assertEquals(1, mapped.size)
        assertEquals(DocPos(6), mapped[0].from)
        assertEquals(DocPos(9), mapped[0].to)
    }

    @Test
    fun storesOverlappingDiagnostics() {
        val state = createState("hello world")
        val diags = listOf(
            Diagnostic(DocPos(0), DocPos(7), Severity.ERROR, "first"),
            Diagnostic(DocPos(3), DocPos(11), Severity.WARNING, "second")
        )
        val updated = setDiags(state, diags)
        val stored = collectDiags(updated)
        assertEquals(2, stored.size)
    }

    @Test
    fun storesEmptyZeroWidthDiagnostics() {
        val state = createState("hello")
        val diags = listOf(
            Diagnostic(DocPos(3), DocPos(3), Severity.INFO, "point1"),
            Diagnostic(DocPos(3), DocPos(3), Severity.INFO, "point2")
        )
        val updated = setDiags(state, diags)
        assertEquals(2, diagnosticCount(updated))
    }

    @Test
    fun diagnosticCountReturnsCorrectCount() {
        val state = createState("hello world")
        val diags = listOf(
            Diagnostic(DocPos(0), DocPos(3), Severity.ERROR, "a"),
            Diagnostic(DocPos(4), DocPos(7), Severity.WARNING, "b"),
            Diagnostic(DocPos(8), DocPos(11), Severity.INFO, "c")
        )
        val updated = setDiags(state, diags)
        assertEquals(3, diagnosticCount(updated))
    }

    @Test
    fun emptyStateHasNoDiagnostics() {
        val state = createState("hello")
        assertEquals(0, diagnosticCount(state))
        val collected = collectDiags(state)
        assertEquals(0, collected.size)
    }
}
