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
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.MarkDecoration
import com.monkopedia.kodemirror.view.WidgetDecoration
import com.monkopedia.kodemirror.view.editorTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    /** The decorations the lint state draws, as `from..to` to the decoration. */
    private fun decorationsOf(state: EditorState): List<Pair<IntRange, Decoration>> {
        val result = mutableListOf<Pair<IntRange, Decoration>>()
        state.field(lintState).decorations.between(DocPos.ZERO, DocPos(state.doc.length)) {
                from,
                to,
                d
            ->
            result.add(from..to to d)
            null
        }
        return result
    }

    @Test
    fun pointAndLineBreakDiagnosticsAreWidgetsAndRangesAreMarks() {
        // As upstream LintState.init: a point, or a short range covering only line breaks, has
        // nothing to underline and gets a DiagnosticWidget; anything else gets a mark (#397).
        val state = setDiags(
            createState("ab\n\ncd"),
            listOf(
                Diagnostic(DocPos(1), DocPos(1), Severity.WARNING, "point"),
                Diagnostic(DocPos(2), DocPos(4), Severity.ERROR, "line breaks"),
                Diagnostic(DocPos(4), DocPos(6), Severity.INFO, "text")
            )
        )
        val decos = decorationsOf(state)
        assertEquals(listOf(1..1, 2..2, 4..6), decos.map { it.first })
        val (point, lineBreaks, text) = decos.map { it.second }
        assertEquals(
            Severity.WARNING,
            ((point as WidgetDecoration).spec.widget as DiagnosticWidget).severity
        )
        assertEquals(
            Severity.ERROR,
            ((lineBreaks as WidgetDecoration).spec.widget as DiagnosticWidget).severity
        )
        assertTrue(text is MarkDecoration)
    }

    @Test
    fun pointDiagnosticKeepsItsWidgetAcrossEdits() {
        val state = setDiags(
            createState("hello"),
            listOf(Diagnostic(DocPos(3), DocPos(3), Severity.ERROR, "point"))
        )
        val edited = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(DocPos(0), DocPos(0), InsertContent.StringContent("> "))
            )
        ).state
        val decos = decorationsOf(edited)
        assertEquals(listOf(5..5), decos.map { it.first })
        assertTrue(decos.single().second is WidgetDecoration)
    }

    /**
     * Diagnostics from a compact spec: `e5` is an error at 5, `w2-4` a warning from 2 to 4
     * (`h`int, `i`nfo, `w`arning, `e`rror).
     */
    private fun diags(spec: String): List<Diagnostic> = spec.split(" ").map { token ->
        val severity = Severity.entries.first { it.name.lowercase()[0] == token[0] }
        val (from, to) = token.drop(1).split("-").map { it.toInt() }.let { it[0] to it.last() }
        Diagnostic(DocPos(from), DocPos(to), severity, token)
    }

    /**
     * The decorations as `from-to:kind:severity`, the severity read back from a widget's
     * [DiagnosticWidget] or a mark's background color. Sorted, since the order `between` visits
     * them in follows the decoration sides, which differ from upstream's and change nothing drawn.
     */
    private fun decorationDump(doc: String, spec: String): String {
        val state = setDiags(createState(doc), diags(spec))
        val theme = state.facet(editorTheme)
        return decorationsOf(state)
            .sortedWith(compareBy({ it.first.first }, { it.first.last }))
            .joinToString(" ") { (range, deco) ->
                val kind = when (deco) {
                    is WidgetDecoration ->
                        "widget:" + (deco.spec.widget as DiagnosticWidget).severity
                    is MarkDecoration -> "mark:" + Severity.entries.single {
                        theme.lintColor(it).copy(alpha = 0.2f) == deco.spec.style?.background
                    }
                    else -> error("unexpected $deco")
                }
                "${range.first}-${range.last}:${kind.lowercase()}"
            }
    }

    @Test
    fun overlappingPointsAreOneMarkerOfTheHighestSeverity() {
        // Upstream LintState.init builds one widget per piece, coloured by maxSeverity of every
        // diagnostic covering it (#419). Expected from @codemirror/lint 6.9.5 (probe419.mjs), sorted.
        assertEquals("5-5:widget:error", decorationDump("hello world", "e5 w5"))
        assertEquals("5-5:widget:error", decorationDump("hello world", "w5 e5"))
        assertEquals("5-5:widget:info", decorationDump("hello world", "h5 i5"))
        assertEquals("5-5:widget:warning", decorationDump("hello world", "i5 w5 h5"))
        assertEquals(
            "5-5:widget:error 6-6:widget:warning",
            decorationDump("hello world", "e5 w6")
        )
    }

    @Test
    fun pointsTouchingRangesTakeTheirSeverity() {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        assertEquals("5-5:widget:error 5-8:mark:error", decorationDump(alphabet, "w5 e5-8"))
        assertEquals(
            "5-7:mark:warning 7-7:widget:error 7-10:mark:warning",
            decorationDump(alphabet, "w5-10 e7")
        )
        assertEquals("5-10:mark:error 10-10:widget:warning", decorationDump(alphabet, "e5-10 w10"))
        // Upstream splits these into two pieces at 2, so it draws two markers there too.
        assertEquals("2-2:widget:error 2-2:widget:warning", decorationDump("ab\n\ncd", "w2-4 e2"))
        // A point at the end, after a range reaching the end, gets no piece upstream.
        assertEquals("3-6:mark:error", decorationDump("abcdef", "w6 e3-6"))
    }

    @Test
    fun overlappingRangesAreSplitIntoMarksOfTheHighestSeverity() {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        assertEquals("5-8:mark:error", decorationDump(alphabet, "w5-8 e5-8"))
        assertEquals(
            "0-5:mark:warning 5-10:mark:error 10-15:mark:error",
            decorationDump(alphabet, "w0-10 e5-15")
        )
        assertEquals(
            "0-5:mark:error 5-8:mark:error 8-20:mark:error",
            decorationDump(alphabet, "e0-20 w5-8")
        )
        assertEquals(
            "0-5:mark:hint 5-8:mark:info 8-20:mark:hint",
            decorationDump(alphabet, "h0-20 i5-8")
        )
        assertEquals("0-5:mark:info 5-10:mark:warning", decorationDump(alphabet, "i0-5 w5-10"))
        assertEquals(
            "0-4:mark:hint 4-8:mark:info 8-12:mark:warning 12-16:mark:warning " +
                "16-20:mark:warning",
            decorationDump(alphabet, "h0-12 i4-16 w8-20")
        )
        assertEquals(
            "2-3:mark:warning 3-4:mark:error 4-5:mark:warning",
            decorationDump("hello", "w2-9 e3-4")
        )
        assertEquals("5-7:mark:error 7-12:mark:warning", decorationDump(alphabet, "w5-12 e5-7"))
        assertEquals(
            "0-1:mark:hint 1-1:widget:error 1-3:mark:hint 3-4:mark:warning 6-6:widget:info " +
                "8-9:mark:error 9-9:widget:error 9-11:mark:error",
            decorationDump("ab\ncd\n\nefgh", "h0-4 e1 w3-4 i6-7 e8-11 w9")
        )
    }

    @Test
    fun aRangeOfLineBreaksIsAWidgetOnlyWhereADiagnosticEnds() {
        // The error spans 5-7, but its piece 5-7 is not a whole diagnostic's range, so the
        // line breaks there get a widget only because the error ends at 7. Expected from
        // @codemirror/lint 6.9.5, sorted.
        assertEquals(
            "4-5:mark:info 5-5:widget:error 7-8:mark:info",
            decorationDump("a\n\nb\n\n\n\nc\nd", "i4-8 e5-7")
        )
    }

    @Test
    fun anInvertedDiagnosticIsAPointAtItsStart() {
        // Upstream draws an inverted diagnostic alone as the point at its start, but loops
        // forever splitting one that overlaps another. The port stores it as that point, which
        // gives what upstream draws for e5-5 e2-6.
        assertEquals("5-5:widget:error", decorationDump("abcdefghij", "e5-3"))
        assertEquals(
            "2-5:mark:error 5-5:widget:error 5-6:mark:error",
            decorationDump("a\nbcdef", "e5-3 e2-6")
        )
    }
}
