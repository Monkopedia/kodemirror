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

import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.decorations
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.logException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Effect used to update the set of diagnostics. */
val setDiagnosticsEffect: StateEffectType<List<Diagnostic>> = StateEffect.define()

/** Internal effect to signal forced linting. */
internal val forceLintEffect: StateEffectType<Unit> = StateEffect.define()

/** Internal state holding diagnostics and their decorations. */
internal class LintStateValue(val diagnostics: List<Diagnostic>, val decorations: DecorationSet) {
    companion object {
        val empty = LintStateValue(emptyList(), RangeSet.empty())

        fun build(
            diagnostics: List<Diagnostic>,
            docLength: Int,
            theme: EditorTheme
        ): LintStateValue {
            if (diagnostics.isEmpty()) return empty
            val docEnd = DocPos(docLength)
            val sorted = diagnostics.sortedBy { it.from }
            val builder = RangeSetBuilder<Decoration>()
            for (diag in sorted) {
                val from = maxOf(DocPos.ZERO, minOf(diag.from, docEnd))
                val to = maxOf(from, minOf(diag.to, docEnd))
                if (from < to) {
                    val color = when (diag.severity) {
                        Severity.HINT -> theme[lintHintColor]
                        Severity.INFO -> theme[lintInfoColor]
                        Severity.WARNING -> theme[lintWarningColor]
                        Severity.ERROR -> theme[lintErrorColor]
                    }
                    builder.add(
                        from,
                        to,
                        Decoration.mark(
                            MarkDecorationSpec(
                                style = SpanStyle(
                                    background = color.copy(alpha = 0.2f)
                                )
                            )
                        )
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
            val theme = tr.startState.facet(editorTheme)
            var result = value
            for (effect in tr.effects) {
                val diagEffect = effect.asType(setDiagnosticsEffect)
                if (diagEffect != null) {
                    result = LintStateValue.build(
                        diagEffect.value,
                        tr.newDoc.length,
                        theme
                    )
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
                result = LintStateValue.build(mapped, tr.newDoc.length, theme)
            }
            result
        },
        provide = { field ->
            decorations.from(field) { it.decorations }
        }
    )
)

/** Set diagnostics on an editor view. */
fun setDiagnostics(view: EditorSession, diagnostics: List<Diagnostic>) {
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

/**
 * Make a pending lint run happen right away instead of waiting for the delay. Does nothing when
 * no run is pending, as upstream `forceLinting` does.
 */
fun forceLinting(view: EditorSession) {
    view.dispatch(
        TransactionSpec(effects = listOf(forceLintEffect.of(Unit)))
    )
}

/** One `linter()`/`lintDisplay()` contribution to [lintConfig]. */
internal class LintConfigInput(val source: SuspendLintSource?, val config: LintConfig)

private typealias DiagnosticFilter = (List<Diagnostic>) -> List<Diagnostic>

/** The combination of every [LintConfigInput] in the state, like upstream's `lintConfig`. */
internal class CombinedLintConfig(
    val sources: List<SuspendLintSource>,
    val delay: Long,
    val tooltipFilter: DiagnosticFilter?
)

/** Apply [a], then [b], like upstream's `combineFilter`. */
private fun combineFilters(a: DiagnosticFilter?, b: DiagnosticFilter?): DiagnosticFilter? =
    if (a == null) {
        b
    } else if (b == null) {
        a
    } else {
        { diagnostics -> b(a(diagnostics)) }
    }

private val defaultDelay = LintConfig().delay

/**
 * Facet collecting every lint source and its configuration, so that all sources lint together and
 * their diagnostics are dispatched as one list (upstream `lintConfig`).
 *
 * Upstream combines only the options a config sets, taking the largest delay. [LintConfig] cannot
 * tell an explicit `delay = 750` from the default, so a config left at the default delay does not
 * take part, which keeps `linter(source)` from overriding another linter's shorter delay.
 */
internal val lintConfig: Facet<LintConfigInput, CombinedLintConfig> = Facet.define(
    combine = { inputs ->
        CombinedLintConfig(
            sources = inputs.mapNotNull { it.source },
            delay =
            inputs.map { it.config.delay }.filter { it != defaultDelay }.maxOrNull()
                ?: defaultDelay,
            tooltipFilter = inputs.fold(null as DiagnosticFilter?) { acc, input ->
                combineFilters(acc, input.config.tooltipFilter)
            }
        )
    }
)

/** How long a source result waits for the remaining sources before the partial list is shown. */
private const val BATCH_TIMEOUT_MS = 200L

/**
 * Runs every configured lint source once the document has been idle for the configured delay, and
 * dispatches their combined diagnostics (upstream `lintPlugin`).
 */
internal class LintPlugin(private val view: EditorSession) : PluginValue {
    private val job = SupervisorJob(view.coroutineScope.coroutineContext[Job])
    private val scope = CoroutineScope(view.coroutineScope.coroutineContext + job)
    private var lintJob: Job? = null

    /** True while a run is scheduled but its delay has not yet passed. */
    private var pending = false

    init {
        schedule()
    }

    override fun update(update: ViewUpdate) {
        val forced = update.transactions.any { tr ->
            tr.effects.any { it.asType(forceLintEffect) != null }
        }
        if (forced) {
            if (pending) launchRun(waitMs = 0)
            return
        }
        if (update.docChanged ||
            update.state.facet(lintConfig) != update.startState.facet(lintConfig)
        ) {
            schedule()
        }
    }

    private fun schedule() {
        launchRun(waitMs = view.state.facet(lintConfig).delay)
    }

    private fun launchRun(waitMs: Long) {
        lintJob?.cancel()
        pending = true
        lintJob = scope.launch {
            delay(waitMs)
            pending = false
            runSources()
        }
    }

    private suspend fun runSources() = coroutineScope {
        val state = view.state
        val sources = state.facet(lintConfig).sources
        if (sources.isEmpty()) return@coroutineScope
        val results = Channel<List<Diagnostic>>(Channel.UNLIMITED)
        for (source in sources) {
            launch {
                try {
                    results.send(source(view))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logException(view.state, e)
                }
            }
        }
        val sink = { collected: List<List<Diagnostic>> ->
            if (view.state.doc === state.doc) {
                view.dispatch(
                    TransactionSpec(effects = listOf(setDiagnosticsEffect.of(collected.flatten())))
                )
            }
        }
        // Like upstream batchResults: dispatch once every source has answered, or, if the rest
        // stall for BATCH_TIMEOUT_MS after an answer, show what has arrived so far.
        val collected = mutableListOf<List<Diagnostic>>()
        while (collected.size < sources.size) {
            val next = if (collected.isEmpty()) {
                results.receive()
            } else {
                withTimeoutOrNull(BATCH_TIMEOUT_MS) { results.receive() }
                    ?: run {
                        sink(collected)
                        results.receive()
                    }
            }
            collected += next
        }
        sink(collected)
    }

    override fun destroy() {
        job.cancel()
    }
}

internal val lintPlugin: ViewPlugin<LintPlugin> = ViewPlugin.define(create = { LintPlugin(it) })
