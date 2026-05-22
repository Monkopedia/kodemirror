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
package com.monkopedia.kodemirror.lsp

import com.monkopedia.kodemirror.lint.Severity
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.lsp.Diagnostic as LSPDiagnostic
import com.monkopedia.lsp.DiagnosticSeverity
import com.monkopedia.lsp.IntOrString
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerDiagnosticsTest {
    private fun doc(vararg lines: String): Text = Text.of(lines.toList())

    private fun lspDiagnostic(
        range: Range,
        severity: DiagnosticSeverity? = null,
        message: String = "boom",
        source: String? = null,
        code: IntOrString? = null
    ): LSPDiagnostic = LSPDiagnostic(
        range = range,
        severity = severity,
        message = message,
        source = source,
        code = code
    )

    @Test
    fun severityMapsAllLevels() {
        assertEquals(Severity.ERROR, mapSeverity(DiagnosticSeverity.ERROR))
        assertEquals(Severity.WARNING, mapSeverity(DiagnosticSeverity.WARNING))
        assertEquals(Severity.INFO, mapSeverity(DiagnosticSeverity.INFORMATION))
        assertEquals(Severity.HINT, mapSeverity(DiagnosticSeverity.HINT))
    }

    @Test
    fun missingSeverityDefaultsToError() {
        assertEquals(Severity.ERROR, mapSeverity(null))
    }

    @Test
    fun rangeMapsToOffsets() {
        val d = doc("hello", "world")
        // line 1, characters 1..3 => offsets 7..9 ("or" in "world").
        val diag = lspDiagnostic(
            range = Range(start = Position(1u, 1u), end = Position(1u, 3u)),
            severity = DiagnosticSeverity.WARNING
        )
        val mapped = mapDiagnostic(diag, d)
        assertEquals(DocPos(7), mapped.from)
        assertEquals(DocPos(9), mapped.to)
        assertEquals(Severity.WARNING, mapped.severity)
        assertEquals("boom", mapped.message)
    }

    @Test
    fun outOfRangePositionsClampToDocument() {
        val d = doc("hi")
        val diag = lspDiagnostic(
            range = Range(start = Position(0u, 0u), end = Position(9u, 9u)),
            severity = DiagnosticSeverity.ERROR
        )
        val mapped = mapDiagnostic(diag, d)
        assertEquals(DocPos(0), mapped.from)
        assertEquals(DocPos(2), mapped.to)
    }

    @Test
    fun invertedRangeIsNormalized() {
        val d = doc("hello")
        // start after end; mapping should produce from <= to.
        val diag = lspDiagnostic(
            range = Range(start = Position(0u, 4u), end = Position(0u, 1u))
        )
        val mapped = mapDiagnostic(diag, d)
        assertEquals(DocPos(1), mapped.from)
        assertEquals(DocPos(4), mapped.to)
    }

    @Test
    fun sourceIsCarried() {
        val d = doc("abc")
        val diag = lspDiagnostic(
            range = Range(start = Position(0u, 0u), end = Position(0u, 1u)),
            source = "rustc"
        )
        val mapped = mapDiagnostic(diag, d)
        assertEquals("rustc", mapped.source)
    }

    @Test
    fun codeIsFoldedIntoMessage() {
        val d = doc("abc")
        val withInt = mapDiagnostic(
            lspDiagnostic(
                range = Range(start = Position(0u, 0u), end = Position(0u, 1u)),
                message = "unused",
                code = IntOrString.IntValue(42)
            ),
            d
        )
        assertEquals("unused [42]", withInt.message)

        val withString = mapDiagnostic(
            lspDiagnostic(
                range = Range(start = Position(0u, 0u), end = Position(0u, 1u)),
                message = "unused",
                code = IntOrString.StringValue("E0425")
            ),
            d
        )
        assertEquals("unused [E0425]", withString.message)

        val withNone = mapDiagnostic(
            lspDiagnostic(
                range = Range(start = Position(0u, 0u), end = Position(0u, 1u)),
                message = "unused"
            ),
            d
        )
        assertEquals("unused", withNone.message)
        assertNull(withNone.source)
    }

    @Test
    fun mapDiagnosticsMapsList() {
        val d = doc("hello")
        val mapped = mapDiagnostics(
            listOf(
                lspDiagnostic(Range(Position(0u, 0u), Position(0u, 2u))),
                lspDiagnostic(Range(Position(0u, 3u), Position(0u, 5u)))
            ),
            d
        )
        assertEquals(2, mapped.size)
        assertEquals(DocPos(0), mapped[0].from)
        assertEquals(DocPos(3), mapped[1].from)
    }
}
