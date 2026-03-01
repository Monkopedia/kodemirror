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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.decorations
import kotlin.math.min

/** Effect used to update the set of diagnostics. */
val setDiagnosticsEffect: StateEffectType<List<Diagnostic>> = StateEffect.define()

/** Internal effect to signal forced linting. */
internal val forceLintEffect: StateEffectType<Unit> = StateEffect.define()

private val hintStyle = SpanStyle(
    background = Color(0x1A2196F3)
)

private val infoStyle = SpanStyle(
    background = Color(0x1A4CAF50)
)

private val warningStyle = SpanStyle(
    background = Color(0x33FF9800)
)

private val errorStyle = SpanStyle(
    background = Color(0x33F44336)
)

/** Internal state holding diagnostics and their decorations. */
internal class LintStateValue(
    val diagnostics: List<Diagnostic>,
    val decorations: DecorationSet
) {
    companion object {
        val empty = LintStateValue(emptyList(), RangeSet.empty())

        fun build(diagnostics: List<Diagnostic>, docLength: Int): LintStateValue {
            if (diagnostics.isEmpty()) return empty
            val sorted = diagnostics.sortedBy { it.from }
            val builder = RangeSetBuilder<Decoration>()
            for (diag in sorted) {
                val from = diag.from.coerceIn(0, docLength)
                val to = min(diag.to, docLength).coerceAtLeast(from)
                if (from < to) {
                    val style = when (diag.severity) {
                        Severity.HINT -> hintStyle
                        Severity.INFO -> infoStyle
                        Severity.WARNING -> warningStyle
                        Severity.ERROR -> errorStyle
                    }
                    val cssClass = diag.markClass ?: "cm-lint-${diag.severity.name.lowercase()}"
                    builder.add(
                        from,
                        to,
                        Decoration.mark(MarkDecorationSpec(style = style, cssClass = cssClass))
                    )
                }
            }
            return LintStateValue(sorted, builder.finish())
        }
    }
}

/** State field tracking diagnostics and their decorations. */
internal val lintState: StateField<LintStateValue> = StateField.define(
    StateFieldSpec(
        create = { LintStateValue.empty },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                val diagEffect = effect.asType(setDiagnosticsEffect)
                if (diagEffect != null) {
                    result = LintStateValue.build(diagEffect.value, tr.newDoc.length)
                }
            }
            if (result === value && tr.docChanged) {
                // Map existing diagnostics through changes
                val mapped = result.diagnostics.mapNotNull { diag ->
                    val newFrom = tr.changes.mapPos(diag.from, 1)
                    val newTo = tr.changes.mapPos(diag.to, -1)
                    if (newFrom < newTo) {
                        diag.copy(from = newFrom, to = newTo)
                    } else {
                        null
                    }
                }
                result = LintStateValue.build(mapped, tr.newDoc.length)
            }
            result
        },
        provide = { field ->
            decorations.from(field) { it.decorations }
        }
    )
)

/** Set diagnostics on an editor view. */
fun setDiagnostics(view: EditorView, diagnostics: List<Diagnostic>) {
    view.dispatch(
        TransactionSpec(
            effects = listOf(setDiagnosticsEffect.of(diagnostics))
        )
    )
}

/** Get the total number of diagnostics in the given state. */
fun diagnosticCount(state: EditorState): Int =
    state.field(lintState, require = false)?.diagnostics?.size ?: 0

/** Iterate over all diagnostics in the given state. */
fun forEachDiagnostic(state: EditorState, callback: (Diagnostic) -> Unit) {
    state.field(lintState, require = false)?.diagnostics?.forEach(callback)
}

/** Force the linter to re-run immediately. */
fun forceLinting(view: EditorView) {
    view.dispatch(
        TransactionSpec(effects = listOf(forceLintEffect.of(Unit)))
    )
}

/** Internal linter plugin that debounces and runs the lint source. */
internal class LinterPlugin(
    private val view: EditorView,
    private val source: LintSource,
    private val config: LintConfig
) : PluginValue {
    private var pendingSince: Long = -1L
    private var hasRun = false

    init {
        // Run the linter on creation
        runLinter()
    }

    override fun update(update: ViewUpdate) {
        // Check for force lint effect
        for (tr in update.transactions) {
            for (effect in tr.effects) {
                if (effect.`is`(forceLintEffect)) {
                    runLinter()
                    return
                }
            }
        }

        if (update.docChanged) {
            pendingSince = currentTimeMillis()
        }

        if (pendingSince >= 0) {
            val elapsed = currentTimeMillis() - pendingSince
            if (elapsed >= config.delay) {
                pendingSince = -1L
                runLinter()
            }
        }
    }

    private fun runLinter() {
        val diagnostics = source(view)
        hasRun = true
        view.dispatch(
            TransactionSpec(
                effects = listOf(setDiagnosticsEffect.of(diagnostics))
            )
        )
    }
}

internal fun createLinterPlugin(source: LintSource, config: LintConfig): ViewPlugin<LinterPlugin> =
    ViewPlugin.define(
        create = { view -> LinterPlugin(view, source, config) }
    )

internal fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
