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
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [LintConfig.markerFilter] and [LintConfig.autoPanel] (#382). `lintDisplay(config)` is the
 * counterpart of upstream's `linter(null, config)`. Every expected value was produced by
 * `@codemirror/lint` 6.9.5 on the same input (review-382/optionsD.mjs).
 */
class LintOptionsTest {

    private fun d(from: Int, to: Int, message: String, severity: Severity = Severity.ERROR) =
        Diagnostic(DocPos(from), DocPos(to), severity, message)

    private fun state(vararg extensions: Extension) = EditorState.create(
        EditorStateConfig(
            doc = "hello world".asDoc(),
            extensions = ExtensionList(extensions.toList())
        )
    )

    private fun EditorState.set(diagnostics: List<Diagnostic>) =
        update(TransactionSpec(effects = listOf(setDiagnosticsEffect.of(diagnostics)))).state

    private fun EditorState.change(change: ChangeSpec) =
        update(TransactionSpec(changes = change)).state

    private fun EditorState.dump(): String {
        val parts = mutableListOf<String>()
        forEachDiagnostic(this) { parts.add("${it.message}@${it.from.value}-${it.to.value}") }
        return "count=${diagnosticCount(this)} [${parts.joinToString(",")}]"
    }

    private val EditorState.panel get() = if (field(lintPanelOpen)) "open" else "closed"

    @Test
    fun markerFilterCanDropEveryDiagnostic() {
        val s = state(
            lintDisplay(
                LintConfig(markerFilter = {
                    emptyList()
                })
            )
        ).set(listOf(d(0, 5, "a")))
        assertEquals("count=0 []", s.dump())
    }

    @Test
    fun markerFilterKeepsWhatItReturns() {
        val s = state(
            lintDisplay(
                LintConfig(markerFilter = { ds ->
                    ds.filter {
                        it.severity ==
                            Severity.ERROR
                    }
                })
            )
        )
            .set(listOf(d(0, 5, "e"), d(6, 11, "w", Severity.WARNING)))
        assertEquals("count=1 [e@0-5]", s.dump())
        val mapped = state(
            lintDisplay(
                LintConfig(markerFilter = { ds ->
                    ds.map {
                        it.copy(
                            message =
                            it.message + "!"
                        )
                    }
                })
            )
        )
            .set(listOf(d(0, 2, "a")))
        assertEquals("count=1 [a!@0-2]", mapped.dump())
    }

    @Test
    fun markerFiltersFromSeveralConfigsCompose() {
        val s = state(
            lintDisplay(LintConfig(markerFilter = { ds -> ds.filter { it.message != "a" } })),
            lintDisplay(LintConfig(markerFilter = { ds -> ds.filter { it.message != "b" } }))
        ).set(listOf(d(0, 2, "a"), d(3, 5, "b"), d(6, 8, "c")))
        assertEquals("count=1 [c@6-8]", s.dump())
    }

    @Test
    fun markerFilterIsNotReappliedWhenMapping() {
        val s = state(
            lintDisplay(
                LintConfig(markerFilter = { ds ->
                    ds.filter {
                        it.from >
                            DocPos.ZERO
                    }
                })
            )
        )
            .set(listOf(d(0, 2, "a"), d(6, 8, "b")))
            .change(ChangeSpec.Single(DocPos(0), insert = InsertContent.StringContent("zz")))
        assertEquals("count=1 [b@8-10]", s.dump())
    }

    @Test
    fun gutterIgnoresTheLinterMarkerFilter() {
        val s = state(
            lintDisplay(
                LintConfig(markerFilter = {
                    emptyList()
                })
            ),
            lintGutter()
        ).set(listOf(d(0, 5, "a")))
        assertEquals("count=0 []", s.dump())
        assertEquals(1, s.field(lintGutterState).diagnostics.size)
    }

    @Test
    fun autoPanelOpensAndClosesWithTheDiagnostics() {
        var s = state(lintDisplay(LintConfig(autoPanel = true))).set(listOf(d(0, 5, "a")))
        assertEquals("open", s.panel)
        s = s.set(emptyList())
        assertEquals("closed", s.panel)
    }

    @Test
    fun autoPanelClosesWhenAnEditRemovesTheLastDiagnostic() {
        val all = state(lintDisplay(LintConfig(autoPanel = true)))
            .set(listOf(d(0, 5, "a")))
            .change(ChangeSpec.Single(DocPos(0), DocPos(6)))
        assertEquals("closed count=0 []", "${all.panel} ${all.dump()}")
        val some = state(lintDisplay(LintConfig(autoPanel = true)))
            .set(listOf(d(0, 5, "a"), d(6, 11, "b")))
            .change(ChangeSpec.Single(DocPos(0), DocPos(6)))
        assertEquals("open count=1 [b@0-5]", "${some.panel} ${some.dump()}")
    }

    @Test
    fun panelStaysClosedWithoutAutoPanel() {
        assertEquals("closed", state(lintDisplay()).set(listOf(d(0, 5, "a"))).panel)
    }

    @Test
    fun autoPanelCountsDiagnosticsBeforeTheMarkerFilter() {
        val s = state(lintDisplay(LintConfig(autoPanel = true, markerFilter = { emptyList() })))
            .set(listOf(d(0, 5, "a")))
        assertEquals("open count=0 []", "${s.panel} ${s.dump()}")
    }

    @Test
    fun autoPanelFromAnyConfigApplies() {
        val s = state(
            lintDisplay(),
            lintDisplay(LintConfig(autoPanel = true))
        ).set(listOf(d(0, 5, "a")))
        assertEquals("open", s.panel)
    }
}
